package me.him188.ani.app.ui.foundation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.util.fastLastOrNull
import androidx.lifecycle.LifecycleOwner
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val onBackUpdated by rememberUpdatedState(onBack)
    val enabledUpdated = rememberUpdatedState(enabled)
    val owner = LocalOnBackPressedDispatcherOwner.current ?: return
    DisposableEffect(owner) {
        val handler = object : OnBackPressedHandler {
            override val enabled: Boolean by enabledUpdated
            override fun onBack() = onBackUpdated()
        }
        owner.onBackPressedDispatcher.registerHandler(handler)
        onDispose { owner.onBackPressedDispatcher.unregisterHandler(handler) }
    }
}

actual object LocalOnBackPressedDispatcherOwner {
    private val LocalOwner = staticCompositionLocalOf<OnBackPressedDispatcherOwner?> { null }

    actual val current: OnBackPressedDispatcherOwner?
        @Composable get() = LocalOwner.current

    actual infix fun provides(dispatcherOwner: OnBackPressedDispatcherOwner): ProvidedValue<OnBackPressedDispatcherOwner?> =
        LocalOwner.provides(dispatcherOwner)
}

actual interface OnBackPressedDispatcherOwner : LifecycleOwner {
    actual val onBackPressedDispatcher: OnBackPressedDispatcher
}

actual class OnBackPressedDispatcher(private val fallback: () -> Unit) {
    actual fun onBackPressed() {
        handlers.fastLastOrNull { it.enabled }?.onBack() ?: fallback()
    }

    private val handlers = mutableListOf<OnBackPressedHandler>()
    fun registerHandler(handler: OnBackPressedHandler) {
        handlers.add(handler)
    }

    fun unregisterHandler(handler: OnBackPressedHandler) {
        handlers.remove(handler)
    }
}

interface OnBackPressedHandler {
    val enabled: Boolean
    fun onBack()
}

@TestOnly
actual fun OnBackPressedDispatcher(fallbackOnBackPressed: (() -> Unit)?): OnBackPressedDispatcher =
    OnBackPressedDispatcher(fallbackOnBackPressed ?: {})
