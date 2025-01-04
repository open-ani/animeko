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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.awaitCompletion
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import me.him188.ani.utils.logging.SilentLogger
import me.him188.ani.utils.logging.debug
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * 访问 [MediaSelector] 的自动选择功能
 */
inline val MediaSelector.autoSelect get() = MediaSelectorAutoSelect(this)

/**
 * [MediaSelector] 自动选择功能
 */
class MediaSelectorAutoSelect(
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
     * @param overrideUserSelection 是否覆盖用户选择.
     * 若为 `true`, 则会忽略用户目前的选择, 使用此函数的结果替换选择.
     * 若为 `false`, 如果用户已经选择了一个 media, 则此函数不会做任何事情.
     * @param blacklistMediaIds 黑名单, 这些 media 不会被选择. 如果遇到黑名单中的 media, 将会跳过.
     * @param allowNonPreferredFlow 是否允许选择不满足用户偏好设置的项目. 如果为 `false`, 将只会从 [me.him188.ani.app.domain.media.selector.MediaSelector.preferredCandidatesMedia] 中选择.
     * 如果为 `true`, 则放弃用户偏好, 只根据数据源顺序选择. 自动选择将会挂起直到此 flow emit 第一个值. 如果此 flow 为 empty, 将不会选择任何 media.
     * 该 flow 每 emit 一个新的值, 都会导致重新选择. 例如可以 `flow { emit(false); delay(5.seconds); emit(true) }` 来做到 5 秒后允许选择非偏好数据源并立即选择一次.
     * 当数据源查询完成后, 将停止监控此 flow 的新的值, 函数返回.
     */ // #1323
    suspend fun fastSelectSources(
        mediaFetchSession: MediaFetchSession,
        fastMediaSourceIdOrder: List<String>,
        preferKind: Flow<MediaSourceKind?>,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
        allowNonPreferredFlow: Flow<Boolean> = flowOf(false),
    ): Media? {
        if (preferKind.first() != MediaSourceKind.WEB) {
            return null // 只处理 WEB 类型
        }
        if (fastMediaSourceIdOrder.isEmpty()) {
            return null
        }

        return cancellableCoroutineScope {
            val backgroundTasks = Job()

            // 开一个协程开始查询, 因为查询是 lazy 的, 不查询下面的 state 就不会更新
            launch(
                backgroundTasks,
                start = CoroutineStart.UNDISPATCHED,
            ) {
                mediaFetchSession.cumulativeResults.collect()
            }

            val fastSources = mediaFetchSession.mediaSourceResults
                .filter { it.mediaSourceId in fastMediaSourceIdOrder }
                .sortedBy { fastMediaSourceIdOrder.indexOf(it.mediaSourceId) }

            var index = 0 // invariant: fastSources 里序号 index 之前的数据源都已经查询完成. index 是第一个未完成的数据源.

            // 将 flow 转接为 Channel, 以便可以 cancel collect
            val allowNonPreferredChannel = Channel<Boolean>().apply {
                launch(backgroundTasks, start = CoroutineStart.UNDISPATCHED) {
                    try {
                        allowNonPreferredFlow.collect { send(it) }
                    } catch (_: ClosedSendChannelException) {
                        throw CancellationException()
                    }
                }
            }

            val allowNonPreferredChannelFlow = allowNonPreferredChannel.receiveAsFlow().let { channelFlow ->

                // 等待第一个 allowNonPreferredFlow 的值, 否则可能会有 race condition: allowNonPreferredFlow 发出值后, 但是 allowNonPreferredChannel 已经被 close 了,
                // 进而会导致 allowNonPreferredFlow 明明有一个值, 但下面的 combine 无法收到.
                // 这个行为有 test. 如果你需要修改, 注意保证 `MediaSelectorFastSelectSourcesTest.allowNonPreferredFlow wont cancel` 通过.
                val first = channelFlow.first()
                flow {
                    emit(first)
                    emitAll(channelFlow) // emit 第一个值然后再 emit 其他值. 这是可以的, 因为 receiveAsFlow 是一个 hot flow.
                }
            }

            combine<MediaSourceFetchState, _>(
                // 每个 source 的 state.
                fastSources
                    .map { source ->
                        source.state
                            .transformWhile {
                                emit(it)
                                // 完成后就不再 collect, 让这个 flow 能 complete
                                it !is MediaSourceFetchState.Completed
                                        && it !is MediaSourceFetchState.Disabled
                            }
                    }, // 这会 launch 很多协程来收集状态
            ) { states ->
                logger.debug {
                    val msg = states.zip(fastSources) { state, source ->
                        "${source.sourceInfo.displayName.take(2)}=$state"
                    }.joinToString(", ")

                    "fastSources state updated: $msg"
                }

                states.toList()
            }.onCompletion {
                // 源完结后, 停止接受新的 allowNonPreferredFlow, 否则函数要等待 allowNonPreferredFlow 完结才能结束.
                logger.debug {
                    "fastSources onCompletion"
                }
                allowNonPreferredChannel.close()
            }.combine(
                allowNonPreferredChannelFlow.onCompletion {
                    logger.debug {
                        "allowNonPreferredChannelFlow onCompletion"
                    }
                },
            ) { states, allowNonPreferred ->
                // 注意, 此时可能有多个 source 同时完成 (状态同时变成 Completed). 所以需要遍历检验所有在此刻完成的 sources.

                // 我们只需要从 index 开始按顺序考虑所有已经完成了的 source, 遇到第一个未完成的就停止. 
                // 对于那些位于这个未完成的 source 之后的 source, 即使它们完成了, 我们也不应该选择它们, 所以不用考虑.
                while (index < states.size) {
                    when (states[index]) {
                        is MediaSourceFetchState.Completed,
                        is MediaSourceFetchState.Disabled -> {
                            logger.debug {
                                "calling trySelectFromMediaSources"
                            }
                            val selected: Media? = mediaSelector.trySelectFromMediaSources(
                                fastSources.subList(0, index + 1).map { it.mediaSourceId },
                                overrideUserSelection = overrideUserSelection,
                                blacklistMediaIds = blacklistMediaIds,
                                allowNonPreferred = allowNonPreferred,
                            )
                            logger.debug {
                                "done trySelectFromMediaSources"
                            }

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
                .run {
                    try {
                        firstOrNull().also {
                            logger.debug { "fastSources selected: $it" }
                        }
                    } catch (e: CancellationException) {
                        logger.debug { "fastSources cancelled" }
                        throw e
                    }
                }
                .also {
                    backgroundTasks.cancel() // 不可以使用 cancelScope, 否则会取消整个函数, 导致总是 return null.
                }
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

// 日常没啥用, 只有出 bug 了才会用到
private val logger = SilentLogger//logger<MediaSelectorAutoSelect>()
