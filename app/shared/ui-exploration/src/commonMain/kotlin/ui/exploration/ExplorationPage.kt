/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.data.models.subject.toNavPlaceholder
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.HorizontalScrollNavigatorOnDesktop
import me.him188.ani.app.ui.adaptive.NavTitleHeader
import me.him188.ani.app.ui.exploration.followed.FollowedSubjectsDefaults
import me.him188.ani.app.ui.exploration.followed.FollowedSubjectsLazyRow
import me.him188.ani.app.ui.exploration.trends.TrendingSubjectsCarousel
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.rememberHorizontalScrollNavigatorState
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing

@Stable
class ExplorationPageState(
    val authState: AuthState,
    selfInfoState: State<UserInfo?>,
    val trendingSubjectInfoPager: LazyPagingItems<TrendingSubjectInfo>,
    val followedSubjectsPager: Flow<PagingData<FollowedSubjectInfo>>,
    val horizontalScrollTipFlow: Flow<Boolean>,
    private val onSetDontShowHorizontalScrollTip: () -> Unit,
) {
    val selfInfo by selfInfoState

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


    val pageScrollState = ScrollState(0)

    fun setDontShowHorizontalScrollTip() {
        onSetDontShowHorizontalScrollTip()
    }
}

@Composable
fun ExplorationPage(
    state: ExplorationPageState,
    onSearch: () -> Unit,
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        topBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title("探索") },
                Modifier.fillMaxWidth(),
                actions = {
                    actions()
                    if (state.authState.isKnownGuest // #1269 游客模式下无法打开设置界面
                        || currentWindowAdaptiveInfo1().windowSizeClass.windowWidthSizeClass.isAtLeastMedium
                    ) {
                        IconButton(onClick = onClickSettings) {
                            Icon(Icons.Rounded.Settings, "设置")
                        }
                    }
                },
                avatar = { recommendedSize ->
                    SelfAvatar(state.authState, state.selfInfo, size = recommendedSize)
                },
                searchIconButton = {
                    IconButton(onSearch) {
                        Icon(Icons.Rounded.Search, "搜索")
                    }
                },
                searchBar = {
                    IconButton(onSearch) {
                        Icon(Icons.Rounded.Search, "搜索")
                    }
                },
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
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
        
        Column(Modifier.padding(topBarPadding).verticalScroll(state.pageScrollState)) {
            NavTitleHeader(
                title = { Text("最高热度") },
                contentPadding = horizontalContentPadding,
            )

            HorizontalScrollNavigatorOnDesktop(
                rememberHorizontalScrollNavigatorState(
                    state.trendingSubjectsCarouselState,
                    with(density) { CarouselItemDefaults.itemSize().preferredWidth.toPx() * 2 },
                    onClickNavigation = {
                        if (showHorizontalNavigateTip) {
                            toaster.toast(getHorizontalScrollNavigatorTipText())
                            state.setDontShowHorizontalScrollTip()
                        }
                    },
                ),
            ) {
                TrendingSubjectsCarousel(
                    state.trendingSubjectInfoPager,
                    onClick = {
                        navigator.navigateSubjectDetails(
                            subjectId = it.bangumiId,
                            placeholder = SubjectDetailPlaceholder(
                                id = it.bangumiId,
                                name = it.nameCn,
                                coverUrl = it.imageLarge,
                            ),
                        )
                    },
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                    carouselState = state.trendingSubjectsCarouselState,
                )
            }

            NavTitleHeader(
                title = { Text("继续观看") },
                contentPadding = horizontalContentPadding,
            )

            val followedSubjectsPager = state.followedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
            val followedSubjectsLayoutParameters =
                FollowedSubjectsDefaults.layoutParameters(currentWindowAdaptiveInfo1())

            HorizontalScrollNavigatorOnDesktop(
                rememberHorizontalScrollNavigatorState(
                    state.followedSubjectsLazyRowState,
                    with(density) { followedSubjectsLayoutParameters.imageSize.height.toPx() * 2 },
                    onClickNavigation = {
                        if (showHorizontalNavigateTip) {
                            toaster.toast(getHorizontalScrollNavigatorTipText())
                            state.setDontShowHorizontalScrollTip()
                        }
                    },
                ),
            ) {
                FollowedSubjectsLazyRow(
                    followedSubjectsPager,
                    onClick = {
                        navigator.navigateSubjectDetails(
                            subjectId = it.subjectInfo.subjectId,
                            placeholder = it.subjectInfo.toNavPlaceholder(),
                        )
                    },
                    onPlay = {
                        it.subjectProgressInfo.nextEpisodeIdToPlay?.let { it1 ->
                            navigator.navigateEpisodeDetails(
                                it.subjectInfo.subjectId,
                                it1,
                            )
                        }
                    },
                    layoutParameters = followedSubjectsLayoutParameters,
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                    lazyListState = state.followedSubjectsLazyRowState,
                )
            }
        }
    }
}

private fun getHorizontalScrollNavigatorTipText(): String {
    return "按住 Shift 键并滚动鼠标滚轮同样可以水平滚动"
}