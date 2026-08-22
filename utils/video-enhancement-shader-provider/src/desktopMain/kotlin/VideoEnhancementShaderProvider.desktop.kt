/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.video.enhancement.shader.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

actual object VideoEnhancementShaderProvider {
    private val lock = Mutex()

    @Volatile
    private var shaderBasePath: Path? = null
    private var temporaryShaderDirectory: Path? = null
    private val extractedShaderPaths = mutableMapOf<String, Path>()

    fun setShaderBasePath(path: Path) {
        shaderBasePath = path
    }

    suspend fun getShaderPath(shaderName: String): Path {
        lock.withLock {
            shaderBasePath?.let { return it.resolve(shaderName) }
            extractedShaderPaths[shaderName]?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            lock.withLock {
                shaderBasePath?.resolve(shaderName) ?: extractShaderLocked(shaderName)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun extractShaderLocked(shaderName: String): Path {
        extractedShaderPaths[shaderName]?.let { return it }

        val directory = temporaryShaderDirectory ?: Files.createTempDirectory("animeko-video-enhancement-shaders-")
            .also {
                it.toFile().deleteOnExit()
                temporaryShaderDirectory = it
            }
        val target = directory.resolve(shaderName).normalize()
        require(target.startsWith(directory)) { "Shader name escapes the temporary directory: $shaderName" }
        target.parent?.let(Files::createDirectories)

        try {
            val resource = Res.readBytes("files/shaders/$shaderName")
            Files.write(target, resource, StandardOpenOption.CREATE_NEW)
        } catch (e: Throwable) {
            Files.deleteIfExists(target)
            throw e
        }

        target.toFile().deleteOnExit()
        extractedShaderPaths[shaderName] = target
        return target
    }
}
