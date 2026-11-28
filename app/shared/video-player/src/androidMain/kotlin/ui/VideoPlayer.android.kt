/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.SurfaceView
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView.ControllerVisibilityListener
import androidx.media3.ui.SubtitleView
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.him188.ani.app.data.models.preference.SubtitleTextConversion
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.videoplayer.media.LibassExoPlayerMediampPlayer
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleAppearance
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleDelayMillis
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleTextConversion
import me.him188.ani.app.videoplayer.subtitle.SubtitleTextOverlay
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurface
import org.openani.mediamp.features.subtitleTracks

@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier
) {
    val isPreviewing by rememberUpdatedState(LocalIsPreviewing.current)

    if (isPreviewing) {
        Box(modifier)
    } else {
        val libassPlayer = player as? LibassExoPlayerMediampPlayer
        val exoPlayer = libassPlayer?.exoMediampPlayer ?: player as ExoPlayerMediampPlayer
        var nativeSubtitleView by remember(player) { mutableStateOf<SubtitleView?>(null) }
        var text by remember(player) { mutableStateOf("") }
        val conversion = LocalSubtitleTextConversion.current
        val selectedSubtitle = player.subtitleTracks?.selected?.collectAsState()?.value
        val appearance = LocalSubtitleAppearance.current
        val scale = appearance.effectiveFontScale
        val subtitleDelay = LocalSubtitleDelayMillis.current
        LaunchedEffect(libassPlayer, subtitleDelay, selectedSubtitle) { libassPlayer?.setSubtitleDelay(if (selectedSubtitle == null) 0 else subtitleDelay) }
        LaunchedEffect(nativeSubtitleView, conversion, scale, selectedSubtitle, appearance.requiresTextOverlay, subtitleDelay) {
            text = ""
            val view = nativeSubtitleView ?: return@LaunchedEffect
            try {
                view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
                libassPlayer?.assHandler?.render?.setFontScale(scale)
                if (conversion == SubtitleTextConversion.ORIGINAL && !appearance.requiresTextOverlay) return@LaunchedEffect
                while (isActive) {
                    val exo = exoPlayer.impl
                    val selected = exo.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
                    val mime = selected?.mediaTrackGroup?.getFormat(0)?.sampleMimeType
                    val cues = exo.currentCues.cues
                    val bitmap = mime in bitmapSubtitleMimeTypes || cues.any { it.bitmap != null }
                    val active = selected != null && !bitmap
                    view.visibility = if (active) View.INVISIBLE else View.VISIBLE
                    text = if (!active) "" else if (mime == "text/x-ssa") {
                        val position = exo.currentPosition - subtitleDelay
                        libassPlayer?.assHandler?.track?.getEvents().orEmpty().filter {
                            position >= it.start && position < it.start + it.duration
                        }.map { it.text }.filterNot { Regex("""\\p[1-9]""").containsMatchIn(it) }
                            .map { it.replace(Regex("\\{[^}]*\\}"), "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ") }
                            .distinct().joinToString("\n")
                    } else cues.mapNotNull { it.text?.toString() }.distinct().joinToString("\n")
                    delay(50)
                }
            } finally {
                text = ""
                view.visibility = View.VISIBLE
            }
        }
        Box(modifier) {
            ExoPlayerMediampPlayerSurface(exoPlayer, Modifier.fillMaxSize()) {
                (videoSurfaceView as? SurfaceView)?.let { registerAndroidVideoSurface(player, it) }
                controllerAutoShow = false
                useController = false
                controllerHideOnTouch = false
                subtitleView?.apply {
                    nativeSubtitleView = this
                    libassPlayer?.let { addView(AssSubtitleView(context, it.assHandler)) }
                    this.setStyle(
                        CaptionStyleCompat(
                            Color.WHITE,
                            0x000000FF,
                            0x00000000,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            Color.BLACK,
                            Typeface.DEFAULT,
                        ),
                    )
                }
                setControllerVisibilityListener(
                    ControllerVisibilityListener { visibility ->
                        if (visibility == View.VISIBLE) {
                            hideController()
                        }
                    },
                )
            }
            SubtitleTextOverlay(if (selectedSubtitle == null) "" else text)
        }
    }
}

private val bitmapSubtitleMimeTypes = setOf("application/pgs", "application/dvbsubs", "application/vobsub")
