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
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import kotlin.math.pow

/**
 * Provide buttons to navigate horizontally. Effectively works on desktop.
 */
@Composable
fun HorizontalScrollControlScaffold(
    state: HorizontalScrollControlState,
    modifier: Modifier = Modifier,
    scrollLeftButton: @Composable () -> Unit = {
        HorizontalScrollNavigatorDefaults.ScrollLeftButton()
    },
    scrollRightButton: @Composable () -> Unit = {
        HorizontalScrollNavigatorDefaults.ScrollRightButton()
    },
    buttonShape: Shape = CircleShape,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Exit) {
                            state.calculateDistance(Offset.Unspecified)
                            continue
                        }
                        event.changes.firstOrNull()?.let { pointerInputChange ->
                            state.calculateDistance(pointerInputChange.position)
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
                .onPlaced { state.updateLeftButtonCoordinate(layoutCoordinates = it) },
        ) {
            Crossfade(targetState = state.showLeftButton) { show ->
                if (show) {
                    Surface(
                        onClick = { state.scrollLeft() },
                        shape = buttonShape,
                        color = Color.Unspecified,
                        content = scrollLeftButton,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = HorizontalScrollNavigatorDefaults.ButtonMargin)
                .onPlaced { state.updateRightButtonCoordinate(layoutCoordinates = it) },
        ) {
            Crossfade(targetState = state.showRightButton) { show ->
                if (show) {
                    Surface(
                        onClick = { state.scrollRight() },
                        shape = buttonShape,
                        color = Color.Unspecified,
                        content = scrollRightButton,
                    )
                }
            }
        }
    }
}

/**
 * Create a [HorizontalScrollControlState] that can be used to navigate horizontally
 *
 * @param scrollableState the incoming scrollable state. Use this to detect
 *      if the content can be scrolled, then finally determine the visibility of navigation button.
 * @param onClickScroll called when clicked navigation button.
 *      `step` is the scroll step, positive means scroll forward.
 *      You should handle actual scrolling in this lambda.
 * @see HorizontalScrollControlScaffold
 */
@Composable
fun rememberHorizontalScrollControlState(
    scrollableState: ScrollableState,
    scrollStep: () -> Dp = { HorizontalScrollNavigatorDefaults.ScrollStep },
    onClickScroll: (step: Dp) -> Unit,
): HorizontalScrollControlState {
    val onClickScrollUpdated by rememberUpdatedState(onClickScroll)
    return remember(scrollableState) {
        HorizontalScrollControlState(scrollableState, scrollStep) { onClickScrollUpdated(it) }
    }
}

@Stable
class HorizontalScrollControlState(
    private val scrollableState: ScrollableState,
    private val scrollStep: () -> Dp,
    private val onClickScroll: (step: Dp) -> Unit
) {
    private var isPointerNearLeftButton by mutableStateOf(false)
    private var isPointerNearRightButton by mutableStateOf(false)
    private var leftButtonPosition: Offset by mutableStateOf(Offset.Unspecified)
    private var rightButtonPosition: Offset by mutableStateOf(Offset.Unspecified)

    val showLeftButton: Boolean by derivedStateOf {
        scrollableState.canScrollBackward && isPointerNearLeftButton
    }

    val showRightButton: Boolean by derivedStateOf {
        scrollableState.canScrollForward && isPointerNearRightButton
    }

    fun calculateDistance(position: Offset) {
        if (position.isUnspecified) {
            isPointerNearLeftButton = false
            isPointerNearRightButton = false
            return
        }
        isPointerNearLeftButton = isPointerNear(leftButtonPosition, position)
        isPointerNearRightButton = isPointerNear(rightButtonPosition, position)
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
        onClickScroll(-scrollStep())
    }

    fun scrollRight() {
        onClickScroll(scrollStep())
    }

    private fun isPointerNear(buttonPosition: Offset, pointerPosition: Offset): Boolean {
        val dist = buttonPosition - pointerPosition
        val buttonSize = HorizontalScrollNavigatorDefaults.ButtonSize.value
        return dist.x * dist.x + dist.y * dist.y <= (buttonSize * 1.5).pow(2)
    }
}

@Stable
object HorizontalScrollNavigatorDefaults {
    val ButtonMargin = 12.dp
    val ButtonSize = 64.dp
    val ScrollStep = 200.dp

    @Composable
    fun ScrollLeftButton(
        modifier: Modifier = Modifier,
        contentDescription: String = "Scroll left"
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.7f),
        ) {
            ProvideContentColor(Color.White) {
                Box(
                    modifier = Modifier.size(ButtonSize).then(modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ArrowBackIosNew,
                        contentDescription = contentDescription,
                    )
                }
            }
        }
    }

    @Composable
    fun ScrollRightButton(
        modifier: Modifier = Modifier,
        contentDescription: String = "Scroll right"
    ) {
        ScrollLeftButton(
            Modifier.rotate(180f).then(modifier),
            contentDescription = contentDescription,
        )
    }
}