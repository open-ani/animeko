/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.episode.MediaFetchSelectBundle
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.autoSelect
import me.him188.ani.app.domain.mediasource.GetWebMediaSourceInstanceFlowUseCase
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.PlaybackState
import kotlin.time.Duration.Companion.seconds

/**
 * 当播放失败时, 自动切换到下一个可选择的 media.
 */
class SwitchMediaOnPlayerErrorExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("SwitchMediaOnPlayerErrorExtension") {
    private val getVideoScaffoldConfigUseCase: GetVideoScaffoldConfigUseCase by koin.inject()
    private val getWebMediaSourceInstanceFlowUseCase: GetWebMediaSourceInstanceFlowUseCase by koin.inject()
    private val getMediaSelectorSettingsFlowUseCase: GetMediaSelectorSettingsFlowUseCase by koin.inject()


    override fun onUIAttach(backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("PlayerErrorListener") {
            invoke(
                context.fetchSelectFlow,
                context.videoLoadingState,
                context.player.playbackState,
            )
        }
    }

    private suspend fun invoke(
        mediaFetchSessionFlow: Flow<MediaFetchSelectBundle?>,
        videoLoadingStateFlow: Flow<VideoLoadingState>,
        playbackStateFlow: Flow<PlaybackState>
    ) {
        val handler = PlayerLoadErrorHandler(
            getWebSources = { getWebMediaSourceInstanceFlowUseCase().first().map { it.mediaSourceId } },
            getPreferKind = { getMediaSelectorSettingsFlowUseCase().first().preferKind },
        )

        // 播放失败时自动切换下一个 media.
        // 即使是 BT 出错, 我们也会尝试切换到下一个 WEB 类型的数据源, 而不是继续尝试 BT.
        getVideoScaffoldConfigUseCase().map { it.autoSwitchMediaOnPlayerError }
            .collectLatest { autoSwitchMediaOnPlayerError ->
                if (!autoSwitchMediaOnPlayerError) {
                    // 设置关闭, 不要自动切换
                    return@collectLatest
                }

                handler.observeLoadErrorAndHandle(mediaFetchSessionFlow, videoLoadingStateFlow, playbackStateFlow)
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

private class PlayerLoadErrorHandler(
    private val getWebSources: suspend () -> List<String>,
    private val getPreferKind: suspend () -> MediaSourceKind?,
) {
    private var blacklistedMediaIds = persistentHashSetOf<String>()

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
        val result = mediaSelector.autoSelect.fastSelectSources(
            session,
            getWebSources(),
            preferKind = flowOf(getPreferKind()),
            overrideUserSelection = true, // Note: 覆盖用户选择
            blacklistMediaIds = blacklistedMediaIds,
            allowNonPreferredFlow = flowOf(true), // 偏好的如果全都播放错误了, 允许播放非偏好的
        )
        logger.info { "Player errored, automatically switched to next media: $result" }
    }

    companion object {
        private val logger = logger<PlayerLoadErrorHandler>()
    }
}
