/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import me.him188.ani.app.data.models.preference.MediaPreference.Companion.Empty
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.utils.platform.annotations.SerializationOnly
import me.him188.ani.utils.platform.annotations.TestOnly

// was in me.him188.ani.app.data.models.preference.MediaPreference,
// moved here in 3.10
/**
 * @see MediaSelectorSettings
 */
@Immutable
@Serializable
data class MediaPreference
@SerializationOnly
constructor(
    /**
     * 精确匹配字幕组
     */
    val alliance: String? = null,
    /**
     * 若精确匹配失败, 则使用正则表达式匹配, 将会选择首个匹配
     */
    val alliancePatterns: List<String>? = null,

    val resolution: String? = null,
    val fallbackResolutions: List<String>? = listOf(
        Resolution.R2160P,
        Resolution.R1440P,
        Resolution.R1080P,
        Resolution.R720P,
    ).map { it.id },

    /**
     * 优先使用的字幕语言
     */
    val subtitleLanguageId: String? = null,
    /**
     * 在线播放时, 若 [subtitleLanguageId] 未匹配, 则按照此列表的顺序选择字幕语言.
     * 缓存时则只会缓存此列表中的字幕语言.
     *
     * 为 `null` 表示任意.
     */
    val fallbackSubtitleLanguageIds: List<String>? = listOf(
        SubtitleLanguage.ChineseSimplified,
        SubtitleLanguage.ChineseTraditional,
    ).map { it.id },
    /**
     * 是否显示没有解析到字幕的资源, 这可能是本身是生肉, 也可能是字幕未匹配到. 是生肉的可能性更高.
     */
    val showWithoutSubtitle: Boolean = false,

    /**
     * 优先使用的媒体源. Can be [ANY_FILTER]
     */
    val mediaSourceId: String? = null,
    @Deprecated("Only for migration") // since 3.1.0-beta03
    val fallbackMediaSourceIds: List<String>? = null,
    val preferredSubtitleLabel: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val subtitleDisabled: Boolean = false,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    @OptIn(SerializationOnly::class)
    companion object {
        /**
         * With default values
         * @see Empty
         */
        val PlatformDefault = MediaPreference()

        /**
         * Prefer nothing
         */
        val Empty = MediaPreference(
            mediaSourceId = null,
            fallbackSubtitleLanguageIds = null,
            fallbackResolutions = null,
        )

        /**
         * Prefer anything
         */
        @TestOnly
        val Any = MediaPreference.Empty.copy(
            alliance = ANY_FILTER,
            resolution = ANY_FILTER,
            subtitleLanguageId = ANY_FILTER,
            mediaSourceId = ANY_FILTER,
            showWithoutSubtitle = true,
        )

        const val ANY_FILTER = "*"
    }

    fun merge(other: MediaPreference): MediaPreference {
        if (other == Empty) return this
        if (this == Empty) return other
        @OptIn(SerializationOnly::class)
        return MediaPreference(
            alliance = other.alliance ?: alliance,
            alliancePatterns = other.alliancePatterns ?: alliancePatterns,
            resolution = other.resolution ?: resolution,
            subtitleLanguageId = other.subtitleLanguageId ?: subtitleLanguageId,
            fallbackSubtitleLanguageIds = other.fallbackSubtitleLanguageIds ?: fallbackSubtitleLanguageIds,
            mediaSourceId = other.mediaSourceId ?: mediaSourceId,
            fallbackResolutions = other.fallbackResolutions ?: fallbackResolutions,
        )
    }
}
