/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.interaction

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.animation.StandardDecelerateEasing
import me.him188.ani.app.ui.foundation.effects.onPointerEventMultiplatform
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState

// https://github.com/JetBrains/compose-multiplatform/issues/4975 // 据说 Jetpack Compose 也有问题
/**
 * 用于解决用鼠标滚轮或触摸板时无法回到顶部的问题. 当 Compose 修复 bug 后, 直接删除此 modifier 就行.
 *
 * @param scrollableState 用于提供滑动状态的容器. LazyList, 或者 LazyGrid, 或者 `Modifier.scrollable`.
 */
fun Modifier.nestedScrollWorkaround(
    scrollableState: ScrollableState,
    connectedScrollState: ConnectedScrollState,
): Modifier {
    return composed {
        val scope = rememberCoroutineScope()
        var isInProgress = false
        onPointerEventMultiplatform(PointerEventType.Scroll, pass = PointerEventPass.Final) {
            if (isInProgress) return@onPointerEventMultiplatform

            val event = it.changes.getOrNull(0) ?: return@onPointerEventMultiplatform
            if (event.type != PointerType.Mouse) {
                // 只有鼠标有 bug
                return@onPointerEventMultiplatform
            }

            val scrollDelta = event.scrollDelta

            if (scrollDelta != Offset.Unspecified && scrollDelta != Offset.Zero) {
                if (!scrollableState.canScrollBackward && scrollDelta.y < -0.5f) { // 0.5 为阈值, 防止稍微动一下
//                    connectedScrollState.scrollableState.dispatchRawDelta(-scrollDelta.y) // 太慢了

                    isInProgress = true
                    scope.launch {
                        try {
                            // 直接滑到顶部
                            connectedScrollState.scrollableState.animateScrollBy(
                                -connectedScrollState.scrolledOffset,
                                tween(500, easing = StandardDecelerateEasing),
                            )
                        } finally {
                            isInProgress = false
                        }
                    }
                }
            }
        }
    }
}