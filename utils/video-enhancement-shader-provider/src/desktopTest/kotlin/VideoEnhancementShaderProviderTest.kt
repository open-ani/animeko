/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.video.enhancement.shader.provider

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoEnhancementShaderProviderTest {
    @Test
    fun `extracts bundled shader and honors base path`() = runBlocking {
        val extractedPath = VideoEnhancementShaderProvider.getShaderPath("Anime4K_Restore_CNN_S.glsl")

        assertTrue(Files.isRegularFile(extractedPath))
        assertTrue(Files.readString(extractedPath).contains("//!DESC"))

        val basePath = Path.of("custom-shaders")
        VideoEnhancementShaderProvider.setShaderBasePath(basePath)

        assertEquals(
            basePath.resolve("custom.glsl"),
            VideoEnhancementShaderProvider.getShaderPath("custom.glsl"),
        )
    }
}
