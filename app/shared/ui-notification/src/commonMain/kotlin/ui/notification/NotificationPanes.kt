/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.PaneScope
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton

/**
 * 宽屏双栏中的详情栏: 透明顶栏和内容. 详情栏本身的背景由 `AniListDetailPaneScaffold` 绘制.
 *
 * @param onNavigateBack 窗口变窄 (只显示详情栏) 时的返回按钮. 为 `null` 时不显示.
 * @param actions 顶栏右侧的操作.
 * @param content 需要自行使用 [PaneScope.paneContentPadding] 和 [PaneScope.paneWindowInsetsPadding].
 */
@Composable
internal fun PaneScope.NotificationDetailPaneLayout(
    title: String,
    onNavigateBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable PaneScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        AniTopAppBar(
            title = { AniTopAppBarDefaults.Title(title) },
            navigationIcon = {
                if (onNavigateBack != null) {
                    BackNavigationIconButton(onNavigateBack)
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            enableFrostedGlass = false,
        )
        Box(
            Modifier
                .fillMaxSize()
                .consumeWindowInsets(paneContentWindowInsets.only(WindowInsetsSides.Top))
                .testTag(NotificationTestTags.DETAIL_PANE),
        ) {
            content()
        }
    }
}

/**
 * 宽屏中尚未选择条目时, 在 [NotificationDetailPaneLayout] 的内容区域中央显示的提示.
 *
 * @param modifier 作用于提示本身.
 */
@Composable
internal fun PaneScope.NotificationDetailPanePlaceholder(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        Modifier
            .paneContentPadding()
            .paneWindowInsetsPadding()
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        NotificationEmptyState(icon, title = null, modifier, supportingText = text)
    }
}
