/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.lang.reflect.Proxy
import javax.swing.JComponent

/**
 * macOS 触摸板手势 (`com.apple.eawt.event`). Compose Desktop 只把触摸板当鼠标, 不会产生捏合事件,
 * 所以直接监听 AWT 的 magnification 手势.
 *
 * 这些类只存在于 macOS 的 JDK 里, 且所在包默认不导出, 因此用反射访问;
 * 需要 JVM 参数 `--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED` (见 app/desktop/build.gradle.kts).
 */
object MacTrackpadGestures {
    private val logger = logger<MacTrackpadGestures>()

    /**
     * 监听 [component] 及其子组件上的捏合手势. [onMagnify] 的参数是本次的缩放增量, 例如 `0.05` 表示放大 5%.
     *
     * @return 用于移除监听的句柄; 当前 JDK 不支持时返回 `null`.
     */
    fun addMagnificationListener(component: JComponent, onMagnify: (magnification: Double) -> Unit): AutoCloseable? {
        return try {
            val utilities = Class.forName("com.apple.eawt.event.GestureUtilities")
            val gestureListenerClass = Class.forName("com.apple.eawt.event.GestureListener")
            val magnificationListenerClass = Class.forName("com.apple.eawt.event.MagnificationListener")
            val getMagnification = Class.forName("com.apple.eawt.event.MagnificationEvent").getMethod("getMagnification")
            val addMethod = utilities.getMethod("addGestureListenerTo", JComponent::class.java, gestureListenerClass)
            val removeMethod = utilities.getMethod("removeGestureListenerFrom", JComponent::class.java, gestureListenerClass)

            lateinit var listener: Any
            listener = Proxy.newProxyInstance(
                magnificationListenerClass.classLoader,
                arrayOf(magnificationListenerClass),
            ) { _, method, args ->
                when (method.name) {
                    "magnify" -> {
                        onMagnify(getMagnification.invoke(args!![0]) as Double)
                        null
                    }

                    "equals" -> args?.get(0) === listener
                    "hashCode" -> System.identityHashCode(listener)
                    "toString" -> "MagnificationListener"
                    else -> null
                }
            }
            addMethod.invoke(null, component, listener)
            AutoCloseable { removeMethod.invoke(null, component, listener) }
        } catch (e: Throwable) {
            logger.warn(e) { "macOS trackpad gestures are unavailable" }
            null
        }
    }
}
