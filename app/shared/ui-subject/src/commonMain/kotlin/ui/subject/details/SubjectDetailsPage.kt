/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import com.kmpalette.rememberPaletteState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.models.subject.TestSubjectInfo
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeRequest
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.Tag
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.layout.NestedScrollableColumn
import me.him188.ani.app.ui.foundation.layout.NestedScrollableColumnState
import me.him188.ani.app.ui.foundation.layout.NestedScrollableScope
import me.him188.ani.app.ui.foundation.layout.PaddingValuesSides
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.only
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.layout.plus
import me.him188.ani.app.ui.foundation.layout.rememberNestedScrollableColumnState
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.LocalAppChromeHazeState
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.theme.MaterialThemeFromPaletteAndImage
import me.him188.ani.app.ui.foundation.theme.appChromeFrostedGlass
import me.him188.ani.app.ui.foundation.theme.appChromeHazeSource
import me.him188.ani.app.ui.foundation.theme.isAppChromeFrostedGlassActive
import me.him188.ani.app.ui.foundation.toComposeImageBitmap
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_richtext_external_app_link_warning_prefix
import me.him188.ani.app.ui.lang.foundation_richtext_open_failed_prefix
import me.him188.ani.app.ui.lang.subject_details_coming_soon
import me.him188.ani.app.ui.lang.subject_details_login_to_collect
import me.him188.ani.app.ui.lang.subject_details_tab_comments
import me.him188.ani.app.ui.lang.subject_details_tab_details
import me.him188.ani.app.ui.lang.subject_details_tab_discussions
import me.him188.ani.app.ui.lang.subject_details_write_review
import me.him188.ani.app.ui.rating.EditableRating
import me.him188.ani.app.ui.rating.EditableRatingDialogsHost
import me.him188.ani.app.ui.rating.EditableRatingState
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import me.him188.ani.app.ui.subject.details.components.CollectionData
import me.him188.ani.app.ui.subject.details.components.SeasonTag
import me.him188.ani.app.ui.subject.details.components.SelectEpisodeButtons
import me.him188.ani.app.ui.subject.details.components.SubjectBlurredBackground
import me.him188.ani.app.ui.subject.details.components.SubjectCommentColumn
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults.MaximumContentWidth
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsHeader
import me.him188.ani.app.ui.subject.details.layout.CompactDetailsTabContent
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsLayoutParams
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsMultiColumnPage
import me.him188.ani.app.ui.subject.person.PeoplePreviewHost
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsMultiColumnPlaceholder
import me.him188.ani.app.ui.subject.details.sections.SubjectCommentsSheet
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.details.state.createTestSubjectDetailsState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDialog
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.toggleCollected
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource

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

    // 断点必须按本页面实际可用宽度决定, 不能按窗口宽度:
    // 本页面会内嵌于播放页 ModalBottomSheet (最大 640dp) 与搜索页 list-detail 详情栏.
    BoxWithConstraints(modifier) {
        val layoutParams = SubjectDetailsLayoutParams.calculate(maxWidth)
        when (state) {
            null, is SubjectDetailsUIState.Placeholder -> PlaceholderSubjectDetailsPage(
                state?.subjectInfo,
                layoutParams,
                Modifier,
                showTopBar,
                windowInsets,
                navigationIcon,
                onClickOpenExternal,
            )

            is SubjectDetailsUIState.Ok -> SubjectDetailsPage(
                state.value,
                selfInfo,
                layoutParams,
                onPlay = onPlay,
                onClickLogin = { navigator.navigateEmailLoginStart() },
                onClickTag,
                onEpisodeCollectionUpdate = onEpisodeCollectionUpdate,
                Modifier,
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
                Modifier,
                showTopBar,
                windowInsets,
                navigationIcon,
                onClickOpenExternal,
            )
        }
    }
}

// endregion

// region page

@Composable
private fun SubjectDetailsPage(
    state: SubjectDetailsState,
    selfInfo: SelfInfoUiState,
    layoutParams: SubjectDetailsLayoutParams,
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
    val externalAppLinkWarningPrefix = stringResource(Lang.foundation_richtext_external_app_link_warning_prefix)
    val openLinkFailedPrefix = stringResource(Lang.foundation_richtext_open_failed_prefix)

    var showSelectEpisode by rememberSaveable { mutableStateOf(false) }

    // image viewer
    val imageViewer = rememberImageViewerHandler()
    BackHandler(enabled = imageViewer.viewing.value) { imageViewer.clear() }

    val presentation by state.presentation.collectAsStateWithLifecycle()

    // 评论中的链接/图片点击 (手机"评价" tab 与桌面评论 sheet 共用)
    val onClickCommentUrl = { url: String ->
        RichTextDefaults.checkSanityAndOpen(
            url,
            browserNavigator,
            toaster,
            externalAppLinkWarningPrefix,
            openLinkFailedPrefix,
        )
    }
    val onClickCommentImage = { url: String -> imageViewer.viewImage(url) }

    val themeSettings = LocalThemeSettings.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val onCoverImageSuccess = { success: AsyncImagePainter.State.Success ->
        bitmap = success.result.image.toComposeImageBitmap()
    }
    val paletteState = rememberPaletteState()
    LaunchedEffect(themeSettings, bitmap) {
        val bitmap = bitmap ?: return@LaunchedEffect
        if (themeSettings.useDynamicSubjectPageTheme || themeSettings.enableAnimatedGradientSubjectPage) {
            paletteState.generate(bitmap)
        }
    }

    MaterialThemeFromPaletteAndImage(
        if (themeSettings.useDynamicSubjectPageTheme) paletteState.palette else null,
        if (themeSettings.useDynamicSubjectPageTheme) bitmap else null,
    ) {
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

        if (layoutParams.isMultiColumn && state.info != null) {
            // 双栏 / 三栏: 全新自适应布局 (复用现有 SubjectDetailsState 数据).
            // 桌面无"评价" tab, 完整评论流与"写评价"从评价预览/热门评价卡进入.
            var showComments by rememberSaveable { mutableStateOf(false) }
            EditableRatingDialogsHost(state.editableRatingState)
            if (showComments) {
                SubjectCommentsSheet(
                    state = state.subjectCommentState,
                    onClickUrl = onClickCommentUrl,
                    onClickImage = onClickCommentImage,
                    onClickWriteReview = { state.editableRatingState.requestEdit() },
                    onDismissRequest = { showComments = false },
                )
            }
            // 中大屏点击人物/角色先打开右侧预览 (方案C), 手机上则直接导航到全页
            PeoplePreviewHost {
                SubjectDetailsMultiColumnPage(
                    state = state,
                    selfInfo = selfInfo,
                    layoutParams = layoutParams,
                    onPlay = onPlay,
                    onClickTag = onClickTag,
                    onClickLogin = onClickLogin,
                    onShowComments = { showComments = true },
                    modifier = modifier,
                    showTopBar = showTopBar,
                    windowInsets = windowInsets,
                    backgroundPalette = if (themeSettings.enableAnimatedGradientSubjectPage) paletteState.palette else null,
                    navigationIcon = navigationIcon,
                    onClickOpenExternal = onClickOpenExternal,
                    onCoverImageSuccess = onCoverImageSuccess,
                )
            }
            return@MaterialThemeFromPaletteAndImage
        }

        val pagerState = rememberPagerState(
            initialPage = SubjectDetailsTab.DETAILS.ordinal,
            pageCount = { 3 },
        )
        val nestedScrollableColumnState = rememberNestedScrollableColumnState()
        SubjectDetailsSingleColumnPage(
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
                        Text(stringResource(Lang.subject_details_login_to_collect))
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
            modifier = modifier,
            showTopBar = showTopBar,
            showBlurredBackground = showBlurredBackground,
            windowInsets = windowInsets,
            navigationIcon = navigationIcon,
            onCoverImageSuccess = onCoverImageSuccess,
            onClickOpenExternal = onClickOpenExternal,
            floatingActionButton = {
                when (SubjectDetailsTab.entries.getOrNull(pagerState.currentPage)) {
                    SubjectDetailsTab.COMMENTS -> {
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(Lang.subject_details_write_review)) },
                            icon = {
                                Icon(Icons.Rounded.AddComment, null)
                            },
                            onClick = { state.editableRatingState.requestEdit() },
                            expanded = !nestedScrollableColumnState.isHeaderScrolledOut,
                        )
                    }

                    else -> {}
                }
            },
            tabRow = { isOverlay, visible ->
                SubjectDetailsContentTabRow(
                    pagerState, 
                    modifier = Modifier.ifThen(!isOverlay) {
                        alpha(if (visible) 1f else 0f)
                    }
                )
            },
            nestedScrollableColumnState = nestedScrollableColumnState,
        ) { contentPadding ->
            SubjectDetailsContentPager(
                pagerState,
                contentPadding,
                detailsTab = { tabContentPadding ->
                    if (state.info == null) return@SubjectDetailsContentPager
                    CompactDetailsTabContent(
                        state = state,
                        info = state.info,
                        onPlay = onPlay,
                        onClickTag = onClickTag,
                        onShowEpisodeList = { showSelectEpisode = true },
                        modifier = Modifier
                            .nestedScrollWorkaround(state.detailsTabLazyListState),
                        listState = state.detailsTabLazyListState,
                        contentPadding = tabContentPadding,
                    )
                },
                commentsTab = { tabContentPadding ->
                    SubjectDetailsDefaults.SubjectCommentColumn(
                        state = state.subjectCommentState,
                        onClickUrl = onClickCommentUrl,
                        onClickImage = onClickCommentImage,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScrollWorkaround(state.commentTabLazyGridState),
                        gridState = state.commentTabLazyGridState,
                        contentPadding = tabContentPadding,
                        // 下拉手势优先交给 NestedScrollableColumn 展开 header;
                        // 只有整页在最顶部时才启用下拉刷新.
                        pullToRefreshEnabled = nestedScrollableColumnState.isHeaderFullyVisible,
                    )
                },
                discussionsTab = {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        // TODO: Add nestedScrollWorkaround when we implement this tab
                    ) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Lang.subject_details_coming_soon), Modifier.padding(16.dp))
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
private fun PlaceholderSubjectDetailsPage(
    subjectInfo: SubjectInfo?,
    layoutParams: SubjectDetailsLayoutParams,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
) {
    if (layoutParams.isMultiColumn) {
        // 与加载完成后的多栏布局几何对齐, 避免跳变.
        SubjectDetailsMultiColumnPlaceholder(
            subjectInfo,
            layoutParams,
            modifier,
            showTopBar,
            windowInsets,
            navigationIcon,
            onClickOpenExternal,
        )
        return
    }

    SubjectDetailsSingleColumnPage(
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
            ) { Text(stringResource(Lang.subject_details_login_to_collect)) }
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
        modifier = modifier,
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
    SubjectDetailsSingleColumnPage(
        info = subjectInfo,
        seasonTags = { },
        collectionData = { },
        collectionActions = { },
        rating = { },
        selectEpisodeButton = { },
        modifier = modifier,
        showTopBar = showTopBar,
        showBlurredBackground = false,
        windowInsets = windowInsets,
        navigationIcon = navigationIcon,
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
 * 单栏 (compact) 条目详情页布局.
 *
 * ### 滚动结构
 *
 * header (封面等信息 + [tabRow]) 与 [content] (pager) 位于同一个 [NestedScrollableColumn] 中:
 * 向上滚动时 header 逐渐滚出布局外, pager 高度随之增大直到占满;
 * 此后才由 pager 内部的 scrollable 消费滚动; 内部 scrollable 到顶后继续下拉, header 才滚回.
 *
 * 当 [tabRow] 的顶边到达顶栏底部 (anchor) 时, 显示粘性面板 (第二个 [TopAppBar] + 第二份 [tabRow]),
 * 覆盖在内容之上, 起到 pinned 的视觉效果而不阻塞 pager 内部滚动.
 * 整个滚动区域是毛玻璃的模糊来源, 粘性面板作为其 sibling 应用玻璃效果.
 *
 * @param info `null` 表示没加载完成
 * @param tabRow pager 的 TabRow. 会被调用两次: 一次随内容滚动, 一次在粘性面板中.
 * @param content pager 区域, 填满 [NestedScrollableColumn] 的 content slot.
 * 可通过 [NestedScrollableScope] 使用 [NestedScrollableScope.nestedScrollWorkaround] 为内部
 * scrollable 接线 (桌面鼠标滚轮).
 */
@Composable
fun SubjectDetailsSingleColumnPage(
    info: SubjectInfo?,
    seasonTags: @Composable () -> Unit,
    collectionData: @Composable () -> Unit,
    collectionActions: @Composable () -> Unit,
    rating: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onCoverImageSuccess: (AsyncImagePainter.State.Success) -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    tabRow: (@Composable (isOverlay: Boolean, visible: Boolean) -> Unit)? = null,
    nestedScrollableColumnState: NestedScrollableColumnState = rememberNestedScrollableColumnState(),
    content: @Composable NestedScrollableScope.(contentPadding: PaddingValues) -> Unit,
) {
    val backgroundColor = AniThemeDefaults.pageContentBackgroundColor
    val stickyTopBarColor = AniThemeDefaults.navigationContainerColor
    val topAppBarActions: @Composable RowScope.() -> Unit = {
        IconButton(onClickOpenExternal) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
        }
    }
    // 详情页自带一个独立的毛玻璃作用域: 粘性面板模糊其下方滚动的内容.
    CompositionLocalProvider(LocalAppChromeHazeState provides rememberHazeState()) {
        val frostedGlassActive = isAppChromeFrostedGlassActive()
        Scaffold(
            topBar = {
                if (showTopBar) {
                    WindowDragArea {
                        // 透明背景的, 总是显示. 第二个 (粘性面板) 在 content 区域作为 overlay 绘制,
                        // 以免 topBar 高度随其出现而变化.
                        TopAppBar(
                            title = {},
                            navigationIcon = navigationIcon,
                            actions = topAppBarActions,
                            colors = AniThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
                            windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        )
                    }
                }
            },
            modifier = modifier,
            floatingActionButton = floatingActionButton,
            contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            containerColor = backgroundColor,
        ) { scaffoldPadding ->
            // 这个页面比较特殊. 背景需要绘制到 TopBar 等区域以内, 也就是要无视 scaffoldPadding.

            // 在背景之上显示的封面和标题等信息
            val headerContentPadding = scaffoldPadding.only(PaddingValuesSides.Horizontal + PaddingValuesSides.Top)
            // 从 tab row 开始的区域
            val remainingContentPadding = scaffoldPadding.only(PaddingValuesSides.Horizontal)

            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                var tabRowHeightPx by remember { mutableStateOf(0) }
                
                // 粘性面板: 第二个 TopAppBar + 第二份 TabRow.
                // 作为模糊来源的 sibling 绘制在其上方, 毛玻璃可采样到下方滚动的内容.
                val density = LocalDensity.current
                val anchorHeightPx = rememberUpdatedState(
                    with(density) { scaffoldPadding.calculateTopPadding().roundToPx() },
                )
                // 触发条件: header 内的 tabRow 顶边滚动到顶栏底部 (anchor).
                val stickyPanelVisible by remember(nestedScrollableColumnState) {
                    derivedStateOf {
                        val state = nestedScrollableColumnState
                        val threshold = state.headerHeight - tabRowHeightPx - anchorHeightPx.value
                        state.headerHeight > 0 && state.scrolledOffset > 0f && state.scrolledOffset >= threshold
                    }
                }

                NestedScrollableColumn(
                    header = {
                        Column(Modifier.fillMaxWidth()) {
                            Box {
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
                                            .fillMaxWidth()
                                            .ifThen(!showTopBar) { padding(top = windowSizeClass.paneVerticalPadding) }
                                            .padding(horizontal = windowSizeClass.paneHorizontalPadding),
                                        onCoverImageSuccess = onCoverImageSuccess,
                                    )
                                }
                            }

                            if (tabRow != null) {
                                Box(
                                    Modifier
                                        .onSizeChanged { tabRowHeightPx = it.height }
                                        .padding(remainingContentPadding)
                                        .fillMaxWidth(),
                                ) {
                                    tabRow(false, !stickyPanelVisible)
                                }
                            }
                        }
                    },
                    content = {
                        content(remainingContentPadding)
                    },
                    // 毛玻璃的模糊来源: 覆盖整个滚动区域 (header 背景 + tabRow + pager 内容).
                    modifier = Modifier
                        .fillMaxSize()
                        .appChromeHazeSource(backgroundColor = backgroundColor),
                    state = nestedScrollableColumnState,
                )

                if (showTopBar || tabRow != null) {
                    // 面板底色/毛玻璃按分段应用: TopAppBar 段随 fade 渐入渐出,
                    // TabRow 段直接 snap, 与 header 中 TabRow (同帧 alpha 隐藏/显示) 无缝交接.
                    val panelBackgroundModifier = Modifier
                        .appChromeFrostedGlass(
                            enabled = frostedGlassActive,
                            containerColor = stickyTopBarColor,
                        )
                        .ifThen(!frostedGlassActive) { background(stickyTopBarColor) }

                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                    ) {
                        if (showTopBar) {
                            AniAnimatedVisibility(
                                stickyPanelVisible,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                WindowDragArea {
                                    TopAppBar(
                                        title = {
                                            Text(
                                                info?.displayName ?: "",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        modifier = panelBackgroundModifier,
                                        navigationIcon = navigationIcon,
                                        actions = topAppBarActions,
                                        colors = AniThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
                                        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                                    )
                                }
                            }
                        }

                        if (tabRow != null && stickyPanelVisible) {
                            Box(
                                panelBackgroundModifier
                                    .padding(remainingContentPadding)
                                    .fillMaxWidth(),
                            ) {
                                tabRow(true, stickyPanelVisible)
                            }
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region content pager

/**
 * Pager 的 TabRow. 在 [SubjectDetailsSingleColumnPage] 中被调用两次:
 * 一次随内容滚动, 一次在粘性面板中 (背景由面板提供).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubjectDetailsContentTabRow(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier.fillMaxWidth(),
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
            containerColor = Color.Transparent,
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
}

/**
 * Pager 页面 (不含 TabRow, 见 [SubjectDetailsContentTabRow]).
 * 填满 [NestedScrollableColumn] 的 content slot.
 */
@Composable
private fun SubjectDetailsContentPager(
    pagerState: PagerState,
    contentPadding: PaddingValues,
    detailsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    commentsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    discussionsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .fillMaxWidth()
            .wrapContentWidth(align = Alignment.CenterHorizontally)
            .widthIn(max = MaximumContentWidth),
    ) {
        HorizontalPager(
            state = pagerState,
            Modifier.fillMaxHeight(),
            userScrollEnabled = LocalPlatform.current.isMobile(),
            verticalAlignment = Alignment.Top,
        ) { index ->
            val type = SubjectDetailsTab.entries[index]
            Column(Modifier.padding()) {
                val panePaddingValues =
                    PaddingValues(
                        bottom = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding,
                    ).plus(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues())
                when (type) {
                    SubjectDetailsTab.DETAILS -> detailsTab(panePaddingValues)
                    SubjectDetailsTab.COMMENTS -> commentsTab(panePaddingValues)
                    SubjectDetailsTab.DISCUSSIONS -> discussionsTab(panePaddingValues)
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
@Composable
private fun renderSubjectDetailsTab(tab: SubjectDetailsTab): String {
    return when (tab) {
        SubjectDetailsTab.DETAILS -> stringResource(Lang.subject_details_tab_details)
        SubjectDetailsTab.COMMENTS -> stringResource(Lang.subject_details_tab_comments)
        SubjectDetailsTab.DISCUSSIONS -> stringResource(Lang.subject_details_tab_discussions)
    }
}

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewSubjectDetails() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    val state = remember {
        createTestSubjectDetailsState(scope)
            .let { SubjectDetailsUIState.Ok(it.subjectId, it) }
    }
    PreviewSubjectDetailsScreen(
        state,
    )
}

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewPlaceholderSubjectDetails() = ProvideCompositionLocalsForPreview {
    val state = remember {
        SubjectDetailsUIState.Placeholder(TestSubjectInfo.subjectId, TestSubjectInfo)
    }
    PreviewSubjectDetailsScreen(
        state,
    )
}


@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewErrorSubjectDetails() = ProvideCompositionLocalsForPreview {
    val state = remember {
        SubjectDetailsUIState.Err(TestSubjectInfo.subjectId, TestSubjectInfo, LoadError.NetworkError)
    }
    PreviewSubjectDetailsScreen(
        state,
    )
}

@TestOnly
@Composable
private fun PreviewSubjectDetailsScreen(
    state: SubjectDetailsUIState,
    modifier: Modifier = Modifier
) {
    SubjectDetailsScreen(
        state,
        TestSelfInfoUiState,
        onPlay = { },
        onLoadErrorRetry = { },
        onClickTag = {},
        {},
        modifier = modifier,
        navigationIcon = { BackNavigationIconButton({}) },
    )
}
