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
import kotlinx.coroutines.flow.flatMapLatest
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.media.selector.eventHandling
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.mediasource.SetPreferredWebMediaSourceUseCase
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin

/**
 * 监听用户偏好的 Web 源变更
 */
class ObserveWebMediaSourcePreferenceExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("ObserveWebMediaSourcePreference") {
    private val getPreferredWebMediaSource: GetPreferredWebMediaSourceUseCase by koin.inject()
    private val setPreferredWebMediaSource: SetPreferredWebMediaSourceUseCase by koin.inject()

    private val logger = logger<ObserveWebMediaSourcePreferenceExtension>()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("ObserveWebMediaSourcePreference") {
            context.sessionFlow.flatMapLatest { it.fetchSelectFlow }.collectLatest { bundle ->
                if (bundle == null) return@collectLatest
                // 手动偏好只由用户指定。查询和播放的暂时失败由 fetcher 与播放页运行记录处理。
                bundle.mediaSelector.eventHandling.preferWebMediaSource { event ->
                    if (event.subjectId != context.subjectId) return@preferWebMediaSource
                    val currentPreference = getPreferredWebMediaSource(event.subjectId).first()
                    if (currentPreference != event.mediaSourceId) {
                        logger.info { "Set web source preference for subject ${context.subjectId} to ${event.mediaSourceId}" }
                        setPreferredWebMediaSource(event.subjectId, event.mediaSourceId)
                    }
                }
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<ObserveWebMediaSourcePreferenceExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): ObserveWebMediaSourcePreferenceExtension {
            return ObserveWebMediaSourcePreferenceExtension(context, koin)
        }
    }
}