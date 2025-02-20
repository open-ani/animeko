/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.adaptive.AniListDetailPaneScaffold
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.ListDetailLayoutParameters
import me.him188.ani.app.ui.adaptive.PaneScope
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.rendering.P2p
import me.him188.ani.app.ui.settings.tabs.AboutTab
import me.him188.ani.app.ui.settings.tabs.DebugTab
import me.him188.ani.app.ui.settings.tabs.app.AppearanceGroup
import me.him188.ani.app.ui.settings.tabs.app.PlayerGroup
import me.him188.ani.app.ui.settings.tabs.app.SoftwareUpdateGroup
import me.him188.ani.app.ui.settings.tabs.media.AutoCacheGroup
import me.him188.ani.app.ui.settings.tabs.media.CacheDirectoryGroup
import me.him188.ani.app.ui.settings.tabs.media.MediaSelectionGroup
import me.him188.ani.app.ui.settings.tabs.media.TorrentEngineGroup
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceGroup
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceSubscriptionGroup
import me.him188.ani.app.ui.settings.tabs.network.DanmakuGroup
import me.him188.ani.app.ui.settings.tabs.network.GlobalProxyGroup
import me.him188.ani.app.ui.settings.tabs.theme.ThemeGroup
import me.him188.ani.utils.platform.hasScrollingBug

/**
 * @see getName 查看名称
 */
typealias SettingsTab = me.him188.ani.app.navigation.SettingsTab

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    modifier: Modifier = Modifier,
    initialTab: SettingsTab? = null,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    navigationIcon: @Composable () -> Unit = {},
) {
    var lastSelectedTab by remember {
        mutableStateOf(initialTab)
    }
    val navigator: ThreePaneScaffoldNavigator<Nothing?> = rememberListDetailPaneScaffoldNavigator(
        initialDestinationHistory = buildList {
            add(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List))
            if (initialTab != null) {
                add(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail))
            }
        },
    )
    val layoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective)
    val coroutineScope = rememberCoroutineScope()

    SettingsPageLayout(
        navigator,
        // TODO: 2025/2/14 We should have a SettingsNavController or so to control the tab state 
        { lastSelectedTab },
        onSelectedTab = { tab ->
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                lastSelectedTab = tab
            }
        },
        onClickBackOnListPage = {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateBack()
            }
        },
        onClickBackOnDetailPage = {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
            }
        },
        navItems = {
            Title("应用与界面", paddingTop = 0.dp)
            Item(SettingsTab.APPEARANCE)
            Item(SettingsTab.THEME)
            Item(SettingsTab.UPDATE)

            Title("数据源与播放")
            Item(SettingsTab.PLAYER)
            Item(SettingsTab.MEDIA_SOURCE)
            Item(SettingsTab.MEDIA_SELECTOR)
            Item(SettingsTab.DANMAKU)

            Title("网络与存储")
            Item(SettingsTab.PROXY)
            Item(SettingsTab.BT)
            Item(SettingsTab.CACHE)
            Item(SettingsTab.STORAGE)

            Title("其他")
            Item(SettingsTab.ABOUT)
            if (vm.isInDebugMode) {
                Item(SettingsTab.DEBUG)
            }
        },
        tabContent = { currentTab ->
            val tabModifier = Modifier
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SettingsScope.itemExtraHorizontalPadding),
            ) {
                when (currentTab) {
                    SettingsTab.ABOUT -> AboutTab({ vm.debugTriggerState.triggerDebugMode() }, tabModifier)
                    SettingsTab.DEBUG -> DebugTab(
                        vm.debugSettingsState,
                        vm.uiSettings,
                        tabModifier,
                    )

                    else -> SettingsTab(
                        tabModifier,
                    ) {
                        when (currentTab) {
                            SettingsTab.APPEARANCE -> AppearanceGroup(vm.uiSettings)
                            SettingsTab.THEME -> ThemeGroup(vm.themeSettings)
                            SettingsTab.UPDATE -> SoftwareUpdateGroup(vm.softwareUpdateGroupState)
                            SettingsTab.PLAYER -> PlayerGroup(
                                vm.videoScaffoldConfig,
                                vm.danmakuFilterConfigState,
                                vm.danmakuRegexFilterState,
                                vm.isInDebugMode,
                            )

                            SettingsTab.MEDIA_SOURCE -> {
                                MediaSourceSubscriptionGroup(
                                    vm.mediaSourceSubscriptionGroupState,
                                )
                                MediaSourceGroup(
                                    vm.mediaSourceGroupState,
                                    vm.editMediaSourceState,
                                )
                            }

                            SettingsTab.MEDIA_SELECTOR -> MediaSelectionGroup(vm.mediaSelectionGroupState)
                            SettingsTab.DANMAKU -> DanmakuGroup(vm.danmakuSettingsState, vm.danmakuServerTesters)
                            SettingsTab.PROXY -> GlobalProxyGroup(vm.proxySettingsState, vm.detectedProxy)
                            SettingsTab.BT -> TorrentEngineGroup(vm.torrentSettingsState)
                            SettingsTab.CACHE -> AutoCacheGroup(vm.mediaCacheSettingsState)
                            SettingsTab.STORAGE -> CacheDirectoryGroup(vm.cacheDirectoryGroupState)
                            SettingsTab.ABOUT -> {} // see above
                            SettingsTab.DEBUG -> {}
                            null -> {}
                        }
                    }
                }
                Spacer(
                    Modifier.height(
                        currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding,
                    ),
                )
            }
        },
        modifier,
        windowInsets,
        navigationIcon = navigationIcon,
        layoutParameters = layoutParameters,
    )
}

@Composable
internal fun SettingsPageLayout(
    navigator: ThreePaneScaffoldNavigator<Nothing?>,
    currentTab: () -> SettingsTab?,
    onSelectedTab: (SettingsTab) -> Unit,
    onClickBackOnListPage: () -> Unit,
    onClickBackOnDetailPage: () -> Unit,
    navItems: @Composable (SettingsDrawerScope.() -> Unit),
    tabContent: @Composable PaneScope.(currentTab: SettingsTab?) -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    containerColor: Color = AniThemeDefaults.pageContentBackgroundColor,
    layoutParameters: ListDetailLayoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective),
    navigationIcon: @Composable () -> Unit = {},
) = Surface(color = containerColor) {
    val layoutParametersState by rememberUpdatedState(layoutParameters)

    @Stable
    fun SettingsTab?.orDefault(): SettingsTab? {
        return if (layoutParametersState.isSinglePane) {
            // 单页模式, 自动选择传入的 tab
            this
        } else {
            // 双页模式, 默认选择第一个 tab, 以免右边很空
            this ?: SettingsTab.entries.first()
        }
    }

    val topAppBarScrollBehavior = if (LocalPlatform.current.hasScrollingBug()) {
        null
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }

    val listPaneScrollState = rememberScrollState()
    AniListDetailPaneScaffold(
        navigator,
        listPaneTopAppBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title("设置") },
                navigationIcon = {
                    if (navigator.canNavigateBack()) {
                        BackNavigationIconButton(
                            onNavigateBack = {
                                onClickBackOnListPage()
                            },
                        )
                    } else {
                        navigationIcon()
                    }
                },
                colors = AniThemeDefaults.transparentAppBarColors(),
                scrollBehavior = topAppBarScrollBehavior,
                windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        listPaneContent = paneScope@{
            PermanentDrawerSheet(
                Modifier
                    .paneContentPadding()
                    .paneWindowInsetsPadding()
                    .fillMaxWidth()
                    .ifThen(!LocalPlatform.current.hasScrollingBug()) {
                        topAppBarScrollBehavior?.let { nestedScroll(it.nestedScrollConnection) }
                    }
                    .verticalScroll(listPaneScrollState),
                drawerContainerColor = Color.Unspecified,
            ) {
                val highlightSelectedItemState = rememberUpdatedState(layoutParametersState.highlightSelectedItem)
                val scope = remember(this, navigator, currentTab, highlightSelectedItemState) {
                    object : SettingsDrawerScope(), ColumnScope by this {
                        @Composable
                        override fun Item(item: SettingsTab) {
                            NavigationDrawerItem(
                                icon = { Icon(getIcon(item), contentDescription = null) },
                                label = { Text(getName(item)) },
                                selected = item == currentTab() && highlightSelectedItemState.value,
                                onClick = {
                                    onSelectedTab(item)
                                },
                            )
                        }
                    }
                }


                val verticalPadding = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding

                Spacer(Modifier.height(verticalPadding)) // scrollable
                navItems(scope)
                Spacer(Modifier.height(verticalPadding)) // scrollable
            }
        },
        // empty because our detailPaneContent already has it
        detailPane = {
            AnimatedContent(
                currentTab(),
                Modifier.fillMaxSize(),
                transitionSpec = LocalAniMotionScheme.current.animatedContent.topLevel,
            ) { navigationTab ->
                val tab = navigationTab.orDefault()
                Column {
                    tab?.let {
                        AniTopAppBar(
                            title = {
                                AniTopAppBarDefaults.Title(getName(tab))
                            },
                            navigationIcon = {
                                if (listDetailLayoutParameters.isSinglePane) {
                                    BackNavigationIconButton(
                                        {
                                            onClickBackOnDetailPage()
                                        },
                                    )
                                }
                            },
                            colors = AniThemeDefaults.transparentAppBarColors(),
                            windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        )
                    }

                    Box(
                        Modifier
                            .fillMaxHeight()
                            .consumeWindowInsets(paneContentWindowInsets.only(WindowInsetsSides.Top)),
                    ) {
                        Column(
                            Modifier
                                .paneContentPadding(
                                    extraStart = -SettingsScope.itemHorizontalPadding,
                                    extraEnd = -SettingsScope.itemHorizontalPadding,
                                )
                                .paneWindowInsetsPadding(),
                        ) {
                            tabContent(tab)
                        }
                    }
                }
            }
        },
        modifier,
        layoutParameters = layoutParameters,
        contentWindowInsets = contentWindowInsets,
    )
}

@Stable
abstract class SettingsDrawerScope internal constructor() : ColumnScope {
    @Composable
    abstract fun Item(item: SettingsTab)

    @Composable
    fun Title(text: String, paddingTop: Dp = 20.dp) {
        Text(
            text,
            Modifier
                .padding(horizontal = 8.dp)
                .padding(top = paddingTop, bottom = 12.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Stable
private fun getIcon(tab: SettingsTab): ImageVector {
    return when (tab) {
        SettingsTab.APPEARANCE -> Icons.Rounded.SettingsApplications
        SettingsTab.THEME -> Icons.Rounded.Palette
        SettingsTab.UPDATE -> Icons.Rounded.Update
        SettingsTab.PLAYER -> Icons.Rounded.SmartDisplay
        SettingsTab.MEDIA_SOURCE -> Icons.Rounded.Subscriptions
        SettingsTab.MEDIA_SELECTOR -> Icons.Rounded.FilterList
        SettingsTab.DANMAKU -> Icons.Rounded.Subtitles
        SettingsTab.PROXY -> Icons.Rounded.VpnKey
        SettingsTab.BT -> Icons.Filled.P2p
        SettingsTab.CACHE -> Icons.Rounded.Download
        SettingsTab.STORAGE -> Icons.Rounded.Storage
        SettingsTab.ABOUT -> Icons.Rounded.Info
        SettingsTab.DEBUG -> Icons.Rounded.Science
    }
}

@Stable
private fun getName(tab: SettingsTab): String {
    return when (tab) {
        SettingsTab.APPEARANCE -> "界面"
        SettingsTab.THEME -> "主题与色彩"
        SettingsTab.UPDATE -> "软件更新"
        SettingsTab.PLAYER -> "播放器和弹幕过滤"
        SettingsTab.MEDIA_SOURCE -> "数据源管理"
        SettingsTab.MEDIA_SELECTOR -> "观看偏好"
        SettingsTab.DANMAKU -> "弹幕源"
        SettingsTab.PROXY -> "代理"
        SettingsTab.BT -> "BitTorrent"
        SettingsTab.CACHE -> "自动缓存"
        SettingsTab.STORAGE -> "存储空间"
        SettingsTab.ABOUT -> "关于、反馈与日志"
        SettingsTab.DEBUG -> "调试"
    }
}

// a lot of call-sites, don't make it internal
@Composable
fun SettingsTab(
    modifier: Modifier = Modifier,
    content: @Composable SettingsScope.() -> Unit,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(SettingsScope.itemVerticalSpacing),
    ) {
        val scope = remember(this) {
            object : SettingsScope(), ColumnScope by this@Column {}
        }
        scope.content()
    }
}
