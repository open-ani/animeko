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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsApplications
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.rememberAsyncBrowserNavigator
import me.him188.ani.app.ui.adaptive.AniListDetailPaneScaffold
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.ListDetailLayoutParameters
import me.him188.ani.app.ui.adaptive.PaneScope
import me.him188.ani.app.ui.adaptive.TopAppBarSize
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.animation.NavigationMotionScheme
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastExpanded
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.acknowledgements
import me.him188.ani.app.ui.lang.developer_list
import me.him188.ani.app.ui.lang.settings
import me.him188.ani.app.ui.lang.settings_category_app_ui
import me.him188.ani.app.ui.lang.settings_category_data_playback
import me.him188.ani.app.ui.lang.settings_category_network_storage
import me.him188.ani.app.ui.lang.settings_category_others
import me.him188.ani.app.ui.lang.settings_debug_mode_enabled
import me.him188.ani.app.ui.lang.settings_tab_about
import me.him188.ani.app.ui.lang.settings_tab_account
import me.him188.ani.app.ui.lang.settings_tab_appearance
import me.him188.ani.app.ui.lang.settings_tab_bt
import me.him188.ani.app.ui.lang.settings_tab_danmaku
import me.him188.ani.app.ui.lang.settings_tab_debug
import me.him188.ani.app.ui.lang.settings_tab_log
import me.him188.ani.app.ui.lang.settings_tab_media_selector
import me.him188.ani.app.ui.lang.settings_tab_media_source
import me.him188.ani.app.ui.lang.settings_tab_player
import me.him188.ani.app.ui.lang.settings_tab_proxy
import me.him188.ani.app.ui.lang.settings_tab_settings_backup
import me.him188.ani.app.ui.lang.settings_tab_storage
import me.him188.ani.app.ui.lang.settings_tab_theme
import me.him188.ani.app.ui.lang.settings_tab_update
import me.him188.ani.app.ui.settings.account.BangumiSyncTab
import me.him188.ani.app.ui.settings.account.ProfileGroup
import me.him188.ani.app.ui.settings.account.SelfInfoBanner
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.rendering.P2p
import me.him188.ani.app.ui.settings.tabs.AniHelperDestination
import me.him188.ani.app.ui.settings.tabs.DebugTab
import me.him188.ani.app.ui.settings.tabs.about.AboutTab
import me.him188.ani.app.ui.settings.tabs.about.AcknowledgementsTab
import me.him188.ani.app.ui.settings.tabs.about.DevelopersTab
import me.him188.ani.app.ui.settings.tabs.app.AppearanceGroup
import me.him188.ani.app.ui.settings.tabs.app.PlayerGroup
import me.him188.ani.app.ui.settings.tabs.app.SoftwareUpdateGroup
import me.him188.ani.app.ui.settings.tabs.log.LogTab
import me.him188.ani.app.ui.settings.tabs.media.BackupSettings
import me.him188.ani.app.ui.settings.tabs.media.CacheDirectoryGroup
import me.him188.ani.app.ui.settings.tabs.media.MediaSelectionGroup
import me.him188.ani.app.ui.settings.tabs.media.TorrentEngineGroup
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceGroup
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceSubscriptionGroup
import me.him188.ani.app.ui.settings.tabs.network.ConfigureProxyGroup
import me.him188.ani.app.ui.settings.tabs.network.ServerSelectionGroup
import me.him188.ani.app.ui.settings.tabs.theme.ThemeGroup
import me.him188.ani.utils.platform.hasScrollingBug
import me.him188.ani.utils.platform.isDesktop
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * @see getName 查看名称
 */
typealias SettingsTab = me.him188.ani.app.navigation.SettingsTab

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onNavigateToEmailLogin: () -> Unit,
    onNavigateToBangumiOAuth: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: SettingsTab? = null,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    navigationIcon: @Composable () -> Unit = {},
) {
    var lastSelectedTab by rememberSaveable {
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
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current

    fun navigateToTab(tab: SettingsTab) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
            lastSelectedTab = tab
        }
    }

    SettingsPageLayout(
        navigator,
        // TODO: 2025/2/14 We should have a SettingsNavController or so to control the tab state 
        { lastSelectedTab },
        onSelectedTab = { tab ->
            navigateToTab(tab)
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
            val selfInfoState by vm.selfInfoFlow.collectAsStateWithLifecycle()
            val bannerChecked by remember {
                derivedStateOf {
                    lastSelectedTab == SettingsTab.PROFILE
                }
            }
            SelfInfoBanner(
                selfInfoState,
                checked = bannerChecked,
                { navigateToTab(SettingsTab.PROFILE) },
                onNavigateToEmailLogin,
                Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )

            Title(stringResource(Lang.settings_category_app_ui))
            Item(SettingsTab.APPEARANCE)
            Item(SettingsTab.THEME)

            Title(stringResource(Lang.settings_category_data_playback))
            Item(SettingsTab.PLAYER)
            Item(SettingsTab.MEDIA_SOURCE)
            Item(SettingsTab.MEDIA_SELECTOR)

            Title(stringResource(Lang.settings_category_network_storage))
            Item(SettingsTab.SERVER)
            Item(SettingsTab.PROXY)
            Item(SettingsTab.BT)
//            Item(SettingsTab.CACHE)
            if (LocalPlatform.current.isDesktop()) {
                Item(SettingsTab.STORAGE)
            }

            Title(stringResource(Lang.settings_category_others))
            Item(SettingsTab.UPDATE)
            Item(SettingsTab.LOG)
            Item(SettingsTab.ABOUT)
            if (vm.isInDebugMode) {
                Item(SettingsTab.DEBUG)
            }
            Item(SettingsTab.SETTINGS_BACKUP)
        },
        tabContent = { currentTab ->
            val tabModifier = Modifier
            val toaster = LocalToaster.current
            val scope = rememberCoroutineScope()

            Column {
                when (currentTab) {
                    SettingsTab.ABOUT -> AboutTab(
                        vm.aboutTabInfo,
                        {
                            scope.launch {
                                if (vm.debugTriggerState.triggerDebugMode()) {
                                    toaster.toast(getString(Lang.settings_debug_mode_enabled))
                                }
                            }
                        },
                        onClickReleaseNotes = {
                            browserNavigator.openBrowser(
                                context,
                                AniHelperDestination.RELEASE_PREFIX + vm.aboutTabInfo.version,
                            )
                        },
                        onClickWebsite = { browserNavigator.openBrowser(context, AniHelperDestination.ANI_WEBSITE) },
                        onClickFeedback = { browserNavigator.openBrowser(context, AniHelperDestination.ISSUE_TRACKER) },
                        onClickSource = { browserNavigator.openBrowser(context, AniHelperDestination.GITHUB_HOME) },
                        onClickDevelopers = {
                            detailPaneNavController.navigate(DetailPaneRoutes.Developers)
                        },
                        onClickAcknowledgements = {
                            detailPaneNavController.navigate(DetailPaneRoutes.Acknowledgements)
                        },
                        modifier = tabModifier,
                    )

                    SettingsTab.LOG -> LogTab(
                        onClickFeedback = { browserNavigator.openBrowser(context, AniHelperDestination.ISSUE_TRACKER) },
                    )

                    SettingsTab.DEBUG -> DebugTab(
                        vm.debugSettingsState,
                        vm.uiSettings,
                        tabModifier,
                    )

                    else -> SettingsTab(
                        tabModifier,
                    ) {
                        when (currentTab) {
                            SettingsTab.PROFILE -> ProfileGroup(
                                onNavigateToEmail = onNavigateToEmailLogin,
                                onNavigateToBangumiSync = {
                                    detailPaneNavController.navigate(DetailPaneRoutes.BangumiSync)
                                },
                                onNavigateToBangumiOAuth = onNavigateToBangumiOAuth,
                            )

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
                            SettingsTab.SERVER -> ServerSelectionGroup(vm.danmakuSettingsState, vm.danmakuServerTesters)
                            SettingsTab.PROXY -> ConfigureProxyGroup(
                                state = vm.configureProxyState,
                                onStartProxyTestLoop = { vm.startProxyTesterLoop() },
                            )

                            SettingsTab.BT -> TorrentEngineGroup(vm.torrentSettingsState)
//                            SettingsTab.CACHE -> AutoCacheGroup(vm.mediaCacheSettingsState)
                            SettingsTab.STORAGE -> CacheDirectoryGroup(vm.cacheDirectoryGroupState)
                            SettingsTab.SETTINGS_BACKUP -> BackupSettings(vm.cacheDirectoryGroupState)
                            SettingsTab.ABOUT -> {} // see above
                            SettingsTab.DEBUG -> {}
                            SettingsTab.LOG -> {}
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
    tabContent: @Composable SettingsDetailPaneScope.(currentTab: SettingsTab?) -> Unit, // inside Column verticalScroll
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    containerColor: Color = AniThemeDefaults.pageContentBackgroundColor,
    layoutParameters: ListDetailLayoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective),
    navigationIcon: @Composable () -> Unit = {},
) = Surface(color = containerColor) {
    val layoutParametersState by rememberUpdatedState(layoutParameters)

    @Stable
    fun SettingsTab?.orDefault(): SettingsTab? {
        return if (layoutParametersState.preferSinglePane) {
            // 单页模式, 自动选择传入的 tab
            this
        } else {
            // 双页模式, 默认选择第一个 tab, 以免右边很空
            this ?: SettingsTab.Default
        }
    }

    val listPaneTopAppBarScrollBehavior = if (LocalPlatform.current.hasScrollingBug()) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }

    val detailPaneTopAppBarScrollBehavior = if (LocalPlatform.current.hasScrollingBug()) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }

    val listPaneScrollState = rememberScrollState()
    val topAppBarSize = if (LocalPlatform.current.hasScrollingBug()) {
        TopAppBarSize.SMALL
    } else {
        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
        when {
            windowSizeClass.isHeightAtLeastExpanded -> TopAppBarSize.LARGE
            windowSizeClass.isHeightAtLeastMedium -> TopAppBarSize.MEDIUM
            else -> TopAppBarSize.SMALL
        }
    }
    AniListDetailPaneScaffold(
        navigator,
        listPaneTopAppBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title(stringResource(Lang.settings)) },
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
                colors = if (isSinglePane) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = containerColor,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = containerColor,
                        scrolledContainerColor = containerColor,
                    )
                },
                scrollBehavior = listPaneTopAppBarScrollBehavior,
                windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                size = topAppBarSize,
            )
        },
        listPaneContent = paneScope@{
            PermanentDrawerSheet(
                Modifier
                    .paneContentPadding(extraStart = (-8).dp, extraEnd = (-8).dp)
                    .paneWindowInsetsPadding()
                    .fillMaxWidth()
                    .nestedScroll(listPaneTopAppBarScrollBehavior.nestedScrollConnection)
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

                Spacer(Modifier.height(verticalPadding - 8.dp)) // scrollable
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
                val navMotionScheme = NavigationMotionScheme.current
                val topAppBarWindowInsets =
                    paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                val topAppBarColors = AniThemeDefaults.topAppBarColors(
                    containerColor = if (isSinglePane) {
                        containerColor
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                )
                val detailPaneNavController = rememberNavController()

                @Composable
                fun PaneScope.RouteContent(
                    scrollable: Boolean = true,
                    content: @Composable SettingsDetailPaneScope.() -> Unit,
                ) {
                    val paneScope = this
                    val scope = remember(paneScope, detailPaneNavController) {
                        object : SettingsDetailPaneScope, PaneScope by paneScope {
                            override val detailPaneNavController: NavHostController =
                                detailPaneNavController

                        }
                    }
                    Column(
                        Modifier
                            .ifThen(scrollable) {
                                verticalScroll(rememberScrollState())
                            }
                            .padding(horizontal = SettingsScope.itemExtraHorizontalPadding)
                            .fillMaxWidth()
                            .wrapContentWidth()
                            .widthIn(max = 1000.dp),
                    ) {
                        scope.content()
                    }
                }

                NavHost(
                    detailPaneNavController,
                    DetailPaneRoutes.Main,
                    enterTransition = { navMotionScheme.enterTransition },
                    exitTransition = { navMotionScheme.exitTransition },
                    popEnterTransition = { navMotionScheme.popEnterTransition },
                    popExitTransition = { navMotionScheme.popExitTransition },
                ) {
                    composable<DetailPaneRoutes.Main> {
                        val tab = navigationTab.orDefault()
                        DetailPaneRoute(
                            topAppBar = {
                                tab?.let {
                                    AniTopAppBar(
                                        title = {
                                            AniTopAppBarDefaults.Title(getName(it))
                                        },
                                        navigationIcon = {
                                            if (listDetailLayoutParameters.preferSinglePane) {
                                                BackNavigationIconButton(onClickBackOnDetailPage)
                                            }
                                        },
                                        colors = topAppBarColors,
                                        windowInsets = topAppBarWindowInsets,
                                        size = topAppBarSize,
                                        scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                    )
                                }
                            },
                            detailPaneTopAppBarScrollBehavior,
                            tabContent = {
                                RouteContent {
                                    tabContent(tab)
                                }
                            },
                        )
                    }
                    composable<DetailPaneRoutes.Acknowledgements> {
                        DetailPaneRoute(
                            topAppBar = {
                                AniTopAppBar(
                                    title = { AniTopAppBarDefaults.Title(stringResource(Lang.acknowledgements)) },
                                    navigationIcon = {
                                        BackNavigationIconButton({ detailPaneNavController.navigateUp() })
                                    },
                                    colors = topAppBarColors,
                                    windowInsets = topAppBarWindowInsets,
                                    size = topAppBarSize,
                                    scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                )
                            },
                            detailPaneTopAppBarScrollBehavior,
                        ) {
                            RouteContent {
                                AcknowledgementsTab(Modifier.fillMaxSize())
                            }
                        }
                    }
                    composable<DetailPaneRoutes.Developers> {
                        DetailPaneRoute(
                            topAppBar = {
                                AniTopAppBar(
                                    title = { AniTopAppBarDefaults.Title(stringResource(Lang.developer_list)) },
                                    navigationIcon = {
                                        BackNavigationIconButton({ detailPaneNavController.navigateUp() })
                                    },
                                    colors = topAppBarColors,
                                    windowInsets = topAppBarWindowInsets,
                                    size = topAppBarSize,
                                    scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                )
                            },
                            detailPaneTopAppBarScrollBehavior,
                        ) {
                            RouteContent {
                                DevelopersTab(Modifier.fillMaxSize())
                            }
                        }
                    }
                    composable<DetailPaneRoutes.BangumiSync> {
                        DetailPaneRoute(
                            topAppBar = {
                                AniTopAppBar(
                                    title = { AniTopAppBarDefaults.Title("Bangumi 同步") },
                                    navigationIcon = {
                                        BackNavigationIconButton({ detailPaneNavController.navigateUp() })
                                    },
                                    colors = topAppBarColors,
                                    windowInsets = topAppBarWindowInsets,
                                    size = topAppBarSize,
                                    scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                )
                            },
                            detailPaneTopAppBarScrollBehavior,
                        ) {
                            RouteContent(scrollable = false) {
                                BangumiSyncTab()
                            }
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
interface SettingsDetailPaneScope : PaneScope {
    val detailPaneNavController: NavHostController
}

@Composable
private fun PaneScope.DetailPaneRoute(
    topAppBar: @Composable () -> Unit,
    detailPaneTopAppBarScrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    tabContent: @Composable (PaneScope.() -> Unit),
) {
    Column(modifier) {
        topAppBar()

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
                    .paneWindowInsetsPadding()
                    .nestedScroll(detailPaneTopAppBarScrollBehavior.nestedScrollConnection),
            ) {
                tabContent()
            }
        }
    }
}

@Serializable
internal sealed class DetailPaneRoutes {
    @Serializable
    data object Main : DetailPaneRoutes()

    @Serializable
    data object Acknowledgements : DetailPaneRoutes()

    @Serializable
    data object Developers : DetailPaneRoutes()

    @Serializable
    data object BangumiSync : DetailPaneRoutes()
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
                .padding(horizontal = 16.dp)
                .padding(top = paddingTop, bottom = 12.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Stable
private fun getIcon(tab: SettingsTab): ImageVector {
    return when (tab) {
        SettingsTab.PROFILE -> Icons.Outlined.AccountCircle
        SettingsTab.APPEARANCE -> Icons.Outlined.SettingsApplications
        SettingsTab.THEME -> Icons.Outlined.Palette
        SettingsTab.UPDATE -> Icons.Outlined.Update
        SettingsTab.PLAYER -> Icons.Outlined.SmartDisplay
        SettingsTab.MEDIA_SOURCE -> Icons.Outlined.Subscriptions
        SettingsTab.MEDIA_SELECTOR -> Icons.Outlined.FilterList
        SettingsTab.SERVER -> Icons.Outlined.Public
        SettingsTab.PROXY -> Icons.Outlined.VpnKey
        SettingsTab.BT -> Icons.Filled.P2p
//        SettingsTab.CACHE -> Icons.Rounded.Download // Icons.Outlined.Download 太 sharp 了
        SettingsTab.STORAGE -> Icons.Outlined.Storage
        SettingsTab.SETTINGS_BACKUP -> Icons.Outlined.Settings
        SettingsTab.ABOUT -> Icons.Outlined.Info
        SettingsTab.LOG -> Icons.Outlined.Feedback
        SettingsTab.DEBUG -> Icons.Outlined.Science
    }
}

@Stable
@Composable
private fun getName(tab: SettingsTab): String {
    return when (tab) {
        SettingsTab.PROFILE -> stringResource(Lang.settings_tab_account)
        SettingsTab.APPEARANCE -> stringResource(Lang.settings_tab_appearance)
        SettingsTab.THEME -> stringResource(Lang.settings_tab_theme)
        SettingsTab.PLAYER -> stringResource(Lang.settings_tab_player)
        SettingsTab.MEDIA_SOURCE -> stringResource(Lang.settings_tab_media_source)
        SettingsTab.MEDIA_SELECTOR -> stringResource(Lang.settings_tab_media_selector)
        SettingsTab.SERVER -> stringResource(Lang.settings_tab_danmaku)
        SettingsTab.PROXY -> stringResource(Lang.settings_tab_proxy)
        SettingsTab.BT -> stringResource(Lang.settings_tab_bt)
//        SettingsTab.CACHE -> stringResource(Lang.settings_tab_cache)
        SettingsTab.STORAGE -> stringResource(Lang.settings_tab_storage)
        SettingsTab.SETTINGS_BACKUP -> stringResource(Lang.settings_tab_settings_backup)
        SettingsTab.LOG -> stringResource(Lang.settings_tab_log)
        SettingsTab.UPDATE -> stringResource(Lang.settings_tab_update)
        SettingsTab.ABOUT -> stringResource(Lang.settings_tab_about)
        SettingsTab.DEBUG -> stringResource(Lang.settings_tab_debug)
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
