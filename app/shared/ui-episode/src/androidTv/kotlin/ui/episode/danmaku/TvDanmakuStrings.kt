/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode.danmaku

import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.episode.danmaku.renderDanmakuServiceId
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.episode_danmaku_match_approximate
import me.him188.ani.app.ui.lang.episode_danmaku_match_with_count
import me.him188.ani.app.ui.lang.episode_danmaku_matched_current
import me.him188.ani.app.ui.lang.subject_episode_danmaku_match_none
import me.him188.ani.app.ui.lang.subject_episode_video_settings_text_conversion_hong_kong
import me.him188.ani.app.ui.lang.subject_episode_video_settings_text_conversion_original
import me.him188.ani.app.ui.lang.subject_episode_video_settings_text_conversion_simplified
import me.him188.ani.app.ui.lang.subject_episode_video_settings_text_conversion_taiwan
import me.him188.ani.app.ui.lang.subject_episode_video_settings_text_conversion_traditional
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import org.jetbrains.compose.resources.stringResource

internal val TvDanmakuOrigin.name: String
    @Composable get() = renderDanmakuServiceId(serviceId)

/**
 * [DanmakuTextConversion] 的显示文本.
 *
 * 手机版把它放在 ui-episode 的 DanmakuSources.kt (手机 UI 文件) 里, TV 不 import 手机 UI 树,
 * 因此同 [matchDescription] 一样在这里自行映射同一套 Lang key.
 */
internal val DanmakuTextConversion.displayText: String
    @Composable get() = stringResource(
        when (this) {
            DanmakuTextConversion.ORIGINAL -> Lang.subject_episode_video_settings_text_conversion_original
            DanmakuTextConversion.SIMPLIFIED -> Lang.subject_episode_video_settings_text_conversion_simplified
            DanmakuTextConversion.TRADITIONAL -> Lang.subject_episode_video_settings_text_conversion_traditional
            DanmakuTextConversion.TAIWAN -> Lang.subject_episode_video_settings_text_conversion_taiwan
            DanmakuTextConversion.HONG_KONG -> Lang.subject_episode_video_settings_text_conversion_hong_kong
        },
    )

internal val TvDanmakuOrigin.matchDescription: String
    @Composable get() {
        val description = when (val method = match) {
            is DanmakuMatchMethod.Exact -> "${method.subjectTitle} · ${method.episodeTitle}"
            is DanmakuMatchMethod.ExactSubjectFuzzyEpisode -> stringResource(Lang.episode_danmaku_match_approximate, method.subjectTitle, method.episodeTitle)
            is DanmakuMatchMethod.Fuzzy -> stringResource(Lang.episode_danmaku_match_approximate, method.subjectTitle, method.episodeTitle)
            is DanmakuMatchMethod.ExactId -> stringResource(Lang.episode_danmaku_matched_current)
            DanmakuMatchMethod.NoMatch -> stringResource(Lang.subject_episode_danmaku_match_none)
        }
        return stringResource(Lang.episode_danmaku_match_with_count, description, count)
    }
