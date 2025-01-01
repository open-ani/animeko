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
) {
    companion object {
        val Empty = MediaExtraFiles()
    }
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
