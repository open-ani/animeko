package me.him188.ani.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

@Composable
actual fun ScreenOnEffectImpl() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose {
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
}