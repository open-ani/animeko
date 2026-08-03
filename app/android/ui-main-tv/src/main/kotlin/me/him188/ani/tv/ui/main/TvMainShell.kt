/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.tv.ui.collection.TvCollectionScreen
import me.him188.ani.tv.ui.exploration.TvExplorationScreen
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.tv.ui.foundation.widgets.TvNavRailItem
import me.him188.ani.tv.ui.foundation.widgets.TvNavigationRailDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvNavigationSideRail
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor
import me.him188.ani.tv.ui.login.TvLoginScreen
import me.him188.ani.tv.ui.schedule.TvScheduleScreen
import me.him188.ani.tv.ui.search.TvSearchScreen
import me.him188.ani.tv.ui.search.TvSearchViewModel
import me.him188.ani.tv.ui.settings.TvSettingsScreen
import me.him188.ani.tv.ui.settings.TvSettingsViewModel

/** 主壳内容区: 探索/时间表/追番 + 搜索/登录/设置 (TV 额外 tab). */
private enum class TvShellContent { Search, Exploration, Schedule, Collection, Login, Settings }

/** 主壳焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvShellFocus : TvFocusKey {
    /** 侧边栏进入落点 ("探索"条目); 菜单键从任意位置直达. */
    Rail,
}

/**
 * TV 主壳. 布局对齐上游 PR#3217 的 MainScreen (TV 变体):
 * 左侧 [TvNavigationSideRail] (收起 48dp 图标列, 焦点进入展开) 浮于内容之上,
 * 内容区让开收起宽度; tab 间切换用 fade 转场.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvMainShell(modifier: Modifier = Modifier) {
    var content by rememberSaveable { mutableStateOf(TvShellContent.Exploration) }

    // 触屏设备上跑 TV 界面时强制键盘输入模式: touch mode 下 clickable 节点不参与
    // 键盘焦点 (requestFocus 恒 false), 遥控器/dpad 导航整个失效. 真 TV 永远非 touch mode.
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        inputModeManager.requestInputMode(InputMode.Keyboard)
    }

    // 返回语义: 非探索内容先回探索; 探索交给系统 (退出应用)
    BackHandler(enabled = content != TvShellContent.Exploration) {
        content = TvShellContent.Exploration
    }

    val navigator = LocalNavigator.current
    // rail 头像的登录态 (登录页状态层已复用手机 EmailLoginViewModel, 这里直取仓库)
    val selfInfo by remember { GlobalKoin.get<UserRepository>() }.selfInfoFlow.collectAsState(null)

    // 菜单键从页面任意位置直达侧边栏 (焦点落到"探索"条目, rail 随 hasFocus 自动展开):
    // rail 的常规进入是"按左", 焦点在卡片行深处时要连按多次, 菜单键是一步到位的快捷路径
    val focus = rememberTvFocusScope()
    focus.Resolver()

    Box(
        modifier
            .fillMaxSize()
            .background(tvShellBackgroundColor())
            .tvFocusHotkey(focus, Key.Menu to TvShellFocus.Rail),
    ) {
        // 内容区: 让开侧边栏收起态宽度
        Box(Modifier.fillMaxSize().padding(start = TvNavigationRailDefaults.CollapsedWidth)) {
            AnimatedContent(
                content,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tvShellContent",
            ) { current ->
                when (current) {
                    TvShellContent.Exploration -> TvExplorationScreen(
                        onClickSubject = { hero ->
                            navigator.navigateSubjectDetails(
                                hero.subjectId,
                                SubjectDetailPlaceholder(
                                    id = hero.subjectId,
                                    nameCN = hero.title,
                                    coverUrl = hero.imageUrl,
                                ),
                            )
                        },
                    )

                    TvShellContent.Schedule -> TvScheduleScreen(
                        onClickSubject = { subjectId ->
                            navigator.navigateSubjectDetails(subjectId, null)
                        },
                    )

                    TvShellContent.Collection -> TvCollectionScreen(
                        onClickSubject = { info ->
                            navigator.navigateSubjectDetails(
                                info.subjectId,
                                SubjectDetailPlaceholder(
                                    id = info.subjectId,
                                    name = info.subjectInfo.name,
                                    nameCN = info.subjectInfo.nameCn,
                                    coverUrl = info.subjectInfo.imageLarge,
                                ),
                            )
                        },
                    )

                    TvShellContent.Search -> TvSearchScreen(
                        viewModel { TvSearchViewModel() },
                        onClickSubject = { details ->
                            navigator.navigateSubjectDetails(
                                details.subjectInfo.subjectId,
                                SubjectDetailPlaceholder(
                                    id = details.subjectInfo.subjectId,
                                    name = details.subjectInfo.name,
                                    nameCN = details.subjectInfo.nameCn,
                                    coverUrl = details.subjectInfo.imageLarge,
                                ),
                            )
                        },
                    )

                    TvShellContent.Login -> TvLoginScreen(
                        onLoggedIn = { content = TvShellContent.Exploration },
                    )

                    TvShellContent.Settings -> TvSettingsScreen(
                        viewModel { TvSettingsViewModel() },
                    )
                }
            }
        }

        // 侧边栏浮于内容之上 (展开时渐变面板压住内容左缘)
        TvNavigationSideRail(
            selfInfo = selfInfo,
            onAvatarClick = { content = TvShellContent.Login },
            items = listOf(
                TvNavRailItem(Icons.Rounded.Search, "搜索") { content = TvShellContent.Search },
                TvNavRailItem(Icons.Rounded.TravelExplore, "探索", defaultFocus = true) {
                    content = TvShellContent.Exploration
                },
                TvNavRailItem(Icons.Rounded.CalendarMonth, "时间表") { content = TvShellContent.Schedule },
                TvNavRailItem(Icons.Rounded.Star, "追番") { content = TvShellContent.Collection },
                TvNavRailItem(Icons.Rounded.Settings, "设置") { content = TvShellContent.Settings },
            ),
            modifier = Modifier.align(Alignment.CenterStart),
            enterFocus = focus.requesterOf(TvShellFocus.Rail),
        )
    }
}
