/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package me.him188.ani.app.platform.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.scene.ComposeScene
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeSceneTouchBridgeTest {
    @Test
    fun `screen physical pixels are converted using content origin density and scene bounds`() {
        assertEquals(
            Offset(104f, 76f),
            windowsScreenPositionToScenePosition(
                screenX = 500,
                screenY = 400,
                contentLocationOnScreen = Point(200, 150),
                density = 1.5f,
                sceneBoundsInPx = Rect(left = 96f, top = 99f, right = 1200f, bottom = 800f),
            ),
        )
    }

    @Test
    fun `queue merges consecutive moves and schedules one drain until emptied`() {
        val queue = WindowsTouchCommandQueue()
        val pointer = WindowsPointerData(1, PT_TOUCH, POINTER_FLAG_INCONTACT, 0, 0)
        fun move(x: Int, time: Long) = WindowsTouchCommand.Inject(
            WindowsTouchEvent(
                WindowsTouchEventType.MOVE,
                pointer.copy(screenX = x, eventTimeMillis = time),
                listOf(WindowsTouchPointer(1, x, 0, pressed = true, WindowsPointerKind.TOUCH, 1f)),
            ),
        )

        assertEquals(true, queue.offer(move(10, 1)))
        assertEquals(false, queue.offer(move(20, 2)))
        assertEquals(false, queue.offer(move(30, 3)))
        assertEquals(false, queue.offer(WindowsTouchCommand.Cancel))
        assertEquals(2, queue.size())

        val merged = queue.poll() as WindowsTouchCommand.Inject
        assertEquals(30, merged.event.pointers.single().screenX)
        assertEquals(listOf(1L, 2L), merged.event.pointers.single().historical.map { it.eventTimeMillis })
        assertEquals(WindowsTouchCommand.Cancel, queue.poll())
        assertEquals(null, queue.poll())

        assertEquals(true, queue.offer(move(40, 4)))
    }

    @Test
    fun `historical samples carry the position the velocity tracker reads`() {
        val change = historicalChange(uptimeMillis = 5, scenePosition = Offset(120f, 340f))
        assertEquals(Offset(120f, 340f), change.originalEventPosition)
    }

    @Test
    fun `current CMP exposes one list pointer event overload`() {
        val methods = ComposeScene::class.java.methods.filter { method ->
            method.name.startsWith("sendPointerEvent-") &&
                method.parameterTypes.size == 10 &&
                method.parameterTypes[1] == List::class.java
        }

        assertEquals(1, methods.size)
        assertEquals(Int::class.javaPrimitiveType, methods.single().parameterTypes[0])
        assertEquals(Long::class.javaPrimitiveType, methods.single().parameterTypes[4])
        assertEquals(Long::class.javaPrimitiveType, methods.single().parameterTypes[5])
        assertEquals(Float::class.javaPrimitiveType, methods.single().parameterTypes[8])
        assertEquals(Long::class.javaPrimitiveType, methods.single().parameterTypes[9])
    }
}
