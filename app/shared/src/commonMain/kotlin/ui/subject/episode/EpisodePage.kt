/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.danmaku.protocol.DanmakuInfo
import me.him188.ani.app.domain.danmaku.protocol.DanmakuLocation
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.features.StreamType
import me.him188.ani.app.platform.features.getComponentAccessors
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.comment.CommentContext
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.LocalImageViewerHandler
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.effects.DarkStatusBarAppearance
import me.him188.ani.app.ui.foundation.effects.OnLifecycleEvent
import me.him188.ani.app.ui.foundation.effects.ScreenOnEffect
import me.him188.ani.app.ui.foundation.effects.ScreenRotationEffect
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.compareTo
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.layout.isSystemInFullscreen
import me.him188.ani.app.ui.foundation.layout.setRequestFullScreen
import me.him188.ani.app.ui.foundation.layout.setSystemBarVisible
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.foundation.theme.weaken
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.app.ui.subject.episode.comments.EpisodeCommentColumn
import me.him188.ani.app.ui.subject.episode.comments.EpisodeEditCommentSheet
import me.him188.ani.app.ui.subject.episode.danmaku.DanmakuEditor
import me.him188.ani.app.ui.subject.episode.danmaku.DummyDanmakuEditor
import me.him188.ani.app.ui.subject.episode.danmaku.PlayerDanmakuState
import me.him188.ani.app.ui.subject.episode.details.EpisodeDetails
import me.him188.ani.app.ui.subject.episode.notif.VideoNotifEffect
import me.him188.ani.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import me.him188.ani.app.videoplayer.ui.VideoControllerState
import me.him188.ani.app.videoplayer.ui.guesture.NoOpLevelController
import me.him188.ani.app.videoplayer.ui.guesture.asLevelController
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults.randomDanmakuPlaceholder
import me.him188.ani.app.videoplayer.ui.progress.rememberMediaProgressSliderState
import me.him188.ani.utils.platform.isMobile


/**
 * 番剧详情 (播放) 页面
 */
@Composable
fun EpisodeScene(
    viewModel: EpisodeViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    Column(modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
        ) {
            EpisodeSceneContent(
                viewModel,
                Modifier,
                windowInsets = windowInsets,
            )
        }
    }
}

@Composable
private fun EpisodeSceneContent(
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

    val playbackState by vm.playerState.state.collectAsStateWithLifecycle()
    if (playbackState.isPlaying) {
        ScreenOnEffect()
    }

    AutoPauseEffect(vm)

    VideoNotifEffect(vm)

    DarkStatusBarAppearance()

    if (vm.videoScaffoldConfig.autoFullscreenOnLandscapeMode) {
        ScreenRotationEffect {
            vm.isFullscreen = it
        }
    }

    SideEffect {
        context.setSystemBarVisible(!vm.isFullscreen)
    }

    BoxWithConstraints(modifier) {
        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
        val width = windowSizeClass.windowWidthSizeClass
        val height = windowSizeClass.windowHeightSizeClass

        val showExpandedUI = when {
            width == WindowWidthSizeClass.COMPACT && height == WindowHeightSizeClass.COMPACT -> false
            width == WindowWidthSizeClass.COMPACT && height > WindowHeightSizeClass.COMPACT -> false
            width > WindowWidthSizeClass.COMPACT && height == WindowHeightSizeClass.COMPACT -> true // #1279
            windowSizeClass.windowWidthSizeClass >= WindowWidthSizeClass.EXPANDED -> true // #932
            else -> false
        }
        CompositionLocalProvider(LocalImageViewerHandler provides imageViewer) {
            when {
                showExpandedUI || isSystemInFullscreen() ->
                    EpisodeSceneTabletVeryWide(vm, Modifier.fillMaxSize(), windowInsets)

                else -> EpisodeSceneContentPhone(vm, Modifier.fillMaxSize(), windowInsets)
            }
        }
        ImageViewer(imageViewer) { imageViewer.clear() }
    }

    vm.videoSourceResolver.ComposeContent()
}

@Composable
private fun EpisodeSceneTabletVeryWide(
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    var showEditCommentSheet by rememberSaveable { mutableStateOf(false) }
    var didSetPaused by rememberSaveable { mutableStateOf(false) }

    val pauseOnPlaying: () -> Unit = {
        if (vm.playerState.state.value.isPlaying) {
            didSetPaused = true
            vm.playerState.pause()
        } else {
            didSetPaused = false
        }
    }
    val tryUnpause: () -> Unit = {
        if (didSetPaused) {
            didSetPaused = false
            vm.playerState.resume()
        }
    }

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
                vm.videoControllerState,
                expanded = true,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                maintainAspectRatio = false,
                windowInsets = if (vm.isFullscreen) {
                    windowInsets
                } else {
                    // 非全屏右边还有东西
                    windowInsets.only(WindowInsetsSides.Left + WindowInsetsSides.Top)
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
                            EpisodeDetails(
                                vm.episodeDetailsState,
                                vm.episodeCarouselState,
                                vm.editableSubjectCollectionTypeState,
                                vm.danmakuStatistics,
                                vm.videoStatisticsFlow,
                                vm.mediaSelectorState,
                                vm.mediaSourceResultsPresentation,
                                vm.authState,
                                onSwitchEpisode = { episodeId ->
                                    if (!vm.episodeSelectorState.selectEpisodeId(episodeId)) {
                                        navigator.navigateEpisodeDetails(vm.subjectId, episodeId)
                                    }
                                },
                                onRefreshMediaSources = { vm.refreshFetch() },
                            )
                        }

                        1 -> EpisodeCommentColumn(
                            commentState = vm.episodeCommentState,
                            commentEditorState = vm.commentEditorState,
                            subjectId = vm.subjectId,
                            episodeId = vm.episodePresentation.episodeId,
                            setShowEditCommentSheet = { showEditCommentSheet = it },
                            pauseOnPlaying = pauseOnPlaying,
                            lazyListState = vm.commentLazyListState,
                        )
                    }
                }
            }
        }
    }
    if (showEditCommentSheet) {
        EpisodeEditCommentSheet(
            state = vm.commentEditorState,
            onDismiss = {
                showEditCommentSheet = false
                tryUnpause()
            },
        )
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
private fun EpisodeSceneContentPhone(
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    var showDanmakuEditor by rememberSaveable { mutableStateOf(false) }
    var showEditCommentSheet by rememberSaveable { mutableStateOf(false) }
    var didSetPaused by rememberSaveable { mutableStateOf(false) }

    val pauseOnPlaying: () -> Unit = {
        if (vm.videoScaffoldConfig.pauseVideoOnEditDanmaku && vm.playerState.state.value.isPlaying) {
            didSetPaused = true
            vm.playerState.pause()
        } else {
            didSetPaused = false
        }
    }
    val tryUnpause: () -> Unit = {
        if (didSetPaused) {
            didSetPaused = false
            vm.playerState.resume()
        }
    }

    EpisodeSceneContentPhoneScaffold(
        videoOnly = vm.isFullscreen,
        commentCount = { vm.episodeCommentState.count },
        video = {
            EpisodeVideo(vm, vm.videoControllerState, vm.isFullscreen)
        },
        episodeDetails = {
            val navigator = LocalNavigator.current
            EpisodeDetails(
                vm.episodeDetailsState,
                vm.episodeCarouselState,
                vm.editableSubjectCollectionTypeState,
                vm.danmakuStatistics,
                vm.videoStatisticsFlow,
                vm.mediaSelectorState,
                vm.mediaSourceResultsPresentation,
                vm.authState,
                onSwitchEpisode = { episodeId ->
                    if (!vm.episodeSelectorState.selectEpisodeId(episodeId)) {
                        navigator.navigateEpisodeDetails(vm.subjectId, episodeId)
                    }
                },
                onRefreshMediaSources = { vm.refreshFetch() },
                Modifier.fillMaxSize(),
            )
        },
        commentColumn = {
            EpisodeCommentColumn(
                commentState = vm.episodeCommentState,
                commentEditorState = vm.commentEditorState,
                subjectId = vm.subjectId,
                episodeId = vm.episodePresentation.episodeId,
                setShowEditCommentSheet = { showEditCommentSheet = it },
                pauseOnPlaying = pauseOnPlaying,
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
                vm.danmaku,
                onSend = { text ->
                    vm.danmaku.danmakuEditorText = ""
                    scope.launch {
                        vm.danmaku.send(
                            DanmakuInfo(
                                vm.playerState.getExactCurrentPositionMillis(),
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

    if (showEditCommentSheet) {
        EpisodeEditCommentSheet(
            vm.commentEditorState,
            onDismiss = {
                showEditCommentSheet = false
                tryUnpause()
            },
        )
    }
}

@Composable
private fun DetachedDanmakuEditorLayout(
    playerDanmakuState: PlayerDanmakuState,
    onSend: (text: String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("发送弹幕", style = MaterialTheme.typography.titleMedium)
        val isSending = playerDanmakuState.isSending.collectAsStateWithLifecycle()
        DanmakuEditor(
            text = playerDanmakuState.danmakuEditorText,
            onTextChange = { playerDanmakuState.danmakuEditorText = it },
            isSending = { isSending.value },
            placeholderText = remember { randomDanmakuPlaceholder() },
            onSend = onSend,
            Modifier.fillMaxWidth().focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(),
        )
    }
}

@Composable
fun EpisodeSceneContentPhoneScaffold(
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
    videoControllerState: VideoControllerState,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    maintainAspectRatio: Boolean = !expanded,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val context by rememberUpdatedState(LocalContext.current)

    // Don't rememberSavable. 刻意让每次切换都是隐藏的
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        videoControllerState.toggleFullVisible(false) // 每次切换全屏后隐藏
    }
    val videoDanmakuState = vm.danmaku


    // Refresh every time on configuration change (i.e. switching theme, entering fullscreen)
    val danmakuTextPlaceholder = remember { randomDanmakuPlaceholder() }
    val window = LocalPlatformWindow.current

    val progressSliderState = rememberMediaProgressSliderState(
        vm.playerState,
        onPreview = {
            // not yet supported
        },
        onPreviewFinished = {
            vm.playerState.seekTo(it)
        },
    )
    val scope = rememberCoroutineScope()

    // 必须在 UI 里, 跟随 context 变化. 否则 #958
    val platformComponents by remember {
        derivedStateOf {
            context.getComponentAccessors()
        }
    }
    EpisodeVideoImpl(
        vm.playerState,
        expanded = expanded,
        hasNextEpisode = vm.episodeSelectorState.hasNextEpisode,
        onClickNextEpisode = { vm.episodeSelectorState.selectNext() },
        videoControllerState = videoControllerState,
        title = {
            val episode = vm.episodePresentation
            val subject = vm.subjectPresentation
            EpisodePlayerTitle(
                episode.ep,
                episode.title,
                subject.title,
                Modifier.placeholder(episode.isPlaceholder || subject.isPlaceholder),
            )
        },
        danmakuHostState = videoDanmakuState.danmakuHostState,
        danmakuEnabled = videoDanmakuState.enabled,
        onToggleDanmaku = { videoDanmakuState.setEnabled(!videoDanmakuState.enabled) },
        videoLoadingStateFlow = vm.videoStatisticsFlow.map { it.videoLoadingState },
        onClickFullScreen = {
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
        },
        onExitFullscreen = {
            scope.launch {
                context.setRequestFullScreen(window, false)
                vm.isFullscreen = false
            }
        },
        danmakuEditor = {
            EpisodeVideoDefaults.DanmakuEditor(
                playerDanmakuState = videoDanmakuState,
                danmakuTextPlaceholder = danmakuTextPlaceholder,
                playerState = vm.playerState,
                videoScaffoldConfig = vm.videoScaffoldConfig,
                videoControllerState = videoControllerState,
            )
        },
        configProvider = remember(vm) { { vm.videoScaffoldConfig } },
        onClickScreenshot = {
            val currentPositionMillis = vm.playerState.currentPositionMillis.value
            val min = currentPositionMillis / 60000
            val sec = (currentPositionMillis - (min * 60000)) / 1000
            val ms = currentPositionMillis - (min * 60000) - (sec * 1000)
            val currentPosition = "${min}m${sec}s${ms}ms"
            // 条目ID-剧集序号-视频时间点.png
            val filename = "${vm.subjectId}-${vm.episodePresentation.ep}-${currentPosition}.png"
            vm.playerState.saveScreenshotFile(filename)
        },
        detachedProgressSlider = {
            PlayerControllerDefaults.MediaProgressSlider(
                progressSliderState,
                cacheProgressState = vm.playerState.cacheProgress,
                enabled = false,
            )
        },
        sidebarVisible = vm.sidebarVisible,
        onToggleSidebar = {
            vm.sidebarVisible = it
        },
        progressSliderState = progressSliderState,
        mediaSelectorState = vm.mediaSelectorState,
        onRefreshMediaSources = { vm.refreshFetch() },
        mediaSourceResultsPresentation = vm.mediaSourceResultsPresentation,
        episodeSelectorState = vm.episodeSelectorState,
        mediaSourceInfoProvider = vm.mediaSourceInfoProvider,
        audioController = remember {
            derivedStateOf {
                platformComponents.audioManager?.asLevelController(StreamType.MUSIC) ?: NoOpLevelController
            }
        }.value,
        brightnessController = remember {
            derivedStateOf {
                platformComponents.brightnessManager?.asLevelController() ?: NoOpLevelController
            }
        }.value,
        leftBottomTips = {
            AnimatedVisibility(
                visible = vm.playerSkipOpEdState.showSkipTips,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                PlayerControllerDefaults.LeftBottomTips(
                    onClick = {
                        vm.playerSkipOpEdState.cancelSkipOpEd()
                    },
                )
            }
        },
        modifier = modifier
            .fillMaxWidth().background(Color.Black)
            .then(if (expanded) Modifier.fillMaxSize() else Modifier.statusBarsPadding()),
        maintainAspectRatio = maintainAspectRatio,
        danmakuRegexFilterState = vm.danmakuRegexFilterState,
        contentWindowInsets = windowInsets,
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
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val toaster = LocalToaster.current
    val browserNavigator = LocalUriHandler.current

    EpisodeCommentColumn(
        state = commentState,
        editCommentStubText = commentEditorState.content,
        onClickReply = {
            setShowEditCommentSheet(true)
            commentEditorState.startEdit(CommentContext.Reply(it.toInt()))
            pauseOnPlaying()

        },
        onClickEditCommentStub = {
            commentEditorState.startEdit(
                CommentContext.Episode(subjectId, episodeId),
            )
            setShowEditCommentSheet(true)
        },
        onClickEditCommentStubEmoji = {
            commentEditorState.startEdit(
                CommentContext.Episode(subjectId, episodeId),
            )
            commentEditorState.toggleStickerPanelState(true)
            setShowEditCommentSheet(true)
        },
        onClickUrl = {
            RichTextDefaults.checkSanityAndOpen(it, browserNavigator, toaster)
        },
        modifier = modifier.fillMaxSize(),
        lazyListState = lazyListState,
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
            if (viewModel.playerState.state.value.isPlaying) {
                pausedVideo = true
                autoPauseTasker.launch {
                    // #160, 切换全屏时视频会暂停半秒
                    // > 这其实是之前写切后台自动暂停导致的，检测了 lifecycle 事件，切全屏和切后台是一样的事件。延迟一下就可以了
                    viewModel.playerState.pause() // 正在播放时, 切到后台自动暂停
                }
            } else {
                // 如果不是正在播放, 则不操作暂停, 当下次切回前台时, 也不要恢复播放
                pausedVideo = false
            }
        } else if (it == Lifecycle.Event.ON_START && pausedVideo) {
            autoPauseTasker.launch {
                viewModel.playerState.resume() // 切回前台自动恢复, 当且仅当之前是自动暂停的
            }
            pausedVideo = false
        }
    }
}
