/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.awaitCompletion
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmInline

/**
 * 访问 [MediaSelector] 的自动选择功能
 */
inline val MediaSelector.autoSelect get() = MediaSelectorAutoSelect(this)

/**
 * [MediaSelector] 自动选择功能
 */
@JvmInline
value class MediaSelectorAutoSelect(
    private val mediaSelector: MediaSelector,
) {
    /**
     * 等待所有数据源查询完成, 然后根据用户的偏好设置自动选择.
     *
     * 返回成功选择的 [Media] 对象. 当用户已经手动选择过一个别的 [Media], 或者没有可选的 [Media] 时返回 `null`.
     */
    suspend fun awaitCompletedAndSelectDefault(
        mediaFetchSession: MediaFetchSession,
        preferKind: Flow<MediaSourceKind?> = flowOf(null)
    ): Media? {
        // 等全部加载完成
        mediaFetchSession.awaitCompletion { completedConditions ->
            return@awaitCompletion preferKind.first()?.let {
                completedConditions[it]
            } ?: completedConditions.allCompleted()
        }
        if (mediaSelector.selected.value == null) {
            val selected = mediaSelector.trySelectDefault()
            return selected
        }
        return null
    }

    /**
     * 按数据源排序, 当高优先级的数据源查询完成后立即自动选择它.
     *
     * 返回成功选择的 [Media] 对象. 当用户已经手动选择过一个别的 [Media], 或者没有可选的 [Media] 时返回 `null`.
     *
     * @param fastMediaSourceIdOrder 所有允许这样快速选择的数据源列表. index 越小, 优先级越高.
     */ // #1323
    suspend fun fastSelectSources(
        mediaFetchSession: MediaFetchSession,
        fastMediaSourceIdOrder: List<String>,
        preferKind: Flow<MediaSourceKind?>,
    ): Media? {
        if (preferKind.first() != MediaSourceKind.WEB) {
            return null // 只处理 WEB 类型
        }
        if (fastMediaSourceIdOrder.isEmpty()) {
            return null
        }

        return coroutineScope {
            // 开一个协程开始查询, 因为查询是 lazy 的, 不查询下面的 state 就不会更新
            val job =
                launch(start = CoroutineStart.UNDISPATCHED) { mediaFetchSession.cumulativeResults.collect() }

            val fastSources = mediaFetchSession.mediaSourceResults
                .filter { it.mediaSourceId in fastMediaSourceIdOrder }
                .sortedBy { fastMediaSourceIdOrder.indexOf(it.mediaSourceId) }

            var index = 0 // invariant: fastSources 里序号 index 之前的数据源都已经查询完成. index 是第一个未完成的数据源.

            combine(
                // 每个 source 的 state
                fastSources
                    .map { source ->
                        source.state
                            .onStart {
                                emit(MediaSourceFetchState.Idle) // 保证至少 emit 一个值
                            }
                            .transformWhile {
                                emit(it)
                                // 完成后就不再 collect, 让这个 flow 能 complete
                                it !is MediaSourceFetchState.Completed
                                        && it !is MediaSourceFetchState.Disabled
                            }
                    },
            ) { states ->
                // 注意, 此时可能有多个 source 同时完成 (状态同时变成 Completed). 所以需要遍历检验所有在此刻完成的 sources.

                // 我们只需要从 index 开始按顺序考虑所有已经完成了的 source, 遇到第一个未完成的就停止. 
                // 对于那些位于这个未完成的 source 之后的 source, 即使它们完成了, 我们也不应该选择它们, 所以不用考虑.
                while (index < states.size) {
                    when (states[index]) {
                        is MediaSourceFetchState.Completed,
                        is MediaSourceFetchState.Disabled -> {
                            val selected: Media? = mediaSelector.trySelectFromMediaSources(
                                fastSources.subList(0, index + 1).map { it.mediaSourceId },
                            )

                            if (selected != null) {
                                // selected one
                                return@combine selected // 'returns' to `firstOrNull`
                            }
                            index++
                            continue // 继续判断下一个 source 是否完成了
                        }

                        else -> {
                            break // 找到了一个未完成的 source, 不再继续判断
                        }
                    }
                }
                null
            }.filterNotNull()
                .firstOrNull()
                .also { job.cancel() }
        }
    }

    /**
     * 自动选择第一个 [MediaSourceKind.LocalCache] [Media].
     *
     * 当成功选择了一个 [Media] 时返回它. 若已经选择了一个别的, 或没有 [MediaSourceKind.LocalCache] 类型的 [Media] 供选择, 返回 `null`.
     */
    suspend fun selectCached(
        mediaFetchSession: MediaFetchSession,
        maxAttempts: Int = Int.MAX_VALUE,
    ): Media? {
        val isSuccess = object {
            @Volatile
            var value: Media? = null

            @Volatile
            var attempted = 0
        }
        combine(
            mediaFetchSession.cumulativeResults,
        ) { _ ->
            if (mediaSelector.selected.value != null) {
                // 用户已经选择了
                isSuccess.value = null
                return@combine STOP
            }

            val selected = mediaSelector.trySelectCached()
            if (selected != null) {
                isSuccess.value = selected
                STOP
            } else {
                if (++isSuccess.attempted >= maxAttempts) {
                    // 尝试次数过多
                    STOP
                } else {
                    // 继续等待
                    !STOP
                }
            }
        }.takeWhile { it == !STOP }.collect()
        return isSuccess.value
    }

    // #355 播放时自动启用上次临时启用选择的数据源
    suspend fun autoEnableLastSelected(mediaFetchSession: MediaFetchSession) {
        val lastSelectedId = mediaSelector.mediaSourceId.finalSelected.first()
        val lastSelected = mediaFetchSession.mediaSourceResults.firstOrNull {
            it.mediaSourceId == lastSelectedId
        } ?: return
        lastSelected.enable()
    }
}

private const val STOP = true
