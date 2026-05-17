/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.interaction

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

fun Modifier.keyboardDirectionToSelectItem(
    selectedItemIndex: () -> Int,
    itemCount: () -> Int,
    onSelect: suspend (Int) -> Unit,
) = composed {
    val scope = rememberCoroutineScope()
    onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyUp) {
            when (it.key) {
                Key.DirectionUp -> {
                    scope.launch {
                        calculateDirectionSelectionIndex(
                            selectedItemIndex = selectedItemIndex(),
                            itemCount = itemCount(),
                            delta = -1,
                        )?.let { newIndex ->
                            onSelect(newIndex)
                        }
                    }
                    true
                }

                Key.DirectionDown -> {
                    scope.launch {
                        calculateDirectionSelectionIndex(
                            selectedItemIndex = selectedItemIndex(),
                            itemCount = itemCount(),
                            delta = 1,
                        )?.let { newIndex ->
                            onSelect(newIndex)
                        }
                    }
                    true
                }

                else -> false
            }
        } else false
    }
}

internal fun calculateDirectionSelectionIndex(
    selectedItemIndex: Int,
    itemCount: Int,
    delta: Int,
): Int? {
    if (itemCount <= 0) return null

    val currentIndex = selectedItemIndex.coerceAtLeast(-1)
    val newIndex = if (currentIndex == -1) {
        0
    } else {
        (currentIndex + delta).coerceIn(0, itemCount - 1)
    }
    return newIndex.takeIf { it != selectedItemIndex }
}

fun Modifier.keyboardPageToScroll(
    height: () -> Float,
    onScrollBy: suspend (px: Float) -> Unit,
) = composed {
    val scope = rememberCoroutineScope()
    onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyUp) {
            when (it.key) {
                Key.PageDown -> {
                    scope.launch { onScrollBy(height()) }
                    true
                }

                Key.PageUp -> {
                    scope.launch { onScrollBy(-height()) }
                    true
                }

                else -> false
            }
        } else false
    }
}
