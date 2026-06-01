/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.delay
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
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.ifNotNullThen
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.plus
import me.him188.ani.app.ui.foundation.rememberHorizontalScrollControlState
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.appChromeHazeSource
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
    private val onRefreshFollowedSubjects: () -> Unit = {},
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

    /** TV: 进入主页时把焦点落到最高热点栏目的第一张卡上. */
    val trendingFirstItemFocusRequester = FocusRequester()

    /** 第一张热点卡当前是否聚焦 (供初始聚焦重试判断是否已成功). */
    val trendingFirstItemFocused = mutableStateOf(false)

    val pageScrollState = LazyGridState()

    fun setDisableHorizontalScrollTip() {
        onSetDisableHorizontalScrollTip()
    }

    /**
     * 强制重拉"继续观看"栏目 (TV 端长按播放键): 平时它只跟着仓库里一小时一跳的定时同步走,
     * 用户想立刻确认某部有没有更新时需要一个入口.
     */
    fun refreshFollowedSubjects() {
        onRefreshFollowedSubjects()
    }
}

/**
 * TV 老布局白色面板相对侧边栏占位 (48dp) 的额外左移量, 使总左缘达 64dp ——
 * 侧边栏按钮中心 (32dp) 恰在屏幕左缘与面板左缘正中间 (与沉浸式页左缘一致).
 */
private val TV_OLD_EXPLORATION_PANEL_LEFT_INSET = 16.dp

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
    // 沉浸式外壳下顶栏 (标题/头像/搜索/设置) 全部由外壳侧边导航承载, 这里不再重复渲染顶栏
    val immersiveShell = LocalAniUiBehavior.current.immersiveShell
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    // 沉浸式变体: 入口提供了变体, 且运行时设置开启 (界面设置 → TV 沉浸式探索页, 默认开)
    val pageVariant = LocalExplorationPageVariant.current
    val useImmersive = pageVariant != null && LocalThemeSettings.current.tvImmersiveExploration
    // TV: 进入主页时把焦点落到最高热点栏目的第一张卡 (至少也在侧边栏之外). 该卡在分页首页加载
    // 完成前不存在, 且要和全局焦点兜底 (AniAppContent) 竞争 —— 故持续重试, 直到 onFocusChanged
    // 报告它真正拿到焦点为止 (不依赖 requestFocus 的返回, 后者未挂载时可能静默 no-op), 或超时放弃.
    // 沉浸式与原布局的热点第一张卡都挂着同一个 FocusRequester, 本机制对两种布局通用.
    // 沉浸式变体的初始/返回焦点由变体内部统一处理 (含从详情页返回时恢复到原卡片);
    // 这里只为旧布局 (沉浸式关闭) 抢初始焦点到热点第一张卡.
    if (focusDriven && !useImmersive) {
        LaunchedEffect(Unit) {
            repeat(80) {
                if (state.trendingFirstItemFocused.value) return@LaunchedEffect
                runCatching { state.trendingFirstItemFocusRequester.requestFocus() }
                delay(100)
            }
        }
    }
    // 沉浸式变体 (设置开关控制); 关掉则走下方默认布局
    if (useImmersive && pageVariant != null) {
        pageVariant.Page(state, modifier.fillMaxSize())
        return
    }
    val isHeightAtLeastMedium = currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium
    val scrollBehavior = if (LocalPlatform.current.hasScrollingBug() || isHeightAtLeastMedium) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        // 在紧凑高度时收起 Top bar
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }
    Scaffold(
        // 沉浸式外壳的回退布局: 白色面板左缘从侧边栏占位 (48dp) 再右移 16dp 到 64dp, 使侧边栏
        // 按钮中心 (32dp) 恰在屏幕左缘与面板左缘正中间; 左侧圆角还原"圆角矩形"面板观感 (右缘贴屏, 不圆角).
        modifier = modifier.fillMaxSize().ifThen(immersiveShell) {
            padding(start = TV_OLD_EXPLORATION_PANEL_LEFT_INSET)
                .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        topBar = {
            if (immersiveShell) return@Scaffold
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
                // 毛玻璃 app chrome 的模糊来源. 内容通过 contentPadding 延伸到 chrome 下方.
                .appChromeHazeSource(backgroundColor = AniThemeDefaults.pageContentBackgroundColor)
                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = 1300.dp)
                .fillMaxSize()
                .ifNotNullThen(scrollBehavior) {
                    nestedScroll(it.nestedScrollConnection)
                },
            state = state.pageScrollState,
            contentPadding = topBarPadding + PaddingValues(horizontal = horizontalPadding),
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
                            firstItemFocusRequester = state.trendingFirstItemFocusRequester,
                            onFirstItemFocusChanged = { state.trendingFirstItemFocused.value = it },
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
