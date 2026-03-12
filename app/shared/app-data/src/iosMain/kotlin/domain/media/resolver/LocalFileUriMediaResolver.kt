/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

private val CONTAINED_VIDEO_EXTENSIONS = setOf(
    ".mp4",
    ".m4v",
    ".mov",
    ".mkv",
    ".avi",
    ".webm",
)

/**
 * 类似 [LocalFileMediaResolver], 但是使用文件 URI.
 */
class LocalFileUriMediaResolver : MediaResolver {
    override fun supports(media: Media): Boolean {
        return media.download is ResourceLocation.LocalFile
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        when (val download = media.download) {
            is ResourceLocation.LocalFile -> {
                val filePath = ensureFileExtension(
                    download.filePath,
                    download.fileType,
                    download.originalUri,
                )
                val fileUri = NSURL.fileURLWithPath(filePath).absoluteString
                    ?: throw IllegalStateException("Failed to convert file path to URI: $filePath")
                return LocalFileUriMediaDataProvider(fileUri, media.originalTitle)
            }

            else -> throw UnsupportedMediaException(media)
        }
    }
}

/**
 * AVFoundation 依赖文件扩展名来识别媒体格式。
 * 如果文件已经有真实扩展名，必须保留；否则仅在缺失扩展名时创建一个带扩展名的 symlink。
 */
@kotlin.OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun ensureFileExtension(
    filePath: String,
    fileType: ResourceLocation.LocalFile.FileType?,
    originalUri: String?,
): String {
    val expectedExtension = inferExpectedFileExtension(filePath, fileType, originalUri) ?: return filePath
    if (filePath.endsWith(expectedExtension, ignoreCase = true)) return filePath

    val symlinkPath = filePath + expectedExtension
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.fileExistsAtPath(symlinkPath)) {
        fileManager.createSymbolicLinkAtPath(symlinkPath, withDestinationPath = filePath, error = null)
    }
    return symlinkPath
}

internal fun inferExpectedFileExtension(
    filePath: String,
    fileType: ResourceLocation.LocalFile.FileType?,
    originalUri: String?,
): String? {
    return when (fileType) {
        ResourceLocation.LocalFile.FileType.MPTS -> ".ts"
        ResourceLocation.LocalFile.FileType.CONTAINED -> {
            containedVideoExtension(filePath)
                ?: containedVideoExtension(originalUri)
                ?: ".mp4"
        }

        null -> null
    }
}

private fun containedVideoExtension(pathOrUri: String?): String? {
    val path = pathOrUri
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?: return null
    val fileName = path.substringAfterLast('/').substringAfterLast('\\')
    val extension = fileName.substringAfterLast('.', "").lowercase()
    if (extension.isEmpty()) return null

    val normalized = ".$extension"
    return normalized.takeIf { it in CONTAINED_VIDEO_EXTENSIONS }
}

class LocalFileUriMediaDataProvider(
    val uri: String,
    val originalTitle: String,
    private val headers: Map<String, String> = emptyMap(),
    override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
) : MediaDataProvider<UriMediaData> {
    override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData = UriMediaData(uri, headers, extraFiles)
    override fun toString(): String = "LocalFileUriVideoSource(uri='$uri')"
}
