/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package me.him188.ani.app.platform.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.HistoricalChange
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.ComposeScenePointer
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import java.awt.Component
import java.awt.EventQueue
import java.awt.Point
import java.awt.Window
import java.lang.reflect.Field
import java.lang.reflect.Method

private val logger = logger("ComposeSceneTouchBridge")

/** AWT reports the mouse as pointer 0; Windows touch ids never collide with it. */
private const val HOVERING_CURSOR_POINTER_ID = 0L

/** What the wndproc thread hands to the event dispatch thread, in order. */
internal sealed interface WindowsTouchCommand {
    data class Inject(val event: WindowsTouchEvent) : WindowsTouchCommand
    data object Cancel : WindowsTouchCommand
}

/**
 * Orders touch commands for the event dispatch thread and merges MOVEs that pile up.
 *
 * AWT coalesces its own queued mouse drags, but a runnable per native message would not be
 * coalesced: when the EDT falls behind, stale positions are replayed after the finger stopped and
 * the UI feels sticky. Consecutive MOVEs with the same contacts are therefore folded into one,
 * with the older positions kept as [WindowsTouchPointer.historical] so velocity tracking still
 * sees them.
 */
internal class WindowsTouchCommandQueue {
    private val lock = Any()
    private val pending = ArrayDeque<WindowsTouchCommand>()
    private var drainScheduled = false

    /** Returns true when the caller has to schedule a drain. */
    fun offer(command: WindowsTouchCommand): Boolean = synchronized(lock) {
        val last = pending.lastOrNull()
        val merged = if (command is WindowsTouchCommand.Inject && last is WindowsTouchCommand.Inject) {
            command.event.mergeOlderMove(last.event)
        } else {
            null
        }
        if (merged != null) {
            pending[pending.lastIndex] = WindowsTouchCommand.Inject(merged)
        } else {
            pending.addLast(command)
        }
        if (drainScheduled) return false
        drainScheduled = true
        true
    }

    /** Returns the next command, or null once the queue is empty; the next [offer] then schedules again. */
    fun poll(): WindowsTouchCommand? = synchronized(lock) {
        pending.removeFirstOrNull().also { if (it == null) drainScheduled = false }
    }

    fun size(): Int = synchronized(lock) { pending.size }
}

/**
 * Injects native Windows touch events into [ComposeScene].
 *
 * Compose Desktop does not expose a supported API for this injection, so this bridge resolves its
 * internal scene and methods through reflection once at creation time. If that lookup fails after a
 * Compose Desktop change, [create] returns null and the caller keeps the default AWT mouse path.
 */
@OptIn(InternalComposeUiApi::class)
internal class ComposeSceneTouchBridge private constructor(
    private val scene: ComposeScene,
    private val mediator: Any,
    private val contentComponent: Component,
    private val sceneBoundsMethod: Method,
    private val sendPointerEventMethod: Method,
) : AutoCloseable {
    private var closed = false
    private var sceneBoundsFallbackLogged = false
    private val queue = WindowsTouchCommandQueue()

    fun send(event: WindowsTouchEvent) {
        if (closed) return
        submit(WindowsTouchCommand.Inject(event))
    }

    fun cancel() {
        if (closed) return
        submit(WindowsTouchCommand.Cancel)
    }

    override fun close() {
        if (closed) return
        closed = true
    }

    private fun submit(command: WindowsTouchCommand) {
        if (EventQueue.isDispatchThread()) {
            execute(command)
            return
        }
        if (queue.offer(command)) {
            EventQueue.invokeLater(::drain)
        }
    }

    private fun drain() {
        while (true) {
            val command = queue.poll() ?: return
            if (closed) continue
            runCatching { execute(command) }.onFailure { error ->
                logger.error(error) { "ComposeScene touch event dispatch failed" }
            }
        }
    }

    private fun execute(command: WindowsTouchCommand) {
        when (command) {
            is WindowsTouchCommand.Inject -> inject(command.event)
            WindowsTouchCommand.Cancel -> scene.cancelPointerInput()
        }
    }

    private fun inject(event: WindowsTouchEvent) {
        check(!closed) { "ComposeSceneTouchBridge is closed" }
        check(contentComponent.isDisplayable) { "Compose content is no longer displayable" }

        val contentLocationOnScreen = contentComponent.locationOnScreen
        val density = scene.density.density
        val sceneBounds = sceneBoundsInPxOrZero()
        fun toScene(screenX: Int, screenY: Int): Offset = windowsScreenPositionToScenePosition(
            screenX = screenX,
            screenY = screenY,
            contentLocationOnScreen = contentLocationOnScreen,
            density = density,
            sceneBoundsInPx = sceneBounds,
        )

        val pointers = event.pointers.map { pointer ->
            ComposeScenePointer(
                id = PointerId(pointer.id),
                position = toScene(pointer.screenX, pointer.screenY),
                pressed = pointer.pressed,
                type = pointer.kind.toComposePointerType(),
                pressure = pointer.pressure,
                historical = pointer.historical.map { sample ->
                    historicalChange(sample.eventTimeMillis, toScene(sample.screenX, sample.screenY))
                },
            )
        }

        // Only before the first contact: the Exit event lists the mouse alone, and CMP synthesizes a
        // Release for every pressed pointer missing from an event.
        if (event.type == WindowsTouchEventType.PRESS && pointers.size == 1) {
            exitHoveringCursor(pointers.single().position, event.eventTimeMillis)
        }
        WindowsNativeTouchDebug.logInjection(event, pointers.map { it.position }, queue.size())

        sendPointerEventMethod.invoke(
            scene,
            event.type.toComposePointerEventType(),
            pointers,
            0,
            0,
            Offset.Zero.packedValue,
            event.eventTimeMillis,
            null,
            null,
            1f, // scaleGestureFactor
            Offset.Zero.packedValue, // panGestureOffset
        )
    }

    /**
     * Ends the AWT mouse hover before the first injected touch of a sequence.
     *
     * CMP resolves an injected pointer against the scene's current cursor input source. While the
     * mouse is still hovering, the touch PRESS arrives as [PointerType.Mouse] and only the RELEASE
     * reports [PointerType.Touch]; the tap is then discarded because down and up disagree, so the
     * first touch after using the mouse does nothing at all.
     */
    private fun exitHoveringCursor(position: Offset, eventTimeMillis: Long) {
        val cursor = ComposeScenePointer(
            id = PointerId(HOVERING_CURSOR_POINTER_ID),
            position = position,
            pressed = false,
            type = PointerType.Mouse,
            pressure = 0f,
        )
        sendPointerEventMethod.invoke(
            scene,
            PointerEventType.Exit.value,
            listOf(cursor),
            0,
            0,
            Offset.Zero.packedValue,
            eventTimeMillis,
            null,
            null,
            1f, // scaleGestureFactor
            Offset.Zero.packedValue, // panGestureOffset
        )
    }

    private fun sceneBoundsInPxOrZero(): Rect {
        return (sceneBoundsMethod.invoke(mediator) as? Rect) ?: Rect(
            left = 0f,
            top = 0f,
            right = 0f,
            bottom = 0f,
        ).also {
            if (!sceneBoundsFallbackLogged) {
                sceneBoundsFallbackLogged = true
                logger.warn("CMP scene bounds are not initialized; using zero scene offset for native touch")
            }
        }
    }

    companion object {
        fun create(window: Window): ComposeSceneTouchBridge? {
            val composeWindow = window as? ComposeWindow ?: return null
            return runCatching {
                val composePanel = ComposeWindow::class.java
                    .getDeclaredField("composePanel")
                    .accessibleField()
                    .get(composeWindow)
                val composeContainer = composePanel.javaClass
                    .getDeclaredField("_composeContainer")
                    .accessibleField()
                    .get(composePanel)
                    ?: error("Compose container is not initialized")
                val mediator = composeContainer.javaClass
                    .getDeclaredField("mediator")
                    .accessibleField()
                    .get(composeContainer)
                val scene = findScene(mediator)
                val sceneBoundsMethod = mediator.javaClass
                    .getDeclaredMethod("getSceneBoundsInPx")
                    .accessibleMethod()
                val sendPointerEventMethod = findSendPointerEventMethod(scene)
                val contentComponent = composeContainer.javaClass
                    .getDeclaredMethod("getContentComponent")
                    .accessibleMethod()
                    .invoke(composeContainer) as? Component
                    ?: error("Compose content component is not initialized")

                ComposeSceneTouchBridge(
                    scene = scene,
                    mediator = mediator,
                    contentComponent = contentComponent,
                    sceneBoundsMethod = sceneBoundsMethod,
                    sendPointerEventMethod = sendPointerEventMethod,
                )
            }.onFailure { error ->
                logger.warn("CMP ComposeScene touch bridge unavailable; keeping default mouse input", error)
            }.getOrNull()
        }

        private fun findScene(mediator: Any): ComposeScene {
            val mediatorClass = mediator.javaClass
            val accessorName = "access${'$'}getScene"
            runCatching {
                val accessor = mediatorClass
                    .getDeclaredMethod(accessorName, mediatorClass)
                    .accessibleMethod()
                accessor.invoke(null, mediator) as? ComposeScene
                    ?: error("CMP scene accessor returned an unexpected value")
            }.getOrNull()?.let { return it }

            val sceneDelegate = mediatorClass
                .getDeclaredField("scene${'$'}delegate")
                .accessibleField()
                .get(mediator)
            return (sceneDelegate as? Lazy<*>)?.value as? ComposeScene
                ?: error("CMP scene delegate returned an unexpected value")
        }

        private fun findSendPointerEventMethod(scene: ComposeScene): Method {
            val methods = (scene.javaClass.methods.asSequence() + ComposeScene::class.java.methods.asSequence())
                .filter { method ->
                    method.name.startsWith("sendPointerEvent-") &&
                        method.parameterTypes.size == 10 &&
                        method.parameterTypes[1] == List::class.java
                }
                .distinctBy { method -> method.name }
                .toList()
            check(methods.size == 1) {
                "Expected exactly one CMP list sendPointerEvent overload, found ${methods.size}"
            }
            return methods.single().accessibleMethod()
        }
    }
}

/**
 * Converts Win32 screen-pixel coordinates to [ComposeScene]-local coordinates.
 *
 * [contentLocationOnScreen] locates the AWT host in screen space; [sceneBoundsInPx] accounts for
 * the scene's offset inside that host.
 */
internal fun windowsScreenPositionToScenePosition(
    screenX: Int,
    screenY: Int,
    contentLocationOnScreen: Point,
    density: Float,
    sceneBoundsInPx: Rect,
): Offset {
    return Offset(
        screenX - contentLocationOnScreen.x * density - sceneBoundsInPx.left,
        screenY - contentLocationOnScreen.y * density - sceneBoundsInPx.top,
    )
}

/**
 * The velocity tracker reads a historical sample through its internal `originalEventPosition`, which
 * the public constructor leaves at zero. The desktop scene passes our samples through untouched, so
 * a sample built with the public constructor registers as a jump from the origin and flings become
 * far too fast. Set it the way ComposeScenePointer sets it for the current position.
 */
internal fun historicalChange(uptimeMillis: Long, scenePosition: Offset): HistoricalChange = HistoricalChange(
    uptimeMillis = uptimeMillis,
    position = scenePosition,
    scaleFactor = 1f,
    panOffset = Offset.Zero,
    originalEventPosition = scenePosition,
)

private fun WindowsTouchEventType.toComposePointerEventType(): Int = when (this) {
    WindowsTouchEventType.PRESS -> PointerEventType.Press.value
    WindowsTouchEventType.MOVE -> PointerEventType.Move.value
    WindowsTouchEventType.RELEASE -> PointerEventType.Release.value
}

@OptIn(InternalComposeUiApi::class)
private fun WindowsPointerKind.toComposePointerType(): PointerType = when (this) {
    WindowsPointerKind.TOUCH -> PointerType.Touch
    WindowsPointerKind.STYLUS -> PointerType.Stylus
    WindowsPointerKind.ERASER -> PointerType.Eraser
}

private fun Field.accessibleField(): Field = apply { trySetAccessible() }

private fun Method.accessibleMethod(): Method = apply { trySetAccessible() }
