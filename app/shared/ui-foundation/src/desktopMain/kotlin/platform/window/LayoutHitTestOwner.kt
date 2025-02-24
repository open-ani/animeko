/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package me.him188.ani.app.platform.window

import androidx.compose.foundation.AbstractClickableNode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.HitTestResult
import androidx.compose.ui.node.RootNodeOwner
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.CopiedList
import androidx.compose.ui.scene.LocalComposeSceneContext
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.packFloats

/**
 * 提供 [ComposeScene] 的点击测试.
 *
 * 使用 [rememberLayoutHitTestOwner] 获取当前平台的 [LayoutHitTestOwner].
 */
sealed interface LayoutHitTestOwner {
    /**
     * 测试这个坐标是否有 Compose 中的可**点击** (clickable), 可**复合点击** (combined clickable),
     * 可**选择** (selectable) 和 可**开关** (toggleable) 的节点视图.
     *
     * @return 如果返回 true, 则表示这个坐标有可点击的节点.
     * 点击这个坐标将会触发对应的 Compose 视图的点击事件.
     */
    fun hitTest(x: Float, y: Float): Boolean
}

/**
 * 获取当前 [ComposeScene] 的 [LayoutHitTestOwner].
 */
@OptIn(InternalComposeUiApi::class)
@Composable
fun rememberLayoutHitTestOwner(): LayoutHitTestOwner? {
    val scene = (LocalComposeSceneContext.current as? ComposeScene) ?: return null
    return remember(scene) {
        when (scene::class.java.canonicalName) {
            "androidx.compose.ui.scene.CanvasLayersComposeSceneImpl" -> {
                CanvasLayersLayoutHitTestOwner(scene)
            }

            "androidx.compose.ui.scene.PlatformLayersComposeSceneImpl" -> {
                PlatformLayersLayoutHitTestOwner(scene)
            }

            else -> error("unsupported compose scene")
        }
    }
}

/*
* reflect implementation for compose 1.8
 */
private abstract class ReflectLayoutHitTestOwner : LayoutHitTestOwner {
    @OptIn(InternalComposeUiApi::class)
    val classLoader = ComposeScene::class.java.classLoader!!

    fun RootNodeOwner.layoutNodeHitTest(
        x: Float,
        y: Float,
    ): Boolean {
        // result type is List<Modifier.Node> (compose 1.8)
        val result = HitTestResult()
        owner.root.hitTest(Offset(x, y), result, false, true)
        // pointer input modifier node detection for Material 3 components
        for (index in result.lastIndex downTo result.lastIndex - 1) {
            val node = result.getOrNull(index) ?: return false
            // SelectableNode, ClickableNode, CombinedClickableNode, ToggleableNode, TriStateToggleableNode
            if (node is AbstractClickableNode) {
                return true
            }
            val nodeClassName = node.javaClass.name
            return excludeNodeNames.any { nodeClassName.contains(it) }
        }
        return false
    }

    private val excludeNodeNames =
        listOf(
            "ScrollableNode",
            "HoverableNode",
        )

}

@OptIn(InternalComposeUiApi::class)
private class PlatformLayersLayoutHitTestOwner(
    scene: ComposeScene,
) : ReflectLayoutHitTestOwner() {
    private val sceneClass = classLoader.loadClass("androidx.compose.ui.scene.PlatformLayersComposeSceneImpl")

    private val mainOwnerRef =
        sceneClass.getDeclaredMethod("getMainOwner").let {
            it.trySetAccessible()
            it.invoke(scene) as RootNodeOwner
        }

    override fun hitTest(
        x: Float,
        y: Float,
    ): Boolean = mainOwnerRef.layoutNodeHitTest(x, y)
}

@OptIn(InternalComposeUiApi::class)
private class CanvasLayersLayoutHitTestOwner(
    private val scene: ComposeScene,
) : ReflectLayoutHitTestOwner() {
    private val sceneClass = classLoader.loadClass("androidx.compose.ui.scene.CanvasLayersComposeSceneImpl")
    private val layerClass =
        sceneClass.declaredClasses.first {
            it.name ==
                    "androidx.compose.ui.scene.CanvasLayersComposeSceneImpl\$AttachedComposeSceneLayer"
        }

    private val mainOwnerRef =
        sceneClass.getDeclaredField("mainOwner").let {
            it.trySetAccessible()
            it.get(scene) as RootNodeOwner
        }

    private val layersCopyCacheRef =
        sceneClass.getDeclaredField("_layersCopyCache").let {
            it.trySetAccessible()
            it.get(scene)
        } as CopiedList<*>

    private val focusedLayerField =
        sceneClass.getDeclaredField("focusedLayer").apply {
            trySetAccessible()
        }

    private val layerOwnerField =
        layerClass
            .getDeclaredField("owner")
            .apply {
                trySetAccessible()
            }

    private val layerIsInBoundMethod =
        layerClass
            .declaredMethods
            .first { it.name.startsWith("isInBounds") }
            .apply {
                trySetAccessible()
            }

    override fun hitTest(
        x: Float,
        y: Float,
    ): Boolean {
        layersCopyCacheRef.withCopy {
            it.fastForEachReversed { layer ->
                if (layerIsInBoundMethod.invoke(layer, packFloats(x, y)) == true) {
                    return (layerOwnerField.get(layer) as RootNodeOwner).layoutNodeHitTest(x, y)
                } else if (layer == focusedLayerField.get(scene)) {
                    return false
                }
            }
        }
        return mainOwnerRef.layoutNodeHitTest(x, y)
    }
}
