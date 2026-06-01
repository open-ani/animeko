/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.him188.ani.app.ui.foundation.FOCUS_REQ_DELAY_MILLIS
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_danmaku_settings_title
import me.him188.ani.app.ui.lang.subject_episode_select_media_source
import me.him188.ani.app.ui.mediafetch.MediaSelectorView
import me.him188.ani.app.ui.foundation.widgets.AniCenteredPanelDialog
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.ui.subject.episode.EpisodeVideoDefaults
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.components.SideSheets
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettings
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettingsViewModel
import me.him188.ani.app.ui.subject.episode.video.sidesheet.DanmakuRegexFilterSettings
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorSheet
import me.him188.ani.app.videoplayer.ui.VideoSideSheetsController
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器内二级页 (数据源选择 / 选集 / 弹幕设置). 全部为 TV 半透明居中弹窗
 * ([AniCenteredPanelDialog], 返回键由 Dialog 自行消费): Dialog 是独立窗口,
 * 关闭时系统自动把焦点还给打开它的按钮; 窗口内的侧边 sheet 做不到这一点
 * (内容连同焦点一起被移除, 焦点悬空按键失效).
 */
@Composable
internal fun TvPlayerSideSheets(
    vm: EpisodeViewModel,
    sheetsController: VideoSideSheetsController<EpisodeVideoSideSheetPage>,
) {
    EpisodeVideoDefaults.SideSheets(
        sheetsController = sheetsController,
        playerControllerState = vm.playerControllerState,
        playerSettingsPage = {
            val viewModel = remember { EpisodeVideoSettingsViewModel() }
            // 初始焦点: 等一帧弹窗窗口就绪后送到第一个设置项 (沿用原侧边 sheet 的时序)
            val firstItemFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(FOCUS_REQ_DELAY_MILLIS)
                runCatching { firstItemFocusRequester.requestFocus() }
            }
            AniCenteredPanelDialog(
                onDismissRequest = { goBack() },
                title = { Text(stringResource(Lang.subject_episode_danmaku_settings_title)) },
                widthFraction = TV_PLAYER_SETTINGS_DIALOG_WIDTH_FRACTION,
            ) {
                EpisodeVideoSettings(
                    viewModel,
                    onNavigateToFilterSettings = {
                        sheetsController.navigateTo(EpisodeVideoSideSheetPage.EDIT_DANMAKU_REGEX_FILTER)
                    },
                    firstItemFocusRequester = firstItemFocusRequester,
                )
            }
        },
        editDanmakuRegexFilterPage = {
            AniCenteredPanelDialog(
                onDismissRequest = { goBack() },
                widthFraction = TV_PLAYER_SETTINGS_DIALOG_WIDTH_FRACTION,
            ) {
                // expanded = false (竖屏形态): 内容自带标题行、全宽透明铺开, 正好嵌进弹窗;
                // expanded = true 是带右侧悬浮容器的侧边 sheet 形态, 不适合弹窗内嵌
                DanmakuRegexFilterSettings(
                    state = vm.danmakuRegexFilterState,
                    onDismissRequest = { goBack() },
                    expanded = false,
                )
            }
        },
        mediaSelectorPage = {
            val pageState by vm.pageState.collectAsStateWithLifecycle()
            pageState?.let { page ->
                val (viewKind, onViewKindChange) = rememberSaveable {
                    mutableStateOf(page.initialMediaSelectorViewKind)
                }
                // TV: 半透明居中大弹窗 (与详情页各弹窗/缓存页选择器形态统一),
                // 视频画面经遮罩透出; 返回键由 Dialog 自行消费关闭
                AniCenteredPanelDialog(
                    onDismissRequest = { goBack() },
                    title = { Text(stringResource(Lang.subject_episode_select_media_source)) },
                ) {
                    MediaSelectorView(
                        page.mediaSelectorState,
                        viewKind,
                        onViewKindChange,
                        page.fetchRequest,
                        { vm.updateFetchRequest(it) },
                        page.mediaSourceResultListPresentation,
                        onRestartSource = { vm.restartSource(it) },
                        onRefresh = { vm.refreshFetch() },
                        // 固定占满弹窗高度: 筛选后条目变少时布局不跳动
                        modifier = Modifier.fillMaxSize(),
                        onClickItem = {
                            page.mediaSelectorState.select(it)
                            goBack()
                        },
                        singleLineFilter = true,
                    )
                }
            }
        },
        episodeSelectorPage = {
            EpisodeVideoSideSheets.EpisodeSelectorSheet(
                vm.episodeSelectorState,
                onDismissRequest = { goBack() },
            )
        },
    )
}

/** 弹幕设置/正则过滤弹窗宽度: 设置列表按侧边栏宽度设计, 用比数据源选择更窄的容器. */
private const val TV_PLAYER_SETTINGS_DIALOG_WIDTH_FRACTION = 0.45f
