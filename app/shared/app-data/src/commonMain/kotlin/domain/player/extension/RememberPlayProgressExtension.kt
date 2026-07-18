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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.SubjectEpisodeInfoBundle
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.PlaybackState
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
            player.playbackState.collectLatest { playbackState ->
                when (playbackState) {
                    PlaybackState.READY -> {
                        haveResumedOnce = false
                    }

                    PlaybackState.PLAYING -> {
                        // Restore immediately, but only report after PLAYING has remained active for 5 seconds.
                        withContext(NonCancellable) {
                            if (!haveResumedOnce) {
                                val positionMillis =
                                    playProgressRepository.getPositionMillisByEpisodeId(episodeSession.episodeId)
                                if (positionMillis == null) {
                                    logger.info { "Did not find saved position" }
                                } else {
                                    logger.info { "Loaded saved position: $positionMillis, seeking to $positionMillis" }
                                    withContext(Dispatchers.Main) { // android must call in main thread
                                        player.seekTo(positionMillis)
                                    }
                                    haveResumedOnce = true
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

                    PlaybackState.PAUSED -> {
                        mediaLoaded.await() // 播放器开始播放了一次之后再保存状态
                        savePlayProgressOrRemove(episodeSession)
                    }

                    PlaybackState.FINISHED -> {
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
        val player = context.player
        val playbackState = player.playbackState.value
        val videoDurationMillis = player.mediaProperties.value?.durationMillis

        if (videoDurationMillis == null || videoDurationMillis <= 0L) {
            return
        }

        when (playbackState) {
            PlaybackState.DESTROYED,
            PlaybackState.CREATED,
            PlaybackState.READY,
            PlaybackState.ERROR -> return

            PlaybackState.FINISHED,
            PlaybackState.PAUSED,
            PlaybackState.PLAYING,
            PlaybackState.PAUSED_BUFFERING -> {
                val currentPositionMillis = withContext(Dispatchers.Main.immediate) {
                    try {
                        player.getCurrentPositionMillis()
                    } catch (e: Error) {
                        // Caused by: java.lang.Error: Invalid memory access
                        // https://github.com/open-ani/animeko/issues/1787
                        0L
                    }
                }

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
                return
            }
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
