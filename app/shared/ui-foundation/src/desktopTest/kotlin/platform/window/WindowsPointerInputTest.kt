/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinDef.POINT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsPointerInputTest {
    @Test
    fun `primary pointer has stable press move release lifecycle`() {
        val state = WindowsPointerSequenceStateMachine()
        val pointer = pointer(id = 7, x = 100, y = 200)

        assertEquals(WindowsTouchEventType.PRESS, state.handle(WindowsPointerMessage.DOWN, pointer).eventType())
        assertEquals(WindowsPointerSequenceState.PRIMARY_ACTIVE, state.state)
        assertEquals(7L, state.primaryPointerId)

        val move = state.handle(
            WindowsPointerMessage.UPDATE,
            pointer.copy(screenX = 120, screenY = 220),
        )
        assertEquals(WindowsTouchEventType.MOVE, move.eventType())
        assertEquals(7L, state.primaryPointerId)

        val release = state.handle(
            WindowsPointerMessage.UP,
            pointer.copy(screenX = 130, screenY = 230, flags = 0),
        )
        assertEquals(WindowsTouchEventType.RELEASE, release.eventType())
        assertEquals(WindowsPointerSequenceState.IDLE, state.state)
        assertEquals(null, state.primaryPointerId)
    }

    @Test
    fun `secondary pointers are consumed but never promoted`() {
        val state = WindowsPointerSequenceStateMachine()
        val primary = pointer(id = 1, x = 0, y = 0)
        val secondary = pointer(id = 2, x = 10, y = 10)

        state.handle(WindowsPointerMessage.DOWN, primary)
        assertEquals(WindowsPointerDispatch.Consume, state.handle(WindowsPointerMessage.DOWN, secondary))
        assertEquals(WindowsPointerDispatch.Consume, state.handle(WindowsPointerMessage.UPDATE, secondary))

        assertEquals(
            WindowsTouchEventType.RELEASE,
            state.handle(WindowsPointerMessage.UP, primary).eventType(),
        )
        assertEquals(WindowsPointerSequenceState.DRAINING, state.state)
        assertEquals(null, state.primaryPointerId)

        assertEquals(WindowsPointerDispatch.Consume, state.handle(WindowsPointerMessage.UP, secondary))
        assertEquals(WindowsPointerSequenceState.IDLE, state.state)
        assertEquals(WindowsPointerDispatch.Pass, state.handle(WindowsPointerMessage.UPDATE, secondary))
    }

    @Test
    fun `unknown touch messages are not taken over while idle`() {
        val state = WindowsPointerSequenceStateMachine()
        val pointer = pointer(id = 3, x = 0, y = 0)

        assertEquals(WindowsPointerDispatch.Pass, state.handle(WindowsPointerMessage.UPDATE, pointer))
        assertEquals(WindowsPointerDispatch.Pass, state.handle(WindowsPointerMessage.UP, pointer))
        assertEquals(WindowsPointerSequenceState.IDLE, state.state)
    }

    @Test
    fun `cancel clears primary and suppressed pointers`() {
        val state = WindowsPointerSequenceStateMachine()
        state.handle(WindowsPointerMessage.DOWN, pointer(id = 1, x = 0, y = 0))
        state.handle(WindowsPointerMessage.DOWN, pointer(id = 2, x = 0, y = 0))

        assertEquals(WindowsPointerDispatch.Cancel, state.handleCaptureChanged())
        assertEquals(WindowsPointerSequenceState.IDLE, state.state)
        assertFalse(state.owns(1))
        assertFalse(state.owns(2))
        assertEquals(WindowsPointerDispatch.Pass, state.handleCaptureChanged())
    }

    @Test
    fun `canceled update cancels an owned sequence`() {
        val state = WindowsPointerSequenceStateMachine()
        state.handle(WindowsPointerMessage.DOWN, pointer(id = 1, x = 0, y = 0))

        assertEquals(
            WindowsPointerDispatch.Cancel,
            state.handle(WindowsPointerMessage.UPDATE, pointer(id = 1, x = 2, y = 2, flags = POINTER_FLAG_CANCELED)),
        )
        assertEquals(WindowsPointerSequenceState.IDLE, state.state)
    }

    @Test
    fun `non-contact primary update cancels instead of producing a move`() {
        val state = WindowsPointerSequenceStateMachine()
        state.handle(WindowsPointerMessage.DOWN, pointer(id = 1, x = 0, y = 0))

        assertEquals(
            WindowsPointerDispatch.Cancel,
            state.handle(WindowsPointerMessage.UPDATE, pointer(id = 1, x = 2, y = 2, flags = 0)),
        )
        assertEquals(WindowsPointerSequenceState.IDLE, state.state)
    }

    @Test
    fun `pointer id is limited to the low word of wParam`() {
        assertEquals(0x1234L, pointerIdFromWParam(WPARAM(0xABCD1234L)))
    }

    @Test
    fun `pointer data preserves the unsigned native event time`() {
        val info = POINTER_INFO().apply {
            pointerType = PT_TOUCH
            pointerFlags = POINTER_FLAG_INCONTACT
            ptPixelLocation = POINT(100, 200)
            dwTime = -1
        }

        assertEquals(0xFFFFFFFFL, info.toPointerData(pointerId = 7).eventTimeMillis)
    }

    @Test
    fun `non-touch pointers are passed through`() {
        val state = WindowsPointerSequenceStateMachine()
        assertEquals(
            WindowsPointerDispatch.Pass,
            state.handle(WindowsPointerMessage.DOWN, pointer(id = 1, x = 0, y = 0, pointerType = 1)),
        )
    }

    @Test
    fun `read failure does not take over a new sequence`() {
        var cancelCount = 0
        val handler = WindowsPointerInputHandler(
            readPointerInfo = { _, _ -> false },
            dispatch = {},
            cancel = { cancelCount++ },
        )

        assertFalse(handler.handleMessage(WM_POINTERDOWN, WPARAM(1)))
        assertEquals(0, cancelCount)
    }

    @Test
    fun `read failure cancels and consumes an owned sequence`() {
        var readCount = 0
        var cancelCount = 0
        val handler = WindowsPointerInputHandler(
            readPointerInfo = { _, info ->
                readCount++
                if (readCount == 1) {
                    info.pointerType = PT_TOUCH
                    info.pointerFlags = POINTER_FLAG_INCONTACT
                    true
                } else {
                    false
                }
            },
            dispatch = {},
            cancel = { cancelCount++ },
        )

        assertTrue(handler.handleMessage(WM_POINTERDOWN, WPARAM(1)))
        assertTrue(handler.handleMessage(WM_POINTERUPDATE, WPARAM(1)))
        assertEquals(1, cancelCount)

        assertFalse(handler.handleMessage(WM_POINTERUPDATE, WPARAM(1)))
    }

    @Test
    fun `dispatch failure disables bridge and falls back for a new sequence`() {
        var cancelCount = 0
        val handler = WindowsPointerInputHandler(
            readPointerInfo = { _, info ->
                info.pointerType = PT_TOUCH
                info.pointerFlags = POINTER_FLAG_INCONTACT
                true
            },
            dispatch = { error("injected dispatch failure") },
            cancel = { cancelCount++ },
        )

        // No sequence was successfully taken over, so the native message may
        // fall back to the original procedure after the fatal bridge failure.
        assertFalse(handler.handleMessage(WM_POINTERDOWN, WPARAM(1)))
        assertEquals(1, cancelCount)
        assertFalse(handler.handleMessage(WM_POINTERDOWN, WPARAM(2)))
    }

    @Test
    fun `close cancels an active sequence exactly once`() {
        var cancelCount = 0
        val handler = WindowsPointerInputHandler(
            readPointerInfo = { _, info ->
                info.pointerType = PT_TOUCH
                info.pointerFlags = POINTER_FLAG_INCONTACT
                true
            },
            dispatch = {},
            cancel = { cancelCount++ },
        )

        assertTrue(handler.handleMessage(WM_POINTERDOWN, WPARAM(1)))
        handler.close()
        handler.close()

        assertEquals(1, cancelCount)
        assertFalse(handler.handleMessage(WM_POINTERUP, WPARAM(1)))
    }

    private fun pointer(
        id: Long,
        x: Int,
        y: Int,
        flags: Int = POINTER_FLAG_INCONTACT,
        pointerType: Int = PT_TOUCH,
    ) = WindowsPointerData(id, pointerType, flags, x, y)

    private fun WindowsPointerDispatch.eventType(): WindowsTouchEventType {
        assertTrue(this is WindowsPointerDispatch.Send)
        return this.event.type
    }
}
