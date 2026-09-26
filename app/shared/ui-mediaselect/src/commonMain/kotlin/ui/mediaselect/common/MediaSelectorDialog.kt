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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import me.him188.ani.app.ui.foundation.dialogs.PlatformDialogProperties
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.mediaselect.MediaSelectorLayoutDefaults

/**
 * 手动查找 / BT 资源的容器布局. 不是 Dialog: 铺满父容器, 自画 scrim (点击 = [onDismissRequest]),
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
 * 只给宽屏详情页 (BT 居中对话框) 与非全屏状态下的播放器; 全屏播放器用 [MediaSelectorDialogLayout] 直接铺在 rhsSheet 槽.
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
            usePlatformInsets = false,
            animateTransition = false,
        ),
    ) {
        MediaSelectorDialogLayout(
            onDismissRequest = onDismissRequest,
            windowInsets = AniWindowInsets.safeDrawing,
            modifier = modifier,
            content = content,
        )
    }
}

object MediaSelectorDialogTestTags {
    const val SURFACE = "media_selector_dialog_surface"
}
