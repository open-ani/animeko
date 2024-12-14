/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.state

import androidx.annotation.UiThread
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.Context
import me.him188.ani.app.videoplayer.data.FileVideoData
import me.him188.ani.app.videoplayer.data.VideoData
import me.him188.ani.app.videoplayer.data.VideoProperties
import me.him188.ani.app.videoplayer.data.VideoSource
import me.him188.ani.app.videoplayer.data.VideoSourceOpenException
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.thisLogger
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * A controller for the [VideoPlayer].
 */
@Stable
interface PlayerState {
    /**
     * Current state of the player.
     *
     * State can be changed internally e.g. buffer exhausted or externally by e.g. [pause], [resume].
     */
    val state: StateFlow<PlaybackState>

    /**
     * The video source that is currently being played.
     */
    val videoSource: StateFlow<VideoSource<*>?>

    /**
     * The video data of the currently playing video.
     */
    val videoData: Flow<VideoData?>

    /**
     * 视频数据缓存进度
     */
    val cacheProgress: MediaCacheProgressState

    /**
     * Sets the video source to play, by [opening][VideoSource.open] the [source],
     * updating [videoSource], and resetting the progress to 0.
     *
     * Suspends until the new source has been updated.
     *
     * If this function failed to [start video streaming][VideoSource.open], it will throw an exception.
     *
     * This function must not be called on the main thread as it will call [VideoSource.open].
     *
     * @param source the video source to play. `null` to stop playing.
     * @throws VideoSourceOpenException 当打开失败时抛出, 包含原因
     */
    @Throws(VideoSourceOpenException::class, CancellationException::class)
    suspend fun setVideoSource(source: VideoSource<*>)

    /**
     * 停止播放并清除上次[设置][setVideoSource]的视频源. 之后还可以通过 [setVideoSource] 恢复播放.
     */
    suspend fun clearVideoSource()

    /**
     * Properties of the video being played.
     *
     * Note that it may not be available immediately after [setVideoSource] returns,
     * since the properties may be callback from the underlying player implementation.
     */
    val videoProperties: StateFlow<VideoProperties?>

    /**
     * 是否正在 buffer (暂停视频中)
     */
    val isBuffering: Flow<Boolean>

    /**
     * Current position of the video being played.
     *
     * `0` if no video is being played.
     */
    val currentPositionMillis: StateFlow<Long>

    @UiThread
    fun getExactCurrentPositionMillis(): Long

    /**
     * `0..100`
     */
    val bufferedPercentage: StateFlow<Int>

    /**
     * 当前播放进度比例 `0..1`
     */
    val playProgress: Flow<Float>

    /**
     * 暂停播放, 直到 [pause]
     */
    @UiThread
    fun pause()

    /**
     * 恢复播放
     */
    @UiThread
    fun resume()

    /**
     * 停止播放, 之后不能恢复, 必须 [setVideoSource]
     */
    @UiThread
    fun stop()

    /**
     * 视频播放速度 (倍速)
     *
     * 1.0 为原速度, 2.0 为两倍速度, 0.5 为一半速度, etc.
     */
    val playbackSpeed: StateFlow<Float>

    @UiThread
    fun setPlaybackSpeed(speed: Float)

    /**
     * 跳转到指定位置
     */
    @UiThread
    fun seekTo(positionMillis: Long)

    /**
     * 快进或快退一段时间. 正数为快进, 负数为快退.
     */
    @UiThread
    fun skip(deltaMillis: Long) {
        seekTo(currentPositionMillis.value + deltaMillis)
    }

    val subtitleTracks: TrackGroup<SubtitleTrack>

    val audioTracks: TrackGroup<AudioTrack>

    fun saveScreenshotFile(filename: String)

    val chapters: StateFlow<ImmutableList<Chapter>>
}

@Immutable
data class Chapter(
    val name: String,
    val durationMillis: Long,
    val offsetMillis: Long
)

fun PlayerState.togglePause() {
    if (state.value.isPlaying) {
        pause()
    } else {
        resume()
    }
}

typealias CacheProgressStateFactory<T> = (T, State<Boolean>) -> UpdatableMediaCacheProgressState?

// TODO: 这可能不是很好, 但这是最不入侵现有代码的修改方案了
object CacheProgressStateFactoryManager : SynchronizedObject() {
    private val factories: MutableMap<KClass<*>, CacheProgressStateFactory<*>> =
        mutableMapOf()

    fun <T : VideoData> register(kClass: KClass<T>, factory: CacheProgressStateFactory<T>) = synchronized(this) {
        factories[kClass] = factory
    }

    fun create(videoData: VideoData, isCacheFinished: State<Boolean>): UpdatableMediaCacheProgressState? =
        synchronized(this) {
            return factories[videoData::class]?.let { factory ->
                @Suppress("UNCHECKED_CAST")
                factory as CacheProgressStateFactory<VideoData>
                factory(videoData, isCacheFinished)
            }
        }
}

abstract class AbstractPlayerState<D : AbstractPlayerState.Data>(
    parentCoroutineContext: CoroutineContext,
) : PlayerState, SynchronizedObject() {
    protected val backgroundScope = CoroutineScope(
        parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]),
    ).apply {
        coroutineContext.job.invokeOnCompletion {
            close()
        }
    }

    protected val logger = thisLogger()
    override val videoSource: MutableStateFlow<VideoSource<*>?> = MutableStateFlow(null)

    override val state: MutableStateFlow<PlaybackState> = MutableStateFlow(PlaybackState.PAUSED_BUFFERING)

    /**
     * Currently playing resource that should be closed when the controller is closed.
     * @see setVideoSource
     */
    protected val openResource = MutableStateFlow<D?>(null)

    open class Data(
        open val videoSource: VideoSource<*>,
        open val videoData: VideoData,
        open val releaseResource: () -> Unit,
    )

    override val isBuffering: Flow<Boolean> by lazy {
        state.map { it == PlaybackState.PAUSED_BUFFERING }
    }

    final override val videoData: Flow<VideoData?> = openResource.map {
        it?.videoData
    }

    private val isCacheFinishedState = videoData.flatMapLatest {
        it?.isCacheFinished ?: flowOf(false)
    }.produceState(false, backgroundScope)

    private val cacheProgressFlow = videoData.map {
        when (it) {
            null -> staticMediaCacheProgressState(ChunkState.NONE)

            is FileVideoData -> staticMediaCacheProgressState(ChunkState.DONE)

            else ->
                CacheProgressStateFactoryManager.create(it, isCacheFinishedState)
                    ?: staticMediaCacheProgressState(ChunkState.NONE)
        }
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(), staticMediaCacheProgressState(ChunkState.NONE))

    protected open suspend fun cacheProgressLoop() {
        while (true) {
            cacheProgressFlow.value.update()
            delay(1000)
        }
    }

    init {
        backgroundScope.launch {
            cacheProgressLoop()
        }
    }

    override val cacheProgress: UpdatableMediaCacheProgressState by
    cacheProgressFlow.produceState(staticMediaCacheProgressState(ChunkState.NONE), backgroundScope)

    final override val playProgress: Flow<Float> by lazy {
        combine(videoProperties.filterNotNull(), currentPositionMillis) { properties, duration ->
            if (properties.durationMillis == 0L) {
                return@combine 0f
            }
            (duration / properties.durationMillis).toFloat().coerceIn(0f, 1f)
        }
    }

    final override suspend fun setVideoSource(source: VideoSource<*>) {
        val previousResource = openResource.value
        if (source == previousResource?.videoSource) {
            return
        }

        openResource.value = null
        previousResource?.releaseResource?.invoke()

        val opened = try {
            openSource(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to open VideoSource: $source" }
            throw e
        }

        try {
            logger.info { "Initializing player with VideoSource: $source" }
            state.value = PlaybackState.PAUSED_BUFFERING
            startPlayer(opened)
            logger.info { "Player is now initialized with media and will play when ready" }
        } catch (e: CancellationException) {
            opened.releaseResource()
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Player failed to initialize" }
            opened.releaseResource()
            throw e
        }

        this.openResource.value = opened
    }

    final override suspend fun clearVideoSource() {
        logger.info { "clearVideoSource: Cleaning up player" }
        cleanupPlayer()
        this.videoSource.value = null
        this.openResource.value = null
    }

    fun closeVideoSource() {
        synchronized(this) {
            val value = openResource.value
            openResource.value = null
            value?.releaseResource?.invoke()
        }
    }

    final override fun stop() {
        stopImpl()
        closeVideoSource()
    }

    protected abstract fun stopImpl()

    /**
     * 开始播放
     */
    protected abstract suspend fun startPlayer(data: D)

    /**
     * 停止播放, 因为要释放资源了
     */
    protected abstract suspend fun cleanupPlayer()

    @Throws(VideoSourceOpenException::class, CancellationException::class)
    protected abstract suspend fun openSource(source: VideoSource<*>): D

    @Volatile
    private var closed = false
    fun close() {
        if (closed) return
        synchronized(this) {
            if (closed) return
            closed = true

            closeImpl()
            closeVideoSource()
            backgroundScope.cancel()
        }
    }

    protected abstract fun closeImpl()
}


enum class PlaybackState(
    val isPlaying: Boolean,
) {
    /**
     * Player is loaded and will be playing as soon as metadata and first frame is available.
     */
    READY(isPlaying = false),

    /**
     * 用户主动暂停. buffer 继续充, 但是充好了也不要恢复 [PLAYING].
     */
    PAUSED(isPlaying = false),

    PLAYING(isPlaying = true),

    /**
     * 播放中但因没 buffer 就暂停了. buffer 填充后恢复 [PLAYING].
     */
    PAUSED_BUFFERING(isPlaying = false),

    FINISHED(isPlaying = false),

    ERROR(isPlaying = false),
    ;
}

fun interface PlayerStateFactory {
    /**
     * Creates a new [PlayerState]
     * [parentCoroutineContext] must have a [Job] so that the player state is bound to the parent coroutine context scope.
     *
     * @param context the platform context to create the underlying player implementation.
     * It is only used by the constructor and not stored.
     */
    fun create(context: Context, parentCoroutineContext: CoroutineContext): PlayerState
}

interface SupportsAudio {

    val volume: StateFlow<Float>
    val isMute: StateFlow<Boolean>
    val maxValue: Float

    fun toggleMute(mute: Boolean? = null)

    @UiThread
    fun setVolume(volume: Float)

    @UiThread
    fun volumeUp(value: Float = 0.05f)

    @UiThread
    fun volumeDown(value: Float = 0.05f)
}

/**
 * For previewing
 */
class DummyPlayerState(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractPlayerState<AbstractPlayerState.Data>(parentCoroutineContext), SupportsAudio {
    override val state: MutableStateFlow<PlaybackState> = MutableStateFlow(PlaybackState.PLAYING)
    override fun stopImpl() {

    }

    override suspend fun cleanupPlayer() {
        // no-op
    }

    override suspend fun openSource(source: VideoSource<*>): Data {
        val data = source.open()
        return Data(
            source,
            data,
            releaseResource = {
                backgroundScope.launch(NonCancellable) {
                    data.close()
                }
            },
        )
    }

    override fun closeImpl() {
    }

    override suspend fun startPlayer(data: Data) {
        // no-op
    }

    override suspend fun cacheProgressLoop() {
        // no-op
        // 测试的时候 delay 会被直接跳过, 导致死循环
    }

    override val videoSource: MutableStateFlow<VideoSource<*>?> = MutableStateFlow(null)

    override val videoProperties: MutableStateFlow<VideoProperties> = MutableStateFlow(
        VideoProperties(
            title = "Test Video",
            durationMillis = 100_000,
        ),
    )
    override val currentPositionMillis = MutableStateFlow(10_000L)
    override fun getExactCurrentPositionMillis(): Long {
        return currentPositionMillis.value
    }

    override val bufferedPercentage: StateFlow<Int> = MutableStateFlow(50)

    override fun pause() {
        state.value = PlaybackState.PAUSED
    }

    override fun resume() {
        state.value = PlaybackState.PLAYING
    }

    override val playbackSpeed: MutableStateFlow<Float> = MutableStateFlow(1.0f)

    override fun setPlaybackSpeed(speed: Float) {
        playbackSpeed.value = speed
    }

    override fun seekTo(positionMillis: Long) {
        this.currentPositionMillis.value = positionMillis
    }

    override val subtitleTracks: TrackGroup<SubtitleTrack> = emptyTrackGroup()
    override val audioTracks: TrackGroup<AudioTrack> = emptyTrackGroup()

    override val volume: MutableStateFlow<Float> = MutableStateFlow(0f)
    override val isMute: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val maxValue: Float = 1f

    override fun toggleMute(mute: Boolean?) {
        isMute.value = mute ?: !isMute.value
    }

    override fun setVolume(volume: Float) {
        this.volume.value = volume
    }

    override fun volumeUp(value: Float) {
        setVolume(volume.value + value)
    }

    override fun volumeDown(value: Float) {
        setVolume(volume.value - value)
    }

    override fun saveScreenshotFile(filename: String) {
    }

    override val chapters: StateFlow<ImmutableList<Chapter>> = MutableStateFlow(
        persistentListOf(
            Chapter("chapter1", durationMillis = 90_000L, 0L),
            Chapter("chapter2", durationMillis = 5_000L, 90_000L),
        ),
    )
}

/**
 * Collects the flow on the main thread into a [State].
 */
private fun <T> Flow<T>.produceState(
    initialValue: T,
    scope: CoroutineScope,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    val state = mutableStateOf(initialValue)
    scope.launch(coroutineContext + Dispatchers.Main) {
        flowOn(Dispatchers.Default) // compute in background
            .collect {
                // update state in main
                state.value = it
            }
    }
    return state
}
