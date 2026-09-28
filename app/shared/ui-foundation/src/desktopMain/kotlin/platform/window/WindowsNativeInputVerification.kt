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
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * Drives a real [ComposeWindow] with OS-level input and asserts that the events Compose receives
 * still carry the pointer types and contact sets the Windows pointer bridge is supposed to produce.
 *
 * Lives in the main source set rather than in the test source set because the same scenarios run
 * twice: [WindowsNativeInputTest][me.him188.ani.app.platform.window] in `desktopTest` against the
 * Gradle classpath, and the `windows-native-input-test` CI task against the packaged app, where the
 * ProGuard/jpackage output is the thing under test and no test framework is present.
 *
 * Touch goes through `InjectTouchInput`, a per-process virtual digitizer, so no touch screen is
 * needed; the mouse goes through [Robot], which uses `SendInput`.
 */
object WindowsNativeInputVerification {
    /**
     * Runs every scenario in one window.
     *
     * Never throws: an environment that cannot host the scenarios at all (no Windows, no
     * interactive desktop, no foreground window, no touch injection) is reported as
     * [WindowsNativeInputVerificationResult.Unavailable], which callers must not confuse with a
     * behavioural regression ([WindowsNativeInputVerificationResult.Failed]).
     */
    fun runAll(): WindowsNativeInputVerificationResult {
        val executed = mutableListOf<String>()
        return try {
            withWindowsNativeInputHarness { harness ->
                for ((name, scenario) in windowsNativeInputScenarios) {
                    scenario(harness)
                    harness.reset()
                    executed += name
                }
            }
            WindowsNativeInputVerificationResult.Passed(executed)
        } catch (e: WindowsNativeInputUnavailableException) {
            WindowsNativeInputVerificationResult.Unavailable(e.message.orEmpty())
        } catch (e: WindowsNativeInputAssertionException) {
            WindowsNativeInputVerificationResult.Failed(
                scenario = windowsNativeInputScenarios.getOrNull(executed.size)?.first ?: "unknown",
                reason = e.message.orEmpty(),
            )
        }
    }
}

sealed interface WindowsNativeInputVerificationResult {
    /** Every scenario ran and behaved as expected. */
    data class Passed(val scenarios: List<String>) : WindowsNativeInputVerificationResult

    /** Nothing was verified because the machine cannot host the scenarios. */
    data class Unavailable(val reason: String) : WindowsNativeInputVerificationResult

    /** A scenario ran and Compose did not receive what it should have. */
    data class Failed(val scenario: String, val reason: String) : WindowsNativeInputVerificationResult
}

/** The environment cannot host the scenarios; nothing was verified. */
internal class WindowsNativeInputUnavailableException(message: String) : Exception(message)

/** A scenario ran and the events Compose received were wrong. */
internal class WindowsNativeInputAssertionException(message: String) : Exception(message)

private fun assertThat(condition: Boolean, message: () -> String) {
    if (!condition) throw WindowsNativeInputAssertionException(message())
}

private fun assumeThat(condition: Boolean, message: () -> String) {
    if (!condition) throw WindowsNativeInputUnavailableException(message())
}

internal class RecordedNativePointer(
    val id: Long,
    val type: PointerType,
    val pressed: Boolean,
    val position: Offset,
)

internal class RecordedNativeEvent(
    val type: PointerEventType,
    val pointers: List<RecordedNativePointer>,
)

internal class WindowsNativeInputHarness(
    val window: ComposeWindow,
    private val windowProc: AutoCloseable,
    val events: CopyOnWriteArrayList<RecordedNativeEvent>,
) : AutoCloseable {
    val hwnd: HWND get() = HWND(Pointer(window.windowHandle))

    /** Window rectangle as user32 reports it; the JVM is DPI aware, so these are physical pixels. */
    fun windowRect(): RECT = RECT().also { User32.INSTANCE.GetWindowRect(hwnd, it) }

    fun physicalCenter(): Pair<Int, Int> {
        val rect = windowRect()
        return (rect.left + rect.right) / 2 to (rect.top + rect.bottom) / 2
    }

    /** Drops what the previous scenario recorded, so scenarios can share one window. */
    fun reset() {
        events.clear()
    }

    fun awaitEvent(timeoutMillis: Long = 5_000, predicate: (RecordedNativeEvent) -> Boolean): RecordedNativeEvent {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            events.firstOrNull(predicate)?.let { return it }
            Thread.sleep(20)
        }
        throw WindowsNativeInputAssertionException(
            "No matching pointer event within ${timeoutMillis}ms; recorded=" +
                    events.joinToString { event ->
                        "${event.type}${event.pointers.map { "${it.id}:${it.type}:${it.pressed}" }}"
                    },
        )
    }

    override fun close() {
        onSwingEdt {
            windowProc.close()
            window.dispose()
        }
    }
}

internal val windowsNativeInputScenarios: List<Pair<String, (WindowsNativeInputHarness) -> Unit>> = listOf(
    "touch-tap" to ::verifyTouchTap,
    "pinch" to ::verifyPinch,
    "mouse-click" to ::verifyMouseClick,
)

/** A tap on the virtual digitizer must reach Compose as a touch press and a touch release. */
internal fun verifyTouchTap(harness: WindowsNativeInputHarness) {
    val touch = TouchInjector.instanceOrUnavailable()
    val (x, y) = harness.physicalCenter()

    touch.frame(TouchContact(id = 1, x = x, y = y, phase = TouchPhase.DOWN))
    Thread.sleep(40)
    touch.frame(TouchContact(id = 1, x = x, y = y, phase = TouchPhase.UP))

    val press = harness.awaitEvent { it.type == PointerEventType.Press }
    assertThat(press.pointers.map { it.type } == listOf(PointerType.Touch)) {
        "press carried ${press.pointers.map { it.type }}, expected a single touch pointer"
    }
    assertThat(press.pointers.single().pressed) { "press pointer is not pressed" }

    val release = harness.awaitEvent { it.type == PointerEventType.Release }
    assertThat(release.pointers.single().type == PointerType.Touch) {
        "release carried ${release.pointers.single().type}, expected touch"
    }
    assertThat(!release.pointers.single().pressed) { "release pointer is still pressed" }
}

/** Two contacts moving apart must reach Compose with both contacts present in every event. */
internal fun verifyPinch(harness: WindowsNativeInputHarness) {
    val touch = TouchInjector.instanceOrUnavailable()
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
    assertThat(twoFingerMoves.size >= 2) { "expected several two-finger moves, got ${twoFingerMoves.size}" }
    assertThat(twoFingerMoves.all { move -> move.pointers.all { it.type == PointerType.Touch } }) {
        "a two-finger move carried a non-touch pointer"
    }

    fun RecordedNativeEvent.spread(): Float = (pointers[0].position - pointers[1].position).getDistance()
    assertThat(twoFingerMoves.last().spread() > twoFingerMoves.first().spread()) {
        "contacts should move apart: first=${twoFingerMoves.first().spread()} last=${twoFingerMoves.last().spread()}"
    }
    // Nothing may be left pressed after the last UP, otherwise the next gesture starts stuck.
    val lastRelease = harness.events.last { it.type == PointerEventType.Release }
    assertThat(lastRelease.pointers.none { it.pressed }) { "a contact is still pressed after the last UP" }
}

/** The pointer bridge must not turn an ordinary OS mouse click into a touch pointer. */
internal fun verifyMouseClick(harness: WindowsNativeInputHarness) {
    val robot = Robot().apply { autoDelay = 20 }
    val location = harness.window.locationOnScreen
    val size = harness.window.size
    robot.mouseMove(location.x + size.width / 2, location.y + size.height / 2)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)

    val press = harness.awaitEvent { it.type == PointerEventType.Press }
    assertThat(press.pointers.map { it.type } == listOf(PointerType.Mouse)) {
        "press carried ${press.pointers.map { it.type }}, expected a single mouse pointer"
    }
    harness.awaitEvent { it.type == PointerEventType.Release }
}

/**
 * Opens a [ComposeWindow] that records every pointer event, installs the app's own window
 * procedure on its Skia layer, and brings it to the foreground before running [block].
 */
internal fun <R> withWindowsNativeInputHarness(block: (WindowsNativeInputHarness) -> R): R {
    assumeThat(System.getProperty("os.name").startsWith("Windows")) { "not Windows" }
    assumeThat(!GraphicsEnvironment.isHeadless()) { "needs an interactive desktop" }

    val events = CopyOnWriteArrayList<RecordedNativeEvent>()
    val window = onSwingEdt {
        ComposeWindow().apply {
            title = "WindowsNativeInputVerification"
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
                                    events += RecordedNativeEvent(
                                        type = event.type,
                                        pointers = event.changes.map {
                                            RecordedNativePointer(it.id.value, it.type, it.pressed, it.position)
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
    val windowProc = onSwingEdt {
        SkiaLayerHitTestWindowProc(
            skiaLayer = window.findSkiaLayer() ?: throw WindowsNativeInputAssertionException(
                "ComposeWindow has no SkiaLayer",
            ),
            user32 = verificationUser32,
            hitTest = { WindowsWindowHitResult.CLIENT },
            window = window,
        )
    }
    val harness = WindowsNativeInputHarness(window, windowProc, events)
    return try {
        forceForeground(harness.hwnd)
        Thread.sleep(300)
        assumeThat(User32.INSTANCE.GetForegroundWindow() == harness.hwnd) {
            "window is not in the foreground; injected input would land elsewhere"
        }
        block(harness)
    } finally {
        harness.close()
    }
}

// Lazy so that loading the class on a non-Windows host does not fail before the assumption runs.
private val verificationUser32: ExtendedUser32 by lazy {
    Native.load("user32", ExtendedUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)
}

private fun <R> onSwingEdt(block: () -> R): R {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: Result<R>? = null
    SwingUtilities.invokeAndWait { result = runCatching(block) }
    return checkNotNull(result).getOrThrow()
}

/**
 * A process that is not already in the foreground may not steal it, and neither the Gradle test
 * worker nor a freshly launched verify run is. Attaching to the input queue of the current
 * foreground thread lifts that restriction without clicking anything, so no stray input reaches
 * whatever window the user has open.
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
            User32.INSTANCE.AttachThreadInput(
                DWORD(foregroundThread.toLong()), DWORD(currentThread.toLong()), false,
            )
        }
    }
}

internal enum class TouchPhase(val pointerFlags: Int) {
    DOWN(POINTER_FLAG_DOWN or POINTER_FLAG_INRANGE or POINTER_FLAG_INCONTACT),
    UPDATE(POINTER_FLAG_UPDATE or POINTER_FLAG_INRANGE or POINTER_FLAG_INCONTACT),
    UP(POINTER_FLAG_UP),
}

internal class TouchContact(val id: Int, val x: Int, val y: Int, val phase: TouchPhase)

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
    @JvmField
    var pointerInfo: POINTER_INFO = POINTER_INFO()

    @JvmField
    var touchFlags: Int = 0

    @JvmField
    var touchMask: Int = 0

    @JvmField
    var rcContact: RECT = RECT()

    @JvmField
    var rcContactRaw: RECT = RECT()

    @JvmField
    var orientation: Int = 0

    @JvmField
    var pressure: Int = 0

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
internal class TouchInjector private constructor(private val user32: TouchInjectionUser32) {
    fun frame(vararg contacts: TouchContact) {
        val native = POINTER_TOUCH_INFO().toArray(contacts.size)
        contacts.forEachIndexed { index, contact ->
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

        fun instanceOrUnavailable(): TouchInjector {
            val injector = shared
            assumeThat(injector != null) { "touch injection unavailable in this session" }
            return injector!!
        }
    }
}
