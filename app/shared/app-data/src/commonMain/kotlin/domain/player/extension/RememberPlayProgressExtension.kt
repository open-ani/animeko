/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.PlaybackState
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * 记忆播放进度.
 *
 * 在以下情况时保存播放进度:
 * - 切换数据源
 * - 暂停
 * - 播放完成
 */
class RememberPlayProgressExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
) : PlayerExtension(name = "SaveProgressExtension") {
    private val playProgressRepository: EpisodePlayHistoryRepository by koin.inject()

    override fun onStart(backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("MediaSelectorListener") {
            context.fetchSelectFlow.collectLatest { fetchSelect ->
                if (fetchSelect == null) return@collectLatest

                fetchSelect.mediaSelector.events.onBeforeSelect.collect {
                    // 切换 数据源 前保存播放进度
                    savePlayProgress()
                }
            }
        }

        backgroundTaskScope.launch("PlaybackStateListener") {
            val player = context.player
            player.playbackState.collect { playbackState ->
                val episodeId = context.episodeIdFlow.value

                when (playbackState) {
                    // 加载播放进度
                    PlaybackState.READY -> {
                        val positionMillis = playProgressRepository.getPositionMillisByEpisodeId(episodeId)
                        if (positionMillis == null) {
                            logger.info { "Did not find saved position" }
                        } else {
                            logger.info { "Loaded saved position: $positionMillis, waiting for video properties" }
                            player.mediaProperties.filter { it != null && it.durationMillis > 0L }.firstOrNull()
                            logger.info { "Loaded saved position: $positionMillis, video properties ready, seeking" }
                            withContext(Dispatchers.Main) { // android must call in main thread
                                player.seekTo(positionMillis)
                            }
                        }
                    }

                    PlaybackState.PAUSED -> savePlayProgress()

                    PlaybackState.FINISHED -> {
                        if (player.mediaProperties.value.let { it != null && it.durationMillis > 0L }) {
                            // 视频长度有效, 说明正常播放中
                            playProgressRepository.remove(episodeId)
                        } else {
                            // 视频加载失败或者在切换数据源时又切换了另一个数据源, 不要删除记录
                        }
                    }

                    else -> Unit
                }
            }

        }
    }

    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        savePlayProgress()
    }

    override suspend fun onClose() {
        savePlayProgress()
    }

    private suspend fun savePlayProgress() {
        val playbackState = context.player.playbackState.value
        val videoDurationMillis = context.player.mediaProperties.value?.durationMillis

        if (playbackState == PlaybackState.FINISHED) return

        val durationMillis = videoDurationMillis.let {
            if (it == null) return@let 0L
            return@let max(0, it - 1000) // 最后一秒不会保存进度
        }

        val currentPositionMillis = withContext(Dispatchers.Main.immediate) {
            context.player.getCurrentPositionMillis()
        }

        val episodeId = context.episodeIdFlow.value

        if (currentPositionMillis in 0..<durationMillis) {
            logger.info { "Saving position for epId=$episodeId: ${currentPositionMillis.milliseconds}" }
            playProgressRepository.saveOrUpdate(episodeId, currentPositionMillis)
        }
    }

    companion object : EpisodePlayerExtensionFactory<RememberPlayProgressExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): RememberPlayProgressExtension =
            RememberPlayProgressExtension(context, koin)

        private val logger = logger<RememberPlayProgressExtension>()
    }
}