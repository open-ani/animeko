/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.subject.episode.EpisodeVideoDefaults
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettings
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettingsViewModel
import me.him188.ani.app.ui.subject.episode.video.settings.SideSheetLayout
import me.him188.ani.app.ui.subject.episode.video.sidesheet.DanmakuRegexFilterSettings
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.VideoSideSheetScope
import me.him188.ani.app.videoplayer.ui.VideoSideSheets
import me.him188.ani.app.videoplayer.ui.VideoSideSheetsController
import me.him188.ani.app.videoplayer.ui.hasPageAsState
import me.him188.ani.app.videoplayer.ui.rememberAlwaysOnRequester
import me.him188.ani.app.videoplayer.ui.rememberVideoSideSheetsController
import me.him188.ani.danmaku.ui.DanmakuConfig

/**
 * See extensions on [EpisodeVideoSideSheets] for sheet implementations.
 */
@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoDefaults.SideSheets(
    sheetsController: VideoSideSheetsController<EpisodeVideoSideSheetPage> = rememberVideoSideSheetsController(),
    playerControllerState: PlayerControllerState,
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

    val alwaysOnRequester = rememberAlwaysOnRequester(playerControllerState, "sideSheets")
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

@Serializable
enum class DanmakuSettingsPage {
    MAIN,
    REGEX_FILTER
}

object EpisodeVideoSideSheets {

    private val sheetModifier = Modifier
        .desktopTitleBarPadding()
        .statusBarsPadding()

    @Composable
    fun DanmakuSettingsSheet(

        onDismissRequest: () -> Unit,
        onNavigateToFilterSettings: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val viewModel = remember { EpisodeVideoSettingsViewModel() }

        SideSheetLayout(
            title = { Text(text = "弹幕设置") },
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            closeButton = {
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            },
        ) {
            EpisodeVideoSettings(
                viewModel,
                onNavigateToFilterSettings,
            ) // TODO: Extract EpisodeVideoSettingsViewModel from DanmakuSettingsSheet
        }
    }

    @Composable
    fun DanmakuSettingsNavigatorSheet(
        expanded: Boolean,
        state: DanmakuRegexFilterState,
        onDismissRequest: () -> Unit,
        onNavigateToFilterSettings: () -> Unit,
    ) {
        if (expanded) {
            DanmakuSettingsSheet(
                onDismissRequest = onDismissRequest,
                onNavigateToFilterSettings = onNavigateToFilterSettings,
            )
            return
        }

        var currentPage by remember { mutableStateOf(DanmakuSettingsPage.MAIN) }

        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            modifier = sheetModifier,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f),
            ) {
                when (currentPage) {
                    DanmakuSettingsPage.MAIN -> {
                        val viewModel = remember { EpisodeVideoSettingsViewModel() }
                        EpisodeVideoSettings(
                            viewModel,
                            onNavigateToFilterSettings = { currentPage = DanmakuSettingsPage.REGEX_FILTER },
                        )
                    }

                    DanmakuSettingsPage.REGEX_FILTER -> {
                        DanmakuRegexFilterSettings(
                            state = state,
                            onDismissRequest = { currentPage = DanmakuSettingsPage.MAIN },
                            expanded = false,
                        )
                    }
                }
            }
        }
    }
}


// todo: shit
@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoSideSheets.DanmakuSettingsSheet(
    danmakuConfig: DanmakuConfig,
    setDanmakuConfig: (config: DanmakuConfig) -> Unit,
    enableRegexFilter: Boolean,
    onNavigateToFilterSettings: () -> Unit,
    switchDanmakuRegexFilterCompletely: () -> Unit,

    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SideSheetLayout(
        title = { Text(text = "弹幕设置") },
        onDismissRequest = onDismissRequest,
        modifier,
        closeButton = {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭")
            }
        },
    ) {
        EpisodeVideoSettings(
            danmakuConfig,
            setDanmakuConfig,
            enableRegexFilter,
            onNavigateToFilterSettings,
            switchDanmakuRegexFilterCompletely,
        )
    }
}
