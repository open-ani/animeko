package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import me.him188.ani.app.domain.media.cache.CacheCleanupService
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 阅后即焚扩展
 * 在播放完成后自动删除缓存
 * 
 * 满足以下条件时才会执行阅后即焚：
 * 1. 播放进度超过 90%
 * 2. 播放时长超过 60 秒
 * 3. 不是由于用户手动跳转到结尾导致的播放完成
 * 4. 播放完成后等待 30 秒，期间如果开始新的播放或暂停则取消删除
 */
class BurnAfterReadExtension(
    private val context: PlayerExtensionContext,
    private val cacheCleanupService: CacheCleanupService,
) : PlayerExtension(name = "BurnAfterReadExtension") {
    private val logger = logger<BurnAfterReadExtension>()
    
    // 使用 Mutex 保护共享状态
    private val stateMutex = Mutex()
    private var state = PlayerState()
    
    // 使用线程安全的列表管理后台任务
    private val backgroundJobs = CopyOnWriteArrayList<Job>()
    
    // 扩展是否已停止
    private volatile var isStopped = false
    
    /**
     * 播放器状态数据类
     */
    private data class PlayerState(
        val lastSeekTime: Long = 0,
        val playStartTime: Long = 0,
        val isManualSeekToEnd: Boolean = false,
        val scheduledDeletionJob: Job? = null,
        val lastMediaData: TorrentMediaData? = null,
    )

    /**
     * 重置所有状态
     * @param cancelJobs 是否需要取消和等待任务完成
     */
    private suspend fun resetState(cancelJobs: Boolean = true) {
        // 先获取需要取消的任务
        val jobToCancel = if (cancelJobs) {
            stateMutex.withLock { 
                state.scheduledDeletionJob
            }
        } else null

        // 在锁外取消任务，避免死锁
        jobToCancel?.cancelAndJoin()

        // 更新状态
        stateMutex.withLock {
            state = PlayerState()
        }
        logger.info { "State reset" }
    }

    /**
     * 检查播放器是否处于活跃状态（播放中或暂停）
     */
    private fun isPlayerActive(state: PlaybackState): Boolean {
        return state == PlaybackState.PLAYING || state == PlaybackState.PAUSED
    }

    /**
     * 计算播放进度，处理除零情况
     */
    private fun calculateProgress(position: Long, duration: Long): Double {
        if (duration <= 0) return 0.0
        return position.toDouble() / duration.toDouble()
    }

    /**
     * 添加后台任务
     */
    private fun addBackgroundJob(job: Job) {
        if (!isStopped) {
            backgroundJobs.add(job)
            // 添加完成回调，自动从列表中移除已完成的任务
            job.invokeOnCompletion { 
                backgroundJobs.remove(job)
            }
        } else {
            // 如果扩展已停止，立即取消新任务
            job.cancel()
        }
    }

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        isStopped = false
        
        // 初始状态重置
        val initJob = backgroundTaskScope.launch {
            resetState()
        }
        addBackgroundJob(initJob)
        
        // 监听媒体数据变化
        val mediaJob = backgroundTaskScope.launch("MediaDataListener") {
            context.player.mediaData.collect { mediaData ->
                if (!isStopped) {
                    stateMutex.withLock {
                        if (mediaData != state.lastMediaData) {
                            resetState(cancelJobs = true)
                            // 安全的类型转换
                            state = state.copy(
                                lastMediaData = (mediaData as? TorrentMediaData).also {
                                    if (it == null && mediaData != null) {
                                        logger.info { "Media data is not TorrentMediaData type: ${mediaData::class.simpleName}" }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        addBackgroundJob(mediaJob)
        
        // 监听播放状态和进度
        val stateJob = backgroundTaskScope.launch("PlaybackStateListener") {
            combine(
                context.player.playbackState,
                context.player.currentPosition,
                context.player.duration,
            ) { state, position, duration ->
                Triple(state, position, duration)
            }.collect { (playbackState, position, duration) ->
                if (!isStopped) {
                    when (playbackState) {
                        PlaybackState.PLAYING -> {
                            stateMutex.withLock {
                                // 只要开始播放就重置状态，不再检查 playStartTime
                                resetState(cancelJobs = true)
                                state = state.copy(playStartTime = System.currentTimeMillis())
                                logger.info { "New playback started, resetting state" }
                            }
                        }
                        PlaybackState.FINISHED -> {
                            val mediaData = context.player.mediaData.value as? TorrentMediaData ?: return@collect
                            
                            stateMutex.withLock {
                                // 检查是否满足阅后即焚条件
                                val playDuration = System.currentTimeMillis() - state.playStartTime
                                val progress = calculateProgress(position, duration)
                                val isLongEnoughPlay = playDuration >= 60.seconds.inWholeMilliseconds
                                val isProgressEnough = progress >= 0.9
                                val isNaturalEnd = !state.isManualSeekToEnd
                                
                                if (isLongEnoughPlay && isProgressEnough && isNaturalEnd) {
                                    try {
                                        logger.info { "Playback completed and conditions met, will execute burn-after-read in 30 seconds" }
                                        // 取消之前的删除任务（如果有）
                                        state.scheduledDeletionJob?.cancel()
                                        // 创建新的删除任务
                                        val deletionJob = backgroundTaskScope.launch {
                                            try {
                                                delay(30.seconds) // 等待30秒
                                                // 再次检查扩展是否已停止
                                                if (!isStopped) {
                                                    val currentState = context.player.playbackState.value
                                                    if (!isPlayerActive(currentState)) {
                                                        logger.info { "Delay ended, player state: $currentState, executing burn-after-read" }
                                                        withContext(Dispatchers.IO) {
                                                            cacheCleanupService.handleBurnAfterRead(mediaData)
                                                        }
                                                        resetState(cancelJobs = false)
                                                    } else {
                                                        logger.info { "Player is active: $currentState, cancelling burn-after-read" }
                                                        // 如果播放器处于活跃状态，也重置状态
                                                        resetState(cancelJobs = true)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                logger.error(e) { "Failed to execute deletion task" }
                                            }
                                        }
                                        state = state.copy(scheduledDeletionJob = deletionJob)
                                        addBackgroundJob(deletionJob)
                                    } catch (e: Exception) {
                                        logger.error(e) { "Burn-after-read failed" }
                                    }
                                } else {
                                    logger.info { 
                                        "Burn-after-read conditions not met: playDuration=${playDuration}ms, " +
                                        "progress=$progress, isManualSeekToEnd=${state.isManualSeekToEnd}" 
                                    }
                                }
                            }
                        }
                        PlaybackState.STOPPED -> {
                            val stopJob = backgroundTaskScope.launch {
                                resetState(cancelJobs = true)
                                logger.info { "Playback stopped, resetting state" }
                            }
                            addBackgroundJob(stopJob)
                        }
                        else -> Unit
                    }
                }
            }
        }
        addBackgroundJob(stateJob)
        
        // 监听用户手动跳转
        val seekJob = backgroundTaskScope.launch("SeekPositionListener") {
            context.player.seekPosition.collect { seekPosition ->
                if (!isStopped) {
                    val duration = context.player.duration.first()
                    if (duration > 0) {
                        val now = System.currentTimeMillis()
                        stateMutex.withLock {
                            val progress = calculateProgress(seekPosition, duration)
                            // 如果用户在最近 2 秒内手动跳转到接近结尾的位置
                            if (progress >= 0.9 && now - state.lastSeekTime <= 2000) {
                                state = state.copy(
                                    isManualSeekToEnd = true,
                                    lastSeekTime = now
                                )
                                logger.info { "Detected manual seek to end" }
                            } else {
                                state = state.copy(lastSeekTime = now)
                            }
                        }
                    }
                }
            }
        }
        addBackgroundJob(seekJob)
    }

    override fun onStop() {
        // 标记扩展已停止
        isStopped = true
        
        // 取消所有后台任务
        backgroundJobs.toList().forEach { job ->
            job.cancel()
        }
        backgroundJobs.clear()
        
        // 在新的协程中清理状态，但不等待完成
        // 因为 onStop 可能在主线程调用，我们不应该阻塞它
        backgroundTaskScope.launch {
            try {
                resetState(cancelJobs = true)
                logger.info { "Extension stopped, resources cleaned up" }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error(e) { "Failed to clean up resources during shutdown" }
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<BurnAfterReadExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): BurnAfterReadExtension =
            BurnAfterReadExtension(
                context = context,
                cacheCleanupService = koin.get(),
            )
    }
} 