/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package me.him188.ani.app.platform.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.scene.ComposeScene
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeSceneTouchBridgeTest {
    @Test
    fun `screen physical pixels are converted using content origin density and scene bounds`() {
        assertEquals(
            Offset(104f, 76f),
            windowsScreenPositionToScenePosition(
                screenX = 500,
                screenY = 400,
                contentLocationOnScreen = Point(200, 150),
                density = 1.5f,
                sceneBoundsInPx = Rect(left = 96f, top = 99f, right = 1200f, bottom = 800f),
            ),
        )
    }

    @Test
    fun `current CMP exposes one list pointer event overload`() {
        val methods = ComposeScene::class.java.methods.filter { method ->
            method.name.startsWith("sendPointerEvent-") &&
                method.parameterTypes.size == 8 &&
                method.parameterTypes[1] == List::class.java
        }

        assertEquals(1, methods.size)
        assertEquals(Int::class.javaPrimitiveType, methods.single().parameterTypes[0])
        assertEquals(Long::class.javaPrimitiveType, methods.single().parameterTypes[4])
        assertEquals(Long::class.javaPrimitiveType, methods.single().parameterTypes[5])
    }
}
