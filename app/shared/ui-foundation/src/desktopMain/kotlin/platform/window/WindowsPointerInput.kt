/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.WPARAM
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import java.awt.EventQueue

private val logger = logger("WindowsPointerInput")

internal const val WINDOWS_NATIVE_TOUCH_DEBUG_PROPERTY = "ani.windows.nativeTouch.debug"

internal const val PT_TOUCH: Int = 0x00000002
internal const val PT_PEN: Int = 0x00000003

internal const val WM_POINTERUPDATE: Int = 0x0245
internal const val WM_POINTERDOWN: Int = 0x0246
internal const val WM_POINTERUP: Int = 0x0247
internal const val WM_POINTERCAPTURECHANGED: Int = 0x024C

// Touches that hit-test into the non-client area (our Compose-drawn caption buttons report
// HTMINBUTTON/HTMAXBUTTON/HTCLOSE) are delivered as WM_NCPOINTER* instead of WM_POINTER*.
// They carry the same pointer id and screen coordinates, so they are injected identically.
internal const val WM_NCPOINTERUPDATE: Int = 0x0241
internal const val WM_NCPOINTERDOWN: Int = 0x0242
internal const val WM_NCPOINTERUP: Int = 0x0243

internal const val POINTER_FLAG_INCONTACT: Int = 0x00000004
internal const val POINTER_FLAG_CANCELED: Int = 0x00008000

internal const val PEN_FLAG_INVERTED: Int = 0x00000002
internal const val PEN_FLAG_ERASER: Int = 0x00000004
internal const val PEN_MASK_PRESSURE: Int = 0x00000001

/** Windows reports pen pressure in the range 0..1024. */
internal const val PEN_PRESSURE_MAX: Float = 1024f

internal object WindowsNativeTouchDebug {
    private var lastUpdateLogNanos = 0L

    /** Every WM_POINTERUPDATE seen by any hook, including the ones the rate limit hides. */
    @Volatile
    var updateMessagesSeen: Long = 0L

    val enabled: Boolean
        get() = System.getProperty(WINDOWS_NATIVE_TOUCH_DEBUG_PROPERTY).toBoolean()

    fun logHook(
        hookName: String,
        hookedWindow: HWND,
        topLevelWindow: HWND?,
        skiaLayerWindow: HWND?,
        contentWindow: HWND?,
    ) {
        if (!enabled) return
        logger.info(
            "Windows touch hook=$hookName " +
                "hooked=${hookedWindow.debugHandle()}, " +
                "topLevel=${topLevelWindow.debugHandle()}, " +
                "skiaLayer=${skiaLayerWindow.debugHandle()}, " +
                "content=${contentWindow.debugHandle()}"
        )
    }

    fun logPointerMessage(
        hookName: String,
        uMsg: Int,
        callbackWindow: HWND?,
        wParam: WPARAM,
        pointerInfo: POINTER_INFO?,
    ) {
        if (!enabled || uMsg == WM_POINTERUPDATE && !shouldLogUpdate()) return
        val pointer = pointerInfo
        // Queue latency: dwTime is GetTickCount at generation, so the difference is how long the
        // message waited before the wndproc saw it.
        val lagMillis = pointer?.let { Kernel32.INSTANCE.GetTickCount() - it.dwTime }
        logger.info(
            "Windows touch message hook=$hookName message=${uMsg.debugName()} " +
                "callback=${callbackWindow.debugHandle()}, " +
                "wParam=0x${wParam.toLong().toString(16)}, " +
                "pointerId=${pointer?.pointerId}, " +
                "type=${pointer?.pointerType}, " +
                "flags=0x${pointer?.pointerFlags?.toString(16)}, " +
                "time=${pointer?.dwTime?.toLong()?.and(0xFFFFFFFFL)}, " +
                "lagMs=$lagMillis, " +
                "updatesSeen=$updateMessagesSeen, " +
                "screen=${pointer?.ptPixelLocation?.x},${pointer?.ptPixelLocation?.y}, " +
                "target=${pointer?.hwndTarget.debugHandle()}, " +
                "edt=${EventQueue.isDispatchThread()}"
        )
    }

    fun logInjection(event: WindowsTouchEvent, scenePositions: List<Any>, pendingAfter: Int) {
        if (!enabled || event.type == WindowsTouchEventType.MOVE && !shouldLogUpdate()) return
        logger.info(
            "Windows touch inject type=${event.type} changed=${event.pointer.id} " +
                "pointers=${event.pointers.map { "${it.id}:${it.kind}:${if (it.pressed) "down" else "up"}+${it.historical.size}h" }} " +
                "scene=$scenePositions pendingAfter=$pendingAfter edt=${EventQueue.isDispatchThread()}"
        )
    }

    private fun shouldLogUpdate(): Boolean {
        val now = System.nanoTime()
        if (now - lastUpdateLogNanos < 100_000_000L) return false
        lastUpdateLogNanos = now
        return true
    }
}

internal enum class WindowsPointerMessage {
    DOWN,
    UPDATE,
    UP,
}

internal enum class WindowsTouchEventType {
    PRESS,
    MOVE,
    RELEASE,
}

/** How a contact should be presented to Compose. */
internal enum class WindowsPointerKind {
    TOUCH,
    STYLUS,
    ERASER,
}

internal data class WindowsPointerData(
    val id: Long,
    val pointerType: Int,
    val flags: Int,
    val screenX: Int,
    val screenY: Int,
    val eventTimeMillis: Long = 0L,
    val kind: WindowsPointerKind = WindowsPointerKind.TOUCH,
    val pressure: Float = 1f,
) {
    val isInContact: Boolean get() = flags and POINTER_FLAG_INCONTACT != 0
    val isCanceled: Boolean get() = flags and POINTER_FLAG_CANCELED != 0
}

/** One screen-space sample of a contact, kept when several MOVEs are merged into one. */
internal data class WindowsTouchSample(
    val eventTimeMillis: Long,
    val screenX: Int,
    val screenY: Int,
)

/** The state of one contact as Compose should see it after an event. */
internal data class WindowsTouchPointer(
    val id: Long,
    val screenX: Int,
    val screenY: Int,
    val pressed: Boolean,
    val kind: WindowsPointerKind,
    val pressure: Float,
    val historical: List<WindowsTouchSample> = emptyList(),
) {
    fun toSample(eventTimeMillis: Long): WindowsTouchSample = WindowsTouchSample(eventTimeMillis, screenX, screenY)
}

/**
 * One native message translated for Compose.
 *
 * [pointers] holds every tracked contact, matching the ComposeScene contract that each event carries
 * the full set of active pointers. A released contact is still listed on its RELEASE with
 * `pressed = false` and is dropped afterwards.
 */
internal data class WindowsTouchEvent(
    val type: WindowsTouchEventType,
    val pointer: WindowsPointerData,
    val pointers: List<WindowsTouchPointer>,
) {
    val eventTimeMillis: Long get() = pointer.eventTimeMillis

    /**
     * Folds [previous], an older MOVE with the same contacts, into this MOVE's history.
     *
     * Returns null when the two cannot be merged, e.g. because the set of contacts differs.
     */
    fun mergeOlderMove(previous: WindowsTouchEvent): WindowsTouchEvent? {
        if (type != WindowsTouchEventType.MOVE || previous.type != WindowsTouchEventType.MOVE) return null
        if (previous.pointers.size != pointers.size) return null
        val previousById = previous.pointers.associateBy { it.id }
        val merged = pointers.map { current ->
            val older = previousById[current.id] ?: return null
            current.copy(historical = older.historical + older.toSample(previous.eventTimeMillis))
        }
        return copy(pointers = merged)
    }
}

internal sealed interface WindowsPointerDispatch {
    data object Pass : WindowsPointerDispatch
    data object Consume : WindowsPointerDispatch
    data object Cancel : WindowsPointerDispatch
    data class Send(val event: WindowsTouchEvent) : WindowsPointerDispatch
}

/**
 * Tracks every finger and pen contact on a window and decides what Compose should see.
 *
 * Each in-contact DOWN starts tracking a pointer; every event then carries all tracked contacts, so
 * multi-finger gestures such as pinch zoom reach Compose intact.
 *
 * Once a pointer's DOWN has been consumed, all of its later messages must be consumed as well:
 * otherwise DefWindowProc synthesizes mouse input for them and the same interaction arrives twice.
 * After a cancel, the remaining contacts are therefore kept in [suppressed] until their UP, but
 * they are never injected again; Compose treats input after `cancelPointerInput` as new pointers.
 *
 * Hovering pens (UPDATE without INCONTACT for an untracked pointer) are passed through so Windows
 * keeps synthesizing mouse moves for them and the hover cursor works.
 */
internal class WindowsPointerContactTracker {
    private val contacts = LinkedHashMap<Long, WindowsPointerData>()
    private val suppressed = mutableSetOf<Long>()

    val trackedPointerIds: Set<Long> get() = contacts.keys
    val suppressedPointerIds: Set<Long> get() = suppressed

    val isActive: Boolean
        get() = contacts.isNotEmpty() || suppressed.isNotEmpty()

    fun owns(pointerId: Long): Boolean = pointerId in contacts || pointerId in suppressed

    fun handle(message: WindowsPointerMessage, pointer: WindowsPointerData): WindowsPointerDispatch {
        if (pointer.pointerType != PT_TOUCH && pointer.pointerType != PT_PEN) return WindowsPointerDispatch.Pass

        if (pointer.isCanceled) {
            return if (owns(pointer.id)) cancel(endedPointerId = pointer.id.takeIf { message == WindowsPointerMessage.UP })
            else WindowsPointerDispatch.Pass
        }

        // Windows reuses pointer ids. A DOWN is always a new contact, so it must not be swallowed by a
        // suppression left behind when the previous contact's UP went to another window.
        if (message != WindowsPointerMessage.DOWN && pointer.id in suppressed) {
            if (message == WindowsPointerMessage.UP) suppressed -= pointer.id
            return WindowsPointerDispatch.Consume
        }

        return when (message) {
            WindowsPointerMessage.DOWN -> {
                if (pointer.id in contacts) return WindowsPointerDispatch.Consume
                suppressed -= pointer.id
                contacts[pointer.id] = pointer
                send(WindowsTouchEventType.PRESS, pointer)
            }

            WindowsPointerMessage.UPDATE -> {
                if (pointer.id !in contacts) return WindowsPointerDispatch.Pass
                // Contact lost without an UP: end the pointer rather than leave it pressed forever.
                if (!pointer.isInContact) return release(pointer)
                contacts[pointer.id] = pointer
                send(WindowsTouchEventType.MOVE, pointer)
            }

            WindowsPointerMessage.UP -> {
                if (pointer.id !in contacts) return WindowsPointerDispatch.Pass
                release(pointer)
            }
        }
    }

    fun handleCaptureChanged(): WindowsPointerDispatch {
        return if (contacts.isNotEmpty()) cancel(endedPointerId = null) else WindowsPointerDispatch.Pass
    }

    fun handleReadFailure(pointerId: Long): WindowsPointerDispatch {
        return if (pointerId in contacts) cancel(endedPointerId = pointerId) else WindowsPointerDispatch.Pass
    }

    /** Forgets every pointer. Returns whether Compose had tracked contacts to cancel. */
    fun clear(): Boolean {
        val hadContacts = contacts.isNotEmpty()
        contacts.clear()
        suppressed.clear()
        return hadContacts
    }

    private fun release(pointer: WindowsPointerData): WindowsPointerDispatch {
        contacts[pointer.id] = pointer
        val dispatch = send(WindowsTouchEventType.RELEASE, pointer)
        contacts.remove(pointer.id)
        return dispatch
    }

    private fun send(type: WindowsTouchEventType, changed: WindowsPointerData): WindowsPointerDispatch {
        val pointers = contacts.values.map { contact ->
            WindowsTouchPointer(
                id = contact.id,
                screenX = contact.screenX,
                screenY = contact.screenY,
                pressed = !(type == WindowsTouchEventType.RELEASE && contact.id == changed.id),
                kind = contact.kind,
                pressure = contact.pressure,
            )
        }
        return WindowsPointerDispatch.Send(WindowsTouchEvent(type, changed, pointers))
    }

    /**
     * Cancels the whole interaction. Tracked contacts other than [endedPointerId] keep being
     * consumed until they lift; [endedPointerId] has already lifted or is unreadable and gets no
     * further messages worth waiting for.
     */
    private fun cancel(endedPointerId: Long?): WindowsPointerDispatch {
        suppressed += contacts.keys
        contacts.clear()
        if (endedPointerId != null) suppressed -= endedPointerId
        return WindowsPointerDispatch.Cancel
    }
}

/** A copy of the Win32 POINTER_INFO structure that is safe to read synchronously. */
@Suppress("SpellCheckingInspection")
internal class POINTER_INFO : Structure() {
    @JvmField var pointerType: Int = 0
    @JvmField var pointerId: Int = 0
    @JvmField var frameId: Int = 0
    @JvmField var pointerFlags: Int = 0
    @JvmField var sourceDevice: Pointer? = null
    @JvmField var hwndTarget: HWND? = null
    @JvmField var ptPixelLocation: POINT = POINT()
    @JvmField var ptHimetricLocation: POINT = POINT()
    @JvmField var ptPixelLocationRaw: POINT = POINT()
    @JvmField var ptHimetricLocationRaw: POINT = POINT()
    @JvmField var dwTime: Int = 0
    @JvmField var historyCount: Int = 0
    @JvmField var inputData: Int = 0
    @JvmField var dwKeyStates: Int = 0
    @JvmField var performanceCount: Long = 0
    @JvmField var buttonChangeType: Int = 0

    override fun getFieldOrder(): List<String> = listOf(
        "pointerType",
        "pointerId",
        "frameId",
        "pointerFlags",
        "sourceDevice",
        "hwndTarget",
        "ptPixelLocation",
        "ptHimetricLocation",
        "ptPixelLocationRaw",
        "ptHimetricLocationRaw",
        "dwTime",
        "historyCount",
        "inputData",
        "dwKeyStates",
        "performanceCount",
        "buttonChangeType",
    )

    fun toPointerData(
        pointerId: Long,
        kind: WindowsPointerKind = WindowsPointerKind.TOUCH,
        pressure: Float = 1f,
    ): WindowsPointerData = WindowsPointerData(
        id = pointerId,
        pointerType = pointerType,
        flags = pointerFlags,
        screenX = ptPixelLocation.x,
        screenY = ptPixelLocation.y,
        eventTimeMillis = dwTime.toLong() and 0xFFFFFFFFL,
        kind = kind,
        pressure = pressure,
    )
}

/** A copy of the Win32 POINTER_PEN_INFO structure. Tilt and rotation have no Compose counterpart. */
@Suppress("SpellCheckingInspection")
internal class POINTER_PEN_INFO : Structure() {
    @JvmField var pointerInfo: POINTER_INFO = POINTER_INFO()
    @JvmField var penFlags: Int = 0
    @JvmField var penMask: Int = 0
    @JvmField var pressure: Int = 0
    @JvmField var rotation: Int = 0
    @JvmField var tiltX: Int = 0
    @JvmField var tiltY: Int = 0

    override fun getFieldOrder(): List<String> = listOf(
        "pointerInfo",
        "penFlags",
        "penMask",
        "pressure",
        "rotation",
        "tiltX",
        "tiltY",
    )

    val kind: WindowsPointerKind
        get() = if (penFlags and (PEN_FLAG_INVERTED or PEN_FLAG_ERASER) != 0) {
            WindowsPointerKind.ERASER
        } else {
            WindowsPointerKind.STYLUS
        }

    val normalizedPressure: Float
        get() = if (penMask and PEN_MASK_PRESSURE != 0) {
            (pressure / PEN_PRESSURE_MAX).coerceIn(0f, 1f)
        } else {
            1f
        }
}

/**
 * Bridges native Win32 pointer messages into [WindowsTouchEvent]s owned by a single contact
 * tracker.
 *
 * Instances are confined to the wndproc thread of their owning hook; the [POINTER_INFO] and
 * [POINTER_PEN_INFO] instances are reused across messages to keep the hot path allocation-free.
 */
internal class WindowsPointerInputHandler(
    private val readPointerInfo: (pointerId: Int, pointerInfo: POINTER_INFO) -> Boolean,
    private val dispatch: (WindowsTouchEvent) -> Unit,
    private val cancel: () -> Unit,
    private val readPointerPenInfo: (pointerId: Int, penInfo: POINTER_PEN_INFO) -> Boolean = { _, _ -> false },
    private val debugHookName: String = "unknown",
) : AutoCloseable {
    private val tracker = WindowsPointerContactTracker()
    private var closed = false
    private var disabled = false

    // Reused across messages; safe because the handler is confined to one wndproc thread and the
    // data is copied into WindowsPointerData before dispatch.
    private val reusedPointerInfo = POINTER_INFO()
    private val reusedPenInfo = POINTER_PEN_INFO()

    fun handleMessage(uMsg: Int, wParam: WPARAM, callbackWindow: HWND? = null): Boolean {
        if (closed || disabled) return false
        if (uMsg == WM_POINTERCAPTURECHANGED) {
            WindowsNativeTouchDebug.logPointerMessage(debugHookName, uMsg, callbackWindow, wParam, null)
        }

        val pointerId = if (uMsg.isPointerMessage()) pointerIdFromWParam(wParam) else null
        val ownedBeforeHandling = pointerId?.let(tracker::owns)
            ?: (uMsg == WM_POINTERCAPTURECHANGED && tracker.isActive)

        return try {
            when (uMsg) {
                WM_POINTERCAPTURECHANGED -> apply(tracker.handleCaptureChanged())
                WM_POINTERDOWN,
                WM_POINTERUPDATE,
                WM_POINTERUP,
                WM_NCPOINTERDOWN,
                WM_NCPOINTERUPDATE,
                WM_NCPOINTERUP,
                    -> handlePointerMessage(uMsg, wParam, callbackWindow)

                else -> false
            }
        } catch (error: Throwable) {
            logger.error(error) { "Windows pointer input bridge failed; disabling native touch" }
            tracker.clear()
            runCatching(cancel)
            disabled = true
            // Keep consuming an already owned sequence even if cancellation or
            // scene injection itself failed. This prevents the same touch from
            // being synthesized as a second mouse sequence after a bridge error.
            ownedBeforeHandling
        }
    }

    private fun handlePointerMessage(uMsg: Int, wParam: WPARAM, callbackWindow: HWND?): Boolean {
        if (uMsg == WM_POINTERUPDATE) WindowsNativeTouchDebug.updateMessagesSeen++
        val pointerId = pointerIdFromWParam(wParam)
        val pointerInfo = reusedPointerInfo
        if (!readPointerInfo(pointerId.toInt(), pointerInfo)) {
            WindowsNativeTouchDebug.logPointerMessage(debugHookName, uMsg, callbackWindow, wParam, null)
            return apply(tracker.handleReadFailure(pointerId))
        }
        WindowsNativeTouchDebug.logPointerMessage(debugHookName, uMsg, callbackWindow, wParam, pointerInfo)

        val message = when (uMsg) {
            WM_POINTERDOWN, WM_NCPOINTERDOWN -> WindowsPointerMessage.DOWN
            WM_POINTERUPDATE, WM_NCPOINTERUPDATE -> WindowsPointerMessage.UPDATE
            WM_POINTERUP, WM_NCPOINTERUP -> WindowsPointerMessage.UP
            else -> error("Unsupported pointer message: $uMsg")
        }
        val pointerData = if (pointerInfo.pointerType == PT_PEN) {
            readPenPointerData(pointerId, pointerInfo)
        } else {
            pointerInfo.toPointerData(pointerId)
        }
        return apply(tracker.handle(message, pointerData))
    }

    private fun readPenPointerData(pointerId: Long, pointerInfo: POINTER_INFO): WindowsPointerData {
        val penInfo = reusedPenInfo
        // A failed pen read still yields a usable stylus contact without pressure.
        if (!readPointerPenInfo(pointerId.toInt(), penInfo)) {
            return pointerInfo.toPointerData(pointerId, kind = WindowsPointerKind.STYLUS)
        }
        return pointerInfo.toPointerData(pointerId, kind = penInfo.kind, pressure = penInfo.normalizedPressure)
    }

    private fun apply(dispatchResult: WindowsPointerDispatch): Boolean {
        return when (dispatchResult) {
            WindowsPointerDispatch.Pass -> false
            WindowsPointerDispatch.Consume -> true
            WindowsPointerDispatch.Cancel -> {
                cancel()
                true
            }

            is WindowsPointerDispatch.Send -> {
                dispatch(dispatchResult.event)
                true
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (tracker.clear()) {
            runCatching(cancel)
        }
    }
}

internal fun pointerIdFromWParam(wParam: WPARAM): Long = wParam.toLong() and 0xFFFF

private fun Int.isPointerMessage(): Boolean = when (this) {
    WM_POINTERDOWN, WM_POINTERUPDATE, WM_POINTERUP,
    WM_NCPOINTERDOWN, WM_NCPOINTERUPDATE, WM_NCPOINTERUP,
        -> true

    else -> false
}

private fun Int.debugName(): String = when (this) {
    WM_POINTERUPDATE -> "WM_POINTERUPDATE"
    WM_POINTERDOWN -> "WM_POINTERDOWN"
    WM_POINTERUP -> "WM_POINTERUP"
    WM_NCPOINTERUPDATE -> "WM_NCPOINTERUPDATE"
    WM_NCPOINTERDOWN -> "WM_NCPOINTERDOWN"
    WM_NCPOINTERUP -> "WM_NCPOINTERUP"
    WM_POINTERCAPTURECHANGED -> "WM_POINTERCAPTURECHANGED"
    else -> "0x${toString(16)}"
}

private fun HWND?.debugHandle(): String = this?.toString() ?: "null"
