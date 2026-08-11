/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.videoenhancement

import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.VideoDimensions
import org.openani.mediamp.mpv.MPVHandle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

/** macOS and Windows share the shader and scaler; Windows omits deband for its lighter profile. */
actual fun createVideoEnhancementController(
    player: MediampPlayer,
    parentCoroutineContext: CoroutineContext,
): VideoEnhancementController? {
    val osName = System.getProperty("os.name")
    if (!osName.startsWith("Mac", ignoreCase = true) && !osName.startsWith("Windows", ignoreCase = true)) {
        return null
    }
    val handle = player.impl as? MPVHandle ?: return null
    return MpvVideoEnhancementController(player, handle, parentCoroutineContext)
}

private class MpvVideoEnhancementController(
    player: MediampPlayer,
    private val handle: MPVHandle,
    parentCoroutineContext: CoroutineContext,
) : BaseVideoEnhancementController(player, parentCoroutineContext) {
    private val lock = Any()
    private val originalProperties = enhancementPropertyNames.associateWith { name ->
        checkNotNull(handle.getPropertyString(name)) { "mpv property is unavailable: $name" }
    }
    private val clearProperties = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        windowsLiteClearProperties
    } else {
        fullClearProperties
    }

    private var effectiveMode: VideoEnhancementMode = VideoEnhancementMode.OFF
    private var anime4kShaderFile: Path? = null
    private var anime4kShaderApplied: Boolean = false

    init {
        startObserving()
    }

    override fun apply(
        mode: VideoEnhancementMode,
        videoSize: VideoDimensions?,
        viewportSize: VideoDimensions?,
    ) {
        synchronized(lock) {
            applyEffectiveModeLocked(mode, videoSize, viewportSize)
        }
    }

    private fun applyEffectiveModeLocked(
        requestedMode: VideoEnhancementMode,
        videoSize: VideoDimensions?,
        viewportSize: VideoDimensions?,
    ) {
        val needsUpscale = videoSize != null && viewportSize != null &&
            minOf(
                viewportSize.width.toDouble() / videoSize.width,
                viewportSize.height.toDouble() / videoSize.height,
            ) > 1.0
        val targetMode = if (requestedMode == VideoEnhancementMode.CLEAR && needsUpscale) {
            VideoEnhancementMode.CLEAR
        } else {
            VideoEnhancementMode.OFF
        }

        if (targetMode != effectiveMode) {
            if (targetMode == VideoEnhancementMode.OFF) {
                removeAnime4kShaderLocked()
            }
            val properties = when (targetMode) {
                VideoEnhancementMode.OFF -> originalProperties
                VideoEnhancementMode.CLEAR -> clearProperties
            }
            properties.forEach { (name, value) ->
                check(handle.setPropertyString(name, value)) {
                    "mpv rejected video enhancement property $name=$value"
                }
            }
            if (targetMode == VideoEnhancementMode.CLEAR) {
                applyAnime4kShaderLocked()
            }
            effectiveMode = targetMode
        }
    }

    private fun applyAnime4kShaderLocked() {
        if (anime4kShaderApplied) return
        val shaderPath = ensureAnime4kShaderFileLocked().toAbsolutePath().toString()
        check(handle.command("change-list", "glsl-shaders", "append", shaderPath)) {
            "mpv rejected Anime4K Lite shader: $shaderPath"
        }
        anime4kShaderApplied = true
    }

    private fun removeAnime4kShaderLocked() {
        if (!anime4kShaderApplied) return
        val shaderPath = checkNotNull(anime4kShaderFile).toAbsolutePath().toString()
        check(handle.command("change-list", "glsl-shaders", "remove", shaderPath)) {
            "mpv failed to remove Anime4K Lite shader: $shaderPath"
        }
        anime4kShaderApplied = false
    }

    private fun ensureAnime4kShaderFileLocked(): Path {
        anime4kShaderFile?.let { return it }
        val target = Files.createTempFile("animeko-anime4k-lite-", ".glsl")
        try {
            val resource = checkNotNull(javaClass.getResourceAsStream(anime4kShaderResource)) {
                "Missing bundled Anime4K Lite shader: $anime4kShaderResource"
            }
            resource.use { input ->
                Files.newOutputStream(target).use { output -> input.copyTo(output) }
            }
        } catch (e: Throwable) {
            Files.deleteIfExists(target)
            throw e
        }
        anime4kShaderFile = target
        return target
    }

    override fun restore() {
        synchronized(lock) {
            if (anime4kShaderApplied) {
                val shaderPath = anime4kShaderFile?.toAbsolutePath()?.toString()
                if (shaderPath != null) {
                    runCatching { handle.command("change-list", "glsl-shaders", "remove", shaderPath) }
                }
                anime4kShaderApplied = false
            }
            originalProperties.forEach { (name, value) ->
                runCatching { handle.setPropertyString(name, value) }
            }
            effectiveMode = VideoEnhancementMode.OFF
            anime4kShaderFile?.let { runCatching { Files.deleteIfExists(it) } }
            anime4kShaderFile = null
        }
    }
}

private const val anime4kShaderResource =
    "/video-enhancement/Anime4K_Restore_CNN_S.glsl"

private val enhancementPropertyNames = listOf(
    "correct-downscaling",
    "linear-downscaling",
    "sigmoid-upscaling",
    "scale",
    "dscale",
    "cscale",
    "scale-antiring",
    "dscale-antiring",
    "deband",
    "deband-iterations",
    "deband-threshold",
    "deband-range",
    "deband-grain",
)

private val fullClearProperties = mapOf(
    "correct-downscaling" to "yes",
    "linear-downscaling" to "yes",
    "sigmoid-upscaling" to "yes",
    "scale" to "ewa_lanczossharp",
    "dscale" to "ewa_lanczossharp",
    "cscale" to "ewa_lanczossharp",
    "scale-antiring" to "0.7",
    "dscale-antiring" to "0.7",
    "deband" to "yes",
    "deband-iterations" to "1",
    "deband-threshold" to "32",
    "deband-range" to "16",
    "deband-grain" to "0",
)

private val windowsLiteClearProperties = fullClearProperties + ("deband" to "no")
