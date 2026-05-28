package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.openani.mediamp.MediampPlayer

@Composable
actual fun VideoPlayer(player: MediampPlayer, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black))
}
