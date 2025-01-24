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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.LocalComposeScene
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.packFloats

sealed interface LayoutHitTestOwner {

    fun hitTest(x: Float, y: Float): Boolean
}

@OptIn(InternalComposeUiApi::class)
@Composable
fun rememberLayoutHitTestOwner(): LayoutHitTestOwner? {
    val scene = LocalComposeScene.current ?: return null
    return remember(scene) {
        when (scene::class.qualifiedName) {
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
* reflect implementation for compose 1.7
 */
private abstract class ReflectLayoutHitTestOwner : LayoutHitTestOwner {
    @OptIn(InternalComposeUiApi::class)
    val classLoader = ComposeScene::class.java.classLoader!!

    private val rootNodeOwnerOwnerField =
        classLoader
            .loadClass("androidx.compose.ui.node.RootNodeOwner")
            .getDeclaredField("owner")
            .apply {
                trySetAccessible()
            }

    private val ownerRootField =
        classLoader
            .loadClass("androidx.compose.ui.node.Owner")
            .getDeclaredMethod("getRoot")
            .apply {
                trySetAccessible()
            }

    private val hitTestResultClass = classLoader.loadClass("androidx.compose.ui.node.HitTestResult")

    private val layoutNodeHitTestMethod =
        classLoader
            .loadClass("androidx.compose.ui.node.LayoutNode")
            .declaredMethods
            .first { it.name.startsWith("hitTest-") && it.parameterCount == 4 }

    fun getLayoutNode(rootNodeOwner: Any): Any {
        val owner = rootNodeOwnerOwnerField.get(rootNodeOwner)
        return ownerRootField.invoke(owner)
    }

    fun Any.layoutNodeHitTest(
        x: Float,
        y: Float,
    ): Boolean {
        // result type is List<Modifier.Node> (compose 1.7)
        val result = hitTestResultClass.getDeclaredConstructor().newInstance() as List<*>
        layoutNodeHitTestMethod.invoke(this, packFloats(x, y), result, false, true)
        // pointer input modifier node detection for Material 3 components
        for (index in result.lastIndex downTo result.lastIndex - 1) {
            val node = result.getOrNull(index) ?: return false
            val nodeClassName = node.javaClass.name
            return excludeNodeNames.any { nodeClassName.contains(it) }
        }
        return false
    }

    private val excludeNodeNames =
        listOf(
            "ClickableNode",
            "SelectableNode",
            "ScrollableNode",
        )

    class CopiedList<T>(
        private val populate: (MutableList<T>) -> Unit,
    ) : MutableList<T> by mutableListOf() {
        inline fun withCopy(block: (List<T>) -> Unit) {
            // In case of recursive calls, allocate new list
            val copy = if (isEmpty()) this else mutableListOf()
            populate(copy)
            try {
                block(copy)
            } finally {
                copy.clear()
            }
        }
    }
}

@OptIn(InternalComposeUiApi::class)
private class PlatformLayersLayoutHitTestOwner(
    scene: ComposeScene,
) : ReflectLayoutHitTestOwner() {
    private val sceneClass = classLoader.loadClass("androidx.compose.ui.scene.PlatformLayersComposeSceneImpl")

    private val mainOwnerRef =
        sceneClass.getDeclaredMethod("getMainOwner").let {
            it.trySetAccessible()
            it.invoke(scene)
        }

    override fun hitTest(
        x: Float,
        y: Float,
    ): Boolean = getLayoutNode(mainOwnerRef).layoutNodeHitTest(x, y)
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
            it.get(scene)
        }

    private val layersRef =
        sceneClass.getDeclaredField("layers").let {
            it.trySetAccessible()
            it.get(scene)
        } as MutableList<*>

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

    private val layers =
        CopiedList {
            for (layer in layersRef) {
                it.add(layer)
            }
        }

    override fun hitTest(
        x: Float,
        y: Float,
    ): Boolean {
        layers.withCopy {
            it.fastForEachReversed { layer ->
                if (layerIsInBoundMethod.invoke(layer, packFloats(x, y)) == true) {
                    return getLayoutNode(layerOwnerField.get(layer)).layoutNodeHitTest(x, y)
                } else if (layer == focusedLayerField.get(scene)) {
                    return false
                }
            }
        }
        return getLayoutNode(mainOwnerRef).layoutNodeHitTest(x, y)
    }
}
