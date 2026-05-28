package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font

@Composable
actual fun rememberPlatformFontFamily(fontName: String?): PlatformFontFamily = PlatformFontFamily(
    FontFamily(Font(Res.font.NotoSansCJKjp_Regular)),
)
