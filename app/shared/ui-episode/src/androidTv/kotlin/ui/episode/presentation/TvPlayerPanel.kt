/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_filter_collection_state
import me.him188.ani.app.ui.lang.episode_comments
import me.him188.ani.app.ui.lang.subject_episode_danmaku_settings_title
import me.him188.ani.app.ui.lang.video_player_video_enhancement
import me.him188.ani.app.ui.lang.watch_together_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Keep locale-dependent display text out of the player's state flows and focus identities.
internal val TvPlayerPanel.title: String
    @Composable get() = stringResource(titleResource)

/**
 * 浮出面板种类与内容宽度 (atv-architecture.md §8.3 功能药丸).
 */
enum class TvPlayerPanelPresentation { Popup, Sidebar }

enum class TvPlayerPanel(
    val titleResource: StringResource,
    val icon: ImageVector,
    val width: Dp,
    val presentation: TvPlayerPanelPresentation = TvPlayerPanelPresentation.Popup,
) {
    Collection(Lang.cache_filter_collection_state, Icons.Rounded.Bookmark, 248.dp),
    Comments(Lang.episode_comments, Icons.Rounded.Comment, 400.dp, TvPlayerPanelPresentation.Sidebar),
    DanmakuSettings(Lang.subject_episode_danmaku_settings_title, Icons.Rounded.Tune, 400.dp, TvPlayerPanelPresentation.Sidebar),
    VideoSettings(Lang.video_player_video_enhancement, Icons.Rounded.AutoAwesome, 400.dp),
    Together(Lang.watch_together_title, Icons.Rounded.Groups, 360.dp, TvPlayerPanelPresentation.Sidebar),
}
