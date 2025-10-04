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
import kotlin.time.Duration.Companion.milliseconds

class MediaSourceSubscriptionUpdater(
    private val subscriptions: MediaSourceSubscriptionRepository,
    private val mediaSourceManager: MediaSourceManager,
    private val codecManager: MediaSourceCodecManager,
    private val requester: MediaSourceSubscriptionRequester,
) {
    /**
     * @param force to ignore lastUpdated time
     * @return delay duration to check next time
     */
    suspend fun updateAllOutdated(force: Boolean = false): Duration {
        logger.info { "MediaSourceSubscriptionUpdater.updateAllOutdated" }
        val subscriptions = subscriptions.flow.first()
        val currentTimeMillis = currentTimeMillis()

        for (subscription in subscriptions) {
            fun shouldUpdate(): Boolean {
                if (force) return true
                if (subscription.lastUpdated == null) return true
                return (currentTimeMillis - subscription.lastUpdated.timeMillis).milliseconds > subscription.updatePeriod
            }

            if (!shouldUpdate()) {
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

            try {
                val count = updateSubscription(subscription)
                setResult(count)
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
            } catch (e: Exception) {
                logger.error(e) { "Failed to update subscription ${subscription.url}" }
                setResult(null, UpdateError(e.toString(), null))
            }
        }

        return subscriptions.minOf { subscription -> subscription.updatePeriod }
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
        for ((save, _) in diff.removed) {
            mediaSourceManager.removeInstance(save.instanceId)
        }

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
