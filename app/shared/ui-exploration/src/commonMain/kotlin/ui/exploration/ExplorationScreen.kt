/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.FloatingActionButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.derivedStateOf
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.recommend.TestRecommendedItemInfos
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.TestFollowedSubjectInfos
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.data.models.subject.toNavPlaceholder
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.HorizontalScrollControlScaffoldOnDesktop
import me.him188.ani.app.ui.adaptive.NavTitleHeader
import me.him188.ani.app.ui.exploration.followed.FollowedSubjectsDefaults
import me.him188.ani.app.ui.exploration.followed.FollowedSubjectsLazyRow
import me.him188.ani.app.ui.exploration.recommend.RecommendationDefaults
import me.him188.ani.app.ui.exploration.recommend.recommendationItems
import me.him188.ani.app.ui.exploration.trends.TestTrendingSubjectInfos
import me.him188.ani.app.ui.exploration.trends.TrendingSubjectsCarousel
import me.him188.ani.app.ui.foundation.HorizontalScrollControlState
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.ifNotNullThen
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.rememberHorizontalScrollControlState
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_continue_watching
import me.him188.ani.app.ui.lang.exploration_horizontal_scroll_tip
import me.him188.ani.app.ui.lang.exploration_recommendations
import me.him188.ani.app.ui.lang.exploration_schedule
import me.him188.ani.app.ui.lang.exploration_search
import me.him188.ani.app.ui.lang.exploration_settings
import me.him188.ani.app.ui.lang.exploration_title
import me.him188.ani.app.ui.lang.exploration_trending
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import me.him188.ani.app.ui.search.rememberLoadErrorState
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import me.him188.ani.utils.analytics.recordEvent
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.hasScrollingBug
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.PreviewLightDark
import org.jetbrains.compose.ui.tooling.preview.PreviewScreenSizes

/**
 * @param horizontalScrollTipFlow 探索界面有横向滚动的列表, 是否显示点击辅助滚动按钮后的提示.
 * @param onSetDisableHorizontalScrollTip 探索界面有横向滚动的列表, 在第一次点击列表左右测的辅助滚动按钮后调用.
 */
@Stable
class ExplorationPageState(
    val trendingSubjectInfoPager: LazyPagingItems<TrendingSubjectInfo>,
    val followedSubjectsPager: Flow<PagingData<FollowedSubjectInfo>>,
    val recommendationPager: Flow<PagingData<RecommendedItemInfo>>,
    val horizontalScrollTipFlow: Flow<Boolean>,
    private val onSetDisableHorizontalScrollTip: () -> Unit,
) {
    val trendingSubjectsCarouselState = CarouselState(
        itemCount = {
            if (trendingSubjectInfoPager.isLoadingFirstPageOrRefreshing) {
                8
            } else {
                trendingSubjectInfoPager.itemCount
            }
        },
    )
    val followedSubjectsLazyRowState = LazyListState()


    val pageScrollState = LazyGridState()

    fun setDisableHorizontalScrollTip() {
        onSetDisableHorizontalScrollTip()
    }
}

@Composable
fun ExplorationScreen(
    state: ExplorationPageState,
    selfInfo: SelfInfoUiState,
    onSearch: () -> Unit,
    onClickLogin: () -> Unit,
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val showScrollToTop by remember {
        derivedStateOf {
            state.pageScrollState.firstVisibleItemIndex > 0
        }
    }

    val scope = rememberCoroutineScope()
    val isHeightAtLeastMedium = currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium
    val scrollBehavior = if (LocalPlatform.current.hasScrollingBug() || isHeightAtLeastMedium) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        // 在紧凑高度时收起 Top bar
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        topBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title(stringResource(Lang.exploration_title)) },
                Modifier.fillMaxWidth(),
                actions = {
                    actions()
                    if (selfInfo.isSessionValid == false // #1269 游客模式下无法打开设置界面
                        || currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium
                    ) {
                        IconButton(onClick = onClickSettings) {
                            Icon(Icons.Rounded.Settings, stringResource(Lang.exploration_settings))
                        }
                    }
                },
                avatar = { recommendedSize ->
                    SelfAvatar(
                        selfInfo,
                        onClick = onClickLogin,
                        size = recommendedSize,
                    )
                },
                searchIconButton = {
                    IconButton(onSearch) {
                        Icon(Icons.Rounded.Search, stringResource(Lang.exploration_search))
                    }
                },
                searchBar = {
                    IconButton(onSearch) {
                        Icon(Icons.Rounded.Search, stringResource(Lang.exploration_search))
                    }
                },
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            // 平滑滚动到顶部
                            state.pageScrollState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier.padding(bottom = 16.dp, end = 16.dp) // 调整一下边距
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Scroll to top"
                    )
                }
            }
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { topBarPadding ->
        val horizontalPadding = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding
        val horizontalContentPadding =
            PaddingValues(horizontal = horizontalPadding)

        val navigator = LocalNavigator.current
        val density = LocalDensity.current
        val showHorizontalNavigateTip by state.horizontalScrollTipFlow.collectAsState(false)
        val toaster = LocalToaster.current
        val scope = rememberCoroutineScope()
        val horizontalScrollTip = stringResource(Lang.exploration_horizontal_scroll_tip)

        val recommendationPager = state.recommendationPager.collectAsLazyPagingItemsWithLifecycle()
        val recommendationPagerLoadError by recommendationPager.rememberLoadErrorState()
        val aniMotionScheme = LocalAniMotionScheme.current
        val layoutParams = RecommendationDefaults.layoutParameters()
        LazyVerticalGrid(
            layoutParams.gridCells,
            Modifier
                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = 1300.dp)
                .padding(topBarPadding)
                .fillMaxSize()
                .ifNotNullThen(scrollBehavior) {
                    nestedScroll(it.nestedScrollConnection)
                },
            state = state.pageScrollState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = layoutParams.horizontalArrangement,
            verticalArrangement = layoutParams.verticalArrangement,
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    NavTitleHeader(
                        title = { Text(stringResource(Lang.exploration_trending), softWrap = false) },
                        trailingActions = {
                            TextButton(
                                { navigator.navigateSchedule() },
                                Modifier,
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(Lang.exploration_schedule), softWrap = false)
                            }
                        },
                    )

                    val carouselItemSize = CarouselItemDefaults.itemSize()
                    HorizontalScrollControlScaffoldOnDesktop(
                        rememberHorizontalScrollControlState(
                            state.trendingSubjectsCarouselState,
                            onClickScroll = { direction ->
                                scope.launch {
                                    state.trendingSubjectsCarouselState.animateScrollBy(
                                        with<Density, Float>(density) { (carouselItemSize.preferredWidth * 2).toPx() } *
                                                if (direction == HorizontalScrollControlState.Direction.BACKWARD) -1 else 1,
                                    )
                                }
                                if (showHorizontalNavigateTip) {
                                    toaster.toast(horizontalScrollTip)
                                    state.setDisableHorizontalScrollTip()
                                }
                            },
                        ),
                    ) {
                        TrendingSubjectsCarousel(
                            state.trendingSubjectInfoPager,
                            onClick = {
                                Analytics.recordEvent(SubjectEnter) {
                                    put("source", "home_trending")
                                    put("subject_id", it.bangumiId)
                                }
                                navigator.navigateSubjectDetails(
                                    subjectId = it.bangumiId,
                                    placeholder = SubjectDetailPlaceholder(
                                        id = it.bangumiId,
                                        name = it.nameCn,
                                        coverUrl = it.imageLarge,
                                    ),
                                )
                            },
                            contentPadding = PaddingValues(vertical = 8.dp),
                            carouselState = state.trendingSubjectsCarouselState,
                        )
                    }

                    NavTitleHeader(
                        title = { Text(stringResource(Lang.exploration_continue_watching), softWrap = false) },
                    )

                    val followedSubjectsPager =
                        state.followedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
                    val followedSubjectsLayoutParameters =
                        FollowedSubjectsDefaults.layoutParameters(currentWindowAdaptiveInfo1())

                    HorizontalScrollControlScaffoldOnDesktop(
                        rememberHorizontalScrollControlState(
                            state.followedSubjectsLazyRowState,
                            onClickScroll = { direction ->
                                scope.launch {
                                    state.followedSubjectsLazyRowState.animateScrollBy(
                                        with<Density, Float>(density) { (followedSubjectsLayoutParameters.imageSize.height * 2).toPx() } *
                                                if (direction == HorizontalScrollControlState.Direction.BACKWARD) -1 else 1,
                                    )
                                }
                                if (showHorizontalNavigateTip) {
                                    toaster.toast(horizontalScrollTip)
                                    state.setDisableHorizontalScrollTip()
                                }
                            },
                        ),
                    ) {
                        FollowedSubjectsLazyRow(
                            followedSubjectsPager,
                            onClick = {
                                Analytics.recordEvent(SubjectEnter) {
                                    put("source", "home_followed")
                                    put("subject_id", it.subjectInfo.subjectId)
                                }
                                navigator.navigateSubjectDetails(
                                    subjectId = it.subjectInfo.subjectId,
                                    placeholder = it.subjectInfo.toNavPlaceholder(),
                                )
                            },
                            onPlay = {
                                it.subjectProgressInfo.nextEpisodeIdToPlay?.let<Int, Unit> { it1 ->
                                    navigator.navigateEpisodeDetails(
                                        it.subjectInfo.subjectId,
                                        it1,
                                    )
                                }
                            },
                            layoutParameters = followedSubjectsLayoutParameters,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            lazyListState = state.followedSubjectsLazyRowState,
                        )
                    }

                    NavTitleHeader(
                        title = { Text(stringResource(Lang.exploration_recommendations), softWrap = false) },
                    )
                }
            }

            recommendationItems(
                recommendationPager,
                loadError = recommendationPagerLoadError,
                onClick = { info ->
                    when (info) {
                        is RecommendedSubjectInfo -> {
                            Analytics.recordEvent(SubjectEnter) {
                                put("source", "home_recommendation")
                                put("subject_id", info.bangumiId)
                            }
                            navigator.navigateSubjectDetails(
                                subjectId = info.bangumiId,
                                placeholder = info.toNavPlaceholder(),
                            )
                        }
                    }
                },
                layoutParams,
            )
        }
    }
}

fun RecommendedSubjectInfo.toNavPlaceholder(): SubjectDetailPlaceholder {
    return SubjectDetailPlaceholder(
        id = bangumiId,
        name = nameCn,
        nameCN = nameCn,
        coverUrl = imageLarge,
    )
}

@OptIn(TestOnly::class)
@Composable
@PreviewScreenSizes
@PreviewLightDark
private fun PreviewExplorationPage() {
    ProvideCompositionLocalsForPreview {
        val scope = rememberCoroutineScope()
        val trendingSubjectInfoPager = createTestPager(TestTrendingSubjectInfos).collectAsLazyPagingItemsWithLifecycle()
        ExplorationScreen(
            remember {
                ExplorationPageState(
                    trendingSubjectInfoPager,
                    followedSubjectsPager = createTestPager(TestFollowedSubjectInfos),
                    recommendationPager = createTestPager(TestRecommendedItemInfos),
                    horizontalScrollTipFlow = flowOf(false),
                    onSetDisableHorizontalScrollTip = {},
                )
            },
            selfInfo = TestSelfInfoUiState,
            {},
            {},
            {},
        )
    }
}
