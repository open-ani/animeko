/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.WPARAM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WindowsPointerInputTest {
    @Test
    fun `single contact has press move release lifecycle`() {
        val tracker = WindowsPointerContactTracker()
        val pointer = pointer(id = 7, x = 100, y = 200)

        val press = tracker.handle(WindowsPointerMessage.DOWN, pointer).event()
        assertEquals(WindowsTouchEventType.PRESS, press.type)
        assertEquals(listOf(7L to true), press.pressedById())

        val move = tracker.handle(WindowsPointerMessage.UPDATE, pointer.copy(screenX = 120, screenY = 220)).event()
        assertEquals(WindowsTouchEventType.MOVE, move.type)
        assertEquals(120, move.pointers.single().screenX)

        val release = tracker.handle(WindowsPointerMessage.UP, pointer.copy(flags = 0)).event()
        assertEquals(WindowsTouchEventType.RELEASE, release.type)
        assertEquals(listOf(7L to false), release.pressedById())
        assertFalse(tracker.isActive)
    }

    @Test
    fun `every event carries all contacts and a released contact is listed once unpressed`() {
        val tracker = WindowsPointerContactTracker()
        val first = pointer(id = 1, x = 0, y = 0)
        val second = pointer(id = 2, x = 100, y = 0)

        tracker.handle(WindowsPointerMessage.DOWN, first)
        val secondPress = tracker.handle(WindowsPointerMessage.DOWN, second).event()
        assertEquals(WindowsTouchEventType.PRESS, secondPress.type)
        assertEquals(listOf(1L to true, 2L to true), secondPress.pressedById())

        val move = tracker.handle(WindowsPointerMessage.UPDATE, second.copy(screenX = 150)).event()
        assertEquals(WindowsTouchEventType.MOVE, move.type)
        assertEquals(listOf(0, 150), move.pointers.map { it.screenX })

        val release = tracker.handle(WindowsPointerMessage.UP, first.copy(flags = 0)).event()
        assertEquals(WindowsTouchEventType.RELEASE, release.type)
        assertEquals(listOf(1L to false, 2L to true), release.pressedById())

        val moveAfterRelease = tracker.handle(WindowsPointerMessage.UPDATE, second.copy(screenX = 160)).event()
        assertEquals(listOf(2L to true), moveAfterRelease.pressedById())
    }

    @Test
    fun `unknown touch messages are not taken over while idle`() {
        val tracker = WindowsPointerContactTracker()
        val pointer = pointer(id = 3, x = 0, y = 0)

        assertEquals(WindowsPointerDispatch.Pass, tracker.handle(WindowsPointerMessage.UPDATE, pointer))
        assertEquals(WindowsPointerDispatch.Pass, tracker.handle(WindowsPointerMessage.UP, pointer))
        assertFalse(tracker.isActive)
    }

    @Test
    fun `hovering pen passes through and contacting pen is injected as stylus`() {
        val tracker = WindowsPointerContactTracker()
        val hovering = pointer(id = 9, x = 0, y = 0, flags = 0, pointerType = PT_PEN, kind = WindowsPointerKind.STYLUS)

        assertEquals(WindowsPointerDispatch.Pass, tracker.handle(WindowsPointerMessage.UPDATE, hovering))

        val contact = hovering.copy(flags = POINTER_FLAG_INCONTACT, pressure = 0.5f)
        val press = tracker.handle(WindowsPointerMessage.DOWN, contact).event()
        assertEquals(WindowsPointerKind.STYLUS, press.pointers.single().kind)
        assertEquals(0.5f, press.pointers.single().pressure)

        tracker.handle(WindowsPointerMessage.UP, contact.copy(flags = 0)).event()
        assertEquals(WindowsPointerDispatch.Pass, tracker.handle(WindowsPointerMessage.UPDATE, hovering))
    }

    @Test
    fun `canceled up cancels everything and keeps consuming the other contacts until they lift`() {
        val tracker = WindowsPointerContactTracker()
        val first = pointer(id = 1, x = 0, y = 0)
        val second = pointer(id = 2, x = 10, y = 10)
        tracker.handle(WindowsPointerMessage.DOWN, first)
        tracker.handle(WindowsPointerMessage.DOWN, second)

        assertEquals(
            WindowsPointerDispatch.Cancel,
            tracker.handle(WindowsPointerMessage.UP, first.copy(flags = POINTER_FLAG_CANCELED)),
        )
        assertEquals(WindowsPointerDispatch.Consume, tracker.handle(WindowsPointerMessage.UPDATE, second))
        assertEquals(WindowsPointerDispatch.Consume, tracker.handle(WindowsPointerMessage.UP, second))
        assertFalse(tracker.isActive)
    }

    @Test
    fun `a new down reuses an id that is still suppressed`() {
        val tracker = WindowsPointerContactTracker()
        val first = pointer(id = 1, x = 0, y = 0)
        val second = pointer(id = 2, x = 10, y = 10)
        tracker.handle(WindowsPointerMessage.DOWN, first)
        tracker.handle(WindowsPointerMessage.DOWN, second)
        // The system took the pointers; their UPs go to another window and never reach us.
        assertEquals(WindowsPointerDispatch.Cancel, tracker.handleCaptureChanged())
        assertEquals(setOf(1L, 2L), tracker.suppressedPointerIds)

        val press = tracker.handle(WindowsPointerMessage.DOWN, second.copy(screenX = 50)).event()
        assertEquals(WindowsTouchEventType.PRESS, press.type)
        assertEquals(listOf(2L to true), press.pressedById())
        assertEquals(setOf(1L), tracker.suppressedPointerIds)
    }

    @Test
    fun `capture change without contacts is passed through`() {
        assertEquals(WindowsPointerDispatch.Pass, WindowsPointerContactTracker().handleCaptureChanged())
    }

    @Test
    fun `contact lost without up releases the pointer`() {
        val tracker = WindowsPointerContactTracker()
        val pointer = pointer(id = 5, x = 0, y = 0)
        tracker.handle(WindowsPointerMessage.DOWN, pointer)

        val release = tracker.handle(WindowsPointerMessage.UPDATE, pointer.copy(flags = 0)).event()
        assertEquals(WindowsTouchEventType.RELEASE, release.type)
        assertFalse(tracker.isActive)
    }

    @Test
    fun `read failure cancels a tracked contact but not an unknown one`() {
        val tracker = WindowsPointerContactTracker()
        assertEquals(WindowsPointerDispatch.Pass, tracker.handleReadFailure(4))

        tracker.handle(WindowsPointerMessage.DOWN, pointer(id = 4, x = 0, y = 0))
        assertEquals(WindowsPointerDispatch.Cancel, tracker.handleReadFailure(4))
        assertFalse(tracker.isActive)
    }

    @Test
    fun `mouse pointers are passed through`() {
        val tracker = WindowsPointerContactTracker()
        assertEquals(
            WindowsPointerDispatch.Pass,
            tracker.handle(WindowsPointerMessage.DOWN, pointer(id = 1, x = 0, y = 0, pointerType = 1)),
        )
    }

    @Test
    fun `pointer id is limited to the low word of wParam`() {
        assertEquals(0x1234L, pointerIdFromWParam(WPARAM(0xABCD_1234L)))
    }

    @Test
    fun `pointer data preserves the unsigned native event time`() {
        val info = POINTER_INFO().apply { dwTime = -1 }
        assertEquals(0xFFFFFFFFL, info.toPointerData(1).eventTimeMillis)
    }

    @Test
    fun `pen info maps eraser flags and normalizes pressure`() {
        val pen = POINTER_PEN_INFO().apply {
            penFlags = PEN_FLAG_INVERTED
            penMask = PEN_MASK_PRESSURE
            pressure = 512
        }
        assertEquals(WindowsPointerKind.ERASER, pen.kind)
        assertEquals(0.5f, pen.normalizedPressure)

        val penWithoutPressure = POINTER_PEN_INFO().apply { pressure = 512 }
        assertEquals(WindowsPointerKind.STYLUS, penWithoutPressure.kind)
        assertEquals(1f, penWithoutPressure.normalizedPressure)
    }

    @Test
    fun `handler reads pen info for pen pointers only`() {
        val dispatched = mutableListOf<WindowsTouchEvent>()
        var penReads = 0
        val handler = WindowsPointerInputHandler(
            readPointerInfo = { id, info ->
                info.pointerType = if (id == 2) PT_PEN else PT_TOUCH
                info.pointerFlags = POINTER_FLAG_INCONTACT
                info.ptPixelLocation = POINT(30, 30)
                true
            },
            dispatch = dispatched::add,
            cancel = {},
            readPointerPenInfo = { _, pen ->
                penReads++
                pen.penMask = PEN_MASK_PRESSURE
                pen.pressure = 256
                true
            },
        )

        assertTrue(handler.handleMessage(WM_POINTERDOWN, WPARAM(1)))
        assertTrue(handler.handleMessage(WM_POINTERDOWN, WPARAM(2)))

        assertEquals(1, penReads)
        val byId = dispatched.last().pointers.associateBy { it.id }
        assertEquals(WindowsPointerKind.TOUCH, byId.getValue(1L).kind)
        assertEquals(WindowsPointerKind.STYLUS, byId.getValue(2L).kind)
        assertEquals(0.25f, byId.getValue(2L).pressure)
    }

    @Test
    fun `dispatch failure disables bridge and falls back for a new sequence`() {
        var cancelCalls = 0
        val handler = touchHandler(
            dispatch = { error("injection failed") },
            cancel = { cancelCalls++ },
        )

        assertFalse(handler.handleMessage(WM_POINTERDOWN, WPARAM(1)))
        assertEquals(1, cancelCalls)
        assertFalse(handler.handleMessage(WM_POINTERDOWN, WPARAM(2)))
        assertEquals(1, cancelCalls)
    }

    @Test
    fun `close cancels active contacts exactly once`() {
        var cancelCalls = 0
        val handler = touchHandler(cancel = { cancelCalls++ })
        assertTrue(handler.handleMessage(WM_POINTERDOWN, WPARAM(1)))

        handler.close()
        handler.close()
        assertEquals(1, cancelCalls)
        assertFalse(handler.handleMessage(WM_POINTERUPDATE, WPARAM(1)))
    }

    @Test
    fun `non-client pointer lifecycle`() {
        val dispatched = mutableListOf<WindowsTouchEvent>()
        val handler = touchHandler(dispatch = dispatched::add)

        assertTrue(handler.handleMessage(WM_NCPOINTERDOWN, WPARAM(1)))
        assertTrue(handler.handleMessage(WM_NCPOINTERUPDATE, WPARAM(1)))
        assertTrue(handler.handleMessage(WM_NCPOINTERUP, WPARAM(1)))

        assertEquals(
            listOf(WindowsTouchEventType.PRESS, WindowsTouchEventType.MOVE, WindowsTouchEventType.RELEASE),
            dispatched.map { it.type },
        )
    }

    @Test
    fun `older move folds into the newer one as history`() {
        val tracker = WindowsPointerContactTracker()
        val pointer = pointer(id = 1, x = 0, y = 0, time = 100)
        tracker.handle(WindowsPointerMessage.DOWN, pointer)
        val first = tracker.handle(WindowsPointerMessage.UPDATE, pointer.copy(screenX = 10, eventTimeMillis = 110)).event()
        val second = tracker.handle(WindowsPointerMessage.UPDATE, pointer.copy(screenX = 20, eventTimeMillis = 120)).event()
        val third = tracker.handle(WindowsPointerMessage.UPDATE, pointer.copy(screenX = 30, eventTimeMillis = 130)).event()

        val merged = third.mergeOlderMove(second.mergeOlderMove(first)!!)!!
        assertEquals(
            listOf(WindowsTouchSample(110, 10, 0), WindowsTouchSample(120, 20, 0)),
            merged.pointers.single().historical,
        )
        assertEquals(30, merged.pointers.single().screenX)
    }

    @Test
    fun `moves with different contacts or non-move events do not merge`() {
        val tracker = WindowsPointerContactTracker()
        val first = pointer(id = 1, x = 0, y = 0)
        val second = pointer(id = 2, x = 50, y = 0)
        val press = tracker.handle(WindowsPointerMessage.DOWN, first).event()
        val soloMove = tracker.handle(WindowsPointerMessage.UPDATE, first.copy(screenX = 5)).event()
        tracker.handle(WindowsPointerMessage.DOWN, second)
        val pairMove = tracker.handle(WindowsPointerMessage.UPDATE, second.copy(screenX = 55)).event()

        assertEquals(null, soloMove.mergeOlderMove(press))
        assertEquals(null, pairMove.mergeOlderMove(soloMove))
    }

    private fun touchHandler(
        dispatch: (WindowsTouchEvent) -> Unit = {},
        cancel: () -> Unit = {},
    ) = WindowsPointerInputHandler(
        readPointerInfo = { _, info ->
            info.pointerType = PT_TOUCH
            info.pointerFlags = POINTER_FLAG_INCONTACT
            info.ptPixelLocation = POINT(30, 30)
            true
        },
        dispatch = dispatch,
        cancel = cancel,
    )

    private fun pointer(
        id: Long,
        x: Int,
        y: Int,
        flags: Int = POINTER_FLAG_INCONTACT,
        pointerType: Int = PT_TOUCH,
        kind: WindowsPointerKind = WindowsPointerKind.TOUCH,
        time: Long = 0L,
    ) = WindowsPointerData(id, pointerType, flags, x, y, eventTimeMillis = time, kind = kind)

    private fun WindowsPointerDispatch.event(): WindowsTouchEvent {
        assertIs<WindowsPointerDispatch.Send>(this)
        return event
    }

    private fun WindowsTouchEvent.pressedById(): List<Pair<Long, Boolean>> = pointers.map { it.id to it.pressed }
}
