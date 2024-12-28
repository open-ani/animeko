/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.serialization.Serializable
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.subject.episode.EpisodeVideoDefaults
import me.him188.ani.app.ui.subject.episode.TAG_DANMAKU_SETTINGS_SHEET
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultsPresentation
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettings
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettingsSideSheet
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettingsViewModel
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EditDanmakuRegexFilterSideSheet
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorSideSheet
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorState
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeVideoMediaSelectorSideSheet
import me.him188.ani.app.videoplayer.ui.VideoControllerState
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
    danmakuRegexFilterState: DanmakuRegexFilterState,
    expanded: Boolean,
    mediaSelectorState: MediaSelectorState,
    mediaSourceResultsPresentation: MediaSourceResultsPresentation,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    episodeSelectorState: EpisodeSelectorState,
    onRefreshMediaSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoSideSheets(
        controller = sheetsController,
        modifier,
    ) { page ->
        when (page) {
            EpisodeVideoSideSheetPage.PLAYER_SETTINGS -> {
                EpisodeVideoSettingsSideSheet(
                    onDismissRequest = { goBack() },
                    Modifier.testTag(TAG_DANMAKU_SETTINGS_SHEET),
                    title = { Text(text = "弹幕设置") },
                    closeButton = {
                        IconButton(onClick = { goBack() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭")
                        }
                    },
                ) {
                    EpisodeVideoSettings(
                        remember { EpisodeVideoSettingsViewModel() },
                        onManageRegexFilters = {
                            sheetsController.navigateTo(EpisodeVideoSideSheetPage.EDIT_DANMAKU_REGEX_FILTER)
                        },
                    )
                }
            }

            EpisodeVideoSideSheetPage.EDIT_DANMAKU_REGEX_FILTER -> {
                EditDanmakuRegexFilterSideSheet(
                    state = danmakuRegexFilterState,
                    onDismissRequest = { goBack() },
                    expanded = expanded,
                )
            }

            EpisodeVideoSideSheetPage.MEDIA_SELECTOR -> {
                EpisodeVideoMediaSelectorSideSheet(
                    mediaSelectorState,
                    mediaSourceResultsPresentation,
                    mediaSourceInfoProvider,
                    onDismissRequest = { goBack() },
                    onRefresh = onRefreshMediaSources,
                )
            }

            EpisodeVideoSideSheetPage.EPISODE_SELECTOR -> {
                EpisodeSelectorSideSheet(
                    episodeSelectorState,
                    onDismissRequest = { goBack() },
                )
            }
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
