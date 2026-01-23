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
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.autoSelect
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.PlaybackState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 当播放失败时, 自动切换到下一个可选择的 media.
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
                    context.player.playbackState,
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
        playbackStateFlow: Flow<PlaybackState>
    ) {
        val handler = PlayerLoadErrorHandler(
            getPreferKind = { getMediaSelectorSettingsFlowUseCase().first().preferKind },
            getWebToleranceDuration = { getMediaSelectorSettingsFlowUseCase().first().fastSelectWebLowTierToleranceDuration },
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
                            playbackStateFlow,
                        )
                    }
                }
            }
    }

    private suspend fun PlayerLoadErrorHandler.observeLoadErrorAndHandle(
        mediaFetchSessionFlow: Flow<MediaFetchSelectBundle?>,
        videoLoadingStateFlow: Flow<VideoLoadingState>,
        playbackStateFlow: Flow<PlaybackState>
    ) {
        mediaFetchSessionFlow.collectLatest { bundle ->
            if (bundle == null) return@collectLatest

            combine(
                videoLoadingStateFlow, // 解析链接出错 (未匹配到链接)
                playbackStateFlow, // 解析成功, 但播放器出错 (无法链接到链接, 例如链接错误)
            ) { videoLoadingState, playbackState ->
                videoLoadingState is VideoLoadingState.Failed || playbackState == PlaybackState.ERROR
            }.distinctUntilChanged()
                .collectLatest { isError ->
                    if (isError) {
                        handleError(bundle.mediaFetchSession, bundle.mediaSelector)
                    } // else: cancel selection
                }
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
    private val getWebToleranceDuration: suspend () -> Duration,
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

    suspend fun handleError(
        session: MediaFetchSession,
        mediaSelector: MediaSelector,
    ) {
        // 播放出错了
        logger.info { "Player errored, automatically switching to next media" }

        // 将当前播放的 mediaId 加入黑名单
        mediaSelector.selected.value?.let {
            blacklistedMediaIds = blacklistedMediaIds.add(it.mediaId) // thread-safe
        }

        delay(1.seconds) // 稍等让用户看到播放出错

        // Load data in parallel
        val (preferKind, sourceTiers) = combine(
            getPreferKind.asFlow(),
            getSourceTiers.asFlow(),
        ) { kind, tiers -> kind to tiers }.first()

        if (preferKind != MediaSourceKind.WEB) {
            logger.info { "Player errored, but preferKind is not WEB ($preferKind), skip automatic switch" }
            return
        }

        val result = mediaSelector.autoSelect.fastSelectWebSources(
            session,
            sourceTiers = sourceTiers,
            overrideUserSelection = true, // Note: 覆盖用户选择
            blacklistMediaIds = blacklistedMediaIds,
            lowTierToleranceDuration = getWebToleranceDuration(),
        )
        logger.info { "Player errored, automatically switched to next media: $result" }
    }

    companion object {
        private val logger = logger<PlayerLoadErrorHandler>()
    }

    @VisibleForTesting
    val blacklist: Set<String> get() = blacklistedMediaIds
}
