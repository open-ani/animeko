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
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerEventType
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

    fun send(event: WindowsTouchEvent) {
        if (closed) return
        runOnEventDispatchThread {
            check(!closed) { "ComposeSceneTouchBridge is closed" }
            check(contentComponent.isDisplayable) { "Compose content is no longer displayable" }

            val position = windowsScreenPositionToScenePosition(
                screenX = event.pointer.screenX,
                screenY = event.pointer.screenY,
                contentLocationOnScreen = contentComponent.locationOnScreen,
                density = scene.density.density,
                sceneBoundsInPx = sceneBoundsInPxOrZero(),
            )
            val pointer = ComposeScenePointer(
                id = PointerId(event.pointer.id),
                position = position,
                pressed = event.type != WindowsTouchEventType.RELEASE,
                type = PointerType.Touch,
                pressure = 1f,
            )
            sendPointerEventMethod.invoke(
                scene,
                event.type.toComposePointerEventType(),
                listOf(pointer),
                0,
                0,
                Offset.Zero.packedValue,
                event.pointer.eventTimeMillis,
                null,
                null,
            )
        }
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

    fun cancel() {
        if (closed) return
        runOnEventDispatchThread {
            if (!closed) scene.cancelPointerInput()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
    }

    private fun runOnEventDispatchThread(block: () -> Unit) {
        if (EventQueue.isDispatchThread()) {
            block()
        } else {
            EventQueue.invokeLater {
                runCatching(block).onFailure { error ->
                    logger.error(error) { "ComposeScene touch event dispatch failed" }
                }
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
                        method.parameterTypes.size == 8 &&
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

private fun WindowsTouchEventType.toComposePointerEventType(): Int = when (this) {
    WindowsTouchEventType.PRESS -> PointerEventType.Press.value
    WindowsTouchEventType.MOVE -> PointerEventType.Move.value
    WindowsTouchEventType.RELEASE -> PointerEventType.Release.value
}

private fun Field.accessibleField(): Field = apply { trySetAccessible() }

private fun Method.accessibleMethod(): Method = apply { trySetAccessible() }
