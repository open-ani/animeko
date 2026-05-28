package me.him188.ani.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

actual fun Image.toComposeImageBitmap(): ImageBitmap =
    error("Coil Image to Compose ImageBitmap conversion is not available in the browser build")

actual fun ImageBitmap.resize(width: Int, height: Int): ImageBitmap = this
