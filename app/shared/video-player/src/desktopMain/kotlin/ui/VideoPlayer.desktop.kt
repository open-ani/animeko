/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.ptr.PointerByReference
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import me.him188.ani.app.videoplayer.HttpStreamingVideoSource
import me.him188.ani.app.videoplayer.data.VideoData
import me.him188.ani.app.videoplayer.data.VideoProperties
import me.him188.ani.app.videoplayer.data.VideoSource
import me.him188.ani.app.videoplayer.io.SeekableInputCallbackMedia
import me.him188.ani.app.videoplayer.ui.VlcjVideoPlayerState.VlcjData
import me.him188.ani.app.videoplayer.ui.state.AbstractPlayerState
import me.him188.ani.app.videoplayer.ui.state.AudioTrack
import me.him188.ani.app.videoplayer.ui.state.Chapter
import me.him188.ani.app.videoplayer.ui.state.Label
import me.him188.ani.app.videoplayer.ui.state.MutableTrackGroup
import me.him188.ani.app.videoplayer.ui.state.PlaybackState
import me.him188.ani.app.videoplayer.ui.state.PlayerState
import me.him188.ani.app.videoplayer.ui.state.SubtitleTrack
import me.him188.ani.app.videoplayer.ui.state.SupportsAudio
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.trace
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.Media
import uk.co.caprica.vlcj.media.MediaEventAdapter
import uk.co.caprica.vlcj.media.MediaParsedStatus
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createDirectories
import kotlin.math.roundToInt


@Stable
class VlcjVideoPlayerState(parentCoroutineContext: CoroutineContext) : PlayerState,
    AbstractPlayerState<VlcjData>(parentCoroutineContext), SupportsAudio {
    companion object {
        private val createPlayerLock = ReentrantLock() // 如果同时加载可能会 SIGSEGV
        fun prepareLibraries() {
            createPlayerLock.withLock {
                NativeDiscovery().discover()
                CallbackMediaPlayerComponent().release()
            }
        }
    }

    //    val mediaPlayerFactory = MediaPlayerFactory(
//        "--video-title=vlcj video output",
//        "--no-snapshot-preview",
//        "--intf=dummy",
//        "-v"
//    )

    private val factory = MediaPlayerFactory("-v")

    val player: EmbeddedMediaPlayer = createPlayerLock.withLock {
        factory
            .mediaPlayers()
            .newEmbeddedMediaPlayer()
    }
    val surface = SkiaBitmapVideoSurface().apply {
        player.videoSurface().set(this) // 只能 attach 一次
        attach(player)
    }

    override val state: MutableStateFlow<PlaybackState> = MutableStateFlow(PlaybackState.PAUSED_BUFFERING)

    init {
        backgroundScope.launch {
            state.collect {
                surface.enableRendering.value = it == PlaybackState.PLAYING
            }
        }
    }

    override fun stopImpl() {
        currentPositionMillis.value = 0L
        player.submit {
            player.controls().stop()
        }
    }

    override fun saveScreenshotFile(filename: String) {
        player.submit {
            val ppszPath = PointerByReference()
            Shell32.INSTANCE.SHGetKnownFolderPath(KnownFolders.FOLDERID_Pictures, 0, null, ppszPath)
            val picturesPath = ppszPath.value.getWideString(0)
            val screenshotPath: Path = Path.of(picturesPath).resolve("Ani")
            try {
                screenshotPath.createDirectories()
            } catch (ex: IOException) {
                logger.warn("Create ani pictures dir fail", ex)
            }
            val filePath = screenshotPath.resolve(filename)
            player.snapshots().save(filePath.toFile())
        }
    }

    override val chapters: MutableStateFlow<ImmutableList<Chapter>> = MutableStateFlow(persistentListOf())

    class VlcjData(
        override val videoSource: VideoSource<*>,
        override val videoData: VideoData,
        val setPlay: () -> Unit,
        releaseResource: () -> Unit
    ) : Data(videoSource, videoData, releaseResource)

    override suspend fun openSource(source: VideoSource<*>): VlcjData {
        if (source is HttpStreamingVideoSource) {
            return VlcjData(
                source,
                source.open(), // no need to close
                setPlay = {

                    player.media().play(
                        source.uri,
                        *buildList {
                            add("http-user-agent=${source.webVideo.headers["User-Agent"] ?: "Mozilla/5.0"}")
                            val referer = source.webVideo.headers["Referer"]
                            if (referer != null) {
                                add("http-referrer=${referer}")
                            }
                        }.toTypedArray(),
                    )
                    lastMedia = null
                },
                releaseResource = {},
            )
        }

        val data = source.open()

        val awaitContext = SupervisorJob(backgroundScope.coroutineContext[Job])
        val input = data.createInput()
        return VlcjData(
            source,
            data,
            setPlay = {
                val new = SeekableInputCallbackMedia(input) { awaitContext.cancel() }
                player.controls().stop()
                player.media().play(new)
                lastMedia = new
            },
            releaseResource = {
                logger.trace { "VLC ReleaseResource: begin" }
                awaitContext.cancel()
                logger.trace { "VLC ReleaseResource: close input" }
                input.close()
                logger.trace { "VLC ReleaseResource: close VideoData" }
                backgroundScope.launch(NonCancellable) {
                    data.close()
                }
            },
        )
    }

    override fun closeImpl() {
        lastMedia?.onClose() // 在调用 VLC 之前停止阻塞线程
        lastMedia = null
        backgroundScope.launch(NonCancellable) {
            logger.trace { "VLC closeImpl: release player" }
            player.release()
            logger.trace { "VLC closeImpl: release player" }
        }
    }

    private var lastMedia: SeekableInputCallbackMedia? = null // keep referenced so won't be gc'ed

    override suspend fun startPlayer(data: VlcjData) {
        data.setPlay()

//        player.media().options().add(*arrayOf(":avcodec-hw=none")) // dxva2
//        player.controls().play()
//        player.media().play/*OR .start*/(data.videoData.file.absolutePath)
    }

    override suspend fun cleanupPlayer() {
        lastMedia?.onClose() // 在调用 VLC 之前停止阻塞线程
        player.submit {
            player.controls().stop()
        }
        withContext(Dispatchers.Main) {
            surface.clearBitmap()
        }
    }

    override val videoProperties: MutableStateFlow<VideoProperties?> = MutableStateFlow(null)
    override val currentPositionMillis: MutableStateFlow<Long> = MutableStateFlow(0)

    override fun getExactCurrentPositionMillis(): Long = player.status().time()

    override val bufferedPercentage = MutableStateFlow(0)
    override val isBuffering: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override fun pause() {
        player.submit {
            player.controls().pause()
        }
    }

    override fun resume() {
        player.submit {
            player.controls().play()
        }
    }

    override val playbackSpeed: MutableStateFlow<Float> = MutableStateFlow(1.0f)
    override val subtitleTracks: MutableTrackGroup<SubtitleTrack> = MutableTrackGroup()
    override val audioTracks: MutableTrackGroup<AudioTrack> = MutableTrackGroup()

    override val volume: MutableStateFlow<Float> = MutableStateFlow(1f)
    override val isMute: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val maxValue: Float = 2f

    override fun toggleMute(mute: Boolean?) {
        if (player.audio().isMute == mute) {
            return
        }
        isMute.value = mute ?: !isMute.value
        player.audio().mute()
    }

    override fun setVolume(volume: Float) {
        this.volume.value = volume.coerceIn(0f, maxValue)
        player.audio().setVolume(volume.times(100).roundToInt())
    }

    override fun volumeUp(value: Float) {
        setVolume(volume.value + value)
    }

    override fun volumeDown(value: Float) {
        setVolume(volume.value - value)
    }

    init {
        // NOTE: must not call native player in a event
        player.events().addMediaEventListener(
            object : MediaEventAdapter() {
                override fun mediaParsedChanged(media: Media, newStatus: MediaParsedStatus) {
                    if (newStatus == MediaParsedStatus.DONE) {
                        createVideoProperties()?.let {
                            videoProperties.value = it
                        }
                        state.value = PlaybackState.READY
                    }
                }
            },
        )
        player.events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    // 对于 m3u8, 这个 callback 会先调用
                    videoProperties.value = videoProperties.value?.copy(
                        durationMillis = newLength,
                    ) ?: VideoProperties(
                        title = null,
                        durationMillis = newLength, // 至少要把 length 放进去, 否则会一直显示缓冲
                    )
                }

                override fun elementaryStreamAdded(mediaPlayer: MediaPlayer?, type: TrackType?, id: Int) {
                    if (type == TrackType.TEXT) {
                        reloadSubtitleTracks() // 字幕轨道更新后，则进行重载UI上的字幕轨道
                    }
                    if (type == TrackType.AUDIO) {
                        reloadAudioTracks()
                    }
                }

                //            override fun buffering(mediaPlayer: MediaPlayer?, newCache: Float) {
//                if (newCache != 1f) {
//                    state.value = PlaybackState.PAUSED_BUFFERING
//                } else {
//                    state.value = PlaybackState.READY
//                }
//            }

                override fun mediaPlayerReady(mediaPlayer: MediaPlayer?) {
                    player.submit {
                        setVolume(volume.value)
                        toggleMute(isMute.value)
                    }

                    chapters.value = player.chapters().allDescriptions().flatMap { title ->
                        title.map {
                            Chapter(
                                name = it.name(),
                                durationMillis = it.duration(),
                                offsetMillis = it.offset(),
                            )
                        }
                    }.toImmutableList()
                }

                override fun playing(mediaPlayer: MediaPlayer) {
                    state.value = PlaybackState.PLAYING
                    player.submit { player.media().parsing().parse() }

                    reloadSubtitleTracks()

                    reloadAudioTracks()
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    state.value = PlaybackState.PAUSED
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    state.value = PlaybackState.FINISHED
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    logger.error { "vlcj player error" }
                    state.value = PlaybackState.ERROR
                }

                override fun positionChanged(mediaPlayer: MediaPlayer?, newPosition: Float) {
                    val properties = videoProperties.value
                    if (properties != null) {
                        currentPositionMillis.value = (newPosition * properties.durationMillis).toLong()
                    }
                }
            },
        )

        backgroundScope.launch {
            var lastPosition = currentPositionMillis.value
            while (true) {
                delay(1500)
                if (state.value == PlaybackState.PLAYING) {
                    isBuffering.value = lastPosition == currentPositionMillis.value
                    lastPosition = currentPositionMillis.value
                }
            }
        }

        backgroundScope.launch {
            subtitleTracks.current.collect { track ->
                try {
                    if (state.value == PlaybackState.READY) {
                        return@collect
                    }
                    if (track == null) {
                        if (player.subpictures().track() != -1) {
                            player.subpictures().setTrack(-1)
                        }
                        return@collect
                    }
                    val id = track.internalId.toIntOrNull() ?: run {
                        logger.error { "Invalid subtitle track id: ${track.id}" }
                        return@collect
                    }
                    val subTrackIds = player.subpictures().trackDescriptions().map { it.id() }
                    logger.info { "All ids: $subTrackIds" }
                    if (!subTrackIds.contains(id)) {
                        logger.error { "Invalid subtitle track id: $id" }
                        return@collect
                    }
                    player.subpictures().setTrack(id)
                    logger.info { "Set subtitle track to $id (${track.labels.firstOrNull()})" }
                } catch (e: Throwable) {
                    logger.error(e) { "Exception while setting subtitle track" }
                }
            }
        }

        backgroundScope.launch {
            audioTracks.current.collect { track ->
                try {
                    if (state.value == PlaybackState.READY) {
                        return@collect
                    }
                    if (track == null) {
                        if (player.audio().track() != -1) {
                            player.audio().setTrack(-1)
                        }
                    }

                    val id = track?.internalId?.toIntOrNull() ?: run {
                        if (track != null) {
                            logger.error { "Invalid audio track id: ${track.id}" }
                        }
                        return@collect
                    }
                    val count = player.audio().trackCount()
                    if (id > count) {
                        logger.error { "Invalid audio track id: $id, count: $count" }
                        return@collect
                    }
                    logger.info { "All ids: ${player.audio().trackDescriptions().map { it.id() }}" }
                    player.audio().setTrack(id)
                    logger.info { "Set audio track to $id (${track.labels.firstOrNull()})" }
                } catch (e: Throwable) {
                    logger.error(e) { "Exception while setting audio track" }
                }
            }
        }

        backgroundScope.launch {
            openResource.filterNotNull().map { it.videoSource.extraFiles.subtitles }
                .distinctUntilChanged()
                .debounce(1000)
                .collectLatest { urls ->
                    logger.info { "Video ExtraFiles changed, updating slaves" }
                    player.media().slaves().clear()
                    for (subtitle in urls) {
                        logger.info { "Adding SUBTITLE slave: $subtitle" }
                        player.media().addSlave(MediaSlaveType.SUBTITLE, subtitle.uri, false)
                    }
                }
        }
    }

    private fun reloadSubtitleTracks() {
        val newSubtitleTracks = player.subpictures().trackDescriptions()
            .filterNot { it.id() == -1 } // "Disable"
            .map {
                SubtitleTrack(
                    openResource.value?.videoData?.filename + "-" + it.id(),
                    it.id().toString(),
                    null,
                    listOf(Label(null, it.description())),
                )
            }
        // 新的字幕轨道和原来不同时才会更改，同时将 current 设置为新字幕轨道列表的第一个
        if (subtitleTracks.candidates.value != newSubtitleTracks) {
            subtitleTracks.candidates.value = newSubtitleTracks
            subtitleTracks.current.value = newSubtitleTracks.firstOrNull()
        }
    }

    private fun reloadAudioTracks() {
        audioTracks.candidates.value = player.audio().trackDescriptions()
            .filterNot { it.id() == -1 } // "Disable"
            .map {
                AudioTrack(
                    openResource.value?.videoData?.filename + "-" + it.id(),
                    it.id().toString(),
                    null,
                    listOf(Label(null, it.description())),
                )
            }
    }

    private fun createVideoProperties(): VideoProperties? {
        val info = player.media().info() ?: return null
        val title = player.titles().titleDescriptions().firstOrNull()
        return VideoProperties(
            title = title?.name(),
            durationMillis = info.duration(),
        )
    }

    override fun setPlaybackSpeed(speed: Float) {
        player.submit {
            player.controls().setRate(speed)
        }
        playbackSpeed.value = speed
    }

    private val setTimeLock = ReentrantLock()

    override fun seekTo(positionMillis: Long) {
        @Suppress("NAME_SHADOWING")
        val positionMillis = positionMillis.coerceIn(0, videoProperties.value?.durationMillis ?: 0)

        // 不要检查这个, 当切换数据源后, `currentPositionMillis` 可能没有更新.
//        if (positionMillis == currentPositionMillis.value) {
//            return
//        }

        currentPositionMillis.value = positionMillis
        player.submit {
            setTimeLock.withLock {
                player.controls().setTime(positionMillis)
            }
        }
        surface.allowedDrawFrames.value = 2 // 多渲染一帧, 防止 race 问题
    }

    override fun skip(deltaMillis: Long) {
        if (state.value == PlaybackState.PAUSED) {
            // 如果是暂停, 上面 positionChanged 事件不会触发, 所以这里手动更新
            // 如果正在播放, 这里不能更新. 否则可能导致进度抖动 1 秒
            currentPositionMillis.value = (currentPositionMillis.value + deltaMillis)
                .coerceIn(0, videoProperties.value?.durationMillis ?: 0)
        }
        player.submit {
            setTimeLock.withLock {
                player.controls().skipTime(deltaMillis) // 采用当前 player 时间
            }
        }
        surface.allowedDrawFrames.value = 2 // 多渲染一帧, 防止 race 问题
    }
}

@Composable
actual fun VideoPlayer(
    playerState: PlayerState,
    modifier: Modifier,
) {
    check(playerState is VlcjVideoPlayerState)

    val mediaPlayer = playerState.player
    val isFullscreen = false
    LaunchedEffect(isFullscreen) {
        /*
         * To be able to access window in the commented code below,
         * extend the player composable function from WindowScope.
         * See https://github.com/JetBrains/compose-jb/issues/176#issuecomment-812514936
         * and its subsequent comments.
         *
         * We could also just fullscreen the whole window:
         * `window.placement = WindowPlacement.Fullscreen`
         * See https://github.com/JetBrains/compose-multiplatform/issues/1489
         */
        // mediaPlayer.fullScreen().strategy(ExclusiveModeFullScreenStrategy(window))
        mediaPlayer.fullScreen().toggle()
    }
//    DisposableEffect(Unit) { onDispose(mediaPlayer::release) }

    val frameSizeCalculator = remember {
        FrameSizeCalculator()
    }
    Canvas(modifier) {
        val bitmap = playerState.surface.bitmap ?: return@Canvas
        frameSizeCalculator.calculate(
            IntSize(bitmap.width, bitmap.height),
            Size(size.width, size.height),
        )
        drawImage(
            bitmap,
            dstSize = frameSizeCalculator.dstSize,
            dstOffset = frameSizeCalculator.dstOffset,
            filterQuality = FilterQuality.High,
        )
    }
}

private class FrameSizeCalculator {
    private var lastImageSize: IntSize = IntSize.Zero
    private var lastFrameSize: Size = Size.Zero

    // no boxing
    var dstSize: IntSize = IntSize.Zero
    var dstOffset: IntOffset = IntOffset.Zero

    private fun calculateImageSizeAndOffsetToFillFrame(
        imageWidth: Int,
        imageHeight: Int,
        frameWidth: Float,
        frameHeight: Float
    ) {
        // 计算图片和画框的宽高比
        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()

        // 初始化最终的宽度和高度
        val finalWidth = frameWidth
        val finalHeight = frameWidth / imageAspectRatio
        if (finalHeight > frameHeight) {
            // 如果高度超出了画框的高度，那么就使用高度来计算宽度
            val finalHeight2 = frameHeight
            val finalWidth2 = frameHeight * imageAspectRatio
            dstSize = IntSize(finalWidth2.roundToInt(), finalHeight2.roundToInt())
            dstOffset = IntOffset(((frameWidth - finalWidth2) / 2).roundToInt(), 0)
            return
        }

        // 计算左上角的偏移量
        val offsetX = 0
        val offsetY = (frameHeight - finalHeight) / 2

        dstSize = IntSize(finalWidth.roundToInt(), finalHeight.roundToInt())
        dstOffset = IntOffset(offsetX, offsetY.roundToInt())
    }

    fun calculate(
        imageSize: IntSize,
        frameSize: Size,
    ) {
        // 缓存上次计算结果, 因为这个函数会每帧绘制都调用
        if (lastImageSize == imageSize && lastFrameSize == frameSize) {
            return
        }
        calculateImageSizeAndOffsetToFillFrame(
            imageWidth = imageSize.width, imageHeight = imageSize.height,
            frameWidth = frameSize.width, frameHeight = frameSize.height,
        )
        lastImageSize = imageSize
        lastFrameSize = frameSize
    }
}

// add contract
private inline fun <T> ReentrantLock.withLock(block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
