/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.subscription

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceData
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription.UpdateError
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.hours

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
                if (!subscription.enabled) return false
                if (force) return true
                if (subscription.lastUpdated == null) return true
                return (currentTimeMillis - subscription.lastUpdated.timeMillis).milliseconds > subscription.updatePeriod
            }

            if (!shouldUpdate()) {
                continue
            }

            updateAndRecordResult(subscription, currentTimeMillis)
        }

        return subscriptions.filter { it.enabled }.minOfOrNull { it.updatePeriod } ?: 1.hours
    }

    /**
     * 立即更新 [subscriptionId] 对应的订阅, 忽略更新周期. 订阅不存在或已禁用时不做任何事.
     */
    suspend fun update(subscriptionId: String) {
        val subscription = subscriptions.flow.first().find { it.subscriptionId == subscriptionId } ?: return
        if (!subscription.enabled) return
        updateAndRecordResult(subscription, currentTimeMillis())
    }

    private suspend fun updateAndRecordResult(subscription: MediaSourceSubscription, currentTimeMillis: Long) {
        logger.info { "Updating subscription: ${subscription.url}" }

        suspend fun setResult(result: UpdateResult?, error: UpdateError? = null) {
            this.subscriptions.update(subscription.subscriptionId) { old ->
                old.copy(
                    lastUpdated = MediaSourceSubscription.LastUpdated(
                        currentTimeMillis,
                        mediaSourceCount = result?.mediaSourceCount,
                        error = error,
                        unsupportedMediaSourceCount = result?.unsupportedMediaSourceCount ?: 0,
                    ),
                )
            }
        }

        try {
            val result = updateSubscription(subscription)
            if (result != null) setResult(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnsupportedSubscriptionSchemaException) {
            setResult(null, UpdateError(e.toString(), null, requiresNewerApp = true))
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

    /**
     * 订阅中的一个数据源. [arguments] 为 `null` 表示这个客户端无法解析它, 例如未知的数据源类型或更高的版本.
     */
    class RemoteEntry(
        val data: ExportedMediaSourceData,
        val arguments: MediaSourceArguments?,
    ) {
        val id: String? get() = data.id

        // 无法解析时也读出名称, 用来对应本地已有的数据源
        val name: String? = arguments?.name
            ?: ((data.arguments as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull

        override fun toString(): String = "RemoteEntry(id=$id, name=$name, supported=${arguments != null})"
    }

    class ExistingArgument(
        val save: MediaSourceSave,
        val arguments: MediaSourceArguments?,
    ) {
        val id: String? get() = save.config.idInSubscription
        val name: String? get() = arguments?.name

        override fun toString(): String = "ExistingArgument(instanceId=${save.instanceId}, id=$id, name=$name)"
    }

    private class UpdateResult(
        val mediaSourceCount: Int,
        val unsupportedMediaSourceCount: Int,
    )

    @Throws(RepositoryException::class, CancellationException::class)
    private suspend fun updateSubscription(subscription: MediaSourceSubscription): UpdateResult? {
        // 下载新订阅列表
        val updateData = requester.request(subscription)
        val remoteEntries = updateData.mediaSources.map { data ->
            val arguments = try {
                codecManager.decode(data)
            } catch (e: Exception) {
                logger.warn(e) { "Unsupported media source in subscription ${subscription.url}: ${data.factoryId} v${data.version}" }
                null
            }
            RemoteEntry(data, arguments)
        }

        var result: UpdateResult? = null
        // Serialize source reconciliation with subscription availability changes. Network I/O stays outside the transaction.
        subscriptions.update(subscription.subscriptionId) { current ->
            if (current.enabled) {
                applyUpdate(current, remoteEntries)
                result = UpdateResult(
                    mediaSourceCount = remoteEntries.size,
                    unsupportedMediaSourceCount = remoteEntries.count { it.arguments == null },
                )
                current.copy(metadata = updateData.metadata)
            } else {
                current
            }
        }
        return result
    }

    private suspend fun applyUpdate(
        subscription: MediaSourceSubscription,
        remoteEntries: List<RemoteEntry>,
    ) {
        // 获取现有的
        val existing = mediaSourceManager.getListBySubscriptionId(subscriptionId = subscription.subscriptionId)
            .map { save ->
                ExistingArgument(save, deserializeArgumentsOrNull(save))
            }

        // 计算差异
        val diff = calculateDiff(remoteEntries, existing)
        logger.info { "updateSubscription diff: $diff" }

        // 解决差异
        mediaSourceManager.removeInstances(diff.removed.map { it.save.instanceId })

        val orderedInstanceIds = mutableListOf<String>()
        for ((remote, local) in diff.matched) {
            when {
                local == null -> {
                    val id = Uuid.randomString()
                    mediaSourceManager.addInstance(
                        id,
                        id,
                        remote.data.factoryId,
                        MediaSourceConfig(
                            serializedArguments = remote.data.arguments,
                            subscriptionId = subscription.subscriptionId,
                            idInSubscription = remote.id,
                        ),
                    )
                    orderedInstanceIds.add(id)
                }

                // 无法解析新的配置, 保留本地已有的, 等用户升级客户端
                remote.arguments == null -> orderedInstanceIds.add(local.save.instanceId)

                else -> {
                    val updated = local.save.config.copy(
                        serializedArguments = remote.data.arguments,
                        idInSubscription = remote.id,
                    )
                    if (!mediaSourceManager.updateConfig(local.save.instanceId, updated)) {
                        logger.error { "Failed to update existing save ${local.save.instanceId}" }
                    }
                    orderedInstanceIds.add(local.save.instanceId)
                }
            }
        }

        // 更新排序, 让本地的排序跟远程一致
        mediaSourceManager.partiallyReorderInstances(orderedInstanceIds)
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
        /**
         * 按订阅中的顺序. 本地没有对应的数据源时为 `null`, 需要新建. 无法解析且本地没有对应的数据源不在其中.
         */
        val matched: List<Pair<RemoteEntry, ExistingArgument?>>,
    )

    internal companion object {
        private val logger = logger<MediaSourceSubscriptionUpdater>()

        internal fun calculateDiff(remoteEntries: List<RemoteEntry>, existing: List<ExistingArgument>): Diff {
            val unclaimed = existing.toMutableList()
            val localByRemote = arrayOfNulls<ExistingArgument>(remoteEntries.size)

            fun claim(predicate: (RemoteEntry, ExistingArgument) -> Boolean) {
                remoteEntries.forEachIndexed { index, remote ->
                    if (localByRemote[index] != null) return@forEachIndexed
                    val local = unclaimed.firstOrNull {
                        it.save.factoryId == remote.data.factoryId && predicate(remote, it)
                    } ?: return@forEachIndexed
                    unclaimed.remove(local)
                    localByRemote[index] = local
                }
            }
            // 先按 id 对应. 名称可能被订阅作者修改, 只在有一方没有 id 时使用
            claim { remote, local -> remote.id != null && remote.id == local.id }
            claim { remote, local -> (remote.id == null || local.id == null) && remote.name != null && remote.name == local.name }

            val matched = remoteEntries.mapIndexedNotNull { index, remote ->
                val local = localByRemote[index]
                if (local == null && remote.arguments == null) return@mapIndexedNotNull null
                remote to local
            }
            // 新订阅里没有对应的, 说明被删除了
            return Diff(removed = unclaimed, matched = matched)
        }
    }
}
