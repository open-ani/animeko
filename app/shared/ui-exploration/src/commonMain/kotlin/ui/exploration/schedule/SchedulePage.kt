/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.HorizontalScrollControlScaffoldOnDesktop
import me.him188.ani.app.ui.foundation.HorizontalScrollControlState
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.compareTo
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.rememberHorizontalScrollControlState
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.utils.platform.collections.ImmutableEnumMap
import me.him188.ani.utils.platform.isDesktop

@Stable
private val dayOfWeekEntries = DayOfWeek.entries

@Stable
class SchedulePageState(
    initialSelectedDay: DayOfWeek = DayOfWeek.MONDAY,
) {
    // on mobile
    internal val pagerState = PagerState(
        currentPage = dayOfWeekEntries.indexOf(initialSelectedDay),
    ) { dayOfWeekEntries.size }

    // on desktop jvm
    val lazyListState = LazyListState(firstVisibleItemIndex = pagerState.currentPage)

    val selectedDay: DayOfWeek by derivedStateOf {
        dayOfWeekEntries[pagerState.currentPage]
    }

    val scheduleColumnStates = ImmutableEnumMap<DayOfWeek, LazyListState> {
        LazyListState()
    }

    suspend fun scrollTo(day: DayOfWeek) {
        pagerState.scrollToPage(dayOfWeekEntries.indexOf(day))
    }

    suspend fun animateScrollTo(day: DayOfWeek) {
        pagerState.animateScrollToPage(dayOfWeekEntries.indexOf(day))
    }
}


@Composable
fun SchedulePage(
    presentation: SchedulePagePresentation,
    onRetry: () -> Unit,
    onClickItem: (item: AiringScheduleItemPresentation) -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: SchedulePageLayoutParams = SchedulePageLayoutParams.calculate(),
    colors: SchedulePageColors = SchedulePageDefaults.colors(),
    navigationIcon: @Composable () -> Unit = {},
    state: SchedulePageState = remember { SchedulePageState() },
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    Scaffold(
        modifier,
        topBar = {
            AniTopAppBar(
                title = { Text("新番时间表") },
                Modifier.fillMaxWidth(),
                navigationIcon = navigationIcon,
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        if (presentation.error != null) {
            LoadErrorCard(
                presentation.error,
                onRetry = onRetry,
                modifier = Modifier.padding(paddingValues)
                    .padding(layoutParams.pageContentPadding)
                    .padding(all = 16.dp),
            )
        } else {
            SchedulePageContent(
                modifier = Modifier.padding(paddingValues),
                layoutParams = layoutParams,
                colors = colors,
                state = state,
            ) { day ->
                ScheduleDayColumn(
                    onClickItem = onClickItem,
                    dayOfWeek = {
                        if (layoutParams.showDayOfWeekHeadline) {
                            DayOfWeekHeadline(day)
                        }
                    },
                    items = presentation.airingSchedules.firstOrNull { it.date.dayOfWeek == day }?.episodes.orEmpty(),
                    layoutParams = layoutParams.columnLayoutParams,
                    state = state.scheduleColumnStates[day],
                    itemColors = colors.itemColors,
                )
            }
        }
    }
}

@Composable
private fun DayOfWeekHeadline(
    day: DayOfWeek,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(IntrinsicSize.Min)) {
        Text(renderDayOfWeek(day), Modifier.width(IntrinsicSize.Max), softWrap = false)

        // Rounded horizontal divider
        val thickness = 2.dp
        val color = MaterialTheme.colorScheme.outlineVariant
        Canvas(
            Modifier.padding(top = 2.dp)
                .fillMaxWidth()
                .height(thickness),
        ) {
            drawLine(
                color = color,
                strokeWidth = thickness.toPx(),
                start = Offset(0f, thickness.toPx() / 2),
                end = Offset(size.width, thickness.toPx() / 2),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun SchedulePageContent(
    modifier: Modifier = Modifier,
    layoutParams: SchedulePageLayoutParams = SchedulePageLayoutParams.calculate(),
    colors: SchedulePageColors = SchedulePageDefaults.colors(),
    state: SchedulePageState = remember { SchedulePageState() },
    pageContent: @Composable (page: DayOfWeek) -> Unit,
) {
    Column(modifier) {
        val uiScope = rememberCoroutineScope()
        if (layoutParams.showTabRow) {
            ScrollableTabRow(
                selectedTabIndex = state.pagerState.currentPage,
                containerColor = colors.tabRowContainerColor,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.pagerTabIndicatorOffset(state.pagerState, tabPositions),
                    )
                },
            ) {
                dayOfWeekEntries.forEach { day ->
                    Tab(
                        selected = state.selectedDay == day,
                        onClick = {
                            uiScope.launch {
                                state.animateScrollTo(day)
                            }
                        },
                        text = { Text(renderDayOfWeek(day), softWrap = false) },
                        selectedContentColor = colors.tabSelectedContentColor,
                        unselectedContentColor = colors.tabUnselectedContentColor,
                    )
                }
            }
        }

        val density = LocalDensity.current

        if (LocalPlatform.current.isDesktop() && !layoutParams.isSinglePage) {
            // CMP bug, HorizontalPager 在 PC 上滚动到末尾后, 内嵌的 LazyColumn 无法纵向滚动
            HorizontalScrollControlScaffoldOnDesktop(
                rememberHorizontalScrollControlState(
                    state.lazyListState,
                    onClickScroll = { direction ->
                        uiScope.launch {
                            state.lazyListState.animateScrollBy(
                                with(density) { (300.dp).toPx() } *
                                        if (direction == HorizontalScrollControlState.Direction.BACKWARD) -1 else 1,
                            )
                        }
                    },
                ),
            ) {
                LazyRow(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(layoutParams.pageSpacing),
                    state = state.lazyListState,
                ) {
                    items(dayOfWeekEntries.size) { index ->
                        val widthModifier = when (val pageSize = layoutParams.pageSize) {
                            PageSize.Fill -> Modifier.fillMaxWidth()
                            is PageSize.Fixed -> Modifier.width(pageSize.pageSize)
                            else -> Modifier
                        }
                        Box(widthModifier.fillParentMaxHeight().padding(layoutParams.pageContentPadding)) {
                            pageContent(dayOfWeekEntries[index])
                        }
                    }
                }
            }
        } else {
            HorizontalPager(
                state.pagerState,
                Modifier.fillMaxSize(),
                pageSize = layoutParams.pageSize,
                pageSpacing = layoutParams.pageSpacing,
                contentPadding = layoutParams.pageContentPadding,
                verticalAlignment = Alignment.Top,
                key = { it },
            ) { index ->
                Box(Modifier.fillMaxSize()) { // ensure the page is scrollable
                    pageContent(dayOfWeekEntries[index])
                }
            }
        }
    }
}

@Immutable
@ExposedCopyVisibility
data class SchedulePageLayoutParams private constructor(
    val pageSize: PageSize,
    val pageSpacing: Dp,
    val pageContentPadding: PaddingValues,
    val showTabRow: Boolean,
    val showDayOfWeekHeadline: Boolean,
    val columnLayoutParams: ScheduleDayColumnLayoutParams,
    val isSinglePage: Boolean, // Workaround for CMP bug
) {
    @Stable
    companion object {
        @Stable
        val Compact = SchedulePageLayoutParams(
            pageSize = PageSize.Fill,
            pageSpacing = 8.dp,
            pageContentPadding = PaddingValues(0.dp),
            showTabRow = true,
            showDayOfWeekHeadline = false,
            columnLayoutParams = ScheduleDayColumnLayoutParams.Default,
            isSinglePage = true,
        )

        @Stable
        val Medium = SchedulePageLayoutParams(
            pageSize = PageSize.Fixed(360.dp),
            pageSpacing = 16.dp,
            pageContentPadding = PaddingValues(horizontal = 8.dp),
            showTabRow = false,
            showDayOfWeekHeadline = true,
            columnLayoutParams = ScheduleDayColumnLayoutParams.Default,
            isSinglePage = false,
        )

        @Composable
        fun calculate(windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass): SchedulePageLayoutParams {
            return if (windowSizeClass.windowWidthSizeClass >= WindowWidthSizeClass.MEDIUM) {
                Medium
            } else {
                Compact
            }
        }
    }
}

@Immutable
data class SchedulePageColors(
    val tabRowContainerColor: Color,
    val tabSelectedContentColor: Color,
    val tabUnselectedContentColor: Color,
    val itemColors: ListItemColors,
)

@Stable
object SchedulePageDefaults {
    @Composable
    fun colors(
        tabRowColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tabSelectedContentColor: Color = contentColorFor(tabRowColor),
        tabUnselectedContentColor: Color = contentColorFor(tabRowColor),
        itemColors: ListItemColors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ): SchedulePageColors = SchedulePageColors(
        tabRowContainerColor = tabRowColor,
        tabSelectedContentColor = tabSelectedContentColor,
        tabUnselectedContentColor = tabUnselectedContentColor,
        itemColors = itemColors,
    )
}


@Stable
private fun renderDayOfWeek(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
    else -> day.toString()
}
