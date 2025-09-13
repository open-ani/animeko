package me.him188.ani.app.videoplayer.ui

import androidx.compose.ui.geometry.Rect
import me.him188.ani.app.platform.Context
import org.openani.mediamp.MediampPlayer

actual class PictureInPictureController actual constructor(
    context: Context,
    player: MediampPlayer
) {
    actual fun enterPictureInPictureMode(rect: Rect) {
        //  TODO
    }
}