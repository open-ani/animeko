package me.him188.ani.app.ui.foundation.interaction

import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import me.him188.ani.app.platform.Context

@Composable
actual inline fun isImeVisible(): Boolean = false

actual inline fun Modifier.onEnterKeyEvent(crossinline action: (KeyEvent) -> Boolean): Modifier =
    onPreviewKeyEvent { action(it) }

actual fun Modifier.onClickEx(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean,
    onDoubleClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit
): Modifier = combinedClickable(
    interactionSource = interactionSource,
    indication = indication,
    enabled = enabled,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
)

actual fun Modifier.onRightClickIfSupported(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit
): Modifier = this

actual fun Context.vibrateIfSupported(strength: VibrationStrength) {}

@Composable
actual inline fun WindowDragArea(modifier: Modifier, crossinline content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier) { content() }
}
