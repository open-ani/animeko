/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_load_error_network
import me.him188.ani.app.ui.lang.foundation_load_error_no_results
import me.him188.ani.app.ui.lang.foundation_load_error_rate_limited
import me.him188.ani.app.ui.lang.foundation_load_error_request_error
import me.him188.ani.app.ui.lang.foundation_load_error_requires_login
import me.him188.ani.app.ui.lang.foundation_load_error_service_unavailable
import me.him188.ani.app.ui.lang.foundation_load_error_unknown_feedback
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Stable
val LocalToaster: ProvidableCompositionLocal<Toaster> = staticCompositionLocalOf {
    error("No LocalToaster provided")
}

@Stable
interface Toaster {
    fun toast(text: String)

    // AI 喜欢补这个
    fun show(text: String) {
        toast(text)
    }
}

private val logger = logger<Toaster>()

@OptIn(DelicateCoroutinesApi::class)
fun Toaster.showLoadError(error: LoadError) {
    GlobalScope.launch(Dispatchers.Main) {
        show(renderLoadErrorToastMessage(error))
    }

    when (error) {
        is LoadError.UnknownError -> {
            logger.warn(error.throwable) {
                "Toaster showing an LoadError.UnknownError"
            }
        }

        is LoadError.RequestError -> {
            logger.warn {
                "Toaster showing an LoadError.RequestError: ${error.localized}"
            }
        }

        else -> {}
    }
}

private suspend fun renderLoadErrorToastMessage(error: LoadError): String {
    return when (error) {
        LoadError.NetworkError -> getString(Lang.foundation_load_error_network)
        LoadError.NoResults -> getString(Lang.foundation_load_error_no_results)
        LoadError.RateLimited -> getString(Lang.foundation_load_error_rate_limited)
        LoadError.RequiresLogin -> getString(Lang.foundation_load_error_requires_login)
        LoadError.ServiceUnavailable -> getString(Lang.foundation_load_error_service_unavailable)
        is LoadError.UnknownError -> getString(Lang.foundation_load_error_unknown_feedback)
        is LoadError.RequestError -> getString(Lang.foundation_load_error_request_error, error.localized)
    }
}

@Stable
@TestOnly
object NoOpToaster : Toaster {
    override fun toast(text: String) {
    }
}

/**
 * 依次显示 toast 的队列. [show] 把文字排到队尾; 每条显示 [ToastShowDuration], 淡出后再显示下一条, 同一时刻发出的多条提示都能看到.
 * 一条显示期间排进队列的相同文字只显示一次.
 */
class ToastQueue(scope: CoroutineScope) {
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val _showing = MutableStateFlow(false)
    private val _content = MutableStateFlow("")

    val showing: StateFlow<Boolean> = _showing.asStateFlow()
    val content: StateFlow<String> = _content.asStateFlow()

    init {
        scope.launch { showQueued() }
    }

    fun show(text: String) {
        queue.trySend(text)
    }

    private suspend fun showQueued() {
        var text = queue.receive()
        while (true) {
            _content.value = text
            _showing.value = true
            delay(ToastShowDuration)
            _showing.value = false
            delay(ToastFadeDuration)

            var next = queue.tryReceive().getOrNull()
            while (next == text) next = queue.tryReceive().getOrNull()
            text = next ?: queue.receive()
        }
    }
}

class ToastViewModel : AbstractViewModel() {
    private val queue = ToastQueue(backgroundScope)

    val showing: StateFlow<Boolean> get() = queue.showing
    val content: StateFlow<String> get() = queue.content

    fun show(text: String) {
        queue.show(text)
    }
}

/**
 * 窗口底部居中显示的 toast. 放在 [Popup] 中, 这样它在同样以 Popup 实现的底部面板、菜单等之上; 放在普通布局中时会被它们盖住.
 * Popup 不获取焦点, toast 出现时不打断正在进行的输入. 淡出结束后才撤掉 Popup.
 */
@Composable
fun Toast(
    showing: () -> Boolean,
    content: @Composable () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = showing()
    if (!visibleState.currentState && !visibleState.targetState) return

    val currentContent by rememberUpdatedState(content)
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        ToastPositionProvider(bottomMargin = with(density) { ToastBottomMargin.roundToPx() })
    }
    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = false),
    ) {
        BoxWithConstraints {
            // 窄窗口中 toast 可以比下限窄, 宽窗口中不超过 640.dp
            val maxToastWidth = maxOf(ToastMinWidth, minOf(maxWidth, ToastMaxWidth))
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(tween(ToastFadeDuration.inWholeMilliseconds.toInt(), easing = LinearEasing)),
                exit = fadeOut(tween(ToastFadeDuration.inWholeMilliseconds.toInt(), easing = LinearEasing)),
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = maxToastWidth)
                        .padding(horizontal = ToastHorizontalMargin),
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            currentContent()
                        }
                    }
                }
            }
        }
    }
}

internal val ToastShowDuration = 4.seconds
internal val ToastFadeDuration = 350.milliseconds

private val ToastBottomMargin = 100.dp
private val ToastHorizontalMargin = 60.dp
private val ToastMaxWidth = 640.dp
private val ToastMinWidth = 100.dp + ToastHorizontalMargin * 2

/**
 * 把 toast 放在窗口底部居中, 距底边 [bottomMargin] 像素.
 */
private class ToastPositionProvider(private val bottomMargin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        y = (windowSize.height - popupContentSize.height - bottomMargin).coerceAtLeast(0),
    )
}
