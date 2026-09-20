/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.W32APIOptions
import me.him188.ani.app.ui.framework.runOnSwingEdt
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drives a real [ComposeWindow] with OS-level input: touch through `InjectTouchInput` (a virtual
 * digitizer, no touch screen needed) and the mouse through [Robot], which uses `SendInput`.
 *
 * Every test needs an interactive Windows session and the window in the foreground. Anything else
 * is reported as skipped rather than failed, so the suite stays green on machines without a desktop.
 */
class WindowsNativeInputTest {
    private class RecordedPointer(val id: Long, val type: PointerType, val pressed: Boolean, val position: Offset)
    private class RecordedEvent(val type: PointerEventType, val pointers: List<RecordedPointer>)

    private class Harness(
        val window: ComposeWindow,
        val windowProc: AutoCloseable,
        val events: CopyOnWriteArrayList<RecordedEvent>,
    ) : AutoCloseable {
        val hwnd: HWND get() = HWND(Pointer(window.windowHandle))

        /** Window rectangle as user32 reports it; the JVM is DPI aware, so these are physical pixels. */
        fun windowRect(): RECT = RECT().also { User32.INSTANCE.GetWindowRect(hwnd, it) }

        fun physicalCenter(): Pair<Int, Int> {
            val rect = windowRect()
            return (rect.left + rect.right) / 2 to (rect.top + rect.bottom) / 2
        }

        fun awaitEvent(timeoutMillis: Long = 5_000, predicate: (RecordedEvent) -> Boolean): RecordedEvent {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                events.firstOrNull(predicate)?.let { return it }
                Thread.sleep(20)
            }
            throw AssertionError(
                "No matching pointer event within ${timeoutMillis}ms; recorded=" +
                    events.joinToString { event ->
                        "${event.type}${event.pointers.map { "${it.id}:${it.type}:${it.pressed}" }}"
                    },
            )
        }

        override fun close() {
            runOnSwingEdt {
                windowProc.close()
                window.dispose()
            }
        }
    }

    @Test
    fun `injected touch tap reaches compose as touch press and release`() = withHarness { harness ->
        val touch = TouchInjector.instanceOrSkip()
        val (x, y) = harness.physicalCenter()

        touch.frame(TouchContact(id = 1, x = x, y = y, phase = TouchPhase.DOWN))
        Thread.sleep(40)
        touch.frame(TouchContact(id = 1, x = x, y = y, phase = TouchPhase.UP))

        val press = harness.awaitEvent { it.type == PointerEventType.Press }
        assertEquals(listOf(PointerType.Touch), press.pointers.map { it.type })
        assertTrue(press.pointers.single().pressed)

        val release = harness.awaitEvent { it.type == PointerEventType.Release }
        assertEquals(PointerType.Touch, release.pointers.single().type)
        assertEquals(false, release.pointers.single().pressed)
    }

    @Test
    fun `injected pinch reaches compose with both contacts in every event`() = withHarness { harness ->
        val touch = TouchInjector.instanceOrSkip()
        val (cx, cy) = harness.physicalCenter()
        val steps = 12
        val startDistance = 60
        val endDistance = 240

        fun contactsAt(step: Int, phase: TouchPhase): Array<TouchContact> {
            val half = (startDistance + (endDistance - startDistance) * step / steps) / 2
            return arrayOf(
                TouchContact(id = 1, x = cx - half, y = cy, phase = phase),
                TouchContact(id = 2, x = cx + half, y = cy, phase = phase),
            )
        }

        touch.frame(*contactsAt(0, TouchPhase.DOWN))
        for (step in 1..steps) {
            Thread.sleep(16)
            touch.frame(*contactsAt(step, TouchPhase.UPDATE))
        }
        Thread.sleep(16)
        touch.frame(*contactsAt(steps, TouchPhase.UP))

        val twoFingerMoves = run {
            harness.awaitEvent { event ->
                event.type == PointerEventType.Move && event.pointers.count { it.pressed } == 2
            }
            harness.awaitEvent { it.type == PointerEventType.Release }
            harness.events.filter { event ->
                event.type == PointerEventType.Move && event.pointers.count { it.pressed } == 2
            }
        }
        assertTrue(twoFingerMoves.size >= 2, "expected several two-finger moves, got ${twoFingerMoves.size}")
        assertTrue(twoFingerMoves.all { move -> move.pointers.all { it.type == PointerType.Touch } })

        fun RecordedEvent.spread(): Float = (pointers[0].position - pointers[1].position).getDistance()
        assertTrue(
            twoFingerMoves.last().spread() > twoFingerMoves.first().spread(),
            "contacts should move apart: first=${twoFingerMoves.first().spread()} last=${twoFingerMoves.last().spread()}",
        )
        // Nothing may be left pressed after the last UP, otherwise the next gesture starts stuck.
        val lastRelease = harness.events.last { it.type == PointerEventType.Release }
        assertTrue(lastRelease.pointers.none { it.pressed })
    }

    @Test
    fun `os mouse click still arrives as a mouse pointer`() = withHarness { harness ->
        val robot = Robot().apply { autoDelay = 20 }
        val location = harness.window.locationOnScreen
        val size = harness.window.size
        robot.mouseMove(location.x + size.width / 2, location.y + size.height / 2)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)

        val press = harness.awaitEvent { it.type == PointerEventType.Press }
        assertEquals(listOf(PointerType.Mouse), press.pointers.map { it.type })
        assertNotNull(harness.awaitEvent { it.type == PointerEventType.Release })
    }

    private fun withHarness(block: (Harness) -> Unit) {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Windows only")
        assumeTrue(!GraphicsEnvironment.isHeadless(), "needs an interactive desktop")

        val events = CopyOnWriteArrayList<RecordedEvent>()
        val window = runOnSwingEdt {
            ComposeWindow().apply {
                title = "WindowsNativeInputTest"
                // Anchored at the origin so the centre of the user32 rectangle lands inside the
                // window whichever DPI awareness the JVM ends up with.
                setLocation(0, 0)
                size = Dimension(640, 480)
                setContent {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        events += RecordedEvent(
                                            type = event.type,
                                            pointers = event.changes.map {
                                                RecordedPointer(it.id.value, it.type, it.pressed, it.position)
                                            },
                                        )
                                    }
                                }
                            },
                    )
                }
                isVisible = true
                toFront()
            }
        }
        // Let the first frame render before hooking, otherwise the scene bounds are not initialised.
        Thread.sleep(1_000)

        // WM_POINTER goes to the heavyweight child under the pointer, the Skia canvas, not to the
        // top-level frame. This is the hook the app installs for its own window.
        val windowProc = runOnSwingEdt {
            SkiaLayerHitTestWindowProc(
                skiaLayer = checkNotNull(window.findSkiaLayer()) { "ComposeWindow has no SkiaLayer" },
                user32 = user32,
                hitTest = { WindowsWindowHitResult.CLIENT },
                window = window,
            )
        }
        val harness = Harness(window, windowProc, events)
        try {
            val hwnd = harness.hwnd
            forceForeground(hwnd)
            Thread.sleep(300)
            assumeTrue(
                User32.INSTANCE.GetForegroundWindow() == hwnd,
                "window is not in the foreground; injected input would land elsewhere",
            )
            block(harness)
        } finally {
            harness.close()
        }
    }

    /**
     * A process that is not already in the foreground may not steal it, and the Gradle test worker
     * never is. Attaching to the input queue of the current foreground thread lifts that restriction
     * without clicking anything, so no stray input reaches whatever window the user has open.
     */
    private fun forceForeground(hwnd: HWND) {
        val foreground = User32.INSTANCE.GetForegroundWindow()
        val foregroundThread = foreground?.let { window ->
            User32.INSTANCE.GetWindowThreadProcessId(window, IntByReference()).takeIf { it != 0 }
        }
        val currentThread = Kernel32.INSTANCE.GetCurrentThreadId()
        val attached = foregroundThread != null && foregroundThread != currentThread &&
            User32.INSTANCE.AttachThreadInput(DWORD(foregroundThread.toLong()), DWORD(currentThread.toLong()), true)
        try {
            User32.INSTANCE.SetForegroundWindow(hwnd)
            User32.INSTANCE.BringWindowToTop(hwnd)
        } finally {
            if (attached) {
                User32.INSTANCE.AttachThreadInput(DWORD(foregroundThread!!.toLong()), DWORD(currentThread.toLong()), false)
            }
        }
    }

    private companion object {
        // Lazy so that loading the class on a non-Windows host does not fail before the assumption runs.
        val user32: ExtendedUser32 by lazy {
            Native.load("user32", ExtendedUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}

private enum class TouchPhase(val pointerFlags: Int) {
    DOWN(POINTER_FLAG_DOWN or POINTER_FLAG_INRANGE or POINTER_FLAG_INCONTACT),
    UPDATE(POINTER_FLAG_UPDATE or POINTER_FLAG_INRANGE or POINTER_FLAG_INCONTACT),
    UP(POINTER_FLAG_UP),
}

private class TouchContact(val id: Int, val x: Int, val y: Int, val phase: TouchPhase)

private const val POINTER_FLAG_INRANGE = 0x00000002
private const val POINTER_FLAG_DOWN = 0x00010000
private const val POINTER_FLAG_UPDATE = 0x00020000
private const val POINTER_FLAG_UP = 0x00040000
private const val TOUCH_MASK_CONTACTAREA = 0x00000001
private const val TOUCH_MASK_ORIENTATION = 0x00000002
private const val TOUCH_MASK_PRESSURE = 0x00000004
private const val TOUCH_FEEDBACK_DEFAULT = 0x1
private const val MAX_INJECTED_CONTACTS = 10

/**
 * Win32 POINTER_TOUCH_INFO; the layout is what user32 reads, so the field order is fixed. JNA reads
 * the fields reflectively, which needs a public class, hence internal rather than private.
 */
@Suppress("SpellCheckingInspection")
internal class POINTER_TOUCH_INFO : Structure() {
    @JvmField var pointerInfo: POINTER_INFO = POINTER_INFO()
    @JvmField var touchFlags: Int = 0
    @JvmField var touchMask: Int = 0
    @JvmField var rcContact: RECT = RECT()
    @JvmField var rcContactRaw: RECT = RECT()
    @JvmField var orientation: Int = 0
    @JvmField var pressure: Int = 0

    override fun getFieldOrder(): List<String> = listOf(
        "pointerInfo", "touchFlags", "touchMask", "rcContact", "rcContactRaw", "orientation", "pressure",
    )
}

internal interface TouchInjectionUser32 : Library {
    fun InitializeTouchInjection(maxCount: Int, dwMode: Int): Boolean
    fun InjectTouchInput(count: Int, contacts: Array<POINTER_TOUCH_INFO>): Boolean
}

/**
 * Wraps the per-process virtual touch device. `InitializeTouchInjection` is a one-time call, and a
 * contact set must be resent complete on every frame until all of it has lifted.
 */
private class TouchInjector private constructor(private val user32: TouchInjectionUser32) {
    fun frame(vararg contacts: TouchContact) {
        val native = POINTER_TOUCH_INFO().toArray(contacts.size)
        contacts.forEachIndexed { index, contact ->
            @Suppress("UNCHECKED_CAST")
            val info = native[index] as POINTER_TOUCH_INFO
            info.pointerInfo.pointerType = PT_TOUCH
            info.pointerInfo.pointerId = contact.id
            info.pointerInfo.pointerFlags = contact.phase.pointerFlags
            info.pointerInfo.ptPixelLocation.x = contact.x
            info.pointerInfo.ptPixelLocation.y = contact.y
            info.touchMask = TOUCH_MASK_CONTACTAREA or TOUCH_MASK_ORIENTATION or TOUCH_MASK_PRESSURE
            info.rcContact.left = contact.x - 2
            info.rcContact.top = contact.y - 2
            info.rcContact.right = contact.x + 2
            info.rcContact.bottom = contact.y + 2
            info.orientation = 90
            info.pressure = 32_000
        }
        @Suppress("UNCHECKED_CAST")
        check(user32.InjectTouchInput(contacts.size, native as Array<POINTER_TOUCH_INFO>)) {
            "InjectTouchInput failed, lastError=${Native.getLastError()}"
        }
    }

    companion object {
        private val shared: TouchInjector? by lazy {
            runCatching {
                val user32 = Native.load("user32", TouchInjectionUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)
                if (user32.InitializeTouchInjection(MAX_INJECTED_CONTACTS, TOUCH_FEEDBACK_DEFAULT)) {
                    TouchInjector(user32)
                } else {
                    null
                }
            }.getOrNull()
        }

        fun instanceOrSkip(): TouchInjector {
            val injector = shared
            assumeTrue(injector != null, "touch injection unavailable in this session")
            return injector!!
        }
    }
}
