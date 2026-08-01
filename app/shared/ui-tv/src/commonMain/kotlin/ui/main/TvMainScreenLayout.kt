/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.session.TvNavigationRailDefaults
import me.him188.ani.app.ui.foundation.session.TvNavigationSideRail
import me.him188.ani.app.ui.foundation.session.TvRailAvatarAction
import me.him188.ani.app.ui.foundation.session.buildTvRailItems
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.playback_history_title
import me.him188.ani.app.ui.lang.settings_account_popup_edit_profile
import me.him188.ani.app.ui.lang.settings_account_popup_login_register
import me.him188.ani.app.ui.lang.settings_account_popup_logout
import me.him188.ani.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource

/**
 * TV 主页外壳: 可展开左侧边栏 (头像置顶 → 用户信息页, 搜索/探索/收藏/缓存/设置),
 * 取代旧的 NavigationRail; 各页面在 TV 上隐藏自身顶栏 (纯 chrome), 由本侧边栏统一承载.
 * 侧边栏收起态为一列图标, 内容整体右移让开; 聚焦展开时图标右侧浮出文字并压一层渐变遮罩, 内容不重排.
 * 与详情页侧边栏共用同一实现 (TvNavigationSideRail).
 */
@Composable
fun TvMainScreenLayout(
    page: MainScreenPage,
    selfInfo: SelfInfoUiState,
    navigator: AniNavigator,
    onNavigateToPage: (MainScreenPage) -> Unit,
    onNavigateToSettings: (tab: SettingsTab?) -> Unit,
    onNavigateToSearch: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable () -> Unit,
) {
    // 内容区是一个独立 focusGroup, 由 contentFocus 精确定位: 进入它就落到其中第一个可聚焦项.
    // 关键: 外层 Box 用 onEnter 把"从 NavHost 外部进来的任何 enter"(尤其是全局兜底那种无方向
    // 的 requestFocus) 直接重定向到 contentFocus —— 绕开侧边栏 (侧边栏的 onEnter 对非 Left
    // 一律 cancelFocus, 会把无方向 enter 整个取消掉, 焦点就哪都落不下去). 侧边栏只能靠内容区
    // 里主动按左键进入. 切页/丢焦点不在此单独补丁: 侧边栏点击后 clearFocus, 由 AniAppContent
    // 的全局兜底反复 requestFocus, 经此处 onEnter 稳定落进内容区.
    val contentFocus = remember { FocusRequester() }
    // TV 返回键: 只有探索页可直接退出; 收藏/缓存等其它页按返回统一回到探索页
    BackHandler(enabled = page != MainScreenPage.Exploration) {
        onNavigateToPage(MainScreenPage.Exploration)
    }
    Box(
        // 全屏背景由本外层 Box 统一绘制, 主壳内各页 (探索/收藏/缓存) 在 TV 上把自身 Scaffold
        // 设透明透出此色 (搜索/设置是独立页面, 不受影响); 颜色与侧边栏展开面板一致.
        modifier.fillMaxSize().background(AniThemeDefaults.shellBackgroundColor)
            // 进入 Main 的焦点一律先送进内容区 (而非侧边栏)
            .focusProperties { onEnter = { contentFocus.requestFocus() } }
            .focusGroup(),
    ) {
        Box(
            Modifier.fillMaxSize()
                .padding(start = TvNavigationRailDefaults.CollapsedWidth)
                .focusRequester(contentFocus)
                .focusGroup(),
        ) {
            pageContent()
        }
        // 头像关联动作 (焦点在头像上时于其上方浮现): 按登录态切换
        val loggedIn = selfInfo.selfInfo != null && selfInfo.isSessionValid != false
        val avatarActions = buildList {
            if (loggedIn) {
                add(
                    TvRailAvatarAction(
                        Icons.Outlined.Edit,
                        stringResource(Lang.settings_account_popup_edit_profile),
                    ) { onNavigateToSettings(SettingsTab.PROFILE) },
                )
                add(
                    TvRailAvatarAction(
                        Icons.Outlined.History,
                        stringResource(Lang.playback_history_title),
                    ) { navigator.navigatePlaybackHistory() },
                )
                add(
                    TvRailAvatarAction(
                        Icons.AutoMirrored.Outlined.Logout,
                        stringResource(Lang.settings_account_popup_logout),
                    ) { onLogout() },
                )
            } else {
                add(
                    TvRailAvatarAction(
                        Icons.AutoMirrored.Outlined.Login,
                        stringResource(Lang.settings_account_popup_login_register),
                    ) { navigator.navigateEmailLoginStart() },
                )
                add(
                    TvRailAvatarAction(
                        Icons.Outlined.History,
                        stringResource(Lang.playback_history_title),
                    ) { navigator.navigatePlaybackHistory() },
                )
            }
        }
        TvNavigationSideRail(
            selfInfo = selfInfo,
            avatarActions = avatarActions,
            onAvatarClick = {
                if (loggedIn) onNavigateToSettings(SettingsTab.PROFILE) else navigator.navigateEmailLoginStart()
            },
            // 返回/右键: 还原回进入侧边栏之前内容区最后聚焦的元素
            onExitFocus = { runCatching { contentFocus.requestFocus() } },
            items = buildTvRailItems(
                onSearch = onNavigateToSearch,
                onNavigateToPage = onNavigateToPage,
                onSettings = { onNavigateToSettings(null) },
            ),
            modifier = Modifier.fillMaxHeight(),
        )
    }
}
