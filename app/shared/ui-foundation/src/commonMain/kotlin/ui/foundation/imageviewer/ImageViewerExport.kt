/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.util.DownloadData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes

/**
 * 图片查看器里当前图片在本地的一份副本, 文件名带有正确的扩展名.
 * 用于"保存图片"和把文件拖拽到其他应用.
 */
class ImageViewerExportedFile(
    val path: SystemPath,
    /** 不含扩展名的文件名, 例如 `cover`. */
    val baseName: String,
    /** 不含点的扩展名, 例如 `png`. */
    val extension: String,
) {
    val fileName: String get() = "$baseName.$extension"
}

/**
 * 把 [model] (图片 URL) 通过 [Sketch] 下载 (命中磁盘缓存则直接读取), 写到 [directory] 下的一个子目录里.
 *
 * 文件名取自 URL 最后一段, 扩展名优先按图片内容判断 (URL 的扩展名不一定可信).
 * 同一个 URL 多次导出会复用同一个文件.
 */
suspend fun Sketch.exportImageForViewer(
    model: String,
    directory: SystemPath,
): ImageViewerExportedFile = withContext(Dispatchers.IO_) {
    val data = executeDownload(ImageRequest(context, model)).getOrThrow()
    val bytes = when (data) {
        is DownloadData.Bytes -> data.bytes
        is DownloadData.Cache -> data.fileSystem.read(data.path) { readByteArray() }
    }
    val (baseName, extension) = deriveImageFileName(model, bytes)
    val target = directory.resolve(model.hashCode().toUInt().toString(16))
        .apply { createDirectories() }
        .resolve("$baseName.$extension")
    if (!target.exists()) {
        target.writeBytes(bytes)
    }
    ImageViewerExportedFile(target, baseName, extension)
}

/**
 * 由 URL 和图片内容推导 `(不含扩展名的文件名, 扩展名)`.
 */
fun deriveImageFileName(model: String, bytes: ByteArray): Pair<String, String> {
    val withoutQuery = model.substringBefore('#').substringBefore('?')
    // 去掉 scheme 和 host, 只看路径部分; 没有 "://" 的 (本地路径等) 整体视为路径
    val path = withoutQuery.substringAfter("://", "").substringAfter('/', "")
        .ifEmpty { if ("://" in withoutQuery) "" else withoutQuery }
    val segments = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
    val lastSegment = segments.lastOrNull().orEmpty()
    val urlExtension = lastSegment.substringAfterLast('.', "").lowercase()
    val hasKnownUrlExtension = urlExtension in KNOWN_IMAGE_EXTENSIONS
    val rawBaseName = when {
        hasKnownUrlExtension -> lastSegment.substringBeforeLast('.')
        // 形如 .../subjects/277554/large 的 URL: 最后一段只是尺寸, 用上一段区分不同图片
        lastSegment.lowercase() in GENERIC_SIZE_SEGMENTS && segments.size >= 2 ->
            segments[segments.size - 2] + "_" + lastSegment

        else -> lastSegment
    }
    val baseName = rawBaseName
        .replace(INVALID_FILE_NAME_CHARS, "_")
        .trim { it == '.' || it == ' ' || it == '_' }
        .take(MAX_BASE_NAME_LENGTH)
        .ifEmpty { "image" }
    val extension = sniffImageExtension(bytes)
        ?: urlExtension.takeIf { hasKnownUrlExtension }
        ?: "jpg"
    return baseName to extension
}

private val GENERIC_SIZE_SEGMENTS = setOf("large", "medium", "small", "grid", "common", "original", "cover", "image", "img", "thumb")
private val KNOWN_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif", "heic")
private val INVALID_FILE_NAME_CHARS = Regex("""[\\/:*?"<>| ]""")
private const val MAX_BASE_NAME_LENGTH = 80

/** 通过文件头识别常见图片格式. */
private fun sniffImageExtension(bytes: ByteArray): String? {
    fun startsWith(vararg prefix: Int, offset: Int = 0): Boolean {
        if (bytes.size < offset + prefix.size) return false
        return prefix.withIndex().all { (i, b) -> bytes[offset + i] == b.toByte() }
    }
    return when {
        startsWith(0x89, 0x50, 0x4E, 0x47) -> "png"
        startsWith(0xFF, 0xD8, 0xFF) -> "jpg"
        startsWith('G'.code, 'I'.code, 'F'.code, '8'.code) -> "gif"
        startsWith('R'.code, 'I'.code, 'F'.code, 'F'.code) &&
                startsWith('W'.code, 'E'.code, 'B'.code, 'P'.code, offset = 8) -> "webp"

        startsWith('B'.code, 'M'.code) -> "bmp"
        else -> null
    }
}
