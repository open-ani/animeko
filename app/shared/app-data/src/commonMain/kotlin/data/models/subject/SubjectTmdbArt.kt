/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 条目对应的 TMDB 作品的图片, 均为服务器镜像后的公开 CDN 直链. 同一部 TMDB 作品的各季共用这些图片.
 */
@Immutable
@Serializable
data class SubjectTmdbArt(
    /** 横幅 (16:9, 基本不带文字), 第一张是 TMDB 的主图. */
    val backdrops: List<TmdbImage> = emptyList(),
    /** 竖版海报, 按图上文字的语言索引: `zh` / `ja` / `en`, 无文字的为 `xx`. */
    val posters: Map<String, TmdbImage> = emptyMap(),
    /** 标题 Logo (透明底的标题美术字), 按语言索引: `zh` / `ja` / `en`. */
    val logos: Map<String, TmdbImage> = emptyMap(),
) {
    /** 首选横幅, 没有横幅时为 `null`. */
    val primaryBackdrop: TmdbImage? get() = backdrops.firstOrNull()
}

@Immutable
@Serializable
data class TmdbImage(
    /** 中等尺寸的位图: 横幅宽 1280px, 海报与 Logo 宽 500px. */
    val medium: String,
    /** 原始尺寸的位图. */
    val large: String,
    /** SVG 原件. 只有原图是 SVG 的标题 Logo 才有. */
    val vector: String? = null,
)
