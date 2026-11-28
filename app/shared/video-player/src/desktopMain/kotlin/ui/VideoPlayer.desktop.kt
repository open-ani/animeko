/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.him188.ani.app.data.models.preference.SubtitleTextConversion
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleAppearance
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleDelayMillis
import me.him188.ani.app.videoplayer.subtitle.LocalSubtitleTextConversion
import me.him188.ani.app.videoplayer.subtitle.SubtitleTextOverlay
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface

@Composable
actual fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier,
) {
    // 桌面端统一使用 mpv (mediamp-mpv) 后端.
    when (player) {
        is MpvMediampPlayer -> Box(modifier) {
            MpvMediampPlayerSurface(player, modifier = Modifier.fillMaxSize())
            MpvSubtitleOverlay(player)
        }
        else -> error("Unsupported desktop MediampPlayer: ${player::class.qualifiedName}")
    }
}

@Composable
private fun MpvSubtitleOverlay(player: MpvMediampPlayer) {
    val handle = remember(player) { player.impl as MPVHandle }
    val appearance = LocalSubtitleAppearance.current
    val scale = appearance.effectiveFontScale
    val subtitleDelay = LocalSubtitleDelayMillis.current
    val conversion = LocalSubtitleTextConversion.current
    val selected = player.subtitleTracks?.selected?.collectAsState()?.value
    var text by remember(player) { mutableStateOf("") }
    DisposableEffect(handle) {
        val visibility = handle.getPropertyBoolean("sub-visibility")
        val fontScale = handle.getPropertyDouble("sub-scale")
        val offset = handle.getPropertyDouble("sub-delay")
        onDispose {
            runCatching {
                handle.setPropertyBoolean("sub-visibility", visibility)
                handle.setPropertyDouble("sub-scale", fontScale)
                handle.setPropertyDouble("sub-delay", offset)
            }
        }
    }
    LaunchedEffect(handle, scale) { handle.setPropertyDouble("sub-scale", scale.toDouble()) }
    LaunchedEffect(handle, subtitleDelay) { handle.setPropertyDouble("sub-delay", subtitleDelay / 1000.0) }
    LaunchedEffect(handle, conversion, selected, appearance.requiresTextOverlay) {
        text = ""
        if (conversion == SubtitleTextConversion.ORIGINAL && !appearance.requiresTextOverlay) {
            return@LaunchedEffect
        }
        val reader = MpvSubtitleReader(handle)
        try {
            while (isActive) {
                text = reader.read(true)
                delay(50)
            }
        } finally {
            text = ""
            reader.close()
        }
    }
    SubtitleTextOverlay(if (selected == null) "" else text)
}

/** 解码所选文本轨道，位图轨道由 mpv 显示；保留选择状态以支持关闭和切轨。 */
internal class MpvSubtitleReader(private val handle: MPVHandle) : AutoCloseable {
    private val originalVisibility = handle.getPropertyBoolean("sub-visibility")

    fun read(convert: Boolean): String {
        val count = handle.getPropertyInt("track-list/count")
        val selected = (0 until count).firstOrNull { index ->
            handle.getPropertyString("track-list/$index/type") == "sub" &&
                    handle.getPropertyBoolean("track-list/$index/selected")
        }
        val codec = selected?.let { handle.getPropertyString("track-list/$it/codec") }
        val textTrack = codec != null && codec !in bitmapSubtitleCodecs
        val active = convert && textTrack
        handle.setPropertyBoolean("sub-visibility", if (active) false else originalVisibility)
        return if (active) handle.getPropertyString("sub-text").orEmpty() else ""
    }

    override fun close() {
        runCatching { handle.setPropertyBoolean("sub-visibility", originalVisibility) }
    }
}

private val bitmapSubtitleCodecs = setOf("dvd_subtitle", "hdmv_pgs_subtitle", "dvb_subtitle", "xsub")
