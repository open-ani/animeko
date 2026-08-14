/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.SubjectEpisodeInfoBundle
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 记忆播放进度.
 *
 * 在以下情况时保存播放进度:
 * - 开始或恢复播放 5 秒后
 * - 播放中每分钟
 * - 切换数据源
 * - 暂停
 * - 播放完成
 */
class RememberPlayProgressExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
    private val periodicReportInterval: Duration = 1.minutes,
    private val initialReportDelay: Duration = 5.seconds,
) : PlayerExtension(name = "SaveProgressExtension") {
    private val playProgressRepository: EpisodePlayHistoryRepository by koin.inject()
    private val automationGate: PlaybackAutomationGate by koin.inject()
    private val latestInfoBundleMutex = Mutex()
    private val latestInfoBundles = mutableMapOf<Int, SubjectEpisodeInfoBundle>()

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        val mediaLoaded = CompletableDeferred<Unit>()
        backgroundTaskScope.launch("MediaLoadedListener") {
            context.subscribeEvents<EpisodeFetchSelectPlayState.MediaLoadedEvent>().collectLatest { event ->
                if (event.episodeId == episodeSession.episodeId && mediaLoaded.isActive) {
                    mediaLoaded.complete(Unit)
                }
            }
        }

        backgroundTaskScope.launch("InfoBundleCache") {
            episodeSession.infoBundleFlow.filterNotNull().collect { info ->
                latestInfoBundleMutex.withLock {
                    latestInfoBundles[info.episodeId] = info
                }
            }
        }

        backgroundTaskScope.launch("MediaSelectorListener") {
            mediaLoaded.await() // 播放器开始播放了再跑这个 extension
            episodeSession.fetchSelectFlow.collectLatest inner@{ fetchSelect ->
                if (fetchSelect == null) return@inner

                fetchSelect.mediaSelector.events.onBeforeSelect.collect {
                    // 切换 数据源 前保存播放进度
                    savePlayProgressOrRemove(episodeSession)
                }
            }
        }

        backgroundTaskScope.launch("PlaybackStateListener") {
            val player = context.player
            var haveResumedOnce = false
            player.state.collectLatest { state ->
                when {
                    state.mediaStatus == MediaStatus.Opening -> {
                        // 新媒体正在打开, 重置恢复进度标记
                        haveResumedOnce = false
                    }

                    state.isPlaying -> {
                        // Some backends (notably desktop mpv) report playing before the loaded file accepts seeks.
                        // Restore once metadata is ready, but only report after playback remains active for 5 seconds.
                        if (!haveResumedOnce) {
                            if (automationGate.suppressed.value) {
                                haveResumedOnce = true
                            } else {
                                val positionMillis =
                                    playProgressRepository.getPositionMillisByEpisodeId(episodeSession.episodeId)
                                if (positionMillis == null) {
                                    logger.info { "Did not find saved position" }
                                    haveResumedOnce = true
                                } else {
                                    logger.info {
                                        "Loaded saved position: $positionMillis, waiting for video properties"
                                    }
                                    player.mediaProperties.first { (it?.durationMillis ?: 0L) > 0L }
                                    withContext(Dispatchers.Main + NonCancellable) { // android must call in main thread
                                        logger.info {
                                            "Video properties ready, seeking to saved position: $positionMillis"
                                        }
                                        player.seekTo(positionMillis)
                                        haveResumedOnce = true
                                    }
                                }
                            }
                        }

                        delay(initialReportDelay)
                        savePlayProgressOrRemove(episodeSession, allowZeroPosition = true)

                        if (periodicReportInterval != Duration.INFINITE) {
                            while (true) {
                                delay(periodicReportInterval)
                                savePlayProgressOrRemove(episodeSession, allowZeroPosition = true)
                            }
                        }
                    }

                    state.mediaStatus == MediaStatus.Ready && !state.playWhenReady -> { // 暂停
                        mediaLoaded.await() // 播放器开始播放了一次之后再保存状态
                        savePlayProgressOrRemove(episodeSession)
                    }

                    state.mediaStatus == MediaStatus.Ended -> { // 播放完成
                        mediaLoaded.await() // 播放器开始播放了一次之后再保存状态
                        savePlayProgressOrRemove(episodeSession)
                    }

                    else -> Unit
                }
            }

        }
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        savePlayProgressOrRemove(context.getCurrentEpisodeId())
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    override suspend fun onClose() {
        savePlayProgressOrRemove(context.getCurrentEpisodeId())
    }

    private suspend fun savePlayProgressOrRemove(
        episodeSession: EpisodeSession,
        allowZeroPosition: Boolean = false,
    ) {
        savePlayProgressOrRemove(episodeSession.episodeId, episodeSession, allowZeroPosition)
    }

    private suspend fun savePlayProgressOrRemove(
        episodeId: Int
    ) {
        savePlayProgressOrRemove(episodeId, null)
    }

    private suspend fun savePlayProgressOrRemove(
        episodeId: Int,
        episodeSession: EpisodeSession?,
        allowZeroPosition: Boolean = false,
    ) {
        // A media replacement briefly reports the new stream from zero. Saving that transient
        // position would overwrite the real episode progress before PlayerSession restores it.
        if (automationGate.transientlySuppressed) {
            return
        }

        val player = context.player
        val mediaStatus = player.state.value.mediaStatus
        val videoDurationMillis = player.mediaProperties.value?.durationMillis

        if (videoDurationMillis == null || videoDurationMillis <= 0L) {
            return
        }

        // 只在媒体已加载 (Ready/Ended) 时保存
        if (mediaStatus != MediaStatus.Ready && mediaStatus != MediaStatus.Ended) {
            return
        }

        val currentPositionMillis = player.currentPositionMillis.value

        if (currentPositionMillis < 0L || (currentPositionMillis == 0L && !allowZeroPosition)) {
            return
        }

        if (videoDurationMillis - currentPositionMillis < 5000 || currentPositionMillis > videoDurationMillis) {
            playProgressRepository.remove(episodeId)
        } else {
            val info = latestInfoBundle(episodeId, episodeSession)
            playProgressRepository.saveOrUpdate(
                episodeId = episodeId,
                positionMillis = currentPositionMillis,
                subjectId = info?.subjectId,
                episodeSort = info?.episodeInfo?.sort?.number,
                subjectName = info?.subjectInfo?.displayName,
                subjectImageUrl = info?.subjectInfo?.imageLarge,
                episodeName = info?.episodeInfo?.displayName,
                durationMillis = videoDurationMillis,
            )
        }
    }

    private suspend fun latestInfoBundle(
        episodeId: Int,
        episodeSession: EpisodeSession?,
    ): SubjectEpisodeInfoBundle? {
        episodeSession?.infoBundleFlow?.replayCache?.lastOrNull()?.let { return it }

        return latestInfoBundleMutex.withLock {
            latestInfoBundles[episodeId]
        }
    }

    companion object : EpisodePlayerExtensionFactory<RememberPlayProgressExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): RememberPlayProgressExtension =
            RememberPlayProgressExtension(context, koin)

        private val logger = logger<RememberPlayProgressExtension>()
    }
}
