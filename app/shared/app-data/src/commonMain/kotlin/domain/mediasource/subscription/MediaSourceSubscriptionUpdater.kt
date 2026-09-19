/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.subscription

import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.updateMediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceData
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription.UpdateError
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MediaSourceSubscriptionUpdater(
    private val subscriptions: MediaSourceSubscriptionRepository,
    private val mediaSourceManager: MediaSourceManager,
    private val codecManager: MediaSourceCodecManager,
    private val requester: MediaSourceSubscriptionRequester,
    private val getCurrentTimeMillis: () -> Long = { currentTimeMillis() },
) {
    /**
     * 每个订阅连续失败的次数, 只存在于内存中.
     * 进程重启一般意味着网络环境已经变了, 所以重启后从头开始退避, 立即重试一次.
     */
    private val consecutiveFailures = mutableMapOf<String, Int>()

    /**
     * @param force to ignore lastUpdated time
     * @return delay duration to check next time
     */
    suspend fun updateAllOutdated(force: Boolean = false): Duration {
        logger.info { "MediaSourceSubscriptionUpdater.updateAllOutdated" }
        val subscriptions = subscriptions.flow.first()
        val currentTimeMillis = getCurrentTimeMillis()

        var nextDelay: Duration? = null
        fun proposeNextDelay(duration: Duration) {
            nextDelay = nextDelay?.coerceAtMost(duration) ?: duration
        }

        for (subscription in subscriptions) {
            // 上次失败时用短得多的重试间隔: 冷启动时设备网络往往还没就绪, 按 updatePeriod 等待意味着
            // 这期间一个订阅数据源都没有
            val period = failureRetryPeriodOrNull(subscription) ?: subscription.updatePeriod

            fun shouldUpdate(): Boolean {
                if (force) return true
                if (subscription.lastUpdated == null) return true
                return (currentTimeMillis - subscription.lastUpdated.timeMillis).milliseconds > period
            }

            if (!shouldUpdate()) {
                val elapsed = (currentTimeMillis - (subscription.lastUpdated?.timeMillis ?: currentTimeMillis))
                    .milliseconds
                proposeNextDelay((period - elapsed).coerceAtLeast(Duration.ZERO))
                continue
            }

            logger.info { "Updating subscription: ${subscription.url}" }

            suspend fun setResult(count: Int?, error: UpdateError? = null) {
                this.subscriptions.update(subscription.subscriptionId) { old ->
                    old.copy(
                        lastUpdated = MediaSourceSubscription.LastUpdated(
                            currentTimeMillis,
                            mediaSourceCount = count,
                            error = error,
                        ),
                    )
                }
            }

            val success = try {
                val count = updateSubscription(subscription)
                setResult(count)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: RepositoryException) {
                when (e) {
                    is RepositoryAuthorizationException ->
                        setResult(null, UpdateError(e.toString(), ApiFailure.Unauthorized))

                    is RepositoryNetworkException ->
                        setResult(null, UpdateError(e.toString(), ApiFailure.NetworkError))

                    is RepositoryRateLimitedException ->
                        setResult(
                            null,
                            UpdateError("请求过于频繁", null), // TODO: 2024/12/3 use ApiFailure.RateLimited
                        )

                    is RepositoryServiceUnavailableException ->
                        setResult(null, UpdateError(e.toString(), ApiFailure.ServiceUnavailable))

                    is RepositoryUnknownException ->
                        setResult(null, UpdateError(e.toString(), null))

                    is RepositoryRequestError ->
                        setResult(null, UpdateError(e.localizedMessage, null))
                }
                false
            } catch (e: Exception) {
                logger.error(e) { "Failed to update subscription ${subscription.url}" }
                setResult(null, UpdateError(e.toString(), null))
                false
            }

            if (success) {
                consecutiveFailures.remove(subscription.subscriptionId)
                proposeNextDelay(subscription.updatePeriod)
            } else {
                val failures = consecutiveFailures.increment(subscription.subscriptionId)
                val retryPeriod = failureRetryPeriod(failures, subscription.updatePeriod)
                logger.info {
                    "Failed to update subscription ${subscription.url} ($failures consecutive failures), " +
                            "retrying in $retryPeriod"
                }
                proposeNextDelay(retryPeriod)
            }
        }

        return nextDelay
            ?: subscriptions.minOfOrNull { subscription -> subscription.updatePeriod }
            ?: DEFAULT_UPDATE_PERIOD
    }

    /**
     * 若这个订阅上次更新是失败的, 返回本次应当采用的 (短) 重试间隔; 上次成功或从未更新过则返回 `null`.
     */
    private fun failureRetryPeriodOrNull(subscription: MediaSourceSubscription): Duration? {
        val lastUpdated = subscription.lastUpdated ?: return null
        if (lastUpdated.mediaSourceCount != null) return null // 上次成功
        return failureRetryPeriod(
            // 进程刚启动时内存里没有计数, 但持久化的状态说明至少失败过一次, 按第一次重试算
            consecutiveFailures[subscription.subscriptionId] ?: 1,
            subscription.updatePeriod,
        )
    }

    data class ExistingArgument(
        val save: MediaSourceSave,
        val arguments: MediaSourceArguments?,
    )

    class NewArgument(
        val data: ExportedMediaSourceData,
        val deserializedArguments: MediaSourceArguments,
    ) {
        val name get() = deserializedArguments.name
        val factoryId get() = data.factoryId

    }

    @Throws(RepositoryException::class, CancellationException::class)
    private suspend fun updateSubscription(subscription: MediaSourceSubscription): Int {
        // 下载新订阅列表
        val updateData = requester.request(subscription)
        val newArguments = updateData.exportedMediaSourceDataList.mediaSources.mapNotNull {
            runCatching {
                NewArgument(it, codecManager.decode(it))
            }.getOrNull()
        }

        // 获取现有的
        val existing = mediaSourceManager.getListBySubscriptionId(subscriptionId = subscription.subscriptionId)
            .map { save ->
                ExistingArgument(save, deserializeArgumentsOrNull(save))
            }

        // 计算差异
        val diff = calculateDiff(newArguments, existing)
        logger.info { "updateSubscription diff: $diff" }

        // 解决差异
        mediaSourceManager.removeInstances(diff.removed.map { (save, _) -> save.instanceId })

        for (argument in diff.added) {
            val id = Uuid.randomString()
            mediaSourceManager.addInstance(
                id,
                id,
                argument.factoryId,
                MediaSourceConfig(
                    serializedArguments = argument.data.arguments,
                    subscriptionId = subscription.subscriptionId,
                ),
            )
        }

        for ((existing, new) in diff.changed) {
            if (!mediaSourceManager.updateMediaSourceArguments(existing.save.instanceId, new.data.arguments)) {
                logger.error { "Failed to update existing save ${existing.save.instanceId}" }
            }
        }

        // 更新排序, 让本地的排序跟远程一致
        kotlin.run {
            val localList = mediaSourceManager.getListBySubscriptionId(subscription.subscriptionId)
            val sorted = localList
                // 按照远程的顺序排序
                .sortedBy { save ->
                    // factory id 都是 `web-selector`, 没法比较
                    updateData.exportedMediaSourceDataList.mediaSources.indexOfFirst { save.config.serializedArguments == it.arguments }
                }
                .map { it.instanceId }
            mediaSourceManager.partiallyReorderInstances(sorted)
        }

        return updateData.exportedMediaSourceDataList.mediaSources.size
    }

    private fun deserializeArgumentsOrNull(save: MediaSourceSave): MediaSourceArguments? {
        return save.config.serializedArguments?.let {
            try {
                codecManager.deserializeArgument(save.factoryId, it)
            } catch (e: IllegalArgumentException) {
                throw e
            }
        }
    }

    data class Diff(
        val removed: List<ExistingArgument>,
        val added: List<NewArgument>,
        val changed: List<Pair<ExistingArgument, NewArgument>>,
    ) {
        override fun toString(): String {
            return "Diff(removed=${removed.joinToString()})"
        }
    }

    private companion object {
        private val logger = logger<MediaSourceSubscriptionUpdater>()

        private val DEFAULT_UPDATE_PERIOD = 1.hours

        /**
         * 更新失败后的重试间隔, 按连续失败次数递增. 超出这个表之后就一直用最后一项.
         *
         * 头几次很短: 最常见的失败原因是启动头几秒网络还没就绪.
         */
        private val FAILURE_RETRY_PERIODS = listOf(15.seconds, 30.seconds, 1.minutes, 5.minutes, 15.minutes)

        /**
         * @param consecutiveFailures 已经连续失败了几次 (至少 1 次)
         */
        fun failureRetryPeriod(consecutiveFailures: Int, updatePeriod: Duration): Duration =
            FAILURE_RETRY_PERIODS[(consecutiveFailures - 1).coerceIn(0, FAILURE_RETRY_PERIODS.lastIndex)]
                // 正常间隔本身就比重试间隔还短时 (用户自己调过), 没必要更频繁
                .coerceAtMost(updatePeriod)

        fun MutableMap<String, Int>.increment(key: String): Int =
            ((this[key] ?: 0) + 1).also { this[key] = it }

        fun calculateDiff(newArguments: List<NewArgument>, existing: List<ExistingArgument>): Diff {
            val removed = existing.filter { (save, local) ->
                // 新到的里面不包含这个, 说明这个被删除了
                newArguments.none { it.name == local?.name }
            }

            val added = newArguments.filter { it ->
                existing.none { (_, args) -> it.name == args?.name }
            }

            val changed = newArguments.mapNotNull { new ->
                val exi = existing.find { (_, args) -> new.name == args?.name }
                    ?: return@mapNotNull null
                exi to new
            }
            return Diff(removed, added, changed)
        }
    }
}
