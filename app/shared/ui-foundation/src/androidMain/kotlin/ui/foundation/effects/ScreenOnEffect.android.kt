package me.him188.ani.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.findActivity

/**
 * Composes an effect that keeps the screen on.
 *
 * When the composable gets removed from the view hierarchy, the screen will be allowed to turn off again.
 */
@Composable
actual fun ScreenOnEffectImpl() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity?.window) {
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
