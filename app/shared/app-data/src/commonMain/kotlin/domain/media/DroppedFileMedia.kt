/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media

import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.extension
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.name

/**
 * 用户拖入播放页的本地视频文件所对应的 [Media].
 *
 * 它不来自任何数据源, 不会出现在 [MediaSelector] 的候选列表中, 只通过 [MediaSelector.selectTemporarily] 选中,
 * 随当前剧集的播放会话结束而失效. 播放由 `LocalFileMediaResolver` 负责.
 */
object DroppedFileMedia {
    /**
     * [Media.mediaSourceId]. 没有数据源实例使用这个 ID, 按 ID 查询数据源信息会得到 `null`.
     */
    const val MEDIA_SOURCE_ID = "dropped-file"

    /**
     * 可以直接交给播放器的视频文件扩展名 (小写).
     */
    val VIDEO_EXTENSIONS: Set<String> = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "wmv",
        "ts", "m2ts", "mpg", "mpeg", "rm", "rmvb", "3gp", "ogv",
    )

    private val MPEG_TS_EXTENSIONS = setOf("ts", "m2ts")

    fun isVideoFile(path: Path): Boolean = path.extension.lowercase() in VIDEO_EXTENSIONS

    /**
     * [files] 中首个视频文件.
     */
    fun findVideoFile(files: List<Path>): SystemPath? = files.firstOrNull { isVideoFile(it) }?.inSystem

    fun isDroppedFile(media: Media): Boolean = media.mediaSourceId == MEDIA_SOURCE_ID

    fun create(file: SystemPath): Media {
        val absolutePath = file.absolutePath
        return DefaultMedia(
            mediaId = "$MEDIA_SOURCE_ID:$absolutePath",
            mediaSourceId = MEDIA_SOURCE_ID,
            originalUrl = "file://$absolutePath",
            download = ResourceLocation.LocalFile(
                absolutePath,
                fileType = ResourceLocation.LocalFile.FileType.MPTS
                    .takeIf { file.extension.lowercase() in MPEG_TS_EXTENSIONS },
            ),
            originalTitle = file.name,
            publishedTime = 0,
            properties = MediaProperties(
                subjectName = null,
                episodeName = null,
                subtitleLanguageIds = emptyList(),
                resolution = "",
                alliance = "",
                size = runCatching { file.length() }.getOrNull()?.takeIf { it > 0 }?.bytes ?: FileSize.Unspecified,
                subtitleKind = null,
            ),
            episodeRange = null,
            location = MediaSourceLocation.Local,
            // 没有对应本地文件的类型. LocalCache 只表示由下载管理器管理的视频, 因此使用同样即点即播的 WEB.
            kind = MediaSourceKind.WEB,
        )
    }
}
