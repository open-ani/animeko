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
    private val anime4kShaderFiles = mutableMapOf<String, Path>()
    private val appliedAnime4kShaders = mutableListOf<String>()

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
        val targetMode = if (requestedMode != VideoEnhancementMode.OFF && needsUpscale) {
            requestedMode
        } else {
            VideoEnhancementMode.OFF
        }

        if (targetMode != effectiveMode) {
            removeAnime4kShadersLocked()
            val properties = when (targetMode) {
                VideoEnhancementMode.OFF -> originalProperties
                VideoEnhancementMode.PERFORMANCE,
                VideoEnhancementMode.QUALITY,
                -> clearProperties
            }
            properties.forEach { (name, value) ->
                check(handle.setPropertyString(name, value)) {
                    "mpv rejected video enhancement property $name=$value"
                }
            }
            when (targetMode) {
                VideoEnhancementMode.OFF -> Unit
                VideoEnhancementMode.PERFORMANCE -> applyAnime4kShaderLocked(anime4kRestoreShaderResource)
                VideoEnhancementMode.QUALITY -> anime4kQualityShaderResources.forEach(::applyAnime4kShaderLocked)
            }
            effectiveMode = targetMode
        }
    }

    private fun applyAnime4kShaderLocked(resource: String) {
        val shaderPath = ensureAnime4kShaderFileLocked(resource).toAbsolutePath().toString()
        check(handle.command("change-list", "glsl-shaders", "append", shaderPath)) {
            "mpv rejected Anime4K shader: $shaderPath"
        }
        appliedAnime4kShaders += resource
    }

    private fun removeAnime4kShadersLocked() {
        appliedAnime4kShaders.asReversed().forEach { resource ->
            val shaderPath = checkNotNull(anime4kShaderFiles[resource]).toAbsolutePath().toString()
            check(handle.command("change-list", "glsl-shaders", "remove", shaderPath)) {
                "mpv failed to remove Anime4K shader: $shaderPath"
            }
        }
        appliedAnime4kShaders.clear()
    }

    private fun ensureAnime4kShaderFileLocked(resourceName: String): Path {
        anime4kShaderFiles[resourceName]?.let { return it }
        val target = Files.createTempFile("animeko-anime4k-", ".glsl")
        try {
            val resource = checkNotNull(javaClass.getResourceAsStream(resourceName)) {
                "Missing bundled Anime4K shader: $resourceName"
            }
            resource.use { input ->
                Files.newOutputStream(target).use { output -> input.copyTo(output) }
            }
        } catch (e: Throwable) {
            Files.deleteIfExists(target)
            throw e
        }
        anime4kShaderFiles[resourceName] = target
        return target
    }

    override fun restore() {
        synchronized(lock) {
            appliedAnime4kShaders.asReversed().forEach { resource ->
                val shaderPath = anime4kShaderFiles[resource]?.toAbsolutePath()?.toString()
                if (shaderPath != null) runCatching {
                    handle.command("change-list", "glsl-shaders", "remove", shaderPath)
                }
            }
            appliedAnime4kShaders.clear()
            originalProperties.forEach { (name, value) ->
                runCatching { handle.setPropertyString(name, value) }
            }
            effectiveMode = VideoEnhancementMode.OFF
            anime4kShaderFiles.values.forEach { runCatching { Files.deleteIfExists(it) } }
            anime4kShaderFiles.clear()
        }
    }
}

private const val anime4kRestoreShaderResource =
    "/video-enhancement/Anime4K_Restore_CNN_S.glsl"

private val anime4kQualityShaderResources = listOf(
    "/video-enhancement/Anime4K_Clamp_Highlights.glsl",
    "/video-enhancement/Anime4K_Restore_CNN_VL.glsl",
    "/video-enhancement/Anime4K_Upscale_CNN_x2_VL.glsl",
    "/video-enhancement/Anime4K_AutoDownscalePre_x2.glsl",
    "/video-enhancement/Anime4K_AutoDownscalePre_x4.glsl",
    "/video-enhancement/Anime4K_Upscale_CNN_x2_M.glsl",
)

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
