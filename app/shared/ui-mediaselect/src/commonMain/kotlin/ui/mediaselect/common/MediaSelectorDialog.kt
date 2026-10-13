/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import me.him188.ani.app.ui.foundation.dialogs.PlatformDialogProperties
import me.him188.ani.app.ui.foundation.effects.DialogSystemBarsAppearance
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.Zero
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_mode_bt
import me.him188.ani.app.ui.lang.media_selector_mode_manual
import me.him188.ani.app.ui.lang.media_selector_sources
import me.him188.ani.app.ui.lang.subject_episode_close_selector
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediaselect.MediaSelectorLayoutDefaults
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.app.ui.mediaselect.bt.BtResourcesPage
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowsePage
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowseState
import me.him188.ani.app.ui.mediaselect.needsContainer
import me.him188.ani.datasources.api.source.MediaFetchRequest
import org.jetbrains.compose.resources.stringResource

/**
 * 侧边栏太矮时手动查找 / BT 资源的容器布局 (见 [needsContainer]). 不是 Dialog: 铺满父容器, 自画 scrim (点击 = [onDismissRequest]),
 * Surface 上拦截点击不落到底层.
 *
 * `maxHeight < CompactDialogMaxHeight` → Surface 铺满 (RectangleShape, 内容避开 [windowInsets]), content(compact = true);
 * 否则居中 Surface, 尺寸 min(DialogMaxWidth, maxWidth − DialogMargin) × min(DialogMaxHeight, maxHeight − DialogMargin), shape extraLarge, content(compact = false).
 * 容器色 BottomSheetDefaults.ContainerColor. 不处理返回键 (宿主自己放 BackHandler).
 *
 * @param windowInsets 铺满分支内容要避开的 insets: 全屏播放器传 LocalVideoScaffoldSheetWindowInsets.current (video-player 模块的 CompositionLocal, 本模块不依赖它),
 *   Dialog 包装传 AniWindowInsets.safeDrawing.
 * @param content `compact` = true 时宿主应画单行顶栏 (用页面的 inlineTitle 槽); false 时画 TopAppBar (标题 + 模式 chip + X, `windowInsets = WindowInsets.Zero`).
 */
@Composable
fun MediaSelectorDialogLayout(
    onDismissRequest: () -> Unit,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
    content: @Composable (compact: Boolean) -> Unit,
) {
    val scrimColor = DrawerDefaults.scrimColor
    BoxWithConstraints(modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            ),
        ) {
            drawRect(color = scrimColor)
        }
        val interceptClicks = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
        )
        val compact = maxHeight < MediaSelectorLayoutDefaults.CompactDialogMaxHeight
        if (compact) {
            Surface(
                Modifier.fillMaxSize().then(interceptClicks).testTag(MediaSelectorDialogTestTags.SURFACE),
                shape = RectangleShape,
                color = BottomSheetDefaults.ContainerColor,
            ) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
                    content(true)
                }
            }
        } else {
            val width = min(MediaSelectorLayoutDefaults.DialogMaxWidth, maxWidth - MediaSelectorLayoutDefaults.DialogMargin)
            val height = min(MediaSelectorLayoutDefaults.DialogMaxHeight, maxHeight - MediaSelectorLayoutDefaults.DialogMargin)
            Surface(
                Modifier.align(Alignment.Center).size(width, height).then(interceptClicks)
                    .testTag(MediaSelectorDialogTestTags.SURFACE),
                shape = MaterialTheme.shapes.extraLarge,
                color = BottomSheetDefaults.ContainerColor,
            ) {
                content(false)
            }
        }
    }
}

/**
 * 窗口级容器: Dialog 内放 [MediaSelectorDialogLayout] (windowInsets = AniWindowInsets.safeDrawing). 返回键 / 点外部都走 [onDismissRequest].
 * `animateTransition = false` 是必须的: CMP 1.11+ 的 Dialog 默认 scale-in 作用于整个内容, fillMaxSize 的 scrim 会从中心缩放出来.
 * Dialog 窗口铺满整屏 (Android 上 `decorFitsSystemWindows = false`, 含挖孔与系统栏区域), 由内容自己避开 insets;
 * 否则窗口本身给系统栏和横屏挖孔留边, 铺满分支的面板盖不住这几条, 底下的视频和页面从边上露出来.
 * 系统栏图标颜色随之由 Dialog 窗口决定, 按面板深浅设置 ([DialogSystemBarsAppearance]).
 * 给非全屏的播放器与详情页侧边栏; 全屏播放器用 [MediaSelectorDialogLayout] 直接铺在 rhsSheet 槽.
 */
@Composable
fun MediaSelectorDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (compact: Boolean) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = PlatformDialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            usePlatformInsets = false,
            animateTransition = false,
        ),
    ) {
        // 窗口铺到系统栏下面, 图标颜色要跟着面板深浅走.
        DialogSystemBarsAppearance(lightBars = BottomSheetDefaults.ContainerColor.luminance() > 0.5f)
        MediaSelectorDialogLayout(
            onDismissRequest = onDismissRequest,
            windowInsets = AniWindowInsets.safeDrawing,
            modifier = modifier,
            content = content,
        )
    }
}

/**
 * 容器 ([MediaSelectorDialog] / [MediaSelectorDialogLayout]) 里的手动查找与 BT 页, 播放器与详情页共用.
 * 自动匹配总在侧边栏, [mode] 为 AUTO 时不画 (宿主先回到侧边栏).
 *
 * @param compact 容器铺满 (可用高度 < [MediaSelectorLayoutDefaults.CompactDialogMaxHeight]) 时为 true: 不画 TopAppBar,
 *   由页面按 inlineTitle 槽画单行顶栏 (X + 模式名), 没有模式 chip; 否则画 TopAppBar (「数据源」+ 模式 chip + X).
 * @param onModeChange 容器里的模式 chip. 宿主决定新模式留在容器还是回侧边栏.
 * @param onClose X: 宿主回到侧边栏.
 * @param onPlayed 选中播放后: 宿主把容器和侧边栏都关掉.
 */
@Composable
fun MediaSelectorDialogContent(
    mode: MediaSelectorMode,
    compact: Boolean,
    onModeChange: (MediaSelectorMode) -> Unit,
    onClose: () -> Unit,
    onPlayed: () -> Unit,
    mediaSelectorState: MediaSelectorState,
    sourceResults: MediaSourceResultListPresentation,
    watching: WatchingEpisode?,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    onRestartSource: (instanceId: String) -> Unit,
    manualBrowseState: ManualBrowseState,
    modifier: Modifier = Modifier,
) {
    val closeSelectorText = stringResource(Lang.subject_episode_close_selector)
    val closeButton: @Composable () -> Unit = {
        IconButton(onClick = onClose, Modifier.testTag(MediaSelectorDialogTestTags.CLOSE)) {
            Icon(Icons.Rounded.Close, contentDescription = closeSelectorText)
        }
    }
    val topBarTitle = stringResource(Lang.media_selector_sources)
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            title = { Text(topBarTitle) },
            actions = {
                MediaSelectorModeChip(mode, onModeChange, showBt = sourceResults.btSources.isNotEmpty())
                closeButton()
            },
            windowInsets = WindowInsets.Zero,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BottomSheetDefaults.ContainerColor),
        )
    }
    val inlineTitle: @Composable (title: String) -> Unit = { title ->
        closeButton()
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
    when (mode) {
        MediaSelectorMode.MANUAL -> {
            val title = stringResource(Lang.media_selector_mode_manual)
            ManualBrowsePage(
                manualBrowseState,
                watching,
                onPlayed = onPlayed,
                topBar = topBar,
                modifier = modifier.fillMaxSize(),
                closeButton = closeButton,
                inlineTitle = if (compact) {
                    { inlineTitle(title) }
                } else {
                    null
                },
            )
        }

        MediaSelectorMode.BT -> {
            Column(modifier.fillMaxSize()) {
                if (!compact) {
                    topBar()
                }
                val title = stringResource(Lang.media_selector_mode_bt)
                BtResourcesPage(
                    mediaSelectorState,
                    sourceResults,
                    watching,
                    fetchRequest,
                    onFetchRequestChange,
                    onClickItem = {
                        mediaSelectorState.select(it)
                        onPlayed()
                    },
                    onRestartSource = onRestartSource,
                    modifier = Modifier.weight(1f),
                    inlineTitle = if (compact) {
                        { inlineTitle(title) }
                    } else {
                        null
                    },
                )
            }
        }

        MediaSelectorMode.AUTO -> {}
    }
}

object MediaSelectorDialogTestTags {
    const val SURFACE = "media_selector_dialog_surface"
    const val CLOSE = "media_selector_dialog_close"
}
