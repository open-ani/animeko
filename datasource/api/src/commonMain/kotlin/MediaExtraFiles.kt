/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api

import kotlinx.serialization.Serializable

/**
 * @see Media.extraFiles
 */
@Serializable
class MediaExtraFiles(
    val subtitles: List<Subtitle> = emptyList(),
    val chapters: List<MediaChapter> = emptyList(),
    val previewThumbnails: MediaPreviewThumbnails? = null,
) {
    companion object {
        val EMPTY = MediaExtraFiles()
    }
}

/**
 * 通用的进度条预览缩略图信息，适用于 Jellyfin, Emby, Plex, YouTube Sprite Tiles 等各类视频数据源。
 */
@Serializable
data class MediaPreviewThumbnails(
    val width: Int,
    val height: Int,
    val intervalMillis: Long,
    val totalCount: Int,
    val layout: Layout,
    val headers: Map<String, String> = emptyMap(),
    val requesterMediaSourceId: String? = null,
) {
    @Serializable
    sealed interface Layout {
        /**
         * 精灵图（Sprite Tile）拼图网格布局（如 Jellyfin/Emby/Plex/YouTube）。
         * 每个大图包含 [columns] 列 x [rows] 行个小缩略图。
         */
        @Serializable
        data class SpriteTile(
            val columns: Int,
            val rows: Int,
            /**
             * 拼图大图的 URL 模板，如 `https://example.com/tiles/{tileIndex}.jpg`。
             * `{tileIndex}` 会被替换为 0, 1, 2...
             */
            val urlPattern: String,
        ) : Layout
    }
}

@Serializable
data class MediaChapter(
    val name: String,
    val durationMillis: Long,
    val offsetMillis: Long,
    val kind: MediaChapterKind = MediaChapterKind.CHAPTER,
)

@Serializable
enum class MediaChapterKind {
    CHAPTER,
    OPENING,
    ENDING,
}

@Serializable
data class Subtitle(
    /**
     * e.g. `https://example.com/1.ass`
     */
    val uri: String,
    /**
     * 将会传递给播放器引擎, 可能会用来判断是否支持这个字幕文件以及解析方式.
     */
    val mimeType: String? = null,
    /**
     * 字幕语言.
     *
     * 这个是历史遗留, 不会影响 UI 和选择.
     * 对传入数据也没有限制. 可以传入 `null`.
     */
    val language: String? = null,
    /**
     * 将会显示在 UI 的名称. 如果有多个字幕, 最好每个字幕都有一个不同的 [label].
     *
     * 该值只影响安卓端. PC 端不采用此属性, 总是从字幕文件中读取.
     */
    val label: String? = null,
)
