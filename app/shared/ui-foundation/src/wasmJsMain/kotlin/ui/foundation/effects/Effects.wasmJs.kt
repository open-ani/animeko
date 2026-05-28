package me.him188.ani.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp

@Composable
actual fun Modifier.blurEffect(radius: Dp, edgeTreatment: BlurredEdgeTreatment): Modifier = blur(radius, edgeTreatment)

actual fun Modifier.cursorVisibility(visible: Boolean): Modifier = this

@Composable
actual fun DarkStatusBarAppearance() {
}

@Composable
actual fun OverrideCaptionButtonAppearance(isDark: Boolean) {
}

@Composable
actual fun ScreenOnEffectImpl() {
}

@Composable
actual fun ScreenRotationEffectImpl(onChange: (isLandscape: Boolean) -> Unit) {
}
