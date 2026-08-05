/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 用户上次手动选择的内嵌字幕轨道, 用于在播放新视频时自动选择相同的轨道.
 *
 * 记录的是轨道的语言与标签特征而非轨道 id, 因为 id 只在单个视频文件内有意义.
 */
@Serializable
data class SubtitleTrackPreference(
    /**
     * 用户上次手动关闭了字幕. 为 `true` 时不自动选择任何轨道.
     */
    val off: Boolean = false,
    /**
     * 上次选中轨道的标签原文, 例如 "简日双语". 用于精确匹配.
     */
    val label: String? = null,
    /**
     * 上次选中轨道的语言代码原文, 例如 "chi". 规范化后用于模糊匹配.
     */
    val language: String? = null,

    @Suppress("PropertyName")
    @Transient val _placeholder: Int = 0,
) {
    /**
     * 用户是否已经做过一次手动选择. 未做过时不应自动选择轨道.
     */
    val isRecorded: Boolean get() = off || label != null || language != null

    companion object {
        val Default = SubtitleTrackPreference()
    }
}
