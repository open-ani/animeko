/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.Tag
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
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import me.him188.ani.app.ui.subject.details.components.CollectionData
import me.him188.ani.app.ui.subject.details.components.DetailsTab
import me.him188.ani.app.ui.subject.details.components.SeasonTag
import me.him188.ani.app.ui.subject.details.components.SelectEpisodeButtons
import me.him188.ani.app.ui.subject.details.components.SubjectBlurredBackground
import me.him188.ani.app.ui.subject.details.components.SubjectCommentColumn
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsHeader
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDialog
import me.him188.ani.app.ui.subject.rating.EditableRating
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.utils.platform.isMobile

@Composable
fun SubjectDetailsPage(
    vm: SubjectDetailsViewModel,
    onPlay: (episodeId: Int) -> Unit,
    onLoadErrorRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    SubjectDetailsPage(
        vm.result,
        onPlay,
        onLoadErrorRetry,
        modifier,
        showTopBar,
        showBlurredBackground,
        windowInsets,
        navigationIcon,
    )
}

@Composable
fun SubjectDetailsPage(
    state: SubjectDetailsStateLoader.LoadState,
    onPlay: (episodeId: Int) -> Unit,
    onLoadErrorRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    when (state) {
        is SubjectDetailsStateLoader.LoadState.Ok -> SubjectDetailsPage(
            state.value,
            onPlay = onPlay,
            modifier,
            showTopBar,
            showBlurredBackground && !state.value.showPlaceholder,
            windowInsets,
            navigationIcon,
        )

        is SubjectDetailsStateLoader.LoadState.Err -> ErrorSubjectDetailsPage(
            state.subjectId,
            state.placeholder,
            error = state.error,
            onRetry = onLoadErrorRetry,
            modifier,
            showTopBar,
            windowInsets,
            navigationIcon,
        )

        SubjectDetailsStateLoader.LoadState.Loading -> {
            // 不需要处理 Loading, 它的职能已在 Ok 时 SubjectDetailsState.showPlaceholder == true 实现.
            // 此处的处理方式和 SubjectDetailsStateLoader 的逻辑有强绑定关系.
        }
    }
}

@Composable
private fun SubjectDetailsPage(
    state: SubjectDetailsState,
    onPlay: (episodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val toaster = LocalToaster.current
    val browserNavigator = LocalUriHandler.current

    var showSelectEpisode by rememberSaveable { mutableStateOf(false) }
    if (showSelectEpisode) {
        EpisodeListDialog(
            state.episodeListState,
            title = {
                Text(state.info?.displayName ?: "")
            },
            onDismissRequest = { showSelectEpisode = false },
        )
    }
    val connectedScrollState = rememberConnectedScrollState()

    // image viewer
    val imageViewer = rememberImageViewerHandler()
    BackHandler(enabled = imageViewer.viewing.value) { imageViewer.clear() }

    val placeholderModifier = Modifier.placeholder(state.showPlaceholder)
    SubjectDetailsPageLayout(
        state.subjectId,
        state.info,
        seasonTags = {
            SubjectDetailsDefaults.SeasonTag(
                airDate = state.info?.airDate ?: PackedDate.Invalid,
                airingLabelState = state.airingLabelState,
                placeholderModifier,
            )
        },
        collectionData = {
            SubjectDetailsDefaults.CollectionData(
                collectionStats = state.info?.collectionStats ?: SubjectCollectionStats.Zero,
                placeholderModifier,
            )
        },
        collectionActions = {
            if (state.authState.isKnownExpired) {
                val navigator = LocalNavigator.current
                OutlinedButton(
                    onClick = { state.authState.launchAuthorize(navigator) },
                    modifier = placeholderModifier,
                ) {
                    Text("登录后可收藏")
                }
            } else {
                EditableSubjectCollectionTypeButton(
                    state.editableSubjectCollectionTypeState,
                    placeholderModifier,
                )
            }
        },
        rating = {
            EditableRating(
                state.editableRatingState,
                placeholderModifier,
            )
        },
        selectEpisodeButton = {
            SubjectDetailsDefaults.SelectEpisodeButtons(
                state.subjectProgressState,
                onShowEpisodeList = { showSelectEpisode = true },
                onPlay = onPlay,
                placeholderModifier,
            )
        },
        connectedScrollState = connectedScrollState,
        modifier,
        showTopBar = showTopBar,
        showBlurredBackground = showBlurredBackground,
        windowInsets = windowInsets,
        navigationIcon = navigationIcon,
    ) { paddingValues ->
        Box {
            AnimatedVisibility(
                visible = state.showPlaceholder,
                enter = EnterTransition.None,
                exit = fadeOut(AniThemeDefaults.feedItemFadeOutSpec),
            ) {
                PlaceholderSubjectDetailsContentPager(paddingValues)
            }
            if (state.showPlaceholder) return@SubjectDetailsPageLayout

            SubjectDetailsContentPager(
                paddingValues,
                connectedScrollState,
                detailsTab = { contentPadding ->
                    if (state.info == null) return@SubjectDetailsContentPager
                    SubjectDetailsDefaults.DetailsTab(
                        info = state.info,
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
                        lazyStaggeredGridState = state.commentTabLazyStaggeredGridState,
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
            )
        }
    }

    ImageViewer(imageViewer) { imageViewer.clear() }
}

@Composable
private fun ErrorSubjectDetailsPage(
    subjectId: Int,
    placeholderSubjectInfo: SubjectInfo?,
    error: LoadError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    SubjectDetailsPageLayout(
        subjectId = subjectId,
        info = placeholderSubjectInfo,
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
    ) { paddingValues ->
        LoadErrorCard(
            problem = error,
            onRetry = onRetry,
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .padding(horizontal = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding)
                .padding(top = 12.dp),
        )
    }
}

@Immutable
enum class SubjectDetailsTab {
    DETAILS,
    COMMENTS,
    DISCUSSIONS,
}


/**
 * 一部番的详情页
 *
 * @param info `null` 表示正在加载中
 */
@Composable
fun SubjectDetailsPageLayout(
    subjectId: Int,
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
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val backgroundColor = AniThemeDefaults.pageContentBackgroundColor
    val stickyTopBarColor = AniThemeDefaults.navigationContainerColor

    val urlHandler = LocalUriHandler.current
    val onClickOpenExternal = {
        urlHandler.openUri("https://bgm.tv/subject/$subjectId")
    }
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
                        AnimatedVisibility(connectedScrollState.isScrolledTop, enter = fadeIn(), exit = fadeOut()) {
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
            Column(Modifier.widthIn(max = 1300.dp).fillMaxHeight()) {
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
                            subjectId,
                            info,
                            info?.imageLarge,
                            seasonTags = seasonTags,
                            collectionData = collectionData,
                            collectionAction = collectionActions,
                            selectEpisodeButton = selectEpisodeButton,
                            rating = rating,
                            Modifier
                                .connectedScrollTarget(connectedScrollState)
                                .fillMaxWidth()
                                .ifThen(!showTopBar) { padding(top = windowSizeClass.paneVerticalPadding) }
                                .padding(horizontal = windowSizeClass.paneHorizontalPadding),
                        )
                    }
                }

                content(remainingContentPadding)
            }
        }
    }
}

@Composable
private fun SubjectDetailsContentPager(
    paddingValues: PaddingValues,
    connectedScrollState: ConnectedScrollState,
    detailsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    commentsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    discussionsTab: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val backgroundColor = AniThemeDefaults.pageContentBackgroundColor
    val stickyTopBarColor = AniThemeDefaults.navigationContainerColor

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = SubjectDetailsTab.DETAILS.ordinal,
        pageCount = { 3 },
    )

    Column(
        Modifier
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
                modifier = Modifier.width(240.dp), // 3个tab的总宽度 = 80 * 3
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
                val paddingValues =
                    PaddingValues(bottom = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding)
                when (type) {
                    SubjectDetailsTab.DETAILS -> detailsTab(paddingValues)
                    SubjectDetailsTab.COMMENTS -> commentsTab(paddingValues)
                    SubjectDetailsTab.DISCUSSIONS -> discussionsTab(paddingValues)
                }
            }
        }
    }
}

/**
 * 加载展位页
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

@Stable
private fun renderSubjectDetailsTab(tab: SubjectDetailsTab): String {
    return when (tab) {
        SubjectDetailsTab.DETAILS -> "详情"
        SubjectDetailsTab.COMMENTS -> "评论"
        SubjectDetailsTab.DISCUSSIONS -> "讨论"
    }
}
