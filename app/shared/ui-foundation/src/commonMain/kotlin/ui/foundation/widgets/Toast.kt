/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.math.max
import kotlin.math.min

@Stable
val LocalToaster: ProvidableCompositionLocal<Toaster> = staticCompositionLocalOf {
    error("No LocalToaster provided")
}

@Stable
interface Toaster {
    fun toast(text: String)
}

@Stable
@TestOnly
object NoOpToaster : Toaster {
    override fun toast(text: String) {
    }
}

class ToastViewModel : AbstractViewModel() {
    private val tasker = MonoTasker(backgroundScope)

    val showing = MutableStateFlow(false)
    val content = MutableStateFlow("")

    fun show(text: String) {
        showing.update { true }
        content.update { text }

        tasker.launch {
            delay(4000L)
            showing.emit(false)
        }
    }
}

@Composable
fun Toast(
    showing: () -> Boolean,
    content: @Composable () -> Unit
) = BoxWithConstraints(Modifier.fillMaxSize()) box@{
    val px640dp = with(LocalDensity.current) { 640.dp.roundToPx() }
    val px100dp = with(LocalDensity.current) { 100.dp.roundToPx() }

    val minToastWidth = with(LocalDensity.current) { px100dp + 60.dp.roundToPx() * 2 }
    val maxToastWidth = max(minToastWidth, min(constraints.maxWidth, px640dp))

    val currentContent by rememberUpdatedState(content)

    AnimatedVisibility(
        visible = showing(),
        enter = fadeIn(tween(350, easing = LinearEasing)),
        exit = fadeOut(tween(350, easing = LinearEasing)),
        modifier = Modifier.layout { measurable, constraints ->
            val rawWidth = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Int.MAX_VALUE)).width

            val placeable = measurable.measure(
                constraints.copy(minWidth = min(rawWidth, minToastWidth), maxWidth = maxToastWidth, minHeight = 0),
            )

            val x = max(this@box.constraints.maxWidth - placeable.width, 0) / 2
            val y = constraints.maxHeight - placeable.height - px100dp

            layout(placeable.width, placeable.height) {
                placeable.place(x, y, 100f)
            }
        },
    ) {
        val isDarkTheme = isSystemInDarkTheme()
        Surface(
            modifier = Modifier.padding(horizontal = 60.dp),
            shape = RoundedCornerShape(15.dp),
            color = (if (isDarkTheme) Color.White else Color.Black).copy(alpha = 0.7f),
            shadowElevation = 4.dp,
        ) {
            CompositionLocalProvider(LocalContentColor provides (if (isDarkTheme) Color.Black else Color.White)) {
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    currentContent()
                }
            }
        }
    }
}