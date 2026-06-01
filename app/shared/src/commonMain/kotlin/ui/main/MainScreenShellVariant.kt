/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.ui.user.SelfInfoUiState

/**
 * 主页外壳变体: 应用入口可提供一个替代外壳 (如遥控器形态的沉浸式侧边栏布局),
 * 取代默认的 [AniNavigationSuiteLayout][me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteLayout].
 *
 * 页面内容 ([pageContent], 三个 tab 的 AnimatedContent) 由 [MainScreen] 构建, 外壳只负责摆放.
 * 未提供 (null, 默认) 时使用默认外壳.
 */
fun interface MainScreenShellVariant {
    @Composable
    fun Shell(
        page: MainScreenPage,
        selfInfo: SelfInfoUiState,
        navigator: AniNavigator,
        onNavigateToPage: (MainScreenPage) -> Unit,
        onNavigateToSettings: (tab: SettingsTab?) -> Unit,
        onNavigateToSearch: () -> Unit,
        onLogout: () -> Unit,
        modifier: Modifier,
        pageContent: @Composable () -> Unit,
    )
}

val LocalMainScreenShellVariant = staticCompositionLocalOf<MainScreenShellVariant?> { null }
