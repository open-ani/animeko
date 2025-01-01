/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.utils.coroutines.cancellableCoroutineScope

/**
 * 从多个 [MediaSource] 并行获取 [Media] 的活跃的惰性会话.
 *
 * 只有在 [MediaSourceFetchResult.results] 有 collector 时, 才会开始查询. 当一段时间没有 collector 后, 查询自动停止
 *
 * 在查询完成 [hasCompleted] 后, 该会话自动关闭.
 *
 * 可通过 [MediaFetcher] 创建.
 */
interface MediaFetchSession {
    /**
     * The request used to initiate this session.
     */
    val request: Flow<MediaFetchRequest>

    /**
     * 从各个数据源获取的结果
     */
    val mediaSourceResults: List<MediaSourceFetchResult> // dev notes: see implementation of [MediaSource]s for the IDs.

    /**
     * 从所有数据源聚合的结果. collect [cumulativeResults] 会导致所有数据源开始查询. 持续 collect 以保持查询不被中断.
     * 停止 collect [cumulativeResults] 几秒后, 查询将被中断.
     *
     * ## 各数据源自身结果拥有缓存
     *
     * 每个数据源自己的[结果][MediaSourceFetchResult.results]是共享且有记忆的. 当它查询成功后, 就不会被因为 collect [cumulativeResults] 而重新查询.
     * 但仍然可以通过 [MediaSourceFetchResult.restart] 来手动重新查询.
     *
     * 重新 collect [cumulativeResults], 已经完成的数据源不会重新查询.
     *
     * ### [cumulativeResults] 没有缓存
     *
     * [cumulativeResults] 不是 [SharedFlow]. 每个 collector 都会独立计算.
     * 每次 collect 都会从当前瞬时的结果开始, flow 一定会 emit 一个当前的结果.
     *
     * ## 获取当前瞬时查询结果
     *
     * ```
     * cumulativeResults.first()
     * ```
     *
     * ## 获取全部结果
     *
     * 因为数据源查询可以重试, 该 flow 永远不会完结.
     *
     * 当 [hasCompletedOrDisabled] emit `true` 后, [cumulativeResults] 一定会 emit 所有的查询结果.
     * 因此, 如需获取所有结果, 可以先使用 [awaitCompletion] 等待查询完成, 再 collect [cumulativeResults] 的 [Flow.first].
     * 也可以便捷地使用 [awaitCompletedResults].
     *
     * ## Sanitization
     *
     * The results are post-processed to eliminate duplicated entries from different sources.
     * Hence [cumulativeResults] might emit less values than a merge of all values from [MediaSourceFetchResult.results].
     */
    val cumulativeResults: Flow<List<Media>>

    /**
     * 所有数据源是否都已经完成, 无论是成功还是失败.
     *
     * 注意, collect [hasCompletedOrDisabled], 不会导致 [cumulativeResults] 开始 collect.
     * 也就是说, 必须要先开始 collect [cumulativeResults], [hasCompletedOrDisabled] 才有可能变为 `true`.
     *
     * 注意, 即使 [hasCompletedOrDisabled] 现在为 `true`, 它也可能在未来因为数据源重试, 或者 [request] 变更而变为 `false`.
     * 因此该 flow 永远不会完结.
     */
    val hasCompleted: Flow<CompletedConditions>

    fun restartAll() {
        mediaSourceResults.forEach {
            it.restart()
        }
    }
}

/**
 * 启动所有 [MediaSource] 的查询, 挂起当前协程, 直到所有 [MediaSource] 都查询完成.
 *
 * 支持 cancellation.
 */
suspend fun MediaFetchSession.awaitCompletion(
    onHasCompletedChanged: suspend (completedConditions: CompletedConditions) -> Boolean = { it.allCompleted() }
) {
    cancellableCoroutineScope {
        cumulativeResults.shareIn(this, started = SharingStarted.Eagerly, replay = 1)
        hasCompleted.first { onHasCompletedChanged(it) }
        cancelScope()
    }
}

/**
 * 启动所有 [MediaSource] 的查询, 挂起当前协程, 直到所有 [MediaSource] 都查询完成, 然后获取所有查询结果.
 *
 * 支持 cancellation.
 */
suspend inline fun MediaFetchSession.awaitCompletedResults(): List<Media> {
    awaitCompletion()
    return cumulativeResults.first()
}
