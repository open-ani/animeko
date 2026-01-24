/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFailedOrAbandoned
import me.him188.ani.app.domain.media.selector.eventHandling
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.mediasource.SetPreferredWebMediaSourceUseCase
import me.him188.ani.datasources.api.source.MediaSourceKind
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
                coroutineScope {
                    // 监听用户喜欢的 Web 源变更, 增加偏好
                    launch {
                        bundle.mediaSelector.eventHandling.preferWebMediaSource { event ->
                            if (event.subjectId != context.subjectId) return@preferWebMediaSource
                            val currentPreference = getPreferredWebMediaSource(event.subjectId).first()
                            if (currentPreference != event.mediaSourceId) {
                                logger.info { "Set web source preference for subject ${context.subjectId} to ${event.mediaSourceId}" }
                                setPreferredWebMediaSource(event.subjectId, event.mediaSourceId)
                            }
                        }
                    }

                    // 监听 Web 源加载失败的情况, 删除偏好
                    combine(
                        // 如果这个 subject 没有偏好, 则不继续监听, 这里将会一直挂起
                        getPreferredWebMediaSource(context.subjectId).filterNotNull(),
                        combine(
                            bundle.mediaFetchSession.mediaSourceResults
                                .filter { it.kind == MediaSourceKind.WEB }
                                .map { r -> r.state.map { r } },
                            Array<MediaSourceFetchResult>::toList,
                        ),
                    ) { preferredWebMediaSourceId, results ->
                        results.forEach {
                            if (it.mediaSourceId != preferredWebMediaSourceId) return@forEach
                            if (it.state.value.isFailedOrAbandoned) {
                                logger.info {
                                    "Remove web source preference for subject ${context.subjectId} from ${it.mediaSourceId}. " +
                                            "because source state in this session is ${it.state.value.str()}."
                                }
                                setPreferredWebMediaSource(context.subjectId, null)
                            }
                        }
                    }.launchIn(this)
                }
            }
        }
    }

    private fun MediaSourceFetchState.str(): String {
        return when (this) {
            is MediaSourceFetchState.Failed -> "failed"
            is MediaSourceFetchState.Abandoned -> "abandoned"
            else -> this::class.simpleName!!.lowercase()
        }
    }

    companion object : EpisodePlayerExtensionFactory<ObserveWebMediaSourcePreferenceExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): ObserveWebMediaSourcePreferenceExtension {
            return ObserveWebMediaSourcePreferenceExtension(context, koin)
        }
    }
}