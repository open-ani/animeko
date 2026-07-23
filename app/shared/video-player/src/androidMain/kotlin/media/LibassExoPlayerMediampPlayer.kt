/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.exoplayer.ExoPlayerAudioTimeStretch
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import org.openani.mediamp.io.SeekableInput
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.SeekableInputMediaData
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds

/**
 * Adds libass parsing and rendering to MediaMP's ExoPlayer backend.
 *
 * MediaMP currently creates ExoPlayer without a renderer/source customization hook. This adapter
 * keeps MediaMP as the owner of playback state and track selection, then replaces the initial
 * Media3 source with one that uses libass's Matroska extractor and subtitle parser.
 */
@OptIn(InternalForInheritanceMediampApi::class)
@AndroidxOptIn(UnstableApi::class)
class LibassExoPlayerMediampPlayer private constructor(
    private val context: Context,
    parentCoroutineContext: CoroutineContext,
    internal val exoMediampPlayer: ExoPlayerMediampPlayer,
) : MediampPlayer by exoMediampPlayer {
    constructor(
        context: Context,
        parentCoroutineContext: CoroutineContext,
        audioTimeStretch: ExoPlayerAudioTimeStretch = ExoPlayerAudioTimeStretch.HighQualityWsola,
    ) : this(
        context,
        parentCoroutineContext,
        ExoPlayerMediampPlayer(context, parentCoroutineContext, audioTimeStretch),
    )

    internal val assHandler = AssHandler(
        renderType = AssRenderType.OVERLAY_OPEN_GL,
        config = AssHandlerConfig(maxRenderPixels = 1920 * 1080),
    )

    private val exoPlayer: ExoPlayer get() = exoMediampPlayer.impl
    private val backgroundScope = CoroutineScope(
        parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job.Key]),
    )
    private val subtitleParserFactory = AssSubtitleParserFactory(assHandler)
    private val extractorsFactory = DefaultExtractorsFactory()
        .withAssMkvSupport(subtitleParserFactory, assHandler)

    private var currentMediaData: MediaData? = null
    private var pendingMediaSource: MediaSource? = null
    private var closed = false

    init {
        assHandler.init(exoPlayer)
        backgroundScope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                // AssRenderer normally supplies this timestamp. MediaMP owns the ExoPlayer
                // builder, so drive the overlay from the same playback clock here instead.
                assHandler.videoTime = exoPlayer.currentPosition * 1_000
                delay(16.milliseconds)
            }
        }
    }

    override val mediaData: Flow<MediaData?> = exoMediampPlayer.mediaData.map { data ->
        (data as? TrackingSeekableInputMediaData)?.source ?: data
    }

    override suspend fun setMediaData(data: MediaData) {
        if (data == currentMediaData) return

        val playerData = if (data is SeekableInputMediaData) {
            TrackingSeekableInputMediaData(data)
        } else {
            data
        }
        exoMediampPlayer.setMediaData(playerData)

        pendingMediaSource = createMediaSource(data, playerData)
        currentMediaData = data
    }

    override fun resume() {
        val mediaSource = pendingMediaSource
        if (mediaSource == null || exoMediampPlayer.getCurrentPlaybackState() != PlaybackState.READY) {
            exoMediampPlayer.resume()
            return
        }

        pendingMediaSource = null
        // Let MediaMP perform its READY -> PLAYING transition, then immediately replace the
        // default source before its loader can consume media. The replacement keeps MediaMP's
        // listeners and track selector while enabling libass parsing.
        exoMediampPlayer.resume()
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun stopPlayback() {
        pendingMediaSource = null
        currentMediaData = null
        exoMediampPlayer.stopPlayback()
    }

    override fun seekTo(positionMillis: Long) {
        if (exoMediampPlayer.getCurrentPlaybackState() < PlaybackState.READY) return
        exoMediampPlayer.seekTo(positionMillis)
        // ExoPlayer applies a seek asynchronously. Update libass immediately as well so the
        // paused overlay does not retain the subtitle from the previous playback position.
        val positionUs = positionMillis * 1_000
        assHandler.videoTime = positionUs
        // AssHandler throttles clock callbacks while video is playing. A paused seek only
        // produces one distinct timestamp, so request that frame explicitly as well.
        assHandler.videoTimeCallback?.invoke(positionUs)
    }

    override fun close() {
        if (closed) return
        closed = true
        pendingMediaSource = null
        currentMediaData = null
        backgroundScope.cancel()
        exoPlayer.removeListener(assHandler)
        assHandler.release()
        exoMediampPlayer.close()
    }

    private fun createMediaSource(
        data: MediaData,
        playerData: MediaData,
    ): MediaSource {
        val dataSourceFactory = when (data) {
            is UriMediaData -> DefaultHttpDataSource.Factory()
                .setUserAgent(data.headers["User-Agent"] ?: DEFAULT_USER_AGENT)
                .setDefaultRequestProperties(data.headers)
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MILLIS)

            is SeekableInputMediaData -> {
                if (data.uri.startsWith("file://")) {
                    DefaultDataSource.Factory(context)
                } else {
                    val trackingData = playerData as TrackingSeekableInputMediaData
                    RoutingDataSourceFactory(
                        mediaUri = data.uri,
                        mediaDataSourceFactory = DataSource.Factory {
                            VideoDataDataSource(data, trackingData.primaryInput)
                        },
                        fallbackDataSourceFactory = DefaultDataSource.Factory(context),
                    )
                }
            }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(data.playbackUri)
            .setSubtitleConfigurations(
                data.extraFiles.subtitles.mapIndexed { index, subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri)).apply {
                        setId("animeko-external-subtitle-$index")
                        subtitle.label?.let(::setLabel)
                        subtitle.mimeType?.let(::setMimeType)
                        subtitle.language?.let(::setLanguage)
                    }.build()
                },
            )
            .build()

        return DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setSubtitleParserFactory(subtitleParserFactory)
            .createMediaSource(mediaItem)
    }

    private val MediaData.playbackUri: String
        get() = when (this) {
            is UriMediaData -> uri
            is SeekableInputMediaData -> uri
        }

    @OptIn(ExperimentalMediampApi::class)
    private class TrackingSeekableInputMediaData(
        val source: SeekableInputMediaData,
    ) : SeekableInputMediaData by source {
        lateinit var primaryInput: SeekableInput
            private set

        override suspend fun createInput(coroutineContext: CoroutineContext): SeekableInput {
            return source.createInput(coroutineContext).also { input ->
                if (!::primaryInput.isInitialized) {
                    primaryInput = input
                }
            }
        }
    }

    private class RoutingDataSourceFactory(
        private val mediaUri: String,
        private val mediaDataSourceFactory: DataSource.Factory,
        private val fallbackDataSourceFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutingDataSource(
            mediaUri,
            mediaDataSourceFactory,
            fallbackDataSourceFactory,
        )
    }

    private class RoutingDataSource(
        private val mediaUri: String,
        private val mediaDataSourceFactory: DataSource.Factory,
        private val fallbackDataSourceFactory: DataSource.Factory,
    ) : DataSource {
        private val transferListeners = mutableListOf<TransferListener>()
        private var activeDataSource: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners += transferListener
        }

        override fun open(dataSpec: DataSpec): Long {
            check(activeDataSource == null) { "Data source is already open" }
            val dataSource = if (dataSpec.uri.toString() == mediaUri) {
                mediaDataSourceFactory.createDataSource()
            } else {
                fallbackDataSourceFactory.createDataSource()
            }
            transferListeners.forEach(dataSource::addTransferListener)
            activeDataSource = dataSource
            return dataSource.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            checkNotNull(activeDataSource) { "Data source is not open" }.read(buffer, offset, length)

        override fun getUri(): Uri? = activeDataSource?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            activeDataSource?.responseHeaders.orEmpty()

        override fun close() {
            activeDataSource?.close()
            activeDataSource = null
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"
    }
}

class LibassExoPlayerMediampPlayerFactory(
    private val enableHighQualityAudioTimeStretch: () -> Boolean = { true },
) : MediampPlayerFactory<LibassExoPlayerMediampPlayer> {
    override val forClass: KClass<LibassExoPlayerMediampPlayer>
        get() = LibassExoPlayerMediampPlayer::class

    override fun create(
        context: Any,
        parentCoroutineContext: CoroutineContext,
    ): LibassExoPlayerMediampPlayer {
        require(context is Context) { "The context argument must be android.content.Context on Android" }
        val audioTimeStretch = if (enableHighQualityAudioTimeStretch()) {
            ExoPlayerAudioTimeStretch.HighQualityWsola
        } else {
            ExoPlayerAudioTimeStretch.Media3Default
        }
        return LibassExoPlayerMediampPlayer(context, parentCoroutineContext, audioTimeStretch)
    }
}
