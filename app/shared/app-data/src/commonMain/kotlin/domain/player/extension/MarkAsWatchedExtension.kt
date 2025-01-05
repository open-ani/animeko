/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.episode.GetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer

/**
 * 自动标记为已看
 */
class MarkAsWatchedExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
    private val enableSamplingAndDebounce: Boolean,
) : PlayerExtension("AutoMarkWatched") {
    private val getVideoScaffoldConfigUseCase: GetVideoScaffoldConfigUseCase by koin.inject()
    private val getEpisodeCollectionTypeUseCase: GetEpisodeCollectionTypeUseCase by koin.inject()
    private val setEpisodeCollectionTypeUseCase: SetEpisodeCollectionTypeUseCase by koin.inject()

    override fun onStart(backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("AutoMarkWatched") {
            context.sessionFlow.collectLatest { session ->
                invoke(
                    context.player,
                    context.subjectId,
                    session.episodeId,
                )
            }
        }
    }

    private suspend fun invoke(
        player: MediampPlayer,
        subjectId: Int,
        episodeId: Int
    ) {
        getVideoScaffoldConfigUseCase()
            .map { it.autoMarkDone }
            .distinctUntilChanged()
            .collectLatest { enabled ->
                if (!enabled) return@collectLatest

                // now config is enabled

                impl(episodeId, player, subjectId)
            }

    }

    private suspend fun impl(
        episodeId: Int,
        player: MediampPlayer,
        subjectId: Int
    ) {
        val collectionType = getEpisodeCollectionTypeUseCase(
            episodeId,
            allowNetwork = false, // 我们只是用来自动标记, 不需要精确的数据
        )
        if (collectionType?.isDoneOrDropped() == true) {
            // 已经看过了
            return
        }

        // 设置启用
        cancellableCoroutineScope {
            combine(
                player.currentPositionMillis
                    .let { if (enableSamplingAndDebounce) it.sampleWithInitial(5000) else it },
                player.mediaProperties.map { it?.durationMillis }
                    .let { if (enableSamplingAndDebounce) it.debounce(5000) else it },
                player.playbackState,
            ) { pos, max, playback ->
                if (max == null || !playback.isPlaying) return@combine
                if (pos > max.toFloat() * 0.9) {
                    logger.info { "观看到 90%, 标记看过" }
                    setEpisodeCollectionTypeUseCase(subjectId, episodeId, UnifiedCollectionType.DONE)
                    cancelScope() // 标记成功一次后就不要再检查了
                }
            }.collect()
        }
    }

    companion object : EpisodePlayerExtensionFactory<MarkAsWatchedExtension> {
        private val logger = logger<MarkAsWatchedExtension>()

        override fun create(context: PlayerExtensionContext, koin: Koin): MarkAsWatchedExtension {
            return MarkAsWatchedExtension(context, koin, enableSamplingAndDebounce = true)
        }
    }
}