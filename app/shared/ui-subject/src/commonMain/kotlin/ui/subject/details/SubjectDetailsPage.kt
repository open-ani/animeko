/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeRequest
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.Tag
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.interaction.nestedScrollWorkaround
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.foundation.layout.PaddingValuesSides
import me.him188.ani.app.ui.foundation.layout.connectedScrollContainer
import me.him188.ani.app.ui.foundation.layout.connectedScrollTarget
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.only
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.layout.rememberConnectedScrollState
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.theme.MaterialThemeFromImage
import me.him188.ani.app.ui.foundation.toComposeImageBitmap
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.rating.EditableRating
import me.him188.ani.app.ui.rating.EditableRatingState
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import me.him188.ani.app.ui.subject.details.components.CollectionData
import me.him188.ani.app.ui.subject.details.components.DetailsTab
import me.him188.ani.app.ui.subject.details.components.SeasonTag
import me.him188.ani.app.ui.subject.details.components.SelectEpisodeButtons
import me.him188.ani.app.ui.subject.details.components.SubjectBlurredBackground
import me.him188.ani.app.ui.subject.details.components.SubjectCommentColumn
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults.MaximumContentWidth
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsHeader
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDialog
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.toggleCollected
import me.him188.ani.utils.platform.isMobile

// region screen

@Composable
fun SubjectDetailsScreen(
    vm: SubjectDetailsViewModel,
    onPlay: (episodeId: Int) -> Unit,
    onLoadErrorRetry: () -> Unit,
    onClickTag: (Tag) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle(null)
    val selfInfo by vm.authState.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.reload()
    }

    SubjectDetailsScreen(
        state,
        selfInfo,
        onPlay = onPlay,
        onLoadErrorRetry,
        onClickTag,
        { request ->
            scope.launch {
                vm.setEpisodeCollectionType.invokeSafe(request)?.let {
                    toaster.showLoadError(it)
                }
            }
        },
        modifier,
        showTopBar,
        showBlurredBackground,
        windowInsets,
        navigationIcon,
    )
}

@Composable
fun SubjectDetailsScreen(
    state: SubjectDetailsUIState?,
    selfInfo: SelfInfoUiState,
    onPlay: (episodeId: Int) -> Unit,
    onLoadErrorRetry: () -> Unit,
    onClickTag: (Tag) -> Unit,
    onEpisodeCollectionUpdate: (SetEpisodeCollectionTypeRequest) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val onClickOpenExternal = {
        if (state != null) uriHandler.openUri("https://bgm.tv/subject/${state.subjectId}")
    }

    when (state) {
        null, is SubjectDetailsUIState.Placeholder -> PlaceholderSubjectDetailsPage(
            state?.subjectInfo,
            modifier,
            showTopBar,
            windowInsets,
            navigationIcon,
            onClickOpenExternal,
        )

        is SubjectDetailsUIState.Ok -> SubjectDetailsPage(
            state.value,
            selfInfo,
            onPlay = onPlay,
            onClickLogin = { navigator.navigateEmailLoginStart() },
            onClickTag,
            onEpisodeCollectionUpdate = onEpisodeCollectionUpdate,
            modifier,
            showTopBar,
            showBlurredBackground,
            windowInsets,
            navigationIcon,
            onClickOpenExternal,
        )

        is SubjectDetailsUIState.Err -> ErrorSubjectDetailsPage(
            state.placeholder,
            error = state.error,
            onRetry = onLoadErrorRetry,
            modifier,
            showTopBar,
            windowInsets,
            navigationIcon,
            onClickOpenExternal,
        )
    }
}

// endregion

// region page

@Composable
private fun SubjectDetailsPage(
    state: SubjectDetailsState,
    selfInfo: SelfInfoUiState,
    onPlay: (episodeId: Int) -> Unit,
    onClickLogin: () -> Unit,
    onClickTag: (Tag) -> Unit,
    onEpisodeCollectionUpdate: (SetEpisodeCollectionTypeRequest) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
) {
    val toaster = LocalToaster.current
    val browserNavigator = LocalUriHandler.current
    val navigator = LocalNavigator.current

    var showSelectEpisode by rememberSaveable { mutableStateOf(false) }
    val connectedScrollState = rememberConnectedScrollState()

    // image viewer
    val imageViewer = rememberImageViewerHandler()
    BackHandler(enabled = imageViewer.viewing.value) { imageViewer.clear() }

    val presentation by state.presentation.collectAsStateWithLifecycle()

    val themeSettings = LocalThemeSettings.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    MaterialThemeFromImage(bitmap) {
        if (showSelectEpisode) {
            EpisodeListDialog(
                presentation.episodeListUiState,
                onDismissRequest = { showSelectEpisode = false },
                { navigator.navigateSubjectCaches(presentation.subjectId) },
                { navigator.navigateEpisodeDetails(presentation.subjectId, it.episodeId) },
                {
                    onEpisodeCollectionUpdate(
                        SetEpisodeCollectionTypeRequest(
                            presentation.subjectId,
                            it.episodeId,
                            it.collectionType.toggleCollected(),
                        ),
                    )
                },
            )
        }

        SubjectDetailsLayout(
            info = state.info,
            seasonTags = {
                SubjectDetailsDefaults.SeasonTag(
                    airDate = state.info?.airDate ?: PackedDate.Invalid,
                    airingLabelState = state.airingLabelState,
                )
            },
            collectionData = {
                SubjectDetailsDefaults.CollectionData(state.info?.collectionStats ?: SubjectCollectionStats.Zero)
            },
            collectionActions = {
                if (selfInfo.isSessionValid == false) {
                    OutlinedButton(onClickLogin) {
                        Text("登录后可收藏")
                    }
                } else {
                    EditableSubjectCollectionTypeButton(state.editableSubjectCollectionTypeState)
                }
            },
            rating = {
                EditableRating(state.editableRatingState)
            },
            selectEpisodeButton = {
                SubjectDetailsDefaults.SelectEpisodeButtons(
                    state.subjectProgressState,
                    onShowEpisodeList = { showSelectEpisode = true },
                    onPlay = onPlay,
                )
            },
            connectedScrollState = connectedScrollState,
            modifier,
            showTopBar = showTopBar,
            showBlurredBackground = showBlurredBackground,
            windowInsets = windowInsets,
            navigationIcon = navigationIcon,
            onCoverImageSuccess = { success ->
                if (themeSettings.useDynamicSubjectPageTheme) {
                    bitmap = success.result.image.toComposeImageBitmap()
                }
            },
            onClickOpenExternal = onClickOpenExternal,
        ) { paddingValues ->
            SubjectDetailsContentPager(
                paddingValues,
                connectedScrollState,
                onClickAddRating = { state.editableRatingState.requestEdit() },
                detailsTab = { contentPadding ->
                    if (state.info == null) return@SubjectDetailsContentPager
                    SubjectDetailsDefaults.DetailsTab(
                        info = state.info,
                        onClickTag,
                        staff = state.staffPager.collectAsLazyPagingItemsWithLifecycle(),
                        exposedStaff = state.exposedStaffPager.collectAsLazyPagingItemsWithLifecycle(),
                        totalStaffCount = state.totalStaffCountState.value,
                        characters = state.charactersPager.collectAsLazyPagingItemsWithLifecycle(),
                        exposedCharacters = state.exposedCharactersPager.collectAsLazyPagingItemsWithLifecycle(),
                        totalCharactersCount = state.totalCharactersCountState.value,
                        relatedSubjects = state.relatedSubjectsPager.collectAsLazyPagingItemsWithLifecycle(),
                        Modifier
                            .nestedScrollWorkaround(state.detailsTabLazyListState, connectedScrollState)
                            .nestedScroll(connectedScrollState.nestedScrollConnection),
                        state.detailsTabLazyListState,
                        contentPadding = contentPadding,
                    )
                },
                commentsTab = { contentPadding ->
                    SubjectDetailsDefaults.SubjectCommentColumn(
                        state = state.subjectCommentState,
                        onClickUrl = {
                            RichTextDefaults.checkSanityAndOpen(it, browserNavigator, toaster)
                        },
                        onClickImage = { imageViewer.viewImage(it) },
                        connectedScrollState,
                        Modifier.fillMaxSize(),
                        gridState = state.commentTabLazyGridState,
                        contentPadding = contentPadding,
                    )
                },
                discussionsTab = {
                    LazyColumn(
                        Modifier.fillMaxSize()
                            // TODO: Add nestedScrollWorkaround when we implement this tab
                            .nestedScroll(connectedScrollState.nestedScrollConnection),
                    ) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("即将上线, 敬请期待", Modifier.padding(16.dp))
                            }
                        }
                    }
                },
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(align = Alignment.CenterHorizontally)
                    .widthIn(max = MaximumContentWidth),
            )
        }
    }

    ImageViewer(imageViewer) { imageViewer.clear() }
}

@Composable
private fun PlaceholderSubjectDetailsPage(
    subjectInfo: SubjectInfo?,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
) {
    val connectedScrollState = rememberConnectedScrollState()

    SubjectDetailsLayout(
        info = subjectInfo,
        seasonTags = {
            SubjectDetailsDefaults.SeasonTag(
                airDate = remember { PackedDate.Invalid },
                airingLabelState = remember { AiringLabelState(stateOf(null), stateOf(null)) },
                modifier = Modifier.placeholder(true),
            )
        },
        collectionData = {
            SubjectDetailsDefaults.CollectionData(
                remember { SubjectCollectionStats.Zero },
                modifier = Modifier.placeholder(true),
            )
        },
        collectionActions = {
            OutlinedButton(
                onClick = {},
                modifier = Modifier.placeholder(true),
            ) { Text("登录后可收藏") }
        },
        rating = {
            val scope = rememberCoroutineScope()
            EditableRating(
                remember {
                    EditableRatingState(
                        stateOf(RatingInfo.Empty),
                        stateOf(SelfRatingInfo.Empty),
                        stateOf(false),
                        { false },
                        { },
                        scope,
                    )
                },
                modifier = Modifier.placeholder(true),
            )
        },
        selectEpisodeButton = {
            SubjectDetailsDefaults.SelectEpisodeButtons(
                remember { SubjectProgressState(stateOf(SubjectProgressInfo.Done)) },
                onShowEpisodeList = { },
                onPlay = { },
                modifier = Modifier.placeholder(true),
            )
        },
        connectedScrollState = connectedScrollState,
        modifier,
        showTopBar = showTopBar,
        showBlurredBackground = false,
        windowInsets = windowInsets,
        navigationIcon = navigationIcon,
        onClickOpenExternal = onClickOpenExternal,
    ) { paddingValues ->
        PlaceholderSubjectDetailsContentPager(paddingValues)
    }
}

@Composable
private fun ErrorSubjectDetailsPage(
    subjectInfo: SubjectInfo?,
    error: LoadError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
) {
    SubjectDetailsLayout(
        info = subjectInfo,
        seasonTags = { },
        collectionData = { },
        collectionActions = { },
        rating = { },
        selectEpisodeButton = { },
        connectedScrollState = rememberConnectedScrollState(),
        modifier,
        showTopBar,
        showBlurredBackground = false,
        windowInsets,
        navigationIcon,
        onClickOpenExternal = onClickOpenExternal,
    ) { paddingValues ->
        LoadErrorCard(
            error = error,
            onRetry = onRetry,
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .padding(horizontal = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding)
                .padding(top = 12.dp),
        )
    }
}

// endregion

// region layout

/**
 * 一部番的详情页
 *
 * @param info `null` 表示没加载完成
 */
@Composable
fun SubjectDetailsLayout(
    info: SubjectInfo?,
    seasonTags: @Composable () -> Unit,
    collectionData: @Composable () -> Unit,
    collectionActions: @Composable () -> Unit,
    rating: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    connectedScrollState: ConnectedScrollState,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onCoverImageSuccess: (AsyncImagePainter.State.Success) -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val backgroundColor = AniThemeDefaults.pageContentBackgroundColor
    val stickyTopBarColor = AniThemeDefaults.navigationContainerColor
    Scaffold(
        topBar = {
            if (showTopBar) {
                WindowDragArea {
                    Box {
                        // 透明背景的, 总是显示
                        TopAppBar(
                            title = {},
                            navigationIcon = navigationIcon,
                            actions = {
                                IconButton(onClickOpenExternal) {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                                }
                            },
                            colors = AniThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
                            windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        )

                        // 有背景, 仅在滚动一段距离后使用
                        AniAnimatedVisibility(connectedScrollState.isScrolledTop) {
                            TopAppBar(
                                title = {
                                    Text(
                                        info?.displayName ?: "",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                navigationIcon = navigationIcon,
                                actions = {
                                    IconButton(onClickOpenExternal) {
                                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                                    }
                                },
                                colors = AniThemeDefaults.topAppBarColors(containerColor = stickyTopBarColor),
                                windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        containerColor = backgroundColor,
    ) { scaffoldPadding ->
        // 这个页面比较特殊. 背景需要绘制到 TopBar 等区域以内, 也就是要无视 scaffoldPadding.

        // 在背景之上显示的封面和标题等信息
        val headerContentPadding = scaffoldPadding.only(PaddingValuesSides.Horizontal + PaddingValuesSides.Top)
        // 从 tab row 开始的区域
        val remainingContentPadding = scaffoldPadding.only(PaddingValuesSides.Horizontal + PaddingValuesSides.Bottom)

        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxHeight()) {
                Box(Modifier.connectedScrollContainer(connectedScrollState)) {
                    // 虚化渐变背景, 需要绘制到 scaffoldPadding 以外区域
                    if (showBlurredBackground) {
                        SubjectBlurredBackground(
                            coverImageUrl = info?.imageLarge,
                            Modifier.matchParentSize(),
                            backgroundColor = backgroundColor,
                        )
                    }

                    // 标题和封面, 以及收藏数据, 可向上滑动
                    // 需要满足 scaffoldPadding 的 horizontal 和 top
                    Column(
                        Modifier
                            .padding(headerContentPadding)
                            .consumeWindowInsets(headerContentPadding),
                    ) {
                        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
                        SubjectDetailsHeader(
                            info,
                            info?.imageLarge,
                            seasonTags = seasonTags,
                            collectionData = collectionData,
                            collectionAction = collectionActions,
                            selectEpisodeButton = selectEpisodeButton,
                            rating = rating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(align = Alignment.CenterHorizontally)
                                .widthIn(max = MaximumContentWidth)
                                .connectedScrollTarget(connectedScrollState)
                                .fillMaxWidth()
                                .ifThen(!showTopBar) { padding(top = windowSizeClass.paneVerticalPadding) }
                                .padding(horizontal = windowSizeClass.paneHorizontalPadding),
                            onCoverImageSuccess = onCoverImageSuccess,
                        )
                    }
                }

                content(remainingContentPadding)
            }
        }
    }
}

// endregion

// region content pager

/**
 * Pager 页面
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubjectDetailsContentPager(
    paddingValues: PaddingValues,
    connectedScrollState: ConnectedScrollState,
    onClickAddRating: () -> Unit,
    detailsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    commentsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    discussionsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = AniThemeDefaults.pageContentBackgroundColor
    val stickyTopBarColor = AniThemeDefaults.navigationContainerColor

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = SubjectDetailsTab.DETAILS.ordinal,
        pageCount = { 3 },
    )

    Scaffold(
        Modifier,
        floatingActionButton = {
            when (SubjectDetailsTab.entries.getOrNull(pagerState.currentPage)) {
                SubjectDetailsTab.DETAILS -> {}
                SubjectDetailsTab.COMMENTS -> {
                    ExtendedFloatingActionButton(
                        text = { Text("写评价") },
                        icon = {
                            Icon(Icons.Rounded.AddComment, null)
                        },
                        onClickAddRating,
                        expanded = !connectedScrollState.isScrolledTop,
                    )
                }

                SubjectDetailsTab.DISCUSSIONS -> {}
                null -> {}
            }
        },
    ) { _ -> // ignore window insets
        Column(
            modifier
                .fillMaxHeight()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
        ) {
            val tabContainerColor by animateColorAsState(
                if (connectedScrollState.isScrolledTop) stickyTopBarColor else backgroundColor,
                tween(),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tabContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.widthIn(max = SubjectDetailsDefaults.TabRowWidth),
                    indicator = @Composable { tabPositions ->
                        TabRowDefaults.PrimaryIndicator(
                            Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                        )
                    },
                    containerColor = tabContainerColor,
                    contentColor = TabRowDefaults.secondaryContentColor,
                    divider = {},
                ) {
                    SubjectDetailsTab.entries.forEachIndexed { index, tabId ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            modifier = Modifier.widthIn(max = SubjectDetailsDefaults.TabWidth),
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Text(text = renderSubjectDetailsTab(tabId))
                            },
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                Modifier.fillMaxHeight(),
                userScrollEnabled = LocalPlatform.current.isMobile(),
                verticalAlignment = Alignment.Top,
            ) { index ->
                val type = SubjectDetailsTab.entries[index]
                Column(Modifier.padding()) {
                    val panePaddingValues =
                        PaddingValues(bottom = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding)
                    when (type) {
                        SubjectDetailsTab.DETAILS -> detailsTab(panePaddingValues)
                        SubjectDetailsTab.COMMENTS -> commentsTab(panePaddingValues)
                        SubjectDetailsTab.DISCUSSIONS -> discussionsTab(panePaddingValues)
                    }
                }
            }
        }
    }
}

/**
 * Pager 占位页面
 */
@Composable
private fun PlaceholderSubjectDetailsContentPager(paddingValues: PaddingValues) {
    val density = LocalDensity.current
    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass

    Column(
        Modifier
            .fillMaxHeight()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues),
    ) {
        // tab row
        Spacer(
            Modifier
                .padding(horizontal = windowSizeClass.paneHorizontalPadding)
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(40.dp)
                .placeholder(true),
        )

        Spacer(Modifier.height(16.dp))

        // 条目描述
        val bodyMediumTextHeight = with(density) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
        val timesDot8TextHeight = (bodyMediumTextHeight.value * 0.8).dp
        val timesDot8TextLinePadding = (bodyMediumTextHeight.value * 0.2).dp

        repeat(5) {
            Spacer(
                Modifier
                    .padding(horizontal = windowSizeClass.paneHorizontalPadding)
                    .padding(bottom = timesDot8TextLinePadding)
                    .fillMaxWidth()
                    .height(timesDot8TextHeight)
                    .placeholder(true, shape = RectangleShape),
            )
        }

        Spacer(Modifier.height(12.dp))

        // 标签
        val labelMediumTextHeight = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }

        FlowRow(
            modifier = Modifier.padding(horizontal = windowSizeClass.paneHorizontalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(5) {
                Tag(
                    Modifier
                        .height(40.dp)
                        .padding(vertical = 4.dp)
                        .placeholder(true),
                ) {
                    Spacer(
                        Modifier
                            .width(remember { (64..80).random().dp })
                            .height(labelMediumTextHeight),
                    )
                }
            }
        }

        Spacer(Modifier.fillMaxWidth().height(20.dp))

        Spacer(
            Modifier
                .padding(horizontal = windowSizeClass.paneHorizontalPadding)
                .width(48.dp)
                .height(with(density) { MaterialTheme.typography.titleMedium.lineHeight.toDp() })
                .placeholder(true, shape = RectangleShape),
        )

        Spacer(Modifier.fillMaxWidth().height(20.dp))

        @Composable
        fun PlaceholderPersonCard(modifier: Modifier = Modifier) {
            Row(modifier) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .size(48.dp)
                            .placeholder(true),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Spacer(
                            Modifier
                                .width(96.dp)
                                .height(bodyMediumTextHeight)
                                .placeholder(true, shape = RectangleShape),
                        )
                        Spacer(
                            Modifier
                                .width(96.dp)
                                .height(labelMediumTextHeight)
                                .placeholder(true, shape = RectangleShape),
                        )
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier
                .padding(horizontal = windowSizeClass.paneHorizontalPadding)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            repeat(4) {
                PlaceholderPersonCard()
            }
        }
    }
}

// endregion

@Immutable
@Serializable
enum class SubjectDetailsTab {
    DETAILS,
    COMMENTS,
    DISCUSSIONS,
}

/**
 * UI state of the subject details page.
 */
sealed interface SubjectDetailsUIState {
    val subjectId: Int

    /**
     * Placeholder, data is still loading.
     * If preview subject info is available, it will show first.
     */
    data class Placeholder(
        override val subjectId: Int,
        val subjectInfo: SubjectInfo? = null
    ) : SubjectDetailsUIState

    /**
     * Content ready.
     */
    class Ok(
        override val subjectId: Int,
        val value: SubjectDetailsState
    ) : SubjectDetailsUIState

    /**
     * Load error, if preview subject info is available, it will also show.
     */
    class Err(
        override val subjectId: Int,
        val placeholder: SubjectInfo?,
        val error: LoadError
    ) : SubjectDetailsUIState
}

@Stable
private fun renderSubjectDetailsTab(tab: SubjectDetailsTab): String {
    return when (tab) {
        SubjectDetailsTab.DETAILS -> "详情"
        SubjectDetailsTab.COMMENTS -> "评价"
        SubjectDetailsTab.DISCUSSIONS -> "讨论"
    }
}
