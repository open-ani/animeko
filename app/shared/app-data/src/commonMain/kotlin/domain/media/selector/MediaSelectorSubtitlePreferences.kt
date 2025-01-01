/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.domain.media.selector.SubtitleKindPreference.HIDE
import me.him188.ani.app.domain.media.selector.SubtitleKindPreference.NORMAL
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.collections.EnumMap
import me.him188.ani.utils.platform.collections.ImmutableEnumMap
import me.him188.ani.utils.platform.currentPlatform
import kotlin.jvm.JvmInline

/**
 * 用于针对各个平台的播放器缺陷，调整选择资源的优先级
 * @see SubtitleKind
 * @since 3.7
 */ // #615
@JvmInline
value class MediaSelectorSubtitlePreferences(
    val values: EnumMap<SubtitleKind, SubtitleKindPreference>
) {
    operator fun get(kind: SubtitleKind): SubtitleKindPreference = values[kind]

    fun copy(
        values: EnumMap<SubtitleKind, SubtitleKindPreference> = this.values,
    ): MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences(values)

    companion object {
        /**
         * 所有类型都正常选择. 用于测试.
         */
        val AllNormal = MediaSelectorSubtitlePreferences(
            ImmutableEnumMap { NORMAL },
        )

        val CurrentPlatform by lazy {
            forPlatform()
        }

        fun forPlatform(platform: Platform = currentPlatform()): MediaSelectorSubtitlePreferences {
            // 对于缺陷列表, 查看 https://github.com/open-ani/ani/issues/615
            val map = when (platform) {
                is Platform.Linux, // TODO: check linux MediaSelectorSubtitlePreferences
                is Platform.MacOS -> ImmutableEnumMap<SubtitleKind, _> {
                    when (it) {
                        SubtitleKind.EMBEDDED -> NORMAL
                        SubtitleKind.CLOSED -> HIDE
                        SubtitleKind.EXTERNAL_PROVIDED -> NORMAL
                        SubtitleKind.EXTERNAL_DISCOVER -> HIDE
                        SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER -> HIDE
                    }
                }

                is Platform.Windows -> ImmutableEnumMap<SubtitleKind, _> {
                    when (it) {
                        SubtitleKind.EMBEDDED -> NORMAL
                        SubtitleKind.CLOSED -> NORMAL
                        SubtitleKind.EXTERNAL_PROVIDED -> NORMAL
                        SubtitleKind.EXTERNAL_DISCOVER -> HIDE
                        SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER -> NORMAL
                    }
                }

                is Platform.Android -> ImmutableEnumMap<SubtitleKind, _> {
                    when (it) {
                        SubtitleKind.EMBEDDED -> NORMAL
                        SubtitleKind.CLOSED -> HIDE
                        SubtitleKind.EXTERNAL_PROVIDED -> NORMAL
                        SubtitleKind.EXTERNAL_DISCOVER -> HIDE
                        SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER -> NORMAL
                    }
                }

                // TODO("MediaSelectorSubtitleKindPreferences.Companion.forPlatform for ios")
                Platform.Ios -> ImmutableEnumMap<SubtitleKind, _> {
                    when (it) {
                        SubtitleKind.EMBEDDED -> NORMAL
                        SubtitleKind.CLOSED -> NORMAL
                        SubtitleKind.EXTERNAL_PROVIDED -> NORMAL
                        SubtitleKind.EXTERNAL_DISCOVER -> NORMAL
                        SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER -> NORMAL
                    }
                }
            }

            return MediaSelectorSubtitlePreferences(map)
        }
    }
}

enum class SubtitleKindPreference {
    /**
     * 正常情况, 不做任何调整
     */
    NORMAL,

    /**
     * 调整为低优先级展示和自动选择
     */
    LOW_PRIORITY,

    /**
     * 完全隐藏, 无法被选择
     */
    HIDE,
}
