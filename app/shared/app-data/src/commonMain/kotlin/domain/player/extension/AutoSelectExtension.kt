/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.awaitCompletedResults
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaAutoSelectExhaustCause
import me.him188.ani.app.domain.media.selector.MediaAutoSelectOutcome
import me.him188.ani.app.domain.media.selector.MediaAutoSelectReason
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.media.selector.ReplayBrowseMemoryUseCase
import me.him188.ani.app.domain.media.selector.ReplayMissReason
import me.him188.ani.app.domain.media.selector.ReplayResult
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.domain.player.analytics.PlayFlowEpisode
import me.him188.ani.app.domain.player.analytics.PlayFlowProps
import me.him188.ani.app.domain.player.analytics.PlayFlowStep
import me.him188.ani.app.domain.player.analytics.toPlayFlowKind
import me.him188.ani.app.domain.player.analytics.toPlayFlowMatch
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark

/**
 * 自动选择数据源. 切集时按「本地缓存 → 浏览记忆回放 → 自动选择」的顺序在同一协程内串行决定,
 * 回放未命中才启动自动选择, 因此两者不会互相覆盖.
 *
 * 本任务挂在 session 的 sessionScope 下, 未捕获的异常会包成 ExtensionException 并终止任务, 之后本集及每次 bundle 重建都失去自动选择;
 * 所以缓存检查与回放都自行捕获非取消异常, 失败只是退回自动选择.
 *
 * 每个 bundle 的自动选择以一条 `play_flow auto_select_done` 收尾: 选到了哪个资源、靠什么选到的 (`method`), 或为什么没选到 (`outcome`).
 * 三个步骤的耗时合并计入 `duration_ms`.
 *
 * @see MediaSelector
 * @see ReplayBrowseMemoryUseCase
 */
class AutoSelectExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("AutoSelect") {
    private val mediaSelectorAutoSelectUseCase: MediaSelectorAutoSelectUseCase by koin.inject()
    private val replayBrowseMemory: ReplayBrowseMemoryUseCase by koin.inject()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("AutoSelect") {
            // 任务挂在本 session 的 sessionScope 下, switchEpisode 先取消旧 scope 再换 session, 任务内不可能观察到别的 session;
            // 直接用 onStart 的 episodeSession, 不经 context.sessionFlow.
            episodeSession.fetchSelectFlow.collectLatest { fetchSelect ->
                if (fetchSelect == null) return@collectLatest
                val report = AutoSelectReport(episodeSession.playFlowEpisode, context.playFlow.markNow())
                try {
                    // fetchSelect 由 infoBundleFlow 派生, 拿到非空 bundle 时 replayCache 已有对应的 infoBundle.
                    // infoBundleFlow 首值 null, 网络错误后也会回到 null; 用 replayCache 而不是 filterNotNull().first(), 否则会连自动选择一起挂住.
                    val episodeInfo = episodeSession.infoBundleFlow.replayCache.lastOrNull()?.episodeInfo
                    if (episodeInfo == null) {
                        report.replaySkipped = true
                    }
                    val replayed = episodeInfo != null
                            && !hasLocalCacheForEpisode(fetchSelect.mediaFetchSession, fetchSelect.mediaSelector)
                            && replayBrowseMemory(episodeInfo, fetchSelect.mediaSelector, report)
                    if (!replayed) {
                        report.onAutoSelected(mediaSelectorAutoSelectUseCase(fetchSelect.mediaFetchSession, fetchSelect.mediaSelector))
                    }
                } finally {
                    report.recordIfNeeded()
                }
            }
        }
    }

    private suspend fun replayBrowseMemory(
        episodeInfo: EpisodeInfo,
        mediaSelector: MediaSelector,
        report: AutoSelectReport,
    ): Boolean {
        return try {
            val result = replayBrowseMemory(context.subjectId, episodeInfo, mediaSelector)
            when (result) {
                ReplayResult.Selected -> report.onBrowseMemorySelected(mediaSelector.selected.value)
                is ReplayResult.NotFound -> report.replayMiss = result.reason
            }
            result.isHit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Replaying browse memory failed, falling back to auto select" }
            report.replayMiss = ReplayMissReason.EXCEPTION
            false
        }
    }

    /**
     * 本地缓存的优先级高于浏览记忆 (`MediaAutoSelector.decide` 第一步就是 selectCache, 回放不能反转它).
     * 本地源是毫秒级完成的: 等它们完成, 再等 [MediaSelector.filteredCandidates] 反映它们的结果
     * (候选列表经共享流异步传播, 刚完成时可能仍是本地源完成前的快照), 然后看其中有没有本集的缓存候选.
     * 超时 ([CACHE_CHECK_TIMEOUT]) 或异常 (非 Cancellation) → false (照常回放).
     */
    @OptIn(UnsafeOriginalMediaAccess::class)
    private suspend fun hasLocalCacheForEpisode(session: MediaFetchSession, mediaSelector: MediaSelector): Boolean {
        val cacheResults = session.mediaSourceResults.filter { it.kind == MediaSourceKind.LocalCache }
        if (cacheResults.isEmpty()) return false
        return try {
            withTimeoutOrNull(CACHE_CHECK_TIMEOUT) {
                val cachedMediaIds = cacheResults.flatMapTo(HashSet()) { result ->
                    result.awaitCompletedResults().map { it.mediaId }
                }
                if (cachedMediaIds.isEmpty()) return@withTimeoutOrNull false
                mediaSelector.filteredCandidates
                    .first { candidates -> candidates.any { it.original.mediaId in cachedMediaIds } }
                    .any { it is MaybeExcludedMedia.Included && it.result.kind == MediaSourceKind.LocalCache }
            } == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Checking local cache failed, replaying browse memory as usual" }
            false
        }
    }

    /**
     * 一个 bundle 的自动选择报告: 浏览记忆回放命中时由 [onBrowseMemorySelected] 记录, 否则由 [onAutoSelected] 按
     * [MediaAutoSelectOutcome] 记录. 只记录一次.
     *
     * `reason`: 没选中时是没选中的原因; 选中时是浏览记忆未命中、退回自动选择的原因 ([replayMiss]).
     */
    private inner class AutoSelectReport(
        private val episode: PlayFlowEpisode,
        private val start: TimeMark,
    ) {
        var replaySkipped = false
        var replayMiss: ReplayMissReason? = null
        private var recorded = false

        fun onAutoSelected(outcome: MediaAutoSelectOutcome) {
            when (outcome) {
                is MediaAutoSelectOutcome.Selected -> record {
                    put("outcome", "selected")
                    put("method", outcome.reason.toMethod())
                    put("reason", replayMiss)
                    put("media_source_id", outcome.media.mediaSourceId)
                    put("kind", outcome.media.toPlayFlowKind())
                    put("match", outcome.matchKind?.toPlayFlowMatch())
                    put("tier", outcome.tier?.value?.toLong())
                    put("sel_no", episode.selectionNoOf(outcome.media.mediaId))
                }

                is MediaAutoSelectOutcome.Rejected -> record {
                    put("outcome", "preempted_by_user")
                    put("method", outcome.reason.toMethod())
                    put("reason", replayMiss)
                    put("media_source_id", outcome.media.mediaSourceId)
                }

                is MediaAutoSelectOutcome.Exhausted -> record {
                    put("outcome", outcome.cause.toOutcome())
                    put("reason", outcome.cause)
                }

                MediaAutoSelectOutcome.Cancelled -> record {
                    put("outcome", "skipped")
                    put("reason", "cancelled")
                }
            }
        }

        fun onBrowseMemorySelected(media: Media?) {
            record {
                put("outcome", "selected")
                put("method", "browse_memory")
                put("media_source_id", media?.mediaSourceId)
                put("kind", media?.toPlayFlowKind())
                put("sel_no", media?.let { episode.selectionNoOf(it.mediaId) })
            }
        }

        /**
         * 自动选择没有进行 (剧集信息缺失) 或被取消时, 仍以一条 `skipped` 收尾.
         */
        fun recordIfNeeded() {
            record {
                put("outcome", "skipped")
                put("reason", if (replaySkipped) "info_missing" else "cancelled")
            }
        }

        private fun record(build: PlayFlowProps.() -> Unit) {
            if (recorded) return
            recorded = true
            context.playFlow.record(PlayFlowStep.AUTO_SELECT_DONE, episode) {
                build()
                put("duration_ms", start.elapsedNow())
            }
        }

        private fun MediaAutoSelectReason.toMethod(): String = when (this) {
            MediaAutoSelectReason.LOCAL_CACHE -> "local_cache"
            MediaAutoSelectReason.PREFERRED_SOURCE -> "preferred_source"
            MediaAutoSelectReason.PREFERRED_KIND_COMPLETED -> "bt"
            MediaAutoSelectReason.WEB_INSTANT -> "instant"
            MediaAutoSelectReason.WEB_EXACT -> "exact"
            MediaAutoSelectReason.WEB_FUZZY -> "fuzzy"
            MediaAutoSelectReason.BT_FALLBACK -> "bt"
        }

        private fun MediaAutoSelectExhaustCause.toOutcome(): String = when (this) {
            MediaAutoSelectExhaustCause.PRECONDITION_SELECTED, MediaAutoSelectExhaustCause.PREEMPTED -> "preempted_by_user"
            MediaAutoSelectExhaustCause.NO_PREFERRED_CANDIDATES, MediaAutoSelectExhaustCause.NO_WEB_CANDIDATES,
            MediaAutoSelectExhaustCause.NO_PREFERENCE_MATCH -> "no_media"

            MediaAutoSelectExhaustCause.FUZZY_DEADLINE -> "timeout"
        }
    }

    companion object : EpisodePlayerExtensionFactory<AutoSelectExtension> {
        private val CACHE_CHECK_TIMEOUT = 2.seconds
        private val logger = logger<AutoSelectExtension>()

        override fun create(context: PlayerExtensionContext, koin: Koin): AutoSelectExtension {
            return AutoSelectExtension(context, koin)
        }
    }
}
