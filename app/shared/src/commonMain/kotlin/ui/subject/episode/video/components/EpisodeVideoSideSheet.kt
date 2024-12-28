/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable
import me.him188.ani.app.ui.subject.episode.EpisodeVideoDefaults
import me.him188.ani.app.videoplayer.ui.VideoControllerState
import me.him188.ani.app.videoplayer.ui.VideoSideSheetScope
import me.him188.ani.app.videoplayer.ui.VideoSideSheets
import me.him188.ani.app.videoplayer.ui.VideoSideSheetsController
import me.him188.ani.app.videoplayer.ui.hasPageAsState
import me.him188.ani.app.videoplayer.ui.rememberAlwaysOnRequester
import me.him188.ani.app.videoplayer.ui.rememberVideoSideSheetsController

@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoDefaults.SideSheets(
    sheetsController: VideoSideSheetsController<EpisodeVideoSideSheetPage> = rememberVideoSideSheetsController(),
    videoControllerState: VideoControllerState,
    playerSettingsPage: @Composable VideoSideSheetScope.() -> Unit,
    editDanmakuRegexFilterPage: @Composable VideoSideSheetScope.() -> Unit,
    mediaSelectorPage: @Composable VideoSideSheetScope.() -> Unit,
    episodeSelectorPage: @Composable VideoSideSheetScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoSideSheets(
        controller = sheetsController,
        modifier,
    ) { page ->
        when (page) {
            EpisodeVideoSideSheetPage.PLAYER_SETTINGS -> playerSettingsPage()
            EpisodeVideoSideSheetPage.EDIT_DANMAKU_REGEX_FILTER -> editDanmakuRegexFilterPage()
            EpisodeVideoSideSheetPage.MEDIA_SELECTOR -> mediaSelectorPage()
            EpisodeVideoSideSheetPage.EPISODE_SELECTOR -> episodeSelectorPage()
        }
    }

    val alwaysOnRequester = rememberAlwaysOnRequester(videoControllerState, "sideSheets")
    val isPage by sheetsController.hasPageAsState()
    if (isPage) {
        DisposableEffect(true) {
            alwaysOnRequester.request()
            onDispose {
                alwaysOnRequester.cancelRequest()
            }
        }
    }
}


@Serializable
enum class EpisodeVideoSideSheetPage {
    PLAYER_SETTINGS,
    EDIT_DANMAKU_REGEX_FILTER,
    MEDIA_SELECTOR,
    EPISODE_SELECTOR,
}
