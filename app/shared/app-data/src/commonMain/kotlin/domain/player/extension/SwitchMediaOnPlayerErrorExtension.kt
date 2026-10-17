/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import androidx.annotation.VisibleForTesting
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.MediaFetchSelectBundle
import me.him188.ani.app.domain.media.DroppedFileMedia
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MediaAutoSelectExhaustCause
import me.him188.ani.app.domain.media.selector.MediaAutoSelectOutcome
import me.him188.ani.app.domain.media.selector.MediaAutoSelector
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.SelectTrigger
import me.him188.ani.app.domain.player.analytics.PlayFlowEpisode
import me.him188.ani.app.domain.player.analytics.PlayFlowStep
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlayerState

/**
 * 当播放失败时, 自动切换到下一个可选择的 media.
 *
 * 用户拖入的本地文件 ([DroppedFileMedia]) 播放失败时不切换, 保留报错.
 */
class SwitchMediaOnPlayerErrorExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("SwitchMediaOnPlayerErrorExtension") {
    private val getVideoScaffoldConfigUseCase: GetVideoScaffoldConfigUseCase by koin.inject()
    private val getMediaSelectorSettingsFlowUseCase: GetMediaSelectorSettingsFlowUseCase by koin.inject()
    private val getSourceTiersUseCase: GetMediaSelectorSourceTiersUseCase by koin.inject()


    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("PlayerErrorListener") {
            context.sessionFlow.collectLatest { session ->
                invoke(
                    session.fetchSelectFlow,
                    context.videoLoadingStateFlow,
                    context.player.state,
                    session.playFlowEpisode,
                )
            }
        }
    }

    /**
     * 启动播放失败处理逻辑。
     *
     * 此函数监听当前播放会话流、视频加载状态和播放器状态，并在发生错误时触发自动切换。
     * 同时也监听媒体选择事件，当用户手动切换媒体时，将先前选中的媒体加入黑名单，避免自动选择时回退。
     */
    private suspend fun invoke(
        mediaFetchSessionFlow: Flow<MediaFetchSelectBundle?>,
        videoLoadingStateFlow: Flow<VideoLoadingState>,
        playerStateFlow: Flow<PlayerState>,
        episode: PlayFlowEpisode,
    ) {
        val handler = PlayerLoadErrorHandler(
            getPreferKind = { getMediaSelectorSettingsFlowUseCase().first().preferKind },
            getSourceTiers = { getSourceTiersUseCase().first() },
        )

        // 播放失败时自动切换下一个 media.
        // 即使是 BT 出错, 我们也会尝试切换到下一个 WEB 类型的数据源, 而不是继续尝试 BT.
        getVideoScaffoldConfigUseCase().map { it.autoSwitchMediaOnPlayerError }
            .collectLatest { autoSwitchMediaOnPlayerError ->
                if (!autoSwitchMediaOnPlayerError) {
                    // 设置关闭, 不要自动切换
                    return@collectLatest
                }

                coroutineScope {
                    launch {
                        handler.observeMediaSelectorBlacklist(
                            mediaFetchSessionFlow.mapNotNull { it?.mediaSelector },
                        )
                    }

                    launch {
                        handler.observeLoadErrorAndHandle(
                            mediaFetchSessionFlow,
                            videoLoadingStateFlow,
                            playerStateFlow,
                            episode,
                        )
                    }
                }
            }
    }

    private suspend fun PlayerLoadErrorHandler.observeLoadErrorAndHandle(
        mediaFetchSessionFlow: Flow<MediaFetchSelectBundle?>,
        videoLoadingStateFlow: Flow<VideoLoadingState>,
        playerStateFlow: Flow<PlayerState>,
        episode: PlayFlowEpisode,
    ) {
        mediaFetchSessionFlow.collectLatest { bundle ->
            if (bundle == null) return@collectLatest

            combine(
                videoLoadingStateFlow, // 解析链接出错 (未匹配到链接)
                playerStateFlow, // 解析成功, 但播放器出错 (无法链接到链接, 例如链接错误)
            ) { videoLoadingState, playerState ->
                when {
                    videoLoadingState is VideoLoadingState.Failed &&
                            PlayerLoadErrorHandler.canRecoverBySwitching(videoLoadingState) -> PlayerErrorTrigger.LOAD_FAILED

                    playerState.mediaStatus is MediaStatus.Error -> PlayerErrorTrigger.PLAYER_ERROR
                    else -> null
                }
            }.distinctUntilChanged()
                .collectLatest { trigger ->
                    if (trigger != null) {
                        val failedMedia = bundle.mediaSelector.selected.value
                        val handling = handleError(bundle.mediaFetchSession, bundle.mediaSelector)
                        recordAutoSwitch(episode, trigger, failedMedia, handling, blacklist.size)
                    } // else: cancel selection
                }
        }
    }

    private fun recordAutoSwitch(
        episode: PlayFlowEpisode,
        trigger: PlayerErrorTrigger,
        failedMedia: Media?,
        handling: PlayerLoadErrorHandling,
        blacklistSize: Int,
    ) {
        context.playFlow.record(PlayFlowStep.AUTO_SWITCH, episode) {
            fun skipped(reason: String) {
                put("outcome", "skipped")
                put("reason", reason)
            }
            put("sel_no", failedMedia?.let { episode.selectionNoOf(it.mediaId) })
            put("trigger", trigger)
            when (handling) {
                is PlayerLoadErrorHandling.Attempted -> when (val outcome = handling.outcome) {
                    is MediaAutoSelectOutcome.Selected -> {
                        put("outcome", "switched")
                        put("media_source_id", outcome.media.mediaSourceId)
                    }

                    is MediaAutoSelectOutcome.Rejected -> skipped("user_switched")
                    is MediaAutoSelectOutcome.Exhausted -> when (outcome.cause) {
                        MediaAutoSelectExhaustCause.PRECONDITION_SELECTED, MediaAutoSelectExhaustCause.PREEMPTED -> skipped("user_switched")
                        else -> {
                            put("outcome", "no_more_sources")
                            put("reason", outcome.cause)
                        }
                    }

                    MediaAutoSelectOutcome.Cancelled -> skipped("cancelled")
                }

                PlayerLoadErrorHandling.SkippedDroppedFile -> skipped("dropped_file")
                PlayerLoadErrorHandling.SkippedSelectionChanged -> skipped("user_switched")
                is PlayerLoadErrorHandling.SkippedPreferKind -> skipped("prefer_bt")
            }
            put("count", blacklistSize)
        }
    }

    companion object : EpisodePlayerExtensionFactory<SwitchMediaOnPlayerErrorExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): SwitchMediaOnPlayerErrorExtension {
            return SwitchMediaOnPlayerErrorExtension(context, koin)
        }
    }
}

internal class PlayerLoadErrorHandler(
    private val getPreferKind: suspend () -> MediaSourceKind?,
    private val getSourceTiers: suspend () -> MediaSelectorSourceTiers,
) {
    private var blacklistedMediaIds = persistentHashSetOf<String>()

    suspend fun observeMediaSelectorBlacklist(
        mediaSelectorFlow: Flow<MediaSelector>
    ) {
        mediaSelectorFlow.collectLatest { selector ->
            selector.events.onSelect.collect { event ->
                event.previousMedia?.let {
                    blacklistedMediaIds = blacklistedMediaIds.add(it.mediaId)
                }
            }
        }
    }

    /**
     * 处理一次播放失败: 把失败的资源加入黑名单, 稍等后在 WEB 资源里自动选一个替代.
     *
     * @return 这次处理做了什么, 供埋点记录.
     */
    suspend fun handleError(
        session: MediaFetchSession,
        mediaSelector: MediaSelector,
    ): PlayerLoadErrorHandling {
        val failedMedia = mediaSelector.selected.value
        if (failedMedia != null && DroppedFileMedia.isDroppedFile(failedMedia)) {
            // 用户拖入的本地文件: 用户明确要播放这个文件, 保留报错, 不替换为其他资源
            logger.info { "Player errored on a dropped file, skip automatic switch" }
            return PlayerLoadErrorHandling.SkippedDroppedFile
        }

        // 播放出错了
        logger.info { "Player errored, automatically switching to next media" }

        // 将当前播放的 mediaId 加入黑名单
        failedMedia?.let {
            blacklistedMediaIds = blacklistedMediaIds.add(it.mediaId) // thread-safe
        }

        delay(1.seconds) // 稍等让用户看到播放出错
        if (mediaSelector.selected.value != failedMedia) return PlayerLoadErrorHandling.SkippedSelectionChanged

        // Load data in parallel
        val (preferKind, sourceTiers) = combine(
            getPreferKind.asFlow(),
            getSourceTiers.asFlow(),
        ) { kind, tiers -> kind to tiers }.first()

        if (preferKind != MediaSourceKind.WEB) {
            logger.info { "Player errored, but preferKind is not WEB ($preferKind), skip automatic switch" }
            return PlayerLoadErrorHandling.SkippedPreferKind(preferKind)
        }

        val outcome = MediaAutoSelector(mediaSelector).select(
            session,
            MediaAutoSelector.Config(
                trigger = SelectTrigger.AUTO_ERROR_SWITCH,
                selectCache = false,
                blacklist = blacklistedMediaIds,
                web = MediaAutoSelector.Web(
                    sourceTiers = sourceTiers,
                    // 错误切换不需要等太长时间。
                    exactMatchAfter = 1.seconds,
                    fuzzyMatchAfter = 1.seconds,
                    waitForPendingSources = false,
                ),
            ),
            expectedSelection = failedMedia,
        )
        logger.info { "Player errored, automatically switched to next media: $outcome" }
        return PlayerLoadErrorHandling.Attempted(outcome)
    }

    companion object {
        private val logger = logger<PlayerLoadErrorHandler>()

        /**
         * 换到另一个 media 是否有可能解决这次失败.
         *
         * 网页引擎不可用是环境问题而不是资源问题, 换源只会让每个网页资源以同样的方式失败一遍,
         * 并且把真正的原因藏在一串"自动切换"之后. 此时保留报错让用户看到.
         */
        fun canRecoverBySwitching(state: VideoLoadingState.Failed): Boolean =
            state !is VideoLoadingState.WebEngineUnavailable
    }

    @VisibleForTesting
    val blacklist: Set<String> get() = blacklistedMediaIds
}

/**
 * 触发自动换源的错误类型.
 */
enum class PlayerErrorTrigger {
    /** 解析或打开资源失败 ([VideoLoadingState.Failed]). */
    LOAD_FAILED,

    /** 播放器报错 ([MediaStatus.Error]). */
    PLAYER_ERROR,
}

/**
 * [PlayerLoadErrorHandler.handleError] 的处理结果.
 */
sealed interface PlayerLoadErrorHandling {
    /** 失败的是用户拖入的本地文件, 不换源. */
    data object SkippedDroppedFile : PlayerLoadErrorHandling

    /** 等待期间用户已自行换源. */
    data object SkippedSelectionChanged : PlayerLoadErrorHandling

    /** 偏好类型不是 WEB, 不自动换源. */
    data class SkippedPreferKind(val preferKind: MediaSourceKind?) : PlayerLoadErrorHandling

    /** 进行了自动选择. */
    data class Attempted(val outcome: MediaAutoSelectOutcome) : PlayerLoadErrorHandling
}
