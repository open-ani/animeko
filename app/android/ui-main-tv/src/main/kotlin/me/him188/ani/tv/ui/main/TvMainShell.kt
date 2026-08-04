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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.tv.ui.collection.TvCollectionScreen
import me.him188.ani.tv.ui.exploration.TvExplorationScreen
import me.him188.ani.tv.ui.foundation.focus.LocalTvFocusMemory
import me.him188.ani.tv.ui.foundation.focus.TvFocusMemory
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusHotkeyToggle
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
fun TvMainShell(
    modifier: Modifier = Modifier,
    /** 焦点记忆; 调用方在 NavHost 之上创建传入使其跨 route 存活 (进详情页返回恢复焦点用). */
    focusMemory: TvFocusMemory? = null,
) {
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

    // 菜单键在"内容区 <-> 侧边栏"间往返: 焦点在内容时直达侧边栏 (落当前页条目, rail 随
    // hasFocus 自动展开); 焦点已在侧边栏时按菜单/返回/点击条目 = 收起并**恢复进入前的
    // 内容区焦点** (而不是落到几何最近的节点). 恢复用 TvFocusMemory: 内容区可聚焦组件
    // 聚焦时自动上报, 换页清记忆 (由新页 InitialFocus 接管), 记忆失效退化为默认进入.
    // (不用 focusRestorer/saveFocusedChild: 它们只保存第一层子 target, 跨壳->页面->Lazy
    // 的多层容器时保存到不可聚焦的中间容器, 恢复必然失败 —— TV 模拟器同帧实测)
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val contentFocus = remember { FocusRequester() }
    val memory = focusMemory ?: remember { TvFocusMemory() }
    memory.ArmOnRouteReturn() // route 重建 (从详情页等返回) 时装填跨 route 恢复目标
    LaunchedEffect(content) { memory.clear() }
    var railHasFocus by remember { mutableStateOf(false) }
    val restoreContentFocus: () -> Unit = remember(contentFocus, memory) {
        {
            if (!memory.restore()) runCatching { contentFocus.requestFocus() }
            Unit
        }
    }
    BackHandler(enabled = railHasFocus) { restoreContentFocus() }

    Box(
        modifier
            .fillMaxSize()
            .background(tvShellBackgroundColor())
            .tvFocusHotkeyToggle(
                focus, Key.Menu, TvShellFocus.Rail,
                onLeave = restoreContentFocus,
            ),
    ) {
        // 内容区: 让开侧边栏收起态宽度. 焦点记忆只对内容子树 provide (侧边栏不上报)
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = TvNavigationRailDefaults.CollapsedWidth)
                .focusRequester(contentFocus),
        ) {
            CompositionLocalProvider(LocalTvFocusMemory provides memory) {
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
        }

        // 侧边栏浮于内容之上 (展开时渐变面板压住内容左缘)
        TvNavigationSideRail(
            selfInfo = selfInfo,
            onAvatarClick = { content = TvShellContent.Login },
            // selected = 当前页条目: 进入侧边栏 (按左/菜单键) 焦点落到它上, 而不是固定落"探索"
            items = listOf(
                TvNavRailItem(
                    Icons.Rounded.Search, "搜索",
                    selected = content == TvShellContent.Search,
                ) { content = TvShellContent.Search },
                TvNavRailItem(
                    Icons.Rounded.TravelExplore, "探索", defaultFocus = true,
                    selected = content == TvShellContent.Exploration,
                ) { content = TvShellContent.Exploration },
                TvNavRailItem(
                    Icons.Rounded.CalendarMonth, "时间表",
                    selected = content == TvShellContent.Schedule,
                ) { content = TvShellContent.Schedule },
                TvNavRailItem(
                    Icons.Rounded.Star, "追番",
                    selected = content == TvShellContent.Collection,
                ) { content = TvShellContent.Collection },
                TvNavRailItem(
                    Icons.Rounded.Settings, "设置",
                    selected = content == TvShellContent.Settings,
                ) { content = TvShellContent.Settings },
            ),
            // Rail 锚点挂容器而非条目: requestFocus 经进入门控落到当前页条目, 而
            // hasFocus 对整个子树上报到位 —— 否则 request(Rail) 的解析轮询永远等不到
            // 确认, 烧满全部轮询期间会把用户点击后移入内容区的焦点一次次抢回 (实测)
            modifier = Modifier
                .align(Alignment.CenterStart)
                .tvFocusAnchor(focus, TvShellFocus.Rail)
                .onFocusChanged { railHasFocus = it.hasFocus },
            // 点击条目后把焦点还给内容区并恢复进入前的位置 (切页时恢复目标随旧页销毁,
            // 自然交给新页 InitialFocus)
            returnFocusToContent = restoreContentFocus,
        )
    }
}
