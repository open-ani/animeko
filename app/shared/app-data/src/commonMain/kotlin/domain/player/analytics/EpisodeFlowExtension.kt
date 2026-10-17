/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.SelectEvent
import me.him188.ani.app.domain.media.selector.SelectOrigin
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.domain.player.extension.EpisodePlayerExtensionFactory
import me.him188.ani.app.domain.player.extension.ExtensionBackgroundTaskScope
import me.him188.ani.app.domain.player.extension.PlayerExtension
import me.him188.ani.app.domain.player.extension.PlayerExtensionContext
import org.koin.core.Koin
import kotlin.time.Duration.Companion.milliseconds

/**
 * 记录每次剧集尝试里剧集信息加载 (`info_loaded`)、开始查找资源 (`search_started`) 与用户手动选择资源 (`user_select`).
 *
 * `search_started` 每次尝试对每个查询会话只记一次; 切集复用上一集的查询会话时 `kind=reused`.
 * 自动选择的结果由 [AutoSelectExtension][me.him188.ani.app.domain.player.extension.AutoSelectExtension] 记录, 这里只记用户的选择.
 */
class EpisodeFlowExtension(
    private val context: PlayerExtensionContext,
    private val describeSearchStart: (MediaFetchSession, PlayFlowEpisode) -> PlayFlowFetchObserver.SearchStart?,
    private val sourceTiers: () -> MediaSelectorSourceTiers?,
) : PlayerExtension("PlayFlowEpisode") {
    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        val recorder = context.playFlow
        val episode = episodeSession.playFlowEpisode

        backgroundTaskScope.launch("InfoLoaded") {
            // 记到首次加载成功为止; 之后的失败不再属于本次尝试的加载流程
            coroutineScope {
                var failed = false
                val failures = launch {
                    episodeSession.infoLoadErrorStateFlow.filterNotNull().collect { error ->
                        failed = true
                        recorder.record(PlayFlowStep.INFO_LOADED, episode) {
                            put("outcome", "failed")
                            put("reason", error.toPlayFlowReason())
                            put("duration_ms", episode.startedAt.elapsedNow())
                        }
                    }
                }
                episodeSession.infoBundleFlow.filterNotNull().first()
                failures.cancel()
                recorder.record(PlayFlowStep.INFO_LOADED, episode) {
                    put("outcome", "ok")
                    put("trigger", if (failed) "retry" else "initial")
                    put("duration_ms", episode.startedAt.elapsedNow())
                }
            }
        }

        backgroundTaskScope.launch("FetchSelect") {
            episodeSession.fetchSelectFlow.filterNotNull().collectLatest { bundle ->
                describeSearchStart(bundle.mediaFetchSession, episode)?.let { start ->
                    recorder.record(PlayFlowStep.SEARCH_STARTED, episode) {
                        put("kind", if (start.reused) "reused" else "new")
                        put("count", start.sourceCount)
                        put("restarts", start.retried)
                    }
                }
                coroutineScope {
                    bundle.mediaSelector.events.onSelect.collect { event ->
                        if (event.origin != SelectOrigin.MANUAL) return@collect
                        // 事件流不缓冲, 匹配信息要等候选列表, 放到子协程里查
                        launch { recordUserSelect(bundle.mediaSelector, event, episode) }
                    }
                }
            }
        }
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    private suspend fun recordUserSelect(selector: MediaSelector, event: SelectEvent, episode: PlayFlowEpisode) {
        val media = event.media ?: return
        val match = withTimeoutOrNull(MATCH_LOOKUP_TIMEOUT) {
            selector.selectedMaybeExcludedMedia.first { it?.original?.mediaId == media.mediaId }
        }
        val tiers = sourceTiers()
        context.playFlow.record(PlayFlowStep.USER_SELECT, episode) {
            put("sel_no", episode.selectionNoOf(media.mediaId))
            put("entry", event.trigger.toPlayFlowEntry())
            put("media_source_id", media.mediaSourceId)
            put("kind", media.toPlayFlowKind())
            put("match", (match as? MaybeExcludedMedia.Included)?.metadata?.subjectMatchKind?.toPlayFlowMatch())
            put("tier", tiers?.tierOf(media))
            put("count", if (event.previousMedia != null) 1 else 0)
        }
    }

    private fun LoadError.toPlayFlowReason(): String = when (this) {
        LoadError.NoResults -> "no_results"
        LoadError.RequiresLogin -> "requires_login"
        LoadError.NetworkError -> "network"
        LoadError.ServiceUnavailable -> "service_unavailable"
        LoadError.RateLimited -> "rate_limited"
        is LoadError.RequestError -> "request_error"
        is LoadError.UnknownError -> "unknown"
    }

    /**
     * @param describeSearchStart 见 [PlayFlowFetchObserver.describeSearchStart].
     * @param sourceTiers 最新的数据源阶级, 没有时为 `null`.
     */
    class Factory(
        private val describeSearchStart: (MediaFetchSession, PlayFlowEpisode) -> PlayFlowFetchObserver.SearchStart?,
        private val sourceTiers: () -> MediaSelectorSourceTiers?,
    ) : EpisodePlayerExtensionFactory<EpisodeFlowExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): EpisodeFlowExtension {
            return EpisodeFlowExtension(context, describeSearchStart, sourceTiers)
        }
    }

    private companion object {
        val MATCH_LOOKUP_TIMEOUT = 500.milliseconds
    }
}
