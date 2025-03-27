package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.him188.ani.app.domain.media.cache.CacheCleanupService
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.error
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.mutate
import kotlin.math.absoluteValue

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
) : PlayerExtension("BurnAfterRead") {
    private val logger = logger<BurnAfterReadExtension>()
    private var isStopped = false
    private val backgroundJobsFlow = MutableStateFlow(persistentListOf<Job>())
    private val stateMutex = Mutex()

    private interface BurnCondition {
        val result: Flow<Boolean>
        suspend fun reset()
    }

    // 获取MediampPlayer的当前状态
    private fun getCurrentPosition(): Long = try {
        (context.player.currentPositionMillis as? StateFlow<Long>)?.value ?: 0L
    } catch (e: Exception) {
        logger.error("Failed to get current position", e)
        0L
    }
    
    private fun getTotalDuration(): Long = try {
        val properties = (context.player.mediaProperties as? StateFlow<*>)?.value
        if (properties is org.openani.mediamp.metadata.MediaProperties) {
            properties.durationMillis ?: 0L
        } else {
            0L
        }
    } catch (e: Exception) {
        logger.error("Failed to get total duration", e)
        0L
    }
    
    private fun getCurrentMedia() = try {
        // 采用更安全的方式访问mediaData
        // 不使用.value直接访问，而是使用反射或其他方式
        context.player.mediaData.let { flow ->
            // 尝试通过StateFlow接口访问
            (flow as? StateFlow<*>)?.value
        }
    } catch (e: Exception) {
        logger.error("Failed to get current media", e)
        null
    }
    
    // 定义一个SeekEvent类型，因为没有找到该类型的定义
    private data class SeekEvent(val position: Long)
    
    private inner class PlaybackProgressCondition : BurnCondition {
        override val result: Flow<Boolean> = combine(
            context.player.currentPositionMillis,
            context.player.mediaProperties.map { it?.durationMillis ?: 0L }
        ) { position: Long, duration: Long ->
            if (duration > 0) (position.toDouble() / duration) >= 0.9 else false
        }
        
        override suspend fun reset() {} // No state to reset
    }

    private inner class PlayDurationCondition : BurnCondition {
        private val _startTime = MutableStateFlow(0L)
        
        override val result: Flow<Boolean> = _startTime.map { startTime ->
            if (startTime == 0L) false
            else (System.currentTimeMillis() - startTime) >= 60.seconds.inWholeMilliseconds
        }
        
        init {
            context.coroutineScope.launch {
                context.player.playbackState
                    .onEach { state ->
                        if (state == PlaybackState.PLAYING) {
                            _startTime.value = System.currentTimeMillis()
                        }
                    }
                    .launchIn(context.coroutineScope)
            }
        }
        
        override suspend fun reset() {
            _startTime.value = 0L
        }
    }

    private inner class NaturalEndCondition : BurnCondition {
        private val _isManualSeekToEnd = MutableStateFlow(false)
        
        override val result: Flow<Boolean> = _isManualSeekToEnd.map { !it }
        
        init {
            context.coroutineScope.launch {
                // 监控大幅度的位置变化，这可能是手动跳转
                var lastPosition = 0L
                context.player.currentPositionMillis
                    .collect { currentPosition ->
                        val duration = getTotalDuration()
                        if (duration > 0) {
                            // 如果位置接近视频结尾，且变化较大（可能是手动跳转）
                            val progress = currentPosition.toDouble() / duration
                            val positionJump = if (currentPosition > lastPosition) 
                                currentPosition - lastPosition 
                            else 
                                lastPosition - currentPosition
                            if (progress >= 0.9 && positionJump > 5000) { // 5秒的跳转视为手动操作
                                _isManualSeekToEnd.value = true
                            }
                            lastPosition = currentPosition
                        }
                    }
            }
        }
        
        override suspend fun reset() {
            _isManualSeekToEnd.value = false
        }
    }

    private inner class WaitingPeriodCondition : BurnCondition {
        private val _isWaitingComplete = MutableStateFlow(false)
        private var waitingJob: Job? = null
        
        override val result: Flow<Boolean> = _isWaitingComplete
        
        fun startWaiting() {
            waitingJob?.cancel()
            waitingJob = context.coroutineScope.launch {
                delay(30.seconds)
                // 安全地访问播放状态
                val state = try {
                    context.player.playbackState.let { flow ->
                        (flow as? StateFlow<*>)?.value as? PlaybackState
                    }
                } catch (e: Exception) {
                    null
                }
                if (!isStopped && state != null && !isPlayerActive(state)) {
                    _isWaitingComplete.value = true
                }
            }
        }
        
        override suspend fun reset() {
            waitingJob?.cancel()
            _isWaitingComplete.value = false
        }
    }

    private fun isPlayerActive(state: PlaybackState): Boolean =
        state == PlaybackState.PLAYING || state == PlaybackState.READY

    private suspend fun resetAllConditions(conditions: List<BurnCondition>) {
        conditions.forEach { it.reset() }
    }

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        val conditions = listOf(
            PlaybackProgressCondition(),
            PlayDurationCondition(),
            NaturalEndCondition(),
            WaitingPeriodCondition()
        )

        val burnJob = backgroundTaskScope.launch("BurnAfterReadMonitor") {
            val waitingCondition = conditions.last() as WaitingPeriodCondition
            
            context.player.playbackState
                .onEach { state ->
                    when (state) {
                        PlaybackState.PLAYING -> {
                            stateMutex.withLock {
                                resetAllConditions(conditions)
                            }
                        }
                        PlaybackState.FINISHED -> {
                            waitingCondition.startWaiting()
                        }
                        PlaybackState.PAUSED -> {
                            stateMutex.withLock {
                                resetAllConditions(conditions)
                            }
                        }
                        else -> {}
                    }
                }
                .launchIn(this)

            combine(conditions.map { it.result }) { results: Array<Boolean> ->
                results.all { it }
            }
            .filter { it }
            .collect {
                val mediaData = getCurrentMedia() as? TorrentMediaData ?: return@collect
                try {
                    logger.info { "All conditions met, executing burn-after-read" }
                    withContext(Dispatchers.IO) {
                        cacheCleanupService.handleBurnAfterRead(mediaData)
                    }
                    resetAllConditions(conditions)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.error("Failed to execute burn-after-read", e)
                }
            }
        }
        
        addBackgroundJob(burnJob)
    }

    private fun addBackgroundJob(job: Job) {
        backgroundJobsFlow.value = backgroundJobsFlow.value.mutate { jobs ->
            jobs.add(job)
        }
        job.invokeOnCompletion { 
            backgroundJobsFlow.value = backgroundJobsFlow.value.mutate { jobs ->
                jobs.remove(job)
            }
        }
    }

    override suspend fun onClose() {
        isStopped = true
        try {
            backgroundJobsFlow.value.forEach { it.cancel() }
            backgroundJobsFlow.value = persistentListOf()
            logger.info { "Extension stopped, resources cleaned up" }
        } catch (e: Exception) {
            logger.error("Failed to clean up resources during shutdown", e)
        }
    }

    companion object : EpisodePlayerExtensionFactory<BurnAfterReadExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): BurnAfterReadExtension {
            return BurnAfterReadExtension(
                context = context,
                cacheCleanupService = koin.get(),
            )
        }
    }
} 