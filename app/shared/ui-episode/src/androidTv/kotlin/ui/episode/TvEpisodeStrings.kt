/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.runtime.Composable
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.ui.episode.danmaku.renderDanmakuServiceId
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.episode_danmaku_match_approximate
import me.him188.ani.app.ui.lang.episode_danmaku_match_empty_query
import me.him188.ani.app.ui.lang.episode_danmaku_match_failed
import me.him188.ani.app.ui.lang.episode_danmaku_match_updated
import me.him188.ani.app.ui.lang.episode_danmaku_match_with_count
import me.him188.ani.app.ui.lang.episode_danmaku_matched_current
import me.him188.ani.app.ui.lang.media_selector_episode_info_failed
import me.him188.ani.app.ui.lang.media_selector_exclusion_complete_single_episode
import me.him188.ani.app.ui.lang.media_selector_exclusion_other_season
import me.him188.ani.app.ui.lang.media_selector_exclusion_possibly_unsupported
import me.him188.ani.app.ui.lang.media_selector_exclusion_sequel
import me.him188.ani.app.ui.lang.media_selector_item_no_subtitle
import me.him188.ani.app.ui.lang.media_selector_item_subject_title_mismatch
import me.him188.ani.app.ui.lang.subject_collection_collect
import me.him188.ani.app.ui.lang.subject_collection_doing
import me.him188.ani.app.ui.lang.subject_collection_done
import me.him188.ani.app.ui.lang.subject_collection_dropped
import me.him188.ani.app.ui.lang.subject_collection_on_hold
import me.him188.ani.app.ui.lang.subject_collection_wish
import me.him188.ani.app.ui.lang.subject_episode_danmaku_match_none
import me.him188.ani.app.ui.lang.subject_episode_default_title
import me.him188.ani.app.ui.lang.tv_player_following_host_hint
import me.him188.ani.app.ui.lang.video_player_operation_failed
import me.him188.ani.app.ui.lang.watch_together_title
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.stringResource

// Keep locale-dependent display text out of the player's state flows and focus identities.
internal val TvPlayerPanel.title: String
    @Composable get() = stringResource(titleResource)

internal val TvStripEpisode.sortLabel: String
    @Composable get() = stringResource(Lang.subject_episode_default_title, sort)

internal val TvEpisodeTitle.episodeLine: String
    @Composable get() = listOf(
        if (episodeSort.isBlank()) "" else stringResource(Lang.subject_episode_default_title, episodeSort),
        episodeName,
    ).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
internal fun UnifiedCollectionType.tvLabel(): String = stringResource(when (this) {
    UnifiedCollectionType.WISH -> Lang.subject_collection_wish
    UnifiedCollectionType.DOING -> Lang.subject_collection_doing
    UnifiedCollectionType.DONE -> Lang.subject_collection_done
    UnifiedCollectionType.ON_HOLD -> Lang.subject_collection_on_hold
    UnifiedCollectionType.DROPPED -> Lang.subject_collection_dropped
    UnifiedCollectionType.NOT_COLLECTED -> Lang.subject_collection_collect
})

@Composable
internal fun TvPlayerError.text(): String = stringResource(when (this) {
    TvPlayerError.SourceInfoUnavailable -> Lang.media_selector_episode_info_failed
    TvPlayerError.EmptyDanmakuQuery -> Lang.episode_danmaku_match_empty_query
    TvPlayerError.DanmakuSearchFailed -> Lang.episode_danmaku_match_failed
})

@Composable
internal fun TvPlayerMessage.text(): String = when (this) {
    TvPlayerMessage.FollowingHost -> stringResource(Lang.tv_player_following_host_hint, stringResource(Lang.watch_together_title))
    TvPlayerMessage.DanmakuMatched -> stringResource(Lang.episode_danmaku_match_updated)
    TvPlayerMessage.OperationFailed -> stringResource(Lang.video_player_operation_failed)
}

@Composable
internal fun MediaExclusionReason.description(): String = stringResource(when (this) {
    is MediaExclusionReason.SingleEpisodeForCompleteSubject -> Lang.media_selector_exclusion_complete_single_episode
    MediaExclusionReason.MediaWithoutSubtitle -> Lang.media_selector_item_no_subtitle
    MediaExclusionReason.UnsupportedByPlatformPlayer -> Lang.media_selector_exclusion_possibly_unsupported
    MediaExclusionReason.FromSequelSeason -> Lang.media_selector_exclusion_sequel
    MediaExclusionReason.FromSeriesSeason -> Lang.media_selector_exclusion_other_season
    MediaExclusionReason.SubjectNameMismatch -> Lang.media_selector_item_subject_title_mismatch
})

internal val TvDanmakuOrigin.name: String
    @Composable get() = renderDanmakuServiceId(serviceId)

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
