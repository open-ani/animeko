package me.him188.ani.app.ui.foundation.interaction

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 当后代没有消费 tap 时清除焦点并隐藏软键盘.
 *
 * 清除焦点是 pointer side effect, 并不是可访问的 click action, 因此这里刻意使用不带语义的
 * pointer input. 若在 APP 根节点使用 `clickable`, 会合并整个后代语义树, 并把本来不是控件的背景
 * 暴露为无障碍点击操作.
 */
fun Modifier.clearFocusOnUnhandledTap(): Modifier = composed {
    val focusManager by rememberUpdatedState(LocalFocusManager.current)
    val keyboard by rememberUpdatedState(LocalSoftwareKeyboardController.current)

    pointerInput(Unit) {
        detectTapGestures {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
fun Modifier.clearFocusOnKeyboardDismiss(onClear: (() -> Unit)? = null): Modifier = composed {
    var isFocused by remember {
        mutableStateOf(false)
    }
    var keyboardAppearedSinceLastFocused by remember { mutableStateOf(false) }
    if (isFocused) {
        val imeIsVisible = isImeVisible()
        val focusManager = LocalFocusManager.current
        LaunchedEffect(imeIsVisible) {
            if (imeIsVisible) {
                keyboardAppearedSinceLastFocused = true
            } else if (keyboardAppearedSinceLastFocused) {
                focusManager.clearFocus()
                onClear?.run {
                    invoke()
                }
            }
        }
    }
    onFocusEvent {
        if (isFocused != it.isFocused) {
            isFocused = it.isFocused
            if (isFocused) {
                keyboardAppearedSinceLastFocused = false
            }
        }
    }
}

@Composable
expect inline fun isImeVisible(): Boolean

/**
 * 返回 IME 最大的高度
 */
@Composable
fun rememberImeMaxHeight(): State<Int> {
    val density = LocalDensity.current
    
    val imePadding by rememberUpdatedState(WindowInsets.ime.getBottom(density))
    val navigationBarPadding by rememberUpdatedState(WindowInsets.navigationBars.getBottom(density))

    var imePresentHeight by rememberSaveable { mutableStateOf(0) }
    return remember {
        derivedStateOf {
            val incomingPresentHeight = imePadding - navigationBarPadding
            if (imePresentHeight < incomingPresentHeight) {
                imePresentHeight = incomingPresentHeight
                incomingPresentHeight
            } else {
                imePresentHeight
            }
        }
    }
}
