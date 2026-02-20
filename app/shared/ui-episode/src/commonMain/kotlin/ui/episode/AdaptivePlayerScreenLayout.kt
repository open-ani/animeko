/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsetsSides.Companion.Bottom
import androidx.compose.foundation.layout.WindowInsetsSides.Companion.End
import androidx.compose.foundation.layout.WindowInsetsSides.Companion.Horizontal
import androidx.compose.foundation.layout.WindowInsetsSides.Companion.Start
import androidx.compose.foundation.layout.WindowInsetsSides.Companion.Top
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.comment.CommentColumn
import me.him188.ani.app.ui.comment.generateUiComment
import me.him188.ani.app.ui.episode.AdaptivePlayerScreenLayoutParams.Mode
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.search.rememberTestLazyPagingItems
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isMobile

@Stable
class AdaptivePlayerScreenScaffoldState(
    commentCount: () -> Int?,
) {
    internal val pagerState: PagerState = PagerState(currentPage = 0, pageCount = { 2 })
    internal val commentCount by derivedStateOf(commentCount)
    internal var preferTheater by mutableStateOf(false)

    fun setPreferTheaterMode(prefer: Boolean) {
        preferTheater = prefer
    }
}

/**
 * Slotting requirements:
 * - 每个组件必须 consume [AdaptivePlayerScreenScope.windowInsets].
 * - 组件无需有侧边 padding.
 *
 * @param topAppBar [AdaptivePlayerScreenScope.TopAppBar]
 * @param summary 播放器下方的简介, 不包含评论.
 * @param player 播放器, 需要 [Modifier.fillMaxSize].
 * @param supporting PC 右侧的信息栏, 数据源选择器, 选择剧集等.
 */
@Composable
fun AdaptivePlayerScreenScaffold(
    state: AdaptivePlayerScreenScaffoldState,
    topAppBar: @Composable AdaptivePlayerScreenScope.() -> Unit,
    player: @Composable AdaptivePlayerScreenScope.(mode: PlayerTopBarMode) -> Unit,
    summary: @Composable AdaptivePlayerScreenScope.(expanded: Boolean) -> Unit,
    comments: @Composable AdaptivePlayerScreenScope.() -> Unit,
    supporting: @Composable AdaptivePlayerScreenScope.() -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val layoutParamsState = rememberUpdatedState(
        AdaptivePlayerScreenLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass, state.preferTheater),
    )
    val windowInsetsState = rememberUpdatedState(windowInsets)
    val coroutineScope = rememberCoroutineScope()

    @Composable
    fun rememberScope(
        windowInsetsSides: WindowInsetsSides?,
    ): AdaptivePlayerScreenScopeImpl {
        val windowInsetsSidesUpdated = rememberUpdatedState(windowInsetsSides)
        return remember(layoutParamsState, windowInsetsState, windowInsetsSidesUpdated) {
            object : AdaptivePlayerScreenScopeImpl() {
                override val layoutParams: AdaptivePlayerScreenLayoutParams get() = layoutParamsState.value
                override val windowInsets: WindowInsets
                    get() {
                        val sides = windowInsetsSidesUpdated.value ?: return windowInsetsState.value
                        return windowInsetsState.value.only(sides)
                    }
            }
        }
    }

    val movablePlayer = movableContentOf {
        val playerShape = when (layoutParamsState.value.mode) {
            Mode.COMPACT -> RectangleShape
            else -> MaterialTheme.shapes.large
        }
        val playerWindowInsetsSides = when (layoutParamsState.value.mode) {
            Mode.COMPACT -> Top + Horizontal
            Mode.HORIZONTAL -> Start
            Mode.THEATER -> Horizontal
        }
        val playerTopBarMode = when (layoutParamsState.value.mode) {
            Mode.COMPACT -> PlayerTopBarMode.FULL
            Mode.HORIZONTAL -> PlayerTopBarMode.ACTIONS
            Mode.THEATER -> PlayerTopBarMode.ACTIONS
        }

        Box(Modifier.clip(playerShape)) {
            player(
                rememberScope(playerWindowInsetsSides),
                playerTopBarMode,
            )
        }
    }

    Surface(
        modifier,
        color = containerColor,
    ) {
        when (layoutParamsState.value.mode) {
            Mode.COMPACT -> {
                Column {
                    Surface(Modifier.aspectRatio(16f / 9f).fillMaxWidth(), color = Color.Black) {
                        movablePlayer()
                    }

                    Row(Modifier.fillMaxWidth()) {
                        TabRow(
                            state.pagerState,
                            coroutineScope, { state.commentCount },
                            Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        )
                    }

                    HorizontalPager(
                        state.pagerState,
                        Modifier.fillMaxWidth().weight(1f),
                        userScrollEnabled = LocalPlatform.current.isMobile(),
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        when (page) {
                            0 -> {
                                Box(
                                    Modifier
                                        .padding(horizontal = layoutParamsState.value.pageHorizontalPadding)
                                        .padding(top = layoutParamsState.value.pageVerticalPadding),
                                ) {
                                    summary(
                                        rememberScope(
                                            windowInsetsSides = Horizontal,
                                        ),
                                        false,
                                    )
                                }
                            }

                            1 -> {
                                Column {
                                    supporting(
                                        rememberScope(
                                            windowInsetsSides = Horizontal + Bottom,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Mode.HORIZONTAL,
            Mode.THEATER,
                -> {
                Column {
                    topAppBar(
                        rememberScope(
                            windowInsetsSides = Horizontal + Top,
                        ),
                    )

                    val pageContentPaddings = PaddingValues(
                        start = layoutParamsState.value.pageHorizontalPadding,
                        end = layoutParamsState.value.pageHorizontalPadding,
                        top = layoutParamsState.value.pageVerticalPadding,
                    )
                    Row(
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(pageContentPaddings)
                            .padding(pageContentPaddings),
                    ) {
                        // LHS Column
                        Column(Modifier.weight(1f)) {
                            Surface(Modifier.aspectRatio(16f / 9f).fillMaxWidth(), color = Color.Black) {
                                movablePlayer()
                            }
                            Spacer(Modifier.height(layoutParamsState.value.pageVerticalPadding))

                            Column(Modifier.padding(horizontal = 8.dp)) {
                                summary(
                                    rememberScope(windowInsetsSides = Start),
                                    true,
                                )
                                comments(
                                    rememberScope(windowInsetsSides = Start + Bottom),
                                )
                            }
                        }

                        Spacer(Modifier.width(layoutParamsState.value.pageHorizontalPadding))

                        Column(Modifier.width(380.dp).windowInsetsPadding(windowInsets.only(End))) {
                            supporting(
                                rememberScope(windowInsetsSides = End + Bottom),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Stable
sealed class AdaptivePlayerScreenScope {
    abstract val layoutParams: AdaptivePlayerScreenLayoutParams

    /**
     * 该组件需要 consume 的 window insets.
     */
    abstract val windowInsets: WindowInsets

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun TopAppBar(
        title: @Composable () -> Unit,
        navigationIcon: @Composable () -> Unit,
        subtitle: @Composable (() -> Unit)? = null,
        modifier: Modifier = Modifier,
    ) {
        var content = @Composable {
            if (subtitle == null) {
                TopAppBar(
                    title, modifier,
                    navigationIcon = navigationIcon,
                    colors = TopAppBarDefaults.topAppBarColors(),
                    windowInsets = windowInsets,
                )
            } else {
                TopAppBar(
                    title, subtitle, modifier,
                    navigationIcon = navigationIcon,
                    windowInsets = windowInsets,
                )
            }
        }

        content = when (layoutParams.mode) {
            Mode.COMPACT,
            Mode.HORIZONTAL,
                -> content

            Mode.THEATER -> {
                @Composable {
                    AniTheme(darkModeOverride = DarkMode.DARK) {
                        content()
                    }
                }
            }
        }

        content()
    }
}

enum class PlayerTopBarMode {
    /**
     * 承担 TopAppBar 的功能.
     */
    FULL,

    /**
     * 只用来显示一些操作, 没有 navigation icon
     */
    ACTIONS,
}

private abstract class AdaptivePlayerScreenScopeImpl : AdaptivePlayerScreenScope()


@Composable
private fun TabRow(
    pagerState: PagerState,
    scope: CoroutineScope,
    commentCount: () -> Int?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    TabRow(
        selectedTabIndex = pagerState.currentPage,
        modifier,
        indicator = @Composable { tabPositions ->
            TabRowDefaults.PrimaryIndicator(
                Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
            )
        },
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor),
//        edgePadding = 0.dp,
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


///////////////////////////////////////////////////////////////////////////
// layout params
///////////////////////////////////////////////////////////////////////////

@Immutable
@ConsistentCopyVisibility
data class AdaptivePlayerScreenLayoutParams private constructor(
    val mode: Mode,
    val pageHorizontalPadding: Dp,
    val pageVerticalPadding: Dp,
) {
    enum class Mode {
        /**
         * 手机竖屏, 平板竖屏
         */
        COMPACT,

        /**
         * 手机横屏, PC, 横屏
         */
        HORIZONTAL,

        /**
         * PC 剧场模式
         */
        THEATER
    }

    companion object {
        private const val BREAKPOINT_WIDTH = 1300

        @Composable
        fun calculate(windowSizeClass: WindowSizeClass, preferTheater: Boolean): AdaptivePlayerScreenLayoutParams {
            val mode = calculateMode(windowSizeClass, preferTheater)

            return AdaptivePlayerScreenLayoutParams(
                mode,
                pageHorizontalPadding = if (windowSizeClass.isWidthAtLeastMedium) 24.dp else 16.dp,
                pageVerticalPadding = if (windowSizeClass.isHeightAtLeastMedium) 24.dp else 16.dp,
            )
        }

        private fun calculateMode(
            windowSizeClass: WindowSizeClass,
            preferTheater: Boolean,
        ) = if (windowSizeClass.isWidthAtLeastBreakpoint(BREAKPOINT_WIDTH)) {
            if (preferTheater) {
                Mode.THEATER
            } else {
                Mode.HORIZONTAL
            }
        } else {
            if (windowSizeClass.minHeightDp < windowSizeClass.minWidthDp) {
                Mode.HORIZONTAL
            } else {
                Mode.COMPACT
            }
        }
    }
}


///////////////////////////////////////////////////////////////////////////
// previews
///////////////////////////////////////////////////////////////////////////


@Composable
private fun DummyPlayerSurface(modifier: Modifier) {
    Surface(
        modifier,
        color = Color.Black,
    ) {}
}

@OptIn(TestOnly::class)
@Composable
@Preview(name = "Phone")
@Preview(
    name = "Desktop",
    device = "id:pixel_tablet",
//    device = "spec:width=1500dp,height=1200dp,dpi=240"
)
private fun PreviewEpisodePageLayout() {
    ProvideCompositionLocalsForPreview {
        AdaptivePlayerScreenScaffold(
            state = remember { AdaptivePlayerScreenScaffoldState { 50 } },
            topAppBar = { TopAppBar(title = { Text("Title") }) },
            player = { DummyPlayerSurface(Modifier.fillMaxSize()) },
            summary = { expanded ->
                PlayingEpisodeSummaryRow(
                    expanded, TestPlayingEpisodeSummary, {}, {},
                    {
                        Button({}) {
                            Text("dummy")
                        }
                    },
                )
            },
            comments = {
                CommentColumn(rememberTestLazyPagingItems(generateUiComment(10))) { _, item ->
                    Text("Comment")
                }
            },
            supporting = {
                Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                    Text("1")
                }
            },
        )
    }
}
