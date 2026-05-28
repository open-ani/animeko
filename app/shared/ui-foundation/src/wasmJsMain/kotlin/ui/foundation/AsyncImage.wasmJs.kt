package me.him188.ani.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

actual fun ImageBitmap.asCoilImage(): Image = unsupportedImageConversion()

private fun unsupportedImageConversion(): Nothing =
    error("ImageBitmap to Coil Image conversion is not available in the browser build")
