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
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.WPARAM
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import java.awt.EventQueue

private val logger = logger("WindowsPointerInput")

internal const val WINDOWS_NATIVE_TOUCH_DEBUG_PROPERTY = "ani.windows.nativeTouch.debug"

internal const val PT_TOUCH: Int = 0x00000002

internal const val WM_POINTERUPDATE: Int = 0x0245
internal const val WM_POINTERDOWN: Int = 0x0246
internal const val WM_POINTERUP: Int = 0x0247
internal const val WM_POINTERCAPTURECHANGED: Int = 0x024C

internal const val POINTER_FLAG_INCONTACT: Int = 0x00000004
internal const val POINTER_FLAG_CANCELED: Int = 0x00008000

internal object WindowsNativeTouchDebug {
    private var lastUpdateLogNanos = 0L

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
        logger.info(
            "Windows touch message hook=$hookName message=${uMsg.debugName()} " +
                "callback=${callbackWindow.debugHandle()}, " +
                "wParam=0x${wParam.toLong().toString(16)}, " +
                "pointerId=${pointer?.pointerId}, " +
                "type=${pointer?.pointerType}, " +
                "flags=0x${pointer?.pointerFlags?.toString(16)}, " +
                "time=${pointer?.dwTime?.toLong()?.and(0xFFFFFFFFL)}, " +
                "screen=${pointer?.ptPixelLocation?.x},${pointer?.ptPixelLocation?.y}, " +
                "target=${pointer?.hwndTarget.debugHandle()}, " +
                "edt=${EventQueue.isDispatchThread()}"
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

internal data class WindowsPointerData(
    val id: Long,
    val pointerType: Int,
    val flags: Int,
    val screenX: Int,
    val screenY: Int,
    val eventTimeMillis: Long = 0L,
) {
    val isInContact: Boolean get() = flags and POINTER_FLAG_INCONTACT != 0
    val isCanceled: Boolean get() = flags and POINTER_FLAG_CANCELED != 0
}

internal data class WindowsTouchEvent(
    val type: WindowsTouchEventType,
    val pointer: WindowsPointerData,
)

internal sealed interface WindowsPointerDispatch {
    data object Pass : WindowsPointerDispatch
    data object Consume : WindowsPointerDispatch
    data object Cancel : WindowsPointerDispatch
    data class Send(val event: WindowsTouchEvent) : WindowsPointerDispatch
}

internal enum class WindowsPointerSequenceState {
    IDLE,
    PRIMARY_ACTIVE,
    DRAINING,
}

/**
 * Owns one complete native touch sequence for the bridge.
 *
 * Once the primary contact is sent to Compose, every related native message must be consumed until
 * the sequence ends or is cancelled; otherwise Windows can also deliver the same interaction through
 * its default mouse path.
 *
 * This is a single-touch MVP. Desktop interactions are currently predominantly single-touch; pinch
 * gestures such as image zoom are intentionally out of scope. Secondary pointers are tracked only so
 * their native messages can be consumed consistently until the sequence drains, never promoted.
 */
internal class WindowsPointerSequenceStateMachine {
    var state: WindowsPointerSequenceState = WindowsPointerSequenceState.IDLE
        private set

    var primaryPointerId: Long? = null
        private set

    private val suppressedPointerIds = mutableSetOf<Long>()

    fun handle(message: WindowsPointerMessage, pointer: WindowsPointerData): WindowsPointerDispatch {
        if (pointer.pointerType != PT_TOUCH) return WindowsPointerDispatch.Pass

        if (pointer.isCanceled) {
            return if (owns(pointer.id)) cancelAndDispatch() else WindowsPointerDispatch.Pass
        }

        return when (state) {
            WindowsPointerSequenceState.IDLE -> handleIdle(message, pointer)
            WindowsPointerSequenceState.PRIMARY_ACTIVE -> handlePrimaryActive(message, pointer)
            WindowsPointerSequenceState.DRAINING -> handleDraining(message, pointer)
        }
    }

    fun handleCaptureChanged(): WindowsPointerDispatch {
        return if (isActive) cancelAndDispatch() else WindowsPointerDispatch.Pass
    }

    fun handleReadFailure(pointerId: Long): WindowsPointerDispatch {
        return if (owns(pointerId)) cancelAndDispatch() else WindowsPointerDispatch.Pass
    }

    fun cancel(): Boolean {
        if (!isActive) return false
        clear()
        return true
    }

    fun owns(pointerId: Long): Boolean {
        return primaryPointerId == pointerId || pointerId in suppressedPointerIds
    }

    private val isActive: Boolean
        get() = state != WindowsPointerSequenceState.IDLE

    private fun handleIdle(
        message: WindowsPointerMessage,
        pointer: WindowsPointerData,
    ): WindowsPointerDispatch {
        if (message != WindowsPointerMessage.DOWN) return WindowsPointerDispatch.Pass

        primaryPointerId = pointer.id
        state = WindowsPointerSequenceState.PRIMARY_ACTIVE
        return WindowsPointerDispatch.Send(WindowsTouchEvent(WindowsTouchEventType.PRESS, pointer))
    }

    private fun handlePrimaryActive(
        message: WindowsPointerMessage,
        pointer: WindowsPointerData,
    ): WindowsPointerDispatch {
        val primaryId = primaryPointerId
        if (pointer.id == primaryId) {
            return when (message) {
                WindowsPointerMessage.DOWN -> WindowsPointerDispatch.Consume
                WindowsPointerMessage.UPDATE -> {
                    if (pointer.isInContact) {
                        WindowsPointerDispatch.Send(WindowsTouchEvent(WindowsTouchEventType.MOVE, pointer))
                    } else {
                        cancelAndDispatch()
                    }
                }

                WindowsPointerMessage.UP -> {
                    primaryPointerId = null
                    state = if (suppressedPointerIds.isEmpty()) {
                        WindowsPointerSequenceState.IDLE
                    } else {
                        WindowsPointerSequenceState.DRAINING
                    }
                    WindowsPointerDispatch.Send(WindowsTouchEvent(WindowsTouchEventType.RELEASE, pointer))
                }
            }
        }

        return when (message) {
            WindowsPointerMessage.DOWN -> {
                suppressedPointerIds += pointer.id
                WindowsPointerDispatch.Consume
            }

            WindowsPointerMessage.UPDATE,
            WindowsPointerMessage.UP,
                -> {
                if (pointer.id !in suppressedPointerIds) {
                    WindowsPointerDispatch.Pass
                } else {
                    if (message == WindowsPointerMessage.UP) {
                        suppressedPointerIds -= pointer.id
                        if (suppressedPointerIds.isEmpty()) {
                            state = WindowsPointerSequenceState.IDLE
                        }
                    }
                    WindowsPointerDispatch.Consume
                }
            }
        }
    }

    private fun handleDraining(
        message: WindowsPointerMessage,
        pointer: WindowsPointerData,
    ): WindowsPointerDispatch {
        return when (message) {
            WindowsPointerMessage.DOWN -> {
                suppressedPointerIds += pointer.id
                WindowsPointerDispatch.Consume
            }

            WindowsPointerMessage.UPDATE -> {
                if (pointer.id in suppressedPointerIds) WindowsPointerDispatch.Consume
                else WindowsPointerDispatch.Pass
            }

            WindowsPointerMessage.UP -> {
                if (pointer.id !in suppressedPointerIds) {
                    WindowsPointerDispatch.Pass
                } else {
                    suppressedPointerIds -= pointer.id
                    if (suppressedPointerIds.isEmpty()) {
                        state = WindowsPointerSequenceState.IDLE
                    }
                    WindowsPointerDispatch.Consume
                }
            }
        }
    }

    private fun cancelAndDispatch(): WindowsPointerDispatch {
        clear()
        return WindowsPointerDispatch.Cancel
    }

    private fun clear() {
        primaryPointerId = null
        suppressedPointerIds.clear()
        state = WindowsPointerSequenceState.IDLE
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

    fun toPointerData(pointerId: Long): WindowsPointerData = WindowsPointerData(
        id = pointerId,
        pointerType = pointerType,
        flags = pointerFlags,
        screenX = ptPixelLocation.x,
        screenY = ptPixelLocation.y,
        eventTimeMillis = dwTime.toLong() and 0xFFFFFFFFL,
    )
}

internal class WindowsPointerInputHandler(
    private val readPointerInfo: (pointerId: Int, pointerInfo: POINTER_INFO) -> Boolean,
    private val dispatch: (WindowsTouchEvent) -> Unit,
    private val cancel: () -> Unit,
    private val debugHookName: String = "unknown",
) : AutoCloseable {
    private val sequence = WindowsPointerSequenceStateMachine()
    private var closed = false
    private var disabled = false

    fun handleMessage(uMsg: Int, wParam: WPARAM, callbackWindow: HWND? = null): Boolean {
        if (closed || disabled) return false
        if (uMsg == WM_POINTERCAPTURECHANGED) {
            WindowsNativeTouchDebug.logPointerMessage(debugHookName, uMsg, callbackWindow, wParam, null)
        }

        val pointerId = if (uMsg == WM_POINTERDOWN || uMsg == WM_POINTERUPDATE || uMsg == WM_POINTERUP) {
            pointerIdFromWParam(wParam)
        } else {
            null
        }
        val ownedBeforeHandling = pointerId?.let(sequence::owns)
            ?: (uMsg == WM_POINTERCAPTURECHANGED && sequence.state != WindowsPointerSequenceState.IDLE)

        return try {
            when (uMsg) {
                WM_POINTERCAPTURECHANGED -> apply(sequence.handleCaptureChanged())
                WM_POINTERDOWN,
                WM_POINTERUPDATE,
                WM_POINTERUP,
                    -> handlePointerMessage(uMsg, wParam, callbackWindow)

                else -> false
            }
        } catch (error: Throwable) {
            logger.error(error) { "Windows pointer input bridge failed; disabling native touch" }
            sequence.cancel()
            runCatching(cancel)
            disabled = true
            // Keep consuming an already owned sequence even if cancellation or
            // scene injection itself failed. This prevents the same touch from
            // being synthesized as a second mouse sequence after a bridge error.
            ownedBeforeHandling
        }
    }

    private fun handlePointerMessage(uMsg: Int, wParam: WPARAM, callbackWindow: HWND?): Boolean {
        val pointerId = pointerIdFromWParam(wParam)
        val pointerInfo = POINTER_INFO()
        if (!readPointerInfo(pointerId.toInt(), pointerInfo)) {
            WindowsNativeTouchDebug.logPointerMessage(debugHookName, uMsg, callbackWindow, wParam, null)
            return apply(sequence.handleReadFailure(pointerId))
        }
        WindowsNativeTouchDebug.logPointerMessage(debugHookName, uMsg, callbackWindow, wParam, pointerInfo)

        val message = when (uMsg) {
            WM_POINTERDOWN -> WindowsPointerMessage.DOWN
            WM_POINTERUPDATE -> WindowsPointerMessage.UPDATE
            WM_POINTERUP -> WindowsPointerMessage.UP
            else -> error("Unsupported pointer message: $uMsg")
        }
        return apply(sequence.handle(message, pointerInfo.toPointerData(pointerId)))
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
        if (sequence.cancel()) {
            runCatching(cancel)
        }
    }
}

internal fun pointerIdFromWParam(wParam: WPARAM): Long = wParam.toLong() and 0xFFFF

private fun Int.debugName(): String = when (this) {
    WM_POINTERUPDATE -> "WM_POINTERUPDATE"
    WM_POINTERDOWN -> "WM_POINTERDOWN"
    WM_POINTERUP -> "WM_POINTERUP"
    WM_POINTERCAPTURECHANGED -> "WM_POINTERCAPTURECHANGED"
    else -> "0x${toString(16)}"
}

private fun HWND?.debugHandle(): String = this?.toString() ?: "null"
