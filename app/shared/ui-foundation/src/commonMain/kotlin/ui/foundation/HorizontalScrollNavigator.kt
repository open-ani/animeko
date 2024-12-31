/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import kotlin.math.pow

/**
 * Provide buttons to navigate horizontally. Effectively works on desktop.
 */
@Composable
fun HorizontalScrollNavigator(
    state: HorizontalScrollNavigatorState,
    modifier: Modifier = Modifier,
    scrollLeftButton: @Composable (modifier: Modifier) -> Unit = {
        HorizontalScrollNavigatorDefaults.ScrollLeftButton(it)
    },
    scrollRightButton: @Composable (modifier: Modifier) -> Unit = {
        HorizontalScrollNavigatorDefaults.ScrollRightButton(it)
    },
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull()?.let { pointerInputChange ->
                            state.updateMousePointerPosition(pointerInputChange.position)
                        }
                    }
                }
            },
    ) {
        content()

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = HorizontalScrollNavigatorDefaults.ButtonMargin)
                .onGloballyPositioned { state.updateLeftButtonCoordinate(layoutCoordinates = it) },
        ) {
            Crossfade(targetState = state.showLeftButton) { show ->
                if (show) {
                    scrollLeftButton(Modifier.clickable { state.scrollLeft() })
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = HorizontalScrollNavigatorDefaults.ButtonMargin)
                .onGloballyPositioned { state.updateRightButtonCoordinate(layoutCoordinates = it) },
        ) {
            Crossfade(targetState = state.showRightButton) { show ->
                if (show) {
                    scrollRightButton(Modifier.clickable { state.scrollRight() })
                }
            }
        }
    }
}

/**
 * Create a [HorizontalScrollNavigatorState] that can be used to navigate horizontally
 *
 * @param scrollableState the incoming scrollable state. Use this to detect
 *      if the content can be scrolled, then finally determine the visibility of navigation button.
 * @param onClickNavigation called when clicked navigation button.
 *      `step` is the scroll step, positive means scroll forward.
 * @see HorizontalScrollNavigator
 */
@Composable
fun rememberHorizontalScrollNavigatorState(
    scrollableState: ScrollableState,
    scrollStep: Float = HorizontalScrollNavigatorDefaults.ScrollStep,
    onClickNavigation: (step: Float) -> Unit = { },
): HorizontalScrollNavigatorState {
    val scope = rememberCoroutineScope()
    return remember(scrollableState, scrollStep, scope) {
        HorizontalScrollNavigatorState(scrollableState, scrollStep, onClickNavigation, scope)
    }
}

class HorizontalScrollNavigatorState(
    private val scrollableState: ScrollableState,
    private val scrollStep: Float,
    private val onClickNavigation: (step: Float) -> Unit,
    private val scope: CoroutineScope,
) {
    private var pointerPosition: Offset? by mutableStateOf(null)
    private var leftButtonPosition: Offset? by mutableStateOf(null)
    private var rightButtonPosition: Offset? by mutableStateOf(null)

    val showLeftButton: Boolean by derivedStateOf {
        scrollableState.canScrollBackward && isPointerNear(leftButtonPosition, pointerPosition)
    }

    val showRightButton: Boolean by derivedStateOf {
        scrollableState.canScrollForward && isPointerNear(rightButtonPosition, pointerPosition)
    }

    fun updateMousePointerPosition(position: Offset) {
        pointerPosition = position
    }

    fun updateLeftButtonCoordinate(layoutCoordinates: LayoutCoordinates) {
        val buttonSize = layoutCoordinates.size.toSize()
        leftButtonPosition = layoutCoordinates.positionInParent() +
                Offset(buttonSize.width / 2f, buttonSize.height / 2f)
    }

    fun updateRightButtonCoordinate(layoutCoordinates: LayoutCoordinates) {
        val buttonSize = layoutCoordinates.size.toSize()
        rightButtonPosition = layoutCoordinates.positionInParent() +
                Offset(buttonSize.width / 2f, buttonSize.height / 2f)
    }

    fun scrollLeft() {
        scroll(-scrollStep)
    }

    fun scrollRight() {
        scroll(scrollStep)
    }

    private fun scroll(step: Float) {
        onClickNavigation(step)
        scope.launch {
            scrollableState.animateScrollBy(step)
        }
    }

    private fun isPointerNear(buttonPosition: Offset?, pointerPosition: Offset?): Boolean {
        if (buttonPosition == null || pointerPosition == null) return false
        val dist = buttonPosition - pointerPosition
        val buttonSize = HorizontalScrollNavigatorDefaults.ButtonSize.value
        return dist.x * dist.x + dist.y * dist.y <= (buttonSize * 2).pow(2)
    }
}

object HorizontalScrollNavigatorDefaults {
    val ButtonMargin = 12.dp
    val ButtonSize = 64.dp
    const val ScrollStep: Float = 200f

    @Composable
    fun ScrollLeftButton(modifier: Modifier = Modifier) {
        Surface(
            modifier = Modifier.size(ButtonSize),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.7f),
        ) {
            ProvideContentColor(Color.White) {
                Box(
                    modifier = modifier,
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ArrowBackIosNew,
                        contentDescription = "Scroll left",
                    )
                }
            }
        }
    }

    @Composable
    fun ScrollRightButton(modifier: Modifier = Modifier) {
        ScrollLeftButton(Modifier.rotate(180f).then(modifier))
    }
}