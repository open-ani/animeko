/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay

/**
 * TV-safe clickable modifier that prevents long-press from triggering unwanted clicks.
 * 
 * On Android TV, this modifier:
 * - Handles DPAD_CENTER (OK button) key events properly
 * - Distinguishes between short-press (click) and long-press
 * - Prevents long-press from triggering onClick
 * - Only triggers onClick on KeyUp for short presses
 * 
 * On non-TV platforms, falls back to standard clickable behavior.
 * 
 * @param enabled Controls the enabled state
 * @param onClickLabel Semantic label for the click action
 * @param onClick Callback invoked on short-press/click
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier = composed {
    val isTv = LocalPlatform.current.isTv()
    
    if (isTv) {
        var keyDownTime by remember { mutableStateOf(0L) }
        var isLongPress by remember { mutableStateOf(false) }
        
        this
            .onKeyEvent { keyEvent ->
                if (!enabled) return@onKeyEvent false
                
                when {
                    keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyDown -> {
                        if (keyDownTime == 0L) {
                            // First key down
                            keyDownTime = System.currentTimeMillis()
                            isLongPress = false
                        } else {
                            // Key repeat (long press)
                            isLongPress = true
                        }
                        true // Consume the event
                    }
                    keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyUp -> {
                        val pressDuration = System.currentTimeMillis() - keyDownTime
                        keyDownTime = 0L
                        
                        // Only trigger click for short press (< 500ms)
                        if (!isLongPress && pressDuration < 500) {
                            onClick()
                        }
                        isLongPress = false
                        true // Consume the event
                    }
                    else -> false
                }
            }
            .combinedClickable(
                enabled = enabled,
                onClickLabel = onClickLabel,
                onClick = onClick
            )
    } else {
        // Non-TV: use standard clickable
        this.combinedClickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
    }
}

/**
 * TV-safe combined clickable modifier with support for both click and long-click.
 * 
 * On Android TV, this modifier:
 * - Handles DPAD_CENTER (OK button) key events properly
 * - Distinguishes between short-press (onClick) and long-press (onLongClick)
 * - Prevents long-press from triggering onClick
 * - Triggers onLongClick after holding for threshold duration
 * 
 * On non-TV platforms, falls back to standard combinedClickable behavior.
 * 
 * @param enabled Controls the enabled state
 * @param onClickLabel Semantic label for the click action
 * @param onLongClickLabel Semantic label for the long-click action
 * @param onDoubleClick Callback invoked on double-click (primarily for touch input)
 * @param onClick Callback invoked on short-press/click
 * @param onLongClick Callback invoked on long-press, or null if not supported
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val isTv = LocalPlatform.current.isTv()
    
    if (isTv && onLongClick != null) {
        var keyDownTime by remember { mutableStateOf(0L) }
        var longClickTriggered by remember { mutableStateOf(false) }
        var isKeyDown by remember { mutableStateOf(false) }
        
        // Monitor for long press threshold
        LaunchedEffect(isKeyDown) {
            if (isKeyDown && keyDownTime > 0L) {
                delay(500) // Long press threshold
                if (isKeyDown && System.currentTimeMillis() - keyDownTime >= 500) {
                    longClickTriggered = true
                    onLongClick()
                }
            }
        }
        
        this
            .onKeyEvent { keyEvent ->
                if (!enabled) return@onKeyEvent false
                
                when {
                    keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyDown -> {
                        if (keyDownTime == 0L) {
                            // First key down
                            keyDownTime = System.currentTimeMillis()
                            longClickTriggered = false
                            isKeyDown = true
                        }
                        true // Consume the event
                    }
                    keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyUp -> {
                        isKeyDown = false
                        val pressDuration = System.currentTimeMillis() - keyDownTime
                        keyDownTime = 0L
                        
                        // Only trigger click if long click wasn't triggered and it was a short press
                        if (!longClickTriggered && pressDuration < 500) {
                            onClick()
                        }
                        longClickTriggered = false
                        true // Consume the event
                    }
                    else -> false
                }
            }
            .combinedClickable(
                enabled = enabled,
                onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel,
                onDoubleClick = onDoubleClick,
                onLongClick = onLongClick,
                onClick = onClick
            )
    } else if (isTv) {
        // TV without long click - use tvClickable
        tvClickable(enabled, onClickLabel, onClick)
    } else {
        // Non-TV: use standard combinedClickable
        this.combinedClickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onDoubleClick = onDoubleClick,
            onLongClick = onLongClick,
            onClick = onClick
        )
    }
}
