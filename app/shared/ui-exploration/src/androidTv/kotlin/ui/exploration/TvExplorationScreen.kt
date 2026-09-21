/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.leanback.ui.foundation.widgets.TvLandscapeCardDefaults
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.subject.components.LocalTvDetailsActionBackdrop

internal enum class TvExplorationFocus : TvFocusKey { Details, FeedStatus }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvExplorationScreen(
    trendsPager: LazyPagingItems<TrendingSubjectInfo>,
    recommendations: LazyPagingItems<RecommendedItemInfo>,
    followed: LazyPagingItems<FollowedSubjectInfo>,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val rowWidth = maxWidth - TvExplorationDefaults.StartPadding - TvExplorationDefaults.EndPadding
        val columns = ((rowWidth + TvLandscapeCardDefaults.Spacing) /
                (TvLandscapeCardDefaults.Width + TvLandscapeCardDefaults.Spacing)).toInt().coerceAtLeast(1)
        TvExplorationContent(trendsPager, recommendations, followed, media, onIntent, columns)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvExplorationContent(
    trendsPager: LazyPagingItems<TrendingSubjectInfo>,
    recommendations: LazyPagingItems<RecommendedItemInfo>,
    followed: LazyPagingItems<FollowedSubjectInfo>,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    columns: Int,
) {
    val scope = rememberCoroutineScope()
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val columnState = rememberLazyListState()
    val followedRowState = rememberLazyListState()
    var pagePlaced by remember { mutableStateOf(false) }
    var pageFocused by remember { mutableStateOf(false) }
    var detailsFocused by remember { mutableStateOf(false) }
    var footerFocused by remember { mutableStateOf(false) }
    var area by rememberSaveable { mutableStateOf(TvExplorationArea.Featured) }
    var carouselId by rememberSaveable { mutableStateOf<Int?>(null) }
    var focusedSubjectId by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastFollowedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastRecommendationId by rememberSaveable { mutableStateOf<Int?>(null) }
    var carouselDirection by remember { mutableIntStateOf(1) }
    var preparingFocus by remember { mutableStateOf(false) }
    var preparationId by remember { mutableIntStateOf(0) }
    val heldCarouselKeys = remember { mutableSetOf<Key>() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val density = LocalDensity.current
    val accessibility = LocalAccessibilityManager.current
    val carouselItems = (0 until trendsPager.itemCount)
        .mapNotNull { trendsPager.peek(it) }.distinctBy { it.bangumiId }
    val carouselIds = carouselItems.map { it.bangumiId }
    val selectedIndex = carouselIds.indexOf(carouselId).coerceAtLeast(0)
    val featuredSubject = carouselItems.getOrNull(selectedIndex)
        ?.let { TvHeroSubject(it.bangumiId, it.nameCn, it.imageLarge) }
    LaunchedEffect(carouselIds) { if (carouselId !in carouselIds) carouselId = carouselIds.firstOrNull() }
    LaunchedEffect(detailsFocused, carouselId, carouselIds, lifecycleState) {
        if (!detailsFocused || carouselIds.size < 2 || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        val interval = accessibility?.calculateRecommendedTimeoutMillis(
            TvExplorationDefaults.CarouselAutoAdvanceMillis.toLong(), containsText = true, containsControls = true,
        ) ?: TvExplorationDefaults.CarouselAutoAdvanceMillis.toLong()
        delay(interval)
        carouselDirection = 1
        carouselId = nextFeaturedSubjectId(carouselIds, carouselId, 1)
    }
    LaunchedEffect(carouselId, trendsPager.itemCount) {
        val index = (0 until trendsPager.itemCount).firstOrNull { trendsPager.peek(it)?.bangumiId == carouselId }
        if (index != null) trendsPager[index]
    }
    val recommendationIndices = (0 until recommendations.itemCount)
        .filter { recommendations.peek(it) is RecommendedSubjectInfo }
    val rows = buildList {
        if (followed.itemCount > 0) add(TvExplorationRow.ContinueWatching(followed))
        repeat((recommendationIndices.size + columns - 1) / columns) {
            add(TvExplorationRow.RecommendationGrid(recommendations, recommendationIndices, it, columns))
        }
    }
    val currentRows by rememberUpdatedState(rows)
    val currentArea by rememberUpdatedState(area)
    val currentSubjectId by rememberUpdatedState(focusedSubjectId)
    fun rememberedId(row: TvExplorationRow) = when (row.area) {
        TvExplorationArea.ContinueWatching -> lastFollowedId
        TvExplorationArea.Recommendations -> lastRecommendationId
        TvExplorationArea.Featured -> null
    }
    val firstRow = rows.firstOrNull() as? TvExplorationRow.ContinueWatching
    val previewIndex = firstRow?.let { it.indexOfSubject(rememberedId(it)).coerceAtLeast(0) } ?: 0
    val previewCard = firstRow?.card(previewIndex)
    val previewSubject = previewCard?.subject
    val previewFollowed = firstRow?.items?.peek(previewIndex)
    val activeRow = rows.firstOrNull { it.area == area && it.indexOfSubject(focusedSubjectId) >= 0 }
    val focusedCard = activeRow?.card(activeRow.indexOfSubject(focusedSubjectId))
    val expanded = area == TvExplorationArea.Featured
    val expandProgress by animateFloatAsState(
        if (expanded || firstRow == null) 1f else 0f,
        tween(TvExplorationDefaults.HeroTransitionMillis, easing = ExplorationPanelEasing),
        label = "home-featured-space",
    )
    val heroSubject = if (expanded) featuredSubject else focusedCard?.subject ?: previewSubject
    LaunchedEffect(heroSubject) { heroSubject?.let { onIntent(TvExplorationIntent.ShowHero(it)) } }
    val backdropSubject = heroSubject
    val backdropUrl = backdropSubject?.let { media.backdropCache[it.subjectId] ?: it.imageUrl }
    if (pagePlaced) focus.InitialFocus(
        if (footerFocused) TvExplorationFocus.FeedStatus
        else focusedSubjectId?.takeIf { !expanded }?.let { TvExplorationCardKey(area, it) }
            ?: TvExplorationFocus.Details,
    )
    val footerState = when {
        recommendations.loadState.refresh is LoadState.Error -> recommendations.loadState.refresh
        recommendations.loadState.append is LoadState.Error -> recommendations.loadState.append
        followed.loadState.refresh is LoadState.Error -> followed.loadState.refresh
        followed.loadState.append is LoadState.Error -> followed.loadState.append
        recommendations.itemCount == 0 -> recommendations.loadState.refresh
        recommendations.loadState.append is LoadState.Loading -> recommendations.loadState.append
        else -> null
    }

    fun returnToHero() {
        area = TvExplorationArea.Featured
        footerFocused = false
        scope.launch {
            focus.requestPrepared(isRelevant = { area == TvExplorationArea.Featured }) {
                columnState.animateScrollToItem(0)
                TvExplorationFocus.Details
            }
        }
    }
    fun navigateToFooter() {
        if (footerState == null) return
        scope.launch {
            focus.requestPrepared {
                columnState.animateScrollToItem(currentRows.size + 1, -with(density) { 64.dp.roundToPx() })
                TvExplorationFocus.FeedStatus
            }
        }
    }
    fun navigateToRow(target: Int, column: Int? = null, subjectId: Int? = null) {
        if (target < 0) { returnToHero(); return }
        val row = currentRows.getOrNull(target) ?: run { navigateToFooter(); return }
        val index = when {
            subjectId != null -> row.indexOfSubject(subjectId).coerceAtLeast(0)
            row is TvExplorationRow.RecommendationGrid && column != null -> column.coerceAtMost(row.count - 1)
            else -> row.indexOfSubject(rememberedId(row)).coerceAtLeast(0)
        }
        val id = row.subjectIdAt(index) ?: return
        val transaction = ++preparationId
        preparingFocus = true
        scope.launch {
            try {
                focus.requestPrepared(isRelevant = { currentRows.any { it.area == row.area && it.indexOfSubject(id) >= 0 } }) {
                    val destinationIndex = currentRows.indexOfFirst { it.area == row.area && it.indexOfSubject(id) >= 0 }
                    if (!focus.isAnchorAttached(TvExplorationCardKey(row.area, id))) {
                        if (row is TvExplorationRow.ContinueWatching) columnState.animateScrollToItem(0)
                        else columnState.animateScrollToItem(
                            destinationIndex + 1, -with(density) { TvExplorationDefaults.RowAnchorInset.roundToPx() },
                        )
                        if (row is TvExplorationRow.ContinueWatching &&
                            followedRowState.layoutInfo.visibleItemsInfo.none { it.key == id }) {
                            followedRowState.scrollToItem(index)
                        }
                    }
                    TvExplorationCardKey(row.area, id)
                }
            } finally {
                if (transaction == preparationId) preparingFocus = false
            }
        }
    }
    BackHandler(enabled = pageFocused && !expanded) { returnToHero() }
    val cardBeforeUpdate = focusedSubjectId?.let { TvExplorationCardKey(area, it) }
    val cardWasFocused = cardBeforeUpdate?.let(focus::isFocused) == true
    val rowsIdentity = rows.map { row -> row.area to (0 until row.count).map(row::subjectIdAt) }
    LaunchedEffect(rowsIdentity) {
        if (expanded || footerFocused || !cardWasFocused) return@LaunchedEffect
        val previous = cardBeforeUpdate ?: return@LaunchedEffect
        val row = currentRows.firstOrNull { it.area == previous.area && it.indexOfSubject(previous.subjectId) >= 0 }
        if (row == null) {
            if (currentRows.isEmpty()) returnToHero()
            else navigateToRow(currentRows.indexOfFirst { it.area == previous.area }.coerceAtLeast(0))
        } else if (!focus.isFocused(previous)) {
            navigateToRow(currentRows.indexOf(row), subjectId = previous.subjectId)
        }
    }
    LaunchedEffect(footerState) {
        if (footerFocused && footerState == null) {
            footerFocused = false
            if (currentRows.isEmpty()) returnToHero()
            else navigateToRow(currentRows.indexOfFirst { it.area == TvExplorationArea.Recommendations }.coerceAtLeast(0))
        }
    }
    LaunchedEffect(area, focusedSubjectId, footerFocused) {
        if (expanded || footerFocused || preparingFocus) return@LaunchedEffect
        val index = currentRows.indexOfFirst { it.area == area && it.indexOfSubject(focusedSubjectId) >= 0 }
        if (index < 0) return@LaunchedEffect
        if (currentRows[index] is TvExplorationRow.ContinueWatching) columnState.animateScrollToItem(0)
        else columnState.animateScrollToItem(
            index + 1, -with(density) { TvExplorationDefaults.RowAnchorInset.roundToPx() },
        )
    }
    val scrollProgress by remember(columnState, density) {
        derivedStateOf {
            val hero = columnState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" }
            if (hero == null) if (columnState.firstVisibleItemIndex > 0) 1f else 0f
            else {
                val distance = hero.size - with(density) { TvExplorationDefaults.RowAnchorInset.toPx() }
                (-hero.offset / distance.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("tv-exploration")
            .onGloballyPositioned { pagePlaced = true }
            .onFocusChanged { pageFocused = it.hasFocus }
            .focusProperties {
                onEnter = { if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) cancelFocus() }
            }
            .focusGroup().semantics { stateDescription = area.name },
    ) {
        val collapsedHeight = (maxHeight - 226.dp).coerceAtLeast(230.dp)
        val heroHeight = collapsedHeight + TvExplorationDefaults.HeroFeaturedExtraSpace * expandProgress
        val measuredHeroHeight by rememberUpdatedState(with(density) { heroHeight.roundToPx() })
        val currentPreparingFocus by rememberUpdatedState(preparingFocus)
        TvExplorationPageLayout(
            viewportHeight = maxHeight, columnState = columnState, focus = focus,
            anchoredAtHero = { currentArea == TvExplorationArea.Featured || currentArea == TvExplorationArea.ContinueWatching },
            focusedRow = { currentRows.firstOrNull { it.area == currentArea && it.indexOfSubject(currentSubjectId) >= 0 } },
            measuredHeroHeight = { measuredHeroHeight },
            preparingFocus = { currentPreparingFocus },
            backdrop = {
                TvExplorationBackdrop(
                    backdropSubject?.copy(imageUrl = backdropUrl.orEmpty()),
                    { scrollProgress }, expanded, it.testTag("tv-exploration-backdrop"),
                )
            },
            hero = { heroModifier ->
                TvExplorationHero(
                    featuredSubject, featuredSubject?.let { media.infoCache[it.subjectId] },
                    previewSubject, previewSubject?.let { media.infoCache[it.subjectId] } ?: previewCard?.collection,
                    previewFollowed, trendsPager.loadState.refresh,
                    expanded, expandProgress, collapsedHeight, carouselDirection, carouselIds.size, selectedIndex,
                    previewVisible = area == TvExplorationArea.ContinueWatching && !footerFocused,
                    animateProgress = area == TvExplorationArea.ContinueWatching && pageFocused &&
                        lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && scrollProgress < 1f,
                    onClickDetails = {
                        if (featuredSubject != null) onIntent(TvExplorationIntent.OpenSubject(featuredSubject))
                        else if (trendsPager.loadState.refresh is LoadState.Error) trendsPager.retry()
                        else if (trendsPager.loadState.refresh is LoadState.NotLoading) trendsPager.refresh()
                    },
                    onButtonFocusChanged = {
                        detailsFocused = it
                        if (it) { area = TvExplorationArea.Featured; footerFocused = false }
                        else heldCarouselKeys.clear()
                    },
                    modifier = heroModifier,
                    buttonModifier = Modifier.tvFocusAnchor(focus, TvExplorationFocus.Details)
                        .onPreviewKeyEvent { event ->
                            when (event.key) {
                                Key.DirectionLeft, Key.DirectionRight -> {
                                    if (event.type == KeyEventType.KeyUp) heldCarouselKeys.remove(event.key)
                                    else if (event.type == KeyEventType.KeyDown && heldCarouselKeys.add(event.key)) {
                                        carouselDirection = if (event.key == Key.DirectionRight) 1 else -1
                                        carouselId = nextFeaturedSubjectId(carouselIds, carouselId, carouselDirection)
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (event.type == KeyEventType.KeyDown) navigateToRow(0)
                                    true
                                }
                                else -> false
                            }
                        },
                )
            },
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                val selectedRow = rows.indexOfFirst { it.area == area && it.indexOfSubject(focusedSubjectId) >= 0 }
                val rowAlpha by animateFloatAsState(
                    when {
                        expanded -> .6f
                        index == selectedRow -> 1f
                        index < selectedRow || footerFocused -> .2f
                        else -> .6f
                    },
                    tween(250, easing = ExplorationPanelEasing), label = "home-row-emphasis",
                )
                TvExplorationRowItem(
                    row, immersive = row is TvExplorationRow.ContinueWatching, selected = !footerFocused && area == row.area,
                    featuredProgress = expandProgress, media, onIntent, focus, followedRowState,
                    focusedSubjectId = currentSubjectId,
                    onCardFocused = { target, subject ->
                        area = target.area; footerFocused = false; focusedSubjectId = subject.subjectId
                        when (target.area) {
                            TvExplorationArea.ContinueWatching -> lastFollowedId = subject.subjectId
                            TvExplorationArea.Recommendations -> lastRecommendationId = subject.subjectId
                            TvExplorationArea.Featured -> Unit
                        }
                    },
                    onNavigateVertical = { delta, column -> navigateToRow(index + delta, column) },
                    modifier = Modifier.padding(bottom = TvExplorationDefaults.RowGap).graphicsLayer { alpha = rowAlpha },
                )
            }
            if (footerState != null) item("feed-status") {
                TvExplorationFeedStatus(
                    footerState,
                    onRetry = {
                        if (recommendations.loadState.hasError) recommendations.retry() else recommendations.refresh()
                        if (followed.loadState.hasError) followed.retry()
                    },
                    modifier = Modifier.tvFocusAnchor(focus, TvExplorationFocus.FeedStatus)
                        .onFocusChanged { if (it.isFocused) { footerFocused = true; area = TvExplorationArea.Recommendations } }
                        .onPreviewKeyEvent {
                            if (it.key == Key.DirectionUp) {
                                if (it.type == KeyEventType.KeyDown) {
                                    if (rows.isEmpty()) returnToHero() else navigateToRow(rows.lastIndex)
                                }
                                true
                            } else false
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvExplorationPageLayout(
    viewportHeight: Dp,
    columnState: LazyListState,
    focus: TvFocusScope,
    anchoredAtHero: () -> Boolean,
    focusedRow: () -> TvExplorationRow?,
    measuredHeroHeight: () -> Int,
    preparingFocus: () -> Boolean,
    backdrop: @Composable (Modifier) -> Unit,
    hero: @Composable (Modifier) -> Unit,
    rows: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scrollSpec = remember(columnState, density) {
        TvExplorationBringIntoViewSpec(
            columnState, anchoredAtHero, focusedRow, measuredHeroHeight, preparingFocus,
            with(density) { TvExplorationDefaults.RowAnchorInset.toPx() },
        )
    }
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .background(TvExplorationDefaults.Background).tvFocusNavSignal(focus),
    ) {
        val actionBackdrop = rememberHazeState()
        backdrop(Modifier.fillMaxSize().hazeSource(actionBackdrop))
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec, LocalTvDetailsActionBackdrop provides actionBackdrop) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("tv-exploration-scroll"),
                state = columnState, contentPadding = PaddingValues(bottom = viewportHeight),
            ) {
                item("hero") { hero(Modifier.fillMaxWidth()) }
                rows()
            }
            if (columnState.canScrollBackward) Box(
                Modifier.fillMaxWidth().height(TvExplorationDefaults.FadingEdgeHeight).background(
                    Brush.verticalGradient(listOf(TvExplorationDefaults.Background, Color.Transparent)),
                ),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal class TvExplorationBringIntoViewSpec(
    private val columnState: LazyListState,
    private val anchoredAtHero: () -> Boolean,
    private val focusedRow: () -> TvExplorationRow?,
    private val measuredHeroHeight: () -> Int,
    private val preparingFocus: () -> Boolean,
    private val anchorInsetPx: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // During preparation the old focused descendant must not pull the viewport back.
        if (preparingFocus()) return 0f
        val visible = columnState.layoutInfo.visibleItemsInfo
        if (anchoredAtHero()) {
            visible.firstOrNull { it.key == "hero" }?.let { return it.offset.toFloat() }
            return visible.firstOrNull { it.index == 1 }?.let { (it.offset - measuredHeroHeight()).toFloat() } ?: 0f
        }
        val row = visible.firstOrNull { it.key == focusedRow()?.key } ?: return 0f
        return row.offset - anchorInsetPx
    }
}
