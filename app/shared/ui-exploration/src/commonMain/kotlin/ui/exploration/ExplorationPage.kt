/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.NavTitleHeader
import me.him188.ani.app.ui.exploration.followed.FollowedSubjectsLazyRow
import me.him188.ani.app.ui.exploration.trends.TrendingSubjectsCarousel
import me.him188.ani.app.ui.exploration.trends.TrendingSubjectsState
import me.him188.ani.app.ui.foundation.layout.isAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

@Stable
class ExplorationPageState(
    val authState: AuthState,
    selfInfoState: State<UserInfo?>,
    val trendingSubjectsState: TrendingSubjectsState,
    val followedSubjectsPager: Flow<PagingData<FollowedSubjectInfo>>,
) {
    val selfInfo by selfInfoState

    val trendingSubjectsCarouselState = CarouselState(itemCount = { trendingSubjectsState.numItems })
    val followedSubjectsLazyRowState = LazyListState()
}

@Composable
fun ExplorationPage(
    state: ExplorationPageState,
    onSearch: () -> Unit,
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        topBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title("探索") },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                Modifier.fillMaxWidth(),
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
                actions = {
                    if (currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass.isAtLeastMedium) {
                        IconButton(onClick = onClickSettings) {
                            Icon(Icons.Rounded.Settings, "设置")
                        }
                    }
                },
                avatar = {
                    SelfAvatar(state.authState, state.selfInfo)
                },
            )
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { topBarPadding ->
        val horizontalPadding = currentWindowAdaptiveInfo().windowSizeClass.paneHorizontalPadding
        val horizontalContentPadding =
            PaddingValues(horizontal = horizontalPadding)

        val navigator = LocalNavigator.current
        Column(Modifier.padding(topBarPadding)) {
            NavTitleHeader(
                title = { Text("最高热度") },
                contentPadding = horizontalContentPadding,
            )

            TrendingSubjectsCarousel(
                state.trendingSubjectsState,
                onClick = {
                    navigator.navigateSubjectDetails(it.bangumiId)
                },
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                carouselState = state.trendingSubjectsCarouselState,
            )

            NavTitleHeader(
                title = { Text("继续观看") },
                contentPadding = horizontalContentPadding,
            )
            val followedSubjectsPager = state.followedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
            FollowedSubjectsLazyRow(
                followedSubjectsPager,
                onClick = {
                    navigator.navigateSubjectDetails(it.subjectInfo.subjectId)
                },
                onPlay = {
                    it.subjectProgressInfo.nextEpisodeIdToPlay?.let { it1 ->
                        navigator.navigateEpisodeDetails(
                            it.subjectInfo.subjectId,
                            it1,
                        )
                    }
                },
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                lazyListState = state.followedSubjectsLazyRowState,
            )
        }
    }
}
