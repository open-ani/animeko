/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.source

import androidx.compose.runtime.Composable
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_exclusion_complete_single_episode
import me.him188.ani.app.ui.lang.media_selector_exclusion_other_season
import me.him188.ani.app.ui.lang.media_selector_exclusion_possibly_unsupported
import me.him188.ani.app.ui.lang.media_selector_exclusion_sequel
import me.him188.ani.app.ui.lang.media_selector_item_no_subtitle
import me.him188.ani.app.ui.lang.media_selector_item_subject_title_mismatch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MediaExclusionReason.description(): String = stringResource(when (this) {
    is MediaExclusionReason.SingleEpisodeForCompleteSubject -> Lang.media_selector_exclusion_complete_single_episode
    MediaExclusionReason.MediaWithoutSubtitle -> Lang.media_selector_item_no_subtitle
    MediaExclusionReason.UnsupportedByPlatformPlayer -> Lang.media_selector_exclusion_possibly_unsupported
    MediaExclusionReason.FromSequelSeason -> Lang.media_selector_exclusion_sequel
    MediaExclusionReason.FromSeriesSeason -> Lang.media_selector_exclusion_other_season
    MediaExclusionReason.SubjectNameMismatch -> Lang.media_selector_item_subject_title_mismatch
})
