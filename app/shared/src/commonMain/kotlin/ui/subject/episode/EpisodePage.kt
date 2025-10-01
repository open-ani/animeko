/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.features.StreamType
import me.him188.ani.app.platform.features.getComponentAccessors
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.danmaku.DanmakuEditorState
import me.him188.ani.app.ui.danmaku.DummyDanmakuEditor
import me.him188.ani.app.ui.danmaku.PlayerDanmakuEditor
import me.him188.ani.app.ui.danmaku.PlayerDanmakuHost
import me.him188.ani.app.ui.episode.danmaku.MatchingDanmakuDialog
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.LocalImageViewerHandler
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.effects.DarkStatusBarAppearance
import me.him188.ani.app.ui.foundation.effects.OnLifecycleEvent
import me.him188.ani.app.ui.foundation.effects.OverrideCaptionButtonAppearance
import me.him188.ani.app.ui.foundation.effects.ScreenOnEffect
import me.him188.ani.app.ui.foundation.effects.ScreenRotationEffect
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isHeightCompact
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastExpanded
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.layout.setRequestFullScreen
import me.him188.ani.app.ui.foundation.layout.setSystemBarVisible
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.theme.weaken
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.app.ui.subject.episode.comments.EpisodeCommentColumn
import me.him188.ani.app.ui.subject.episode.comments.EpisodeEditCommentSheet
import me.him188.ani.app.ui.subject.episode.details.EpisodeDetails
import me.him188.ani.app.ui.subject.episode.notif.VideoNotifEffect
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.components.FloatingFullscreenSwitchButton
import me.him188.ani.app.ui.subject.episode.video.components.SideSheets
import me.him188.ani.app.ui.subject.episode.video.sidesheet.DanmakuRegexFilterSettings
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorSheet
import me.him188.ani.app.ui.subject.episode.video.sidesheet.MediaSelectorSheet
import me.him188.ani.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.gesture.LevelController
import me.him188.ani.app.videoplayer.ui.gesture.NoOpLevelController
import me.him188.ani.app.videoplayer.ui.gesture.asLevelController
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults.randomDanmakuPlaceholder
import me.him188.ani.app.videoplayer.ui.progress.rememberMediaProgressSliderState
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.utils.platform.isAndroid
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isMobile
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.Screenshots
import org.openani.mediamp.features.toggleMute


/**
 * 番剧详情 (播放) 页面
 */
@Composable
fun EpisodeScreen(
    viewModel: EpisodeViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val themeSettings = LocalThemeSettings.current
    AniTheme(
        darkModeOverride = if (themeSettings.alwaysDarkInEpisodePage) DarkMode.DARK else null,
    ) {
        Column(modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
            ) {
                EpisodeScreenContent(
                    viewModel,
                    Modifier,
                    windowInsets = windowInsets,
                )
            }
        }
    }
}

@Composable
private fun EpisodeScreenContent(
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    // 处理当用户点击返回键时, 如果是全屏, 则退出全屏
    // 按返回退出全屏
    val context by rememberUpdatedState(LocalContext.current)
    val window = LocalPlatformWindow.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = vm.isFullscreen) {
        scope.launch {
            context.setRequestFullScreen(window, false)
            vm.isFullscreen = false
        }
    }

    // image viewer
    val imageViewer = rememberImageViewerHandler()
    BackHandler(enabled = imageViewer.viewing.value) { imageViewer.clear() }

    val playbackState by vm.player.playbackState.collectAsStateWithLifecycle()
    if (playbackState.isPlaying) {
        ScreenOnEffect()
    }

    var showEditCommentSheet by rememberSaveable { mutableStateOf(false) }
    var didSetPaused by rememberSaveable { mutableStateOf(false) }

    val pauseOnPlaying: () -> Unit = {
        if (vm.player.playbackState.value.isPlaying) {
            didSetPaused = true
            vm.player.pause()
        } else {
            didSetPaused = false
        }
    }
    val tryUnpause: () -> Unit = {
        if (didSetPaused) {
            didSetPaused = false
            vm.player.resume()
        }
    }

    AutoPauseEffect(vm)
    DisplayModeEffect(vm.videoScaffoldConfig)

    VideoNotifEffect(vm)

    DarkStatusBarAppearance()

    if (vm.videoScaffoldConfig.autoFullscreenOnLandscapeMode) {
        ScreenRotationEffect {
            vm.isFullscreen = it
        }
    }

    //Enable window fullscreen mode detection
    if (LocalPlatform.current.isDesktop()) {
        vm.isFullscreen = LocalPlatformWindow.current.isUndecoratedFullscreen
    }

    LaunchedEffect(vm.isFullscreen) {
        // Update system bar visibility whenever fullscreen state changes
        context.setSystemBarVisible(window, !vm.isFullscreen)
    }

    // 只有在首次进入的时候需要设置
    LaunchedEffect(Unit) {
        val audioController = vm.player.features[AudioLevelController]
        if (audioController != null) {
            val persistedPlayerVolume = vm.playerVolumeFlow.first()
            audioController.setVolume(persistedPlayerVolume.level)
            audioController.setMute(persistedPlayerVolume.mute)
        }
    }

    BoxWithConstraints(modifier) {
        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass

        val showExpandedUI = when {
            windowSizeClass.isWidthCompact && windowSizeClass.isHeightCompact -> false
            windowSizeClass.isWidthCompact && windowSizeClass.isHeightAtLeastMedium -> false
            windowSizeClass.isWidthAtLeastMedium && windowSizeClass.isHeightCompact -> true // #1279
            windowSizeClass.isWidthAtLeastExpanded -> true // #932
            else -> false
        }

        // only show dark caption button on compact ui and full screen mode(windows only).
        if (vm.isFullscreen || !showExpandedUI) {
            OverrideCaptionButtonAppearance(isDark = true)
        }

        val pageState = vm.pageState.collectAsStateWithLifecycle()

        when (val page = pageState.value) {
            null -> {
                // TODO: EpisodePage loading
            }

            else -> {
                LaunchedEffect(Unit) {
                    vm.collectDanmakuConfig()
                }

                val danmakuEditorState = remember(scope) {
                    DanmakuEditorState(
                        onPost = { vm.postDanmaku(it) },
                        onPostSuccess = { danmaku ->
                            vm.playerControllerState.toggleFullVisible(false)
                            scope.launch {
                                // May suspend for a while
                                vm.danmakuHostState.send(DanmakuPresentation(danmaku, isSelf = true))
                            }
                        },
                        scope,
                    )
                }

                page.matchingDanmakuUiState?.let { uiState ->
                    MatchingDanmakuDialog(
                        onDismissRequest = { vm.cancelMatchingDanmaku() },
                        initialQuery = uiState.initialQuery,
                        page.matchingDanmakuUiState,
                        onSubmitQuery = {
                            page.matchingDanmakuPresenter?.submitQuery(it)
                        },
                        onSelectSubject = {
                            page.matchingDanmakuPresenter?.selectSubject(it)
                        },
                        onSelectEpisode = {
                            page.matchingDanmakuPresenter?.selectEpisode(it)
                        },
                        onComplete = { list ->
                            page.matchingDanmakuPresenter?.providerId?.let {
                                vm.onMatchingDanmakuComplete(it, list)
                            }
                        },
                    )
                }

                CompositionLocalProvider(LocalImageViewerHandler provides imageViewer) {
                    when {
                        showExpandedUI ->
                            EpisodeScreenTabletVeryWide(
                                vm,
                                page,
                                vm.danmakuHostState,
                                danmakuEditorState,
                                page.fetchRequest,
                                { vm.updateFetchRequest(it) },
                                pauseOnPlaying = pauseOnPlaying,
                                tryUnpause = tryUnpause,
                                setShowEditCommentSheet = { showEditCommentSheet = it },
                                modifier = Modifier.fillMaxSize(),
                                windowInsets = windowInsets,
                            )

                        else -> EpisodeScreenContentPhone(
                            vm,
                            page,
                            vm.danmakuHostState,
                            danmakuEditorState,
                            Modifier.fillMaxSize(),
                            pauseOnPlaying = pauseOnPlaying,
                            tryUnpause = tryUnpause,
                            setShowEditCommentSheet = { showEditCommentSheet = it },
                            windowInsets,
                        )
                    }
                }
            }
        }
        ImageViewer(imageViewer) { imageViewer.clear() }
    }

    if (showEditCommentSheet) {
        EpisodeEditCommentSheet(
            state = vm.commentEditorState,
            turnstileState = vm.turnstileState,
            onDismiss = {
                showEditCommentSheet = false
                vm.turnstileState.cancel()
                vm.commentEditorState.cancelSend()
                tryUnpause()
            },
            onSendComplete = {
                scope.launch {
                    vm.commentLazyGirdState.scrollToItem(0)
                }
            },
        )
    }

    vm.mediaResolver.ComposeContent()
}

@Composable
private fun EpisodeScreenTabletVeryWide(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    danmakuHostState: DanmakuHostState,
    danmakuEditorState: DanmakuEditorState,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    pauseOnPlaying: () -> Unit,
    tryUnpause: () -> Unit,
    setShowEditCommentSheet: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    BoxWithConstraints {
        val maxWidth = maxWidth
        Row(
            modifier
                .then(
                    if (vm.isFullscreen) Modifier.fillMaxSize()
                    else Modifier,
                ),
        ) {
            EpisodeVideo(
                // do consume insets
                vm,
                page,
                danmakuHostState,
                danmakuEditorState,
                vm.playerControllerState,
                expanded = true,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                maintainAspectRatio = false,
                windowInsets = if (vm.isFullscreen) {
                    windowInsets
                } else {
                    // 非全屏右边还有东西
                    // Consider #1923 平板横屏模式下播放器底栏和导航栏重合
                    windowInsets.only(WindowInsetsSides.Left + WindowInsetsSides.Vertical)
                },
            )

            if (vm.isFullscreen || !vm.sidebarVisible) {
                return@Row
            }

            val pagerState = rememberPagerState(initialPage = 0) { 2 }
            val scope = rememberCoroutineScope()

            Column(
                Modifier
                    .width(
                        width = (maxWidth * 0.25f)
                            .coerceIn(340.dp, 460.dp),
                    )
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Right + WindowInsetsSides.Bottom))
                    .background(MaterialTheme.colorScheme.background), // scrollable background
            ) {

                val themeSettings = LocalThemeSettings.current
                val isEpPageDarkTheme = when {
                    themeSettings.alwaysDarkInEpisodePage -> true
                    themeSettings.darkMode == DarkMode.AUTO -> isSystemInDarkTheme()
                    else -> themeSettings.darkMode == DarkMode.DARK
                }
                // 如果当前不是 dark theme 并且 是安卓平台 并且 没有设置播放页始终使用暗色主题，则加一个渐变色避免看不清状态栏
                // ios 宽屏模式下会自动隐藏状态栏, 无需处理
                val needShadeBackground = !isEpPageDarkTheme && LocalPlatform.current.isAndroid() 
                // 填充 insets 背景颜色
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .ifThen(needShadeBackground) {
                            background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.scrim, Color.Transparent)))
                        }
                        .windowInsetsPadding(
                            // Consider #1767
                            WindowInsets.safeContent // Note: this does not include desktop title bar.
                                .only(WindowInsetsSides.Top),
                        ),
                )
                TabRow(
                    pagerState, scope, { vm.episodeCommentState.count }, Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.weaken())

                HorizontalPager(
                    state = pagerState,
                    Modifier.fillMaxSize(),
                    userScrollEnabled = LocalPlatform.current.isMobile(),
                ) { index ->
                    when (index) {
                        0 -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            val navigator = LocalNavigator.current
                            val pageState by vm.pageState.collectAsStateWithLifecycle()
                            val toaster = LocalToaster.current
                            pageState?.let { page ->
                                EpisodeDetails(
                                    page.mediaSelectorSummary,
                                    vm.episodeDetailsState,
                                    page.initialMediaSelectorViewKind,
                                    fetchRequest,
                                    onFetchRequestChange,
                                    vm.episodeCarouselState,
                                    vm.editableSubjectCollectionTypeState,
                                    page.danmakuStatistics,
                                    vm.videoStatisticsFlow,
                                    page.mediaSelectorState,
                                    { page.mediaSourceResultListPresentation },
                                    page.selfInfo,
                                    onSwitchEpisode = { episodeId ->
                                        if (!vm.episodeSelectorState.selectEpisodeId(episodeId)) {
                                            navigator.navigateEpisodeDetails(vm.subjectId, episodeId)
                                        }
                                    },
                                    onRefreshMediaSources = { vm.refreshFetch() },
                                    onRestartSource = { vm.restartSource(it) },
                                    onSetDanmakuSourceEnabled = { providerId, enabled ->
                                        vm.setDanmakuSourceEnabled(providerId, enabled)
                                    },
                                    onClickLogin = { navigator.navigateBangumiAuthorize() },
                                    onClickTag = { navigator.navigateSubjectSearch(it.name) },
                                    onManualMatchDanmaku = {
                                        vm.startMatchingDanmaku(it)
                                    },
                                    onEpisodeCollectionUpdate = { request ->
                                        scope.launch {
                                            vm.setEpisodeCollectionType.invokeSafe(request)?.let {
                                                toaster.showLoadError(it)
                                            }
                                        }
                                    },
                                    shareData = page.shareData,
                                    page.loadError,
                                    onRetryLoad = {
                                        page.loadError?.let {
                                            vm.retryLoad(it)
                                        }
                                    },
                                )
                            }
                        }

                        1 -> {
                            EpisodeCommentColumn(
                                commentState = vm.episodeCommentState,
                                commentEditorState = vm.commentEditorState,
                                subjectId = vm.subjectId,
                                episodeId = page.episodePresentation.episodeId,
                                setShowEditCommentSheet = setShowEditCommentSheet,
                                pauseOnPlaying = pauseOnPlaying,
                                gridState = vm.commentLazyGirdState,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabRow(
    pagerState: PagerState,
    scope: CoroutineScope,
    commentCount: () -> Int?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    ScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        modifier,
        indicator = @Composable { tabPositions ->
            TabRowDefaults.PrimaryIndicator(
                Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
            )
        },
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor),
        edgePadding = 0.dp,
        divider = {},
    ) {
        Tab(
            selected = pagerState.currentPage == 0,
            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
            text = { Text("详情", softWrap = false) },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
        )
        Tab(
            selected = pagerState.currentPage == 1,
            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
            text = {
                val text by remember(commentCount) {
                    derivedStateOf {
                        val count = commentCount()
                        if (count == null) "评论" else "评论 $count"
                    }
                }
                Text(text, softWrap = false)
            },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EpisodeScreenContentPhone(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    danmakuHostState: DanmakuHostState,
    danmakuEditorState: DanmakuEditorState,
    modifier: Modifier = Modifier,
    pauseOnPlaying: () -> Unit,
    tryUnpause: () -> Unit,
    setShowEditCommentSheet: (Boolean) -> Unit,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    var showDanmakuEditor by rememberSaveable { mutableStateOf(false) }
    val toaster = LocalToaster.current

    EpisodeScreenContentPhoneScaffold(
        videoOnly = vm.isFullscreen,
        commentCount = { vm.episodeCommentState.count },
        video = {
            EpisodeVideo(
                vm, page,
                danmakuHostState,
                danmakuEditorState, vm.playerControllerState, vm.isFullscreen,
                windowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.desktopTitleBar),
            )
        },
        episodeDetails = {
            val navigator = LocalNavigator.current
            val pageState by vm.pageState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            pageState?.let { page ->
                EpisodeDetails(
                    page.mediaSelectorSummary,
                    vm.episodeDetailsState,
                    page.initialMediaSelectorViewKind,
                    page.fetchRequest,
                    { vm.updateFetchRequest(it) },
                    vm.episodeCarouselState,
                    vm.editableSubjectCollectionTypeState,
                    page.danmakuStatistics,
                    vm.videoStatisticsFlow,
                    page.mediaSelectorState,
                    { page.mediaSourceResultListPresentation },
                    page.selfInfo,
                    onSwitchEpisode = { episodeId ->
                        if (!vm.episodeSelectorState.selectEpisodeId(episodeId)) {
                            navigator.navigateEpisodeDetails(vm.subjectId, episodeId)
                        }
                    },
                    onRefreshMediaSources = { vm.refreshFetch() },
                    onRestartSource = { vm.restartSource(it) },
                    onSetDanmakuSourceEnabled = { providerId, enabled ->
                        vm.setDanmakuSourceEnabled(providerId, enabled)
                    },
                    onClickLogin = { navigator.navigateBangumiAuthorize() },
                    onClickTag = { navigator.navigateSubjectSearch(it.name) },
                    onManualMatchDanmaku = {
                        vm.startMatchingDanmaku(it)
                    },
                    onEpisodeCollectionUpdate = { request ->
                        scope.launch {
                            vm.setEpisodeCollectionType.invokeSafe(request)?.let {
                                toaster.showLoadError(it)
                            }
                        }
                    },
                    shareData = page.shareData,
                    loadError = page.loadError,
                    onRetryLoad = {
                        page.loadError?.let { vm.retryLoad(it) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        commentColumn = {
            EpisodeCommentColumn(
                commentState = vm.episodeCommentState,
                commentEditorState = vm.commentEditorState,
                subjectId = vm.subjectId,
                episodeId = page.episodePresentation.episodeId,
                setShowEditCommentSheet = setShowEditCommentSheet,
                pauseOnPlaying = pauseOnPlaying,
                gridState = vm.commentLazyGirdState,
            )
        },
        modifier.then(if (vm.isFullscreen) Modifier.fillMaxSize() else Modifier.navigationBarsPadding()),
        tabRowContent = {
            DummyDanmakuEditor(
                onClick = {
                    showDanmakuEditor = true
                    pauseOnPlaying()
                },
            )
        },
    )

    if (showDanmakuEditor) {
        val focusRequester = remember { FocusRequester() }
        val dismiss = {
            showDanmakuEditor = false
            tryUnpause()
        }
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = dismiss,
            modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
            contentWindowInsets = { BottomSheetDefaults.windowInsets.union(windowInsets) },
        ) {
            DetachedDanmakuEditorLayout(
                danmakuEditorState,
                onSend = { text ->
                    scope.launch {
                        danmakuEditorState.post(
                            DanmakuContent(
                                vm.player.getCurrentPositionMillis(),
                                text = text,
                                color = Color.White.toArgb(),
                                location = DanmakuLocation.NORMAL,
                            ),
                        )
                        dismiss()
                    }
                },
                focusRequester,
                Modifier.imePadding(),
            )
            LaunchedEffect(true) {
                focusRequester.requestFocus()
            }
        }
    }
}

@Composable
private fun DetachedDanmakuEditorLayout(
    danmakuEditorState: DanmakuEditorState,
    onSend: (text: String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("发送弹幕", style = MaterialTheme.typography.titleMedium)
        val isSending = danmakuEditorState.isSending.collectAsStateWithLifecycle()
        PlayerDanmakuEditor(
            text = danmakuEditorState.text,
            onTextChange = { danmakuEditorState.text = it },
            isSending = { isSending.value },
            placeholderText = remember { randomDanmakuPlaceholder() },
            onSend = onSend,
            Modifier.fillMaxWidth().focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(),
        )
    }
}

@Composable
fun EpisodeScreenContentPhoneScaffold(
    videoOnly: Boolean,
    commentCount: () -> Int?,
    video: @Composable () -> Unit,
    episodeDetails: @Composable () -> Unit,
    commentColumn: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tabRowContent: @Composable () -> Unit = {},
) {
    Column(modifier) {
        video()

        if (videoOnly) {
            return@Column
        }

        val pagerState = rememberPagerState(initialPage = 0) { 2 }
        val scope = rememberCoroutineScope()

        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row {
                    TabRow(
                        pagerState, scope, commentCount, Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    Box(
                        modifier = Modifier.weight(0.618f) // width
                            .height(48.dp)
                            .padding(vertical = 4.dp, horizontal = 16.dp),
                    ) {
                        Row(Modifier.align(Alignment.CenterEnd)) {
                            tabRowContent()
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.weaken())

            HorizontalPager(state = pagerState, Modifier.fillMaxSize()) { index ->
                when (index) {
                    0 -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        episodeDetails()
                    }

                    1 -> Box(Modifier.fillMaxSize()) {
                        commentColumn()
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeVideo(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    danmakuHostState: DanmakuHostState,
    danmakuEditorState: DanmakuEditorState,
    playerControllerState: PlayerControllerState,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    maintainAspectRatio: Boolean = !expanded,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val context by rememberUpdatedState(LocalContext.current)

    // Don't rememberSavable. 刻意让每次切换都是隐藏的
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        playerControllerState.toggleFullVisible(false) // 每次切换全屏后隐藏
    }

    // Refresh every time on configuration change (i.e. switching theme, entering fullscreen)
    val danmakuTextPlaceholder = remember { randomDanmakuPlaceholder() }
    val window = LocalPlatformWindow.current

    SideEffect {
        vm.onUIReady()
    }

    val progressSliderState = rememberMediaProgressSliderState(
        vm.player,
        vm.progressChaptersFlow,
        onPreview = {
            // not yet supported
        },
        onPreviewFinished = {
            vm.player.seekTo(it)
        },
    )
    val scope = rememberCoroutineScope()

    // 必须在 UI 里, 跟随 context 变化. 否则 #958
    val platformComponents by remember {
        derivedStateOf {
            context.getComponentAccessors()
        }
    }
    val onClickFullScreen: () -> Unit = {
        if (vm.isFullscreen) {
            scope.launch {
                context.setRequestFullScreen(window, false)
                vm.isFullscreen = false
            }
        } else {
            scope.launch {
                vm.isFullscreen = true
                context.setRequestFullScreen(window, true)
            }
        }
    }

    EpisodeVideoImpl(
        vm.player,
        expanded = expanded,
        hasNextEpisode = vm.episodeSelectorState.hasNextEpisode,
        onClickNextEpisode = { vm.episodeSelectorState.selectNext() },
        playerControllerState = playerControllerState,
        onClickSkip85 = { vm.onClickSkip85(it) },
        title = {
            val episode = page.episodePresentation
            val subject = page.subjectPresentation
            EpisodePlayerTitle(
                episode.ep,
                episode.title,
                subject.title,
                Modifier.placeholder(episode.isPlaceholder || subject.isPlaceholder),
            )
        },
        danmakuHost = {
            PlayerDanmakuHost(vm.player, danmakuHostState, vm.uiDanmakuEventFlow)
        },
        danmakuEnabled = page.danmakuEnabled,
        onToggleDanmaku = { vm.setDanmakuEnabled(!page.danmakuEnabled) },
        videoLoadingStateFlow = vm.videoStatisticsFlow.map { it.videoLoadingState },
        onClickFullScreen = onClickFullScreen,
        onExitFullscreen = {
            scope.launch {
                context.setRequestFullScreen(window, false)
                vm.isFullscreen = false
            }
        },
        danmakuEditor = {
            PlayerDanmakuEditor(
                danmakuEditorState,
                danmakuTextPlaceholder = danmakuTextPlaceholder,
                playerState = vm.player,
                videoScaffoldConfig = vm.videoScaffoldConfig,
                playerControllerState = playerControllerState,
            )
        },
        onClickScreenshot = {
            val currentPositionMillis = vm.player.getCurrentPositionMillis()
            val min = currentPositionMillis / 60000
            val sec = (currentPositionMillis - (min * 60000)) / 1000
            val ms = currentPositionMillis - (min * 60000) - (sec * 1000)
            val currentPosition = "${min}m${sec}s${ms}ms"
            // 条目ID-剧集序号-视频时间点.png
            val filename = "${vm.subjectId}-${page.episodePresentation.ep}-${currentPosition}.png"
            scope.launch {
                vm.player.features[Screenshots]?.takeScreenshot(filename)
            }
        },
        detachedProgressSlider = {
            PlayerControllerDefaults.MediaProgressSlider(
                progressSliderState,
                cacheProgressInfoFlow = vm.cacheProgressInfoFlow,
                enabled = false,
            )
        },
        sidebarVisible = vm.sidebarVisible,
        onToggleSidebar = {
            vm.sidebarVisible = it
        },
        progressSliderState = progressSliderState,
        cacheProgressInfoFlow = vm.cacheProgressInfoFlow,
        audioController = remember {
            derivedStateOf {
                platformComponents.audioManager?.asLevelController(StreamType.MUSIC)
                    ?: vm.player.features[AudioLevelController]
                        ?.let { MediampAudioLevelController(it, vm::savePlayerVolume) }
                    ?: NoOpLevelController
            }
        }.value,
        brightnessController = remember {
            derivedStateOf {
                platformComponents.brightnessManager?.asLevelController() ?: NoOpLevelController
            }
        }.value,
        playbackSpeedControllerState = remember {
            vm.player.features[PlaybackSpeed]?.let { PlaybackSpeedControllerState(it, scope = scope) }
        },
        leftBottomTips = {
            AniAnimatedVisibility(
                visible = vm.playerSkipOpEdState.showSkipTips,
            ) {
                PlayerControllerDefaults.LeftBottomTips(
                    onClick = {
                        vm.playerSkipOpEdState.cancelSkipOpEd()
                    },
                )
            }
        },
        fullscreenSwitchButton = {
            EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
                vm.videoScaffoldConfig.fullscreenSwitchMode,
                isFullscreen = expanded,
                onClickFullScreen,
            )
        },
        sideSheets = { sheetsController ->
            EpisodeVideoDefaults.SideSheets(
                sheetsController,
                playerControllerState,
                playerSettingsPage = {
                    EpisodeVideoSideSheets.DanmakuSettingsNavigatorSheet(
                        expanded = expanded,
                        state = vm.danmakuRegexFilterState,
                        onDismissRequest = { goBack() },
                        onNavigateToFilterSettings = {
                            sheetsController.navigateTo(EpisodeVideoSideSheetPage.EDIT_DANMAKU_REGEX_FILTER)
                        },
                    )
                },
                editDanmakuRegexFilterPage = {
                    DanmakuRegexFilterSettings(
                        state = vm.danmakuRegexFilterState,
                        onDismissRequest = { goBack() },
                        expanded = expanded,
                    )
                },
                mediaSelectorPage = {
                    val pageState by vm.pageState.collectAsStateWithLifecycle()
                    pageState?.let { page ->
                        val (viewKind, onViewKindChange) = rememberSaveable { mutableStateOf(page.initialMediaSelectorViewKind) }
                        EpisodeVideoSideSheets.MediaSelectorSheet(
                            page.mediaSelectorState,
                            page.mediaSourceResultListPresentation,
                            viewKind,
                            onViewKindChange,
                            page.fetchRequest,
                            { vm.updateFetchRequest(it) },
                            onDismissRequest = { goBack() },
                            onRefresh = { vm.refreshFetch() },
                            onRestartSource = { vm.restartSource(it) },
                        )
                    }
                },
                episodeSelectorPage = {
                    EpisodeVideoSideSheets.EpisodeSelectorSheet(
                        vm.episodeSelectorState,
                        onDismissRequest = { goBack() },
                    )
                },
            )
        },
        modifier = modifier
            .fillMaxWidth().background(Color.Black)
            .then(if (expanded) Modifier.fillMaxSize() else Modifier.statusBarsPadding()),
        maintainAspectRatio = maintainAspectRatio,
        contentWindowInsets = windowInsets,
        fastForwardSpeed = vm.videoScaffoldConfig.fastForwardSpeed,
    )
}

@Composable
private fun EpisodeCommentColumn(
    commentState: CommentState,
    commentEditorState: CommentEditorState,
    subjectId: Int,
    episodeId: Int,
    setShowEditCommentSheet: (Boolean) -> Unit,
    pauseOnPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val toaster = LocalToaster.current
    val browserNavigator = LocalUriHandler.current

    EpisodeCommentColumn(
        state = commentState,
        onClickReply = {
            setShowEditCommentSheet(true)
            commentEditorState.startEdit(CommentContext.EpisodeReply(subjectId, episodeId, it.toInt()))
            pauseOnPlaying()

        },
        onNewCommentClick = {
            commentEditorState.startEdit(
                CommentContext.Episode(subjectId, episodeId),
            )
            setShowEditCommentSheet(true)
        },
        onClickUrl = {
            RichTextDefaults.checkSanityAndOpen(it, browserNavigator, toaster)
        },
        modifier = modifier.fillMaxSize(),
        gridState = gridState,
    )
}


/**
 * 切后台自动暂停
 */
@Composable
private fun AutoPauseEffect(viewModel: EpisodeViewModel) {
    var pausedVideo by rememberSaveable { mutableStateOf(true) } // live after configuration change
    if (LocalIsPreviewing.current) return

    val autoPauseTasker = rememberUiMonoTasker()
    OnLifecycleEvent {
        if (it == Lifecycle.Event.ON_STOP) {
            if (viewModel.player.playbackState.value.isPlaying) {
                pausedVideo = true
                autoPauseTasker.launch {
                    // #160, 切换全屏时视频会暂停半秒
                    // > 这其实是之前写切后台自动暂停导致的，检测了 lifecycle 事件，切全屏和切后台是一样的事件。延迟一下就可以了
                    viewModel.player.pause() // 正在播放时, 切到后台自动暂停
                }
            } else {
                // 如果不是正在播放, 则不操作暂停, 当下次切回前台时, 也不要恢复播放
                pausedVideo = false
            }
        } else if (it == Lifecycle.Event.ON_START && pausedVideo) {
            autoPauseTasker.launch {
                viewModel.player.resume() // 切回前台自动恢复, 当且仅当之前是自动暂停的
            }
            pausedVideo = false
        }
    }
}

@Composable
internal expect fun DisplayModeEffect(config: VideoScaffoldConfig)

/**
 * Delegation of [AudioLevelController], which allows observing volume state changes.
 */
class MediampAudioLevelController(
    private val controller: AudioLevelController,
    private val onVolumeStateChanged: (level: Float, mute: Boolean) -> Unit,
) : LevelController {
    override val level: Float get() = controller.volume.value

    val levelFlow = controller.volume
    val muteFlow = controller.isMute

    override val range: ClosedRange<Float> = 0f..controller.maxVolume

    override fun setLevel(level: Float) {
        val newLevel = level.coerceIn(range)
        controller.setVolume(newLevel)
        onVolumeStateChanged(newLevel, controller.isMute.value)
    }

    fun toggleMute() {
        val targetIsMute = !muteFlow.value
        controller.toggleMute()
        onVolumeStateChanged(level, targetIsMute)
    }
}
