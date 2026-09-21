/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.relations

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.SubjectRelationGraph
import me.him188.ani.app.data.models.subject.SubjectRelationGraphSubject
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.HorizontalScrollControlScaffoldOnDesktop
import me.him188.ani.app.ui.foundation.HorizontalScrollControlState
import me.him188.ani.app.ui.foundation.rememberHorizontalScrollControlState
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_relation_graph_summary
import me.him188.ani.app.ui.lang.subject_relation_graph_summary_main_only
import me.him188.ani.app.ui.lang.subject_relation_graph_title
import me.him188.ani.app.ui.lang.subject_relation_graph_truncated
import me.him188.ani.app.ui.search.LoadErrorCard
import org.jetbrains.compose.resources.stringResource

const val SUBJECT_RELATION_GRAPH_TEST_TAG = "SubjectRelationGraph"

/**
 * 系列关系图页面. 主线条目构成一条时间线, 番外和衍生挂在对应的主线条目下.
 *
 * 可用宽度小于 [WIDE_LAYOUT_MIN_WIDTH] 时时间线纵向排列 ([SubjectRelationGraphColumn]),
 * 否则横向排列 ([SubjectRelationGraphRow]).
 */
@Composable
fun SubjectRelationGraphScreen(
    vm: SubjectRelationGraphViewModel,
    onClickSubject: (SubjectRelationGraphSubject) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    SubjectRelationGraphScreen(state, vm::retry, onClickSubject, modifier, navigationIcon, windowInsets)
}

@Composable
fun SubjectRelationGraphScreen(
    state: SubjectRelationGraphUiState,
    onRetry: () -> Unit,
    onClickSubject: (SubjectRelationGraphSubject) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    Scaffold(
        modifier,
        topBar = {
            AniTopAppBar(
                title = { Text(stringResource(Lang.subject_relation_graph_title)) },
                navigationIcon = navigationIcon,
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        // 页面可能显示在窄的 pane 中, 因此用实际可用宽度而不是 WindowSizeClass
        BoxWithConstraints(Modifier.padding(paddingValues).fillMaxSize()) {
            val graph = state.graph
            when {
                graph != null -> {
                    val presentation = remember(graph) { SubjectRelationGraphPresentation(graph) }
                    if (maxWidth < WIDE_LAYOUT_MIN_WIDTH) {
                        SubjectRelationGraphColumn(
                            presentation, onClickSubject,
                            Modifier.fillMaxSize().testTag(SUBJECT_RELATION_GRAPH_TEST_TAG),
                        )
                    } else {
                        SubjectRelationGraphRow(
                            presentation, onClickSubject,
                            Modifier.fillMaxSize().testTag(SUBJECT_RELATION_GRAPH_TEST_TAG),
                        )
                    }
                }

                state.error != null -> LoadErrorCard(
                    state.error,
                    onRetry,
                    Modifier.align(Alignment.TopCenter).padding(16.dp).widthIn(max = 480.dp),
                )

                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * [SubjectRelationGraph] 中与布局无关的派生信息.
 */
@Immutable
internal class SubjectRelationGraphPresentation(
    val graph: SubjectRelationGraph,
) {
    /**
     * 用户查看的条目所在的主线位置: 它自己在主线上, 或它是该主线条目的分支. 找不到时为 -1.
     */
    val currentMainIndex: Int = graph.mainline.indexOfFirst { node ->
        node.subject.subjectId == graph.subjectId || node.branches.any { it.subject.subjectId == graph.subjectId }
    }

    /**
     * 每个主线条目是 "第几部". 剧场版不计数, 为 `null`.
     */
    val ordinals: List<Int?> = run {
        var count = 0
        graph.mainline.map { if (it.isMovie) null else ++count }
    }

    val seriesName: String = (graph.mainline.firstOrNull { !it.isMovie } ?: graph.mainline.firstOrNull())
        ?.subject?.displayName.orEmpty()

    /** 时间线走到 [index] 处是否已经经过用户查看的条目 */
    fun isReached(index: Int): Boolean = index <= currentMainIndex
}

/**
 * 纵向时间线, 适合手机.
 */
@Composable
internal fun SubjectRelationGraphColumn(
    presentation: SubjectRelationGraphPresentation,
    onClickSubject: (SubjectRelationGraphSubject) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 16.dp, bottom = 24.dp),
) {
    val graph = presentation.graph
    // 第 0 项是标题, 因此下标 currentMainIndex 是当前条目的前一部: 把它显示在顶部以保留上下文.
    // 当前条目在前两部时从标题开始显示.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = presentation.currentMainIndex.takeIf { it > 1 } ?: 0,
    )
    LazyColumn(modifier, listState, contentPadding = contentPadding) {
        item("header") {
            SubjectRelationGraphHeader(presentation, Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp))
        }
        itemsIndexed(graph.mainline, key = { _, node -> node.subject.subjectId }) { index, node ->
            val colors = SubjectRelationGraphDefaults.timelineColors()
            Row(
                Modifier.fillMaxWidth().timelineVertical(
                    colors = colors,
                    // 圆点对齐海报的垂直中心
                    dotCenterY = SubjectRelationGraphDefaults.CompactCardPadding +
                            SubjectRelationGraphDefaults.CompactPosterHeight / 2,
                    dot = timelineDot(presentation, index),
                    lineBefore = timelineLine(presentation, index, before = true),
                    lineAfter = timelineLine(presentation, index, before = false),
                ),
            ) {
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1f).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectRelationGraphCompactCard(
                        node,
                        ordinal = presentation.ordinals[index],
                        isCurrent = node.subject.subjectId == graph.subjectId,
                        onClick = { onClickSubject(node.subject) },
                        Modifier.fillMaxWidth(),
                    )
                    SubjectRelationGraphBranchList(
                        node.branches,
                        currentSubjectId = graph.subjectId,
                        seriesName = presentation.seriesName,
                        collapsedCount = SubjectRelationGraphDefaults.COLLAPSED_BRANCH_COUNT_COMPACT,
                        nameMaxLines = 1,
                        onClick = onClickSubject,
                        Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        if (graph.truncated) {
            item("truncated") { TruncatedHint(Modifier.padding(start = 4.dp)) }
        }
    }
}

/**
 * 横向时间线, 适合平板和桌面. 时间轴在上方, 每个主线条目是一列: 年份, 大海报, 以及它的相关条目列表.
 */
@Composable
internal fun SubjectRelationGraphRow(
    presentation: SubjectRelationGraphPresentation,
    onClickSubject: (SubjectRelationGraphSubject) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 48.dp,
) {
    val graph = presentation.graph
    val horizontalScrollState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(presentation) {
        // 让当前条目的前一部显示在最左, 保留上下文
        val index = (presentation.currentMainIndex - 1).coerceAtLeast(0)
        horizontalScrollState.scrollTo(with(density) { (WIDE_COLUMN_WIDTH * index).roundToPx() })
    }
    val verticalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .verticalWheelScrollsHorizontally(horizontalScrollState, verticalScrollState)
            .verticalScroll(verticalScrollState)
            .padding(bottom = 24.dp),
    ) {
        SubjectRelationGraphHeader(
            presentation,
            Modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 4.dp, bottom = 24.dp),
        )
        // 桌面端鼠标没有横向滚轮, 悬停时显示左右翻页按钮
        HorizontalScrollControlScaffoldOnDesktop(
            rememberHorizontalScrollControlState(horizontalScrollState) { direction ->
                val distance = with(density) { (WIDE_COLUMN_WIDTH * 3).toPx() }
                scope.launch {
                    horizontalScrollState.animateScrollBy(
                        if (direction == HorizontalScrollControlState.Direction.BACKWARD) -distance else distance,
                    )
                }
            },
        ) {
            Row(Modifier.horizontalScroll(horizontalScrollState).padding(horizontal = horizontalPadding)) {
                val colors = SubjectRelationGraphDefaults.timelineColors()
                graph.mainline.forEachIndexed { index, node ->
                    val isCurrent = index == presentation.currentMainIndex
                    Column(Modifier.width(WIDE_COLUMN_WIDTH)) {
                        Text(
                            renderYear(node.subject).orEmpty(),
                            Modifier.padding(start = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(
                            Modifier.fillMaxWidth().height(28.dp).timelineHorizontal(
                                colors = colors,
                                dotCenterX = 14.dp,
                                dot = timelineDot(presentation, index),
                                lineBefore = timelineLine(presentation, index, before = true),
                                lineAfter = timelineLine(presentation, index, before = false),
                            ),
                        )
                        Column(
                            Modifier.padding(end = WIDE_COLUMN_WIDTH - SubjectRelationGraphDefaults.PosterWidth),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SubjectRelationGraphPoster(
                                node,
                                ordinal = presentation.ordinals[index],
                                isCurrent = node.subject.subjectId == graph.subjectId,
                                onClick = { onClickSubject(node.subject) },
                            )
                            if (node.branches.isNotEmpty()) {
                                HorizontalDivider()
                                SubjectRelationGraphBranchList(
                                    node.branches,
                                    currentSubjectId = graph.subjectId,
                                    seriesName = presentation.seriesName,
                                    collapsedCount = SubjectRelationGraphDefaults.COLLAPSED_BRANCH_COUNT_WIDE,
                                    nameMaxLines = 2,
                                    onClick = onClickSubject,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (graph.truncated) {
            TruncatedHint(Modifier.padding(horizontal = horizontalPadding).padding(top = 24.dp))
        }
    }
}

/**
 * 页面的主轴是横向的, 而鼠标通常只有纵向滚轮: 当页面在滚轮方向上无法纵向滚动时, 用纵向滚轮横向滚动时间线.
 */
private fun Modifier.verticalWheelScrollsHorizontally(
    horizontalScrollState: ScrollState,
    verticalScrollState: ScrollState,
): Modifier = pointerInput(horizontalScrollState, verticalScrollState) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            val delta = change.scrollDelta
            if (delta.x != 0f || delta.y == 0f) continue
            val canScrollVertically =
                if (delta.y > 0) verticalScrollState.canScrollForward else verticalScrollState.canScrollBackward
            if (canScrollVertically) continue
            horizontalScrollState.dispatchRawDelta(delta.y * WHEEL_SCROLL_STEP.toPx())
            change.consume()
        }
    }
}

private fun timelineDot(presentation: SubjectRelationGraphPresentation, index: Int): TimelineDot = when {
    index == presentation.currentMainIndex -> TimelineDot.CURRENT
    presentation.isReached(index) -> TimelineDot.REACHED
    else -> TimelineDot.UPCOMING
}

private fun timelineLine(
    presentation: SubjectRelationGraphPresentation,
    index: Int,
    before: Boolean,
): TimelineLine = when {
    before && index == 0 -> TimelineLine.NONE
    !before && index == presentation.graph.mainline.lastIndex -> TimelineLine.NONE
    before -> if (presentation.isReached(index)) TimelineLine.REACHED else TimelineLine.UPCOMING
    else -> if (presentation.isReached(index + 1)) TimelineLine.REACHED else TimelineLine.UPCOMING
}

@Composable
private fun SubjectRelationGraphHeader(
    presentation: SubjectRelationGraphPresentation,
    modifier: Modifier = Modifier,
) {
    val graph = presentation.graph
    Column(modifier) {
        Text(
            presentation.seriesName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (graph.branchCount > 0) {
                stringResource(Lang.subject_relation_graph_summary, graph.mainCount, graph.branchCount)
            } else {
                stringResource(Lang.subject_relation_graph_summary_main_only, graph.mainCount)
            },
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TruncatedHint(modifier: Modifier = Modifier) {
    Text(
        stringResource(Lang.subject_relation_graph_truncated),
        modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val WIDE_LAYOUT_MIN_WIDTH = 600.dp
private val WIDE_COLUMN_WIDTH = 204.dp
private val WHEEL_SCROLL_STEP = 64.dp
