/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(InternalForInheritanceMediampApi::class, InternalMediampApi::class, ExperimentalForeignApi::class)

package me.him188.ani.app.videoplayer.player

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import me.him188.ani.app.videoplayer.subtitle.SubtitleCueTimeline
import me.him188.ani.app.videoplayer.subtitle.parseSubtitleCues
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.avkit.AVKitMediampPlayer
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.features.MediaMetadata
import org.openani.mediamp.features.NetworkStats
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.features.buildPlayerFeatures
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.metadata.TrackGroup
import org.openani.mediamp.metadata.TrackLabel
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.Subtitle
import platform.AVFoundation.AVMediaCharacteristicLegible
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.asset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentMediaSelection
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.selectedMediaOptionInMediaSelectionGroup
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.preferredLanguages

/** AVKit 播控与原生字幕轨道、MKV 文本字幕共用现有的字幕选择菜单。 */
class AniAVKitMediampPlayer(
    private val delegate: AVKitMediampPlayer,
    parentCoroutineContext: CoroutineContext,
) : MediampPlayer by delegate {
    override val impl: AVPlayer get() = delegate.impl
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob(parentCoroutineContext[Job]))
    private val subtitles = IosSubtitleTracks(scope, impl) { currentPositionMillis.value }
    val subtitleText get() = subtitles.text

    override val features = buildPlayerFeatures {
        delegate.features[Buffering]?.let { add(Buffering, it) }
        delegate.features[NetworkStats]?.let { add(NetworkStats, it) }
        delegate.features[AudioLevelController]?.let { add(AudioLevelController, it) }
        delegate.features[PlaybackSpeed]?.let { add(PlaybackSpeed, it) }
        delegate.features[VideoAspectRatio]?.let { add(VideoAspectRatio, it) }
        add(MediaMetadata, object : MediaMetadata {
            override val subtitleTracks: TrackGroup<SubtitleTrack> = subtitles
            override val audioTracks = null
            override val chapters = null
        })
    }

    init {
        scope.launch {
            combine(currentPositionMillis, subtitles.cues) { position, cues -> cues.textAt(position) }
                .collect { subtitles.text.value = it }
        }
    }

    override suspend fun setMediaData(data: MediaData, playWhenReady: Boolean, startPositionMillis: Long) {
        val generation = withContext(Dispatchers.Main) { subtitles.clear() }
        delegate.setMediaData(data, playWhenReady, startPositionMillis)
        withContext(Dispatchers.Main) {
            if (subtitles.generation == generation && delegate.mediaData.value === data) {
                impl.currentItem?.let { subtitles.attach(it, data.extraFiles.subtitles) }
            }
        }
    }

    override fun stopPlayback() {
        subtitles.clear()
        delegate.stopPlayback()
    }

    override fun close() {
        scope.cancel()
        delegate.close()
    }
}

private class IosSubtitleTracks(
    private val scope: CoroutineScope,
    private val player: AVPlayer,
    private val position: () -> Long,
) : TrackGroup<SubtitleTrack> {
    override val candidates = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    override val selected = MutableStateFlow<SubtitleTrack?>(null)
    val cues = MutableStateFlow(SubtitleCueTimeline(emptyList()))
    val text = MutableStateFlow("")
    var generation = 0L
        private set
    private var loadJob: Job? = null
    private var item: AVPlayerItem? = null
    private var group: AVMediaSelectionGroup? = null
    private val nativeOptions = mutableMapOf<String, AVMediaSelectionOption>()
    private val files = mutableMapOf<String, Subtitle>()

    fun clear(): Long {
        generation++
        loadJob?.cancel()
        loadJob = null
        item = null
        group = null
        nativeOptions.clear()
        files.clear()
        candidates.value = emptyList()
        selected.value = null
        cues.value = SubtitleCueTimeline(emptyList())
        text.value = ""
        return generation
    }

    fun attach(item: AVPlayerItem, subtitles: List<Subtitle>) {
        this.item = item
        group = item.asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
        val native = group?.options.orEmpty().filterIsInstance<AVMediaSelectionOption>().mapIndexed { index, option ->
            val id = "native:$index"
            nativeOptions[id] = option
            SubtitleTrack(id, id, null, listOf(TrackLabel(null, option.displayName)))
        }
        val external = subtitles.mapIndexed { index, subtitle ->
            val id = "file:$index"
            files[id] = subtitle
            SubtitleTrack(id, id, null, listOf(TrackLabel(null, subtitle.label?.takeIf { it.isNotBlank() }
                ?: subtitle.language ?: "${index + 1}")))
        }
        candidates.value = native + external
        val current = group?.let { item.currentMediaSelection.selectedMediaOptionInMediaSelectionGroup(it) }
        val nativeSelected = native.firstOrNull { nativeOptions[it.id] == current }
        val language = NSLocale.preferredLanguages.firstOrNull()?.toString().orEmpty().substringBefore('-')
        val matchingExternal = external.firstOrNull { track ->
            val source = files.getValue(track.id)
            when (language) {
                "zh" -> source.language?.lowercase() in setOf("zh", "zho", "chi", "zh-hans", "zh-hant")
                "en" -> source.language?.lowercase() in setOf("en", "eng")
                else -> source.language?.startsWith(language) == true && language.isNotBlank()
            }
        }
        select(nativeSelected ?: matchingExternal)
    }

    override fun select(track: SubtitleTrack?): Boolean {
        if (track != null && track !in candidates.value) return false
        if (item !== player.currentItem) return false
        loadJob?.cancel()
        loadJob = null
        cues.value = SubtitleCueTimeline(emptyList())
        text.value = ""
        selected.value = track
        group?.let { item?.selectMediaOption(track?.let { nativeOptions[it.id] }, it) }
        val file = track?.let { files[it.id] } ?: return true
        val expectedGeneration = generation
        loadJob = scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO_) {
                    val source = if (file.uri.startsWith("file:")) {
                        val path = checkNotNull(NSURL.URLWithString(file.uri)?.path)
                        SystemFileSystem.source(Path(path)).buffered().use { it.readString() }
                    } else {
                        createDefaultHttpClient().use { it.get(file.uri).bodyAsText() }
                    }
                    SubtitleCueTimeline(parseSubtitleCues(source))
                }
                if (generation == expectedGeneration && selected.value?.id == track.id) {
                    cues.value = parsed
                    text.value = parsed.textAt(position())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger.warn(failure) { "Failed to read selected video subtitle" }
            }
        }
        return true
    }

    private companion object {
        val logger = logger<IosSubtitleTracks>()
    }
}
