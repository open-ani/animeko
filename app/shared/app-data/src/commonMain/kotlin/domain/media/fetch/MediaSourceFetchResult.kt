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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.cancellableCoroutineScope

/**
 * 表示一个数据源 [MediaSource] 的线程安全的查询结果.
 */
interface MediaSourceFetchResult {
    val instanceId: String
    val mediaSourceId: String
    val sourceInfo: MediaSourceInfo
    val kind: MediaSourceKind

    val state: StateFlow<MediaSourceFetchState>

    /**
     * 从该数据源查询到的结果.
     *
     * ## 初始值为 [emptyList]
     *
     * 该 flow 一定至少有一个元素, [emptyList]. 第一次调用 [Flow.first] 一定返回 [emptyList].
     *
     * ## 查询是惰性的
     *
     * 只有在 [results] 有 collector 时, 才会开始查询. 当一段时间没有 collector 后, 查询自动停止.
     *
     * 查询结果会 share 在指定的 context.
     */
    val results: Flow<List<Media>>

    /**
     * @suppress soft deprecated
     */
    val resultsIfEnabled
        get() = results

    /**
     * 重新请求获取结果.
     *
     * 若状态已经完成 ([MediaSourceFetchState.Completed]), 将会被重置为 [MediaSourceFetchState.Idle].
     * 即使状态是 [MediaSourceFetchState.Disabled], 也会被重置为 [MediaSourceFetchState.Idle].
     *
     * 若请求已经在进行, 则继续该请求.
     *
     * 基于惰性加载性质, 该方法不会立即触发查询, 只有在 [results] 有 collector 时才会开始查询.
     */
    fun restart()

    /**
     * 使禁用的数据源重新启用.
     *
     * 如果该数据源已经被 [restart] 过了, 此函数不会有任何效果. 否则, 会将状态重置为 [MediaSourceFetchState.Idle].
     * 随后使用 [results] 将会发起查询.
     */
    fun enable()

    /**
     * 当前这一代查询的搜索统计, 数据源不报告时为 `null`. 每次 [restart] 重新计数.
     */
    val searchStats: StateFlow<MediaSourceSearchStats?> get() = NoSearchStats
}

val MediaSourceFetchResult.hasCompletedOrDisabled: Flow<Boolean>
    get() = state.map { it is MediaSourceFetchState.Completed || it is MediaSourceFetchState.Disabled }

/**
 * 挂起当前协程, 直到 [MediaSourceFetchResult.results] 查询完成. 注意, 这并不代表 [MediaSourceFetchResult.results] 会完结.
 *
 * 对于已经禁用的数据源, 这个函数返回空 list.
 *
 * 支持 cancellation.
 */
suspend fun MediaSourceFetchResult.awaitCompletion() {
    if (state.value is MediaSourceFetchState.Disabled) {
        return
    }
    cancellableCoroutineScope {
        resultsIfEnabled.shareIn(this, started = SharingStarted.Eagerly, replay = 1)
        hasCompletedOrDisabled.first { it }
        cancelScope()
    }
}

/**
 * 挂起当前协程, 直到 [MediaSourceFetchResult.results] 查询完成, 然后获取所有查询结果.
 *
 * 对于已经禁用的数据源, 这个函数返回空 list.
 *
 * 注意, 这并不代表 [MediaSourceFetchResult.results] 会完结.
 *
 * 支持 cancellation.
 */
suspend inline fun MediaSourceFetchResult.awaitCompletedResults(): List<Media> {
    awaitCompletion()
    return resultsIfEnabled.first()
}
