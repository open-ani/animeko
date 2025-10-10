/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.dialogs.PlatformDialogProperties

@Composable
fun rememberModalSideSheetState(): ModalSideSheetState {
    return remember { ModalSideSheetState() }
}

@Stable
class ModalSideSheetState {
    var dismissed: Boolean by mutableStateOf(false)
        private set

    fun close() {
        dismissed = true
    }
}

enum class SheetSide { Start, End }

/**
 * A simple modal side sheet implementation following Material3 guidelines.
 * The sheet animates in from [side] and blocks interaction with underlying content using a scrim.
 *
 * @param onDismiss Called when the sheet has been completely dismissed.
 */
@Composable
fun ModalSideSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: ModalSideSheetState = rememberModalSideSheetState(),
    side: SheetSide = SheetSide.End,
    shape: Shape = MaterialTheme.shapes.extraLarge.copy(
        topStart = if (side == SheetSide.Start) CornerSize(0f) else MaterialTheme.shapes.extraLarge.topStart,
        topEnd = if (side == SheetSide.End) CornerSize(0f) else MaterialTheme.shapes.extraLarge.topEnd,
        bottomStart = if (side == SheetSide.Start) CornerSize(0f) else MaterialTheme.shapes.extraLarge.bottomStart,
        bottomEnd = if (side == SheetSide.End) CornerSize(0f) else MaterialTheme.shapes.extraLarge.bottomEnd,
    ),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = DrawerDefaults.scrimColor,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var presentVisibility by rememberSaveable { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (presentVisibility) 1f else 0f,
        animationSpec = tween(),
    )
    var sheetWidth by rememberSaveable { mutableIntStateOf(0) }
    val sheetOffset by remember {
        derivedStateOf {
            val offset = sheetWidth * (1 - scrimAlpha)
            if (side == SheetSide.End) offset else -offset
        }
    }

    val animateToDismiss: () -> Unit = {
        focusManager.clearFocus(force = true)
        presentVisibility = false
        scope.launch {
            snapshotFlow { scrimAlpha }.collect {
                if (it == 0f && !presentVisibility) onDismiss()
            }
        }
    }

    LaunchedEffect(Unit) {
        presentVisibility = true
        snapshotFlow { state.dismissed }
            .distinctUntilChanged()
            .collect { if (it) animateToDismiss() }
    }

    Dialog(
        onDismissRequest = animateToDismiss,
        properties = PlatformDialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            usePlatformInsets = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().imePadding()) {
            Canvas(
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = animateToDismiss,
                ),
            ) {
                drawRect(color = scrimColor, alpha = scrimAlpha)
            }
            Surface(
                modifier = modifier
                    .fillMaxHeight()
                    .align(if (side == SheetSide.End) Alignment.CenterEnd else Alignment.CenterStart)
                    .graphicsLayer { translationX = sheetOffset }
                    .onSizeChanged { sheetWidth = it.width },
                shape = shape,
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation,
            ) {
                Box { content() }
            }
        }
    }
}
