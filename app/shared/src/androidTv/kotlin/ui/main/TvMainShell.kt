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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.MaterialTheme
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.drop
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search
import me.him188.ani.app.ui.lang.main_screen_page_cache_management
import me.him188.ani.app.ui.lang.main_screen_page_collection
import me.him188.ani.app.ui.lang.main_screen_page_exploration
import me.him188.ani.app.ui.lang.settings
import me.him188.ani.app.ui.lang.tv_nav_schedule
import me.him188.ani.tv.ui.foundation.focus.LocalTvFocusMemory
import me.him188.ani.tv.ui.foundation.focus.TvFocusBoundary
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusMemory
import me.him188.ani.tv.ui.foundation.focus.TvKeyboardInputMode
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusHotkeyToggle
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.layout.tvModalUnderlay
import me.him188.ani.tv.ui.foundation.widgets.TvNavRailItem
import me.him188.ani.tv.ui.foundation.widgets.TvNavigationRailDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvNavigationSideRail
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor
import org.jetbrains.compose.resources.stringResource

enum class TvShellContent { Search, Exploration, Schedule, Collection, Downloads, Login }

/** 主壳焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvShellFocus : TvFocusKey {
    Content,
    /** 菜单键入口；进入时由侧栏选择当前页面或待恢复条目。 */
    Rail,
    Settings,
    Avatar,
    Logout,
}

/**
 * TV 主壳。页面占满屏幕；左侧悬浮导航栏展开时压暗页面，并在左侧以渐进模糊衬托。
 * [pageContent] 接收收起态导航栏的避让范围，可分别安排背景和前景内容。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvMainShell(
    uiState: TvMainUiState,
    content: TvShellContent,
    onContentChange: (TvShellContent) -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    /** 焦点记忆; 调用方在 NavHost 之上创建传入使其跨 route 存活 (进详情页返回恢复焦点用). */
    focusMemory: TvFocusMemory? = null,
    contentModalOpen: Boolean = false,
    pageContent: @Composable (TvShellContent, navigationRailInsets: PaddingValues) -> Unit,
) {
    val selfInfo = uiState.selfInfo
    val currentContent by rememberUpdatedState(content)
    val pageStates = rememberSaveableStateHolder()
    val railBackdrop = rememberHazeState()
    val backgroundColor = tvShellBackgroundColor()
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var restoreAccountFocus by remember { mutableStateOf<TvShellFocus?>(null) }

    TvKeyboardInputMode()

    // 返回语义: 非探索内容先回探索; 探索交给系统 (退出应用)
    BackHandler(enabled = content != TvShellContent.Exploration) {
        onContentChange(TvShellContent.Exploration)
    }

    // 菜单键进入侧栏的当前页条目，再次按菜单或返回键时恢复内容区的最后焦点。
    // 记忆由实际聚焦节点上报，内容切页时清除，由目标页的 InitialFocus 选择入口。
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val memory = focusMemory ?: remember { TvFocusMemory() }
    val settingsFocus = remember { FocusRequester() }
    val avatarFocus = remember { FocusRequester() }
    val logoutFocus = remember { FocusRequester() }
    // 内容页切换清除当前记忆，路由返回保留保存的身份。
    LaunchedEffect(Unit) {
        snapshotFlow { currentContent }.drop(1).collect { memory.clear() }
    }
    // 用户交互取消账号操作的待恢复位置。
    LaunchedEffect(focus, memory) {
        snapshotFlow { focus.userNavGeneration }
            .drop(1)
            .collect {
                restoreAccountFocus = null
            }
    }
    var railHasFocus by remember { mutableStateOf(false) }
    val railReveal by animateFloatAsState(
        if (railHasFocus || showLogoutConfirmation || restoreAccountFocus != null) 1f else 0f,
        tween(TvNavigationRailDefaults.ExpandDurationMillis, easing = FastOutSlowInEasing),
        label = "navigation-rail-backdrop",
    )
    val restoreContentFocus: () -> Unit = remember(focus, memory) {
        {
            if (memory.lastId == TvShellFocus.Settings) memory.clear()
            focus.restore(memory, TvShellFocus.Content)
        }
    }
    BackHandler(enabled = railHasFocus && !showLogoutConfirmation) { restoreContentFocus() }
    LaunchedEffect(uiState.isLoggedIn, showLogoutConfirmation) {
        if (uiState.isLoggedIn == false && showLogoutConfirmation) {
            showLogoutConfirmation = false
            restoreAccountFocus = TvShellFocus.Avatar
        }
    }
    LaunchedEffect(showLogoutConfirmation, restoreAccountFocus) {
        if (!showLogoutConfirmation) restoreAccountFocus?.let { focus.request(it) }
    }

    Box(
        modifier
            .fillMaxSize()
            .testTag("tv-main-shell")
            .background(backgroundColor)
            .then(
                if (showLogoutConfirmation || contentModalOpen) Modifier.tvFocusNavSignal(focus) else Modifier.tvFocusHotkeyToggle(
                    focus, Key.Menu, TvShellFocus.Rail, onLeave = restoreContentFocus,
                ),
            ),
    ) {
        Box(Modifier.fillMaxSize().tvModalUnderlay(showLogoutConfirmation)) {
            // 背景与页面均占满窗口；焦点记忆仅向内容子树提供。
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("tv-main-page-content")
                    .hazeSource(railBackdrop)
                    .tvFocusAnchor(focus, TvShellFocus.Content),
            ) {
                CompositionLocalProvider(LocalTvFocusMemory provides memory) {
                    AnimatedContent(
                        content,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tvShellContent",
                    ) { current ->
                        TvFocusBoundary(current == currentContent, Modifier.fillMaxSize()) {
                            pageStates.SaveableStateProvider(current) {
                                pageContent(current, TvNavigationRailDefaults.ContentInsets)
                            }
                        }
                    }
                }

                Box(
                    Modifier.fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface
                                .copy(alpha = railReveal * TvNavigationRailDefaults.BackgroundDimAlpha),
                        ),
                )
            }

            if (railReveal > 0f) {
                val surfaceColor = MaterialTheme.colorScheme.surface
                Box(
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(TvNavigationRailDefaults.BackgroundBlurWidthFraction)
                        .hazeEffect(railBackdrop) {
                            this.backgroundColor = backgroundColor
                            blurRadius = TvNavigationRailDefaults.BackgroundBlurRadius
                            progressive = HazeProgressive.horizontalGradient(
                                startIntensity = 1f, endIntensity = 0f,
                            )
                            tints = listOf(HazeTint(surfaceColor))
                            noiseFactor = 0f
                            alpha = railReveal
                        },
                )
            }

            TvNavigationSideRail(
                selfInfo = selfInfo,
                isLoggedIn = uiState.isLoggedIn == true,
                onAvatarClick = { if (uiState.isLoggedIn == false) onContentChange(TvShellContent.Login) },
                onLogoutClick = { showLogoutConfirmation = true },
                avatarModifier = Modifier.focusRequester(avatarFocus).tvFocusAnchor(focus, TvShellFocus.Avatar)
                    .onFocusChanged { if (it.isFocused) restoreAccountFocus = null },
                logoutModifier = Modifier.focusRequester(logoutFocus).tvFocusAnchor(focus, TvShellFocus.Logout)
                    .onFocusChanged { if (it.isFocused) restoreAccountFocus = null },
                accountRestoreFocus = when (restoreAccountFocus) {
                    TvShellFocus.Avatar -> avatarFocus
                    TvShellFocus.Logout -> logoutFocus
                    else -> null
                },
                keepAccountActionsVisible = showLogoutConfirmation || restoreAccountFocus != null,
                // selected = 当前页条目: 进入侧边栏 (按左/菜单键) 焦点落到它上, 而不是固定落"探索"
                items = listOf(
                    TvNavRailItem(
                        Icons.Rounded.Search, stringResource(Lang.exploration_search),
                        selected = content == TvShellContent.Search,
                    ) { onContentChange(TvShellContent.Search) },
                    TvNavRailItem(
                        Icons.Rounded.TravelExplore,
                        stringResource(Lang.main_screen_page_exploration),
                        defaultFocus = true,
                        selected = content == TvShellContent.Exploration,
                    ) { onContentChange(TvShellContent.Exploration) },
                    TvNavRailItem(
                        Icons.Rounded.CalendarMonth, stringResource(Lang.tv_nav_schedule),
                        selected = content == TvShellContent.Schedule,
                    ) { onContentChange(TvShellContent.Schedule) },
                    TvNavRailItem(
                        Icons.Rounded.Star, stringResource(Lang.main_screen_page_collection),
                        selected = content == TvShellContent.Collection,
                    ) { onContentChange(TvShellContent.Collection) },
                    TvNavRailItem(
                        Icons.Rounded.Download, stringResource(Lang.main_screen_page_cache_management),
                        selected = content == TvShellContent.Downloads,
                    ) { onContentChange(TvShellContent.Downloads) },
                    TvNavRailItem(
                        Icons.Rounded.Settings, stringResource(Lang.settings),
                        focusRequester = settingsFocus,
                        restoreFocus = memory.lastId == TvShellFocus.Settings,
                        modifier = Modifier.tvFocusMemorable(TvShellFocus.Settings, memory, rememberOnFocus = false),
                        keepFocusOnClick = true,
                    ) {
                        memory.remember(TvShellFocus.Settings)
                        onOpenSettings()
                    },
                ),
                // Rail 锚点包含整个侧栏，入口门控选择当前页条目，hasFocus 汇总子树状态。
                modifier = Modifier
                    .testTag("tv-main-navigation")
                    .tvModalUnderlay(contentModalOpen)
                    .align(Alignment.CenterStart)
                    .tvFocusAnchor(focus, TvShellFocus.Rail)
                    .onFocusChanged { railHasFocus = it.hasFocus },
                // 点击条目后把焦点还给内容区并恢复进入前的位置 (切页时恢复目标随旧页销毁,
                // 自然交给新页 InitialFocus)
                returnFocusToContent = restoreContentFocus,
                onExitFocus = restoreContentFocus,
            )
        }
        if (showLogoutConfirmation) {
            TvLogoutConfirmation(
                onCancel = {
                    showLogoutConfirmation = false
                    restoreAccountFocus = TvShellFocus.Logout
                },
                onConfirm = {
                    showLogoutConfirmation = false
                    restoreAccountFocus = TvShellFocus.Avatar
                    onLogout()
                },
            )
        }
    }
}
