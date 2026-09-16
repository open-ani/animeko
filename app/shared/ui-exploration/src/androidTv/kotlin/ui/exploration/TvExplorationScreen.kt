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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.widgets.TvLandscapeCardDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

private enum class TvExplorationFocus : TvFocusKey { Details, FeedStatus }

/** Featured → immersive first row → ordinary whole-page browsing, all in one scroll container. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun TvExplorationScreen(
    trendsPager: LazyPagingItems<TrendingSubjectInfo>,
    recommendations: LazyPagingItems<RecommendedItemInfo>,
    followed: LazyPagingItems<FollowedSubjectInfo>,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focus = rememberTvFocusScope()
    focus.Resolver()
    // Lazy items attach during measurement, before they can receive focus.
    var pagePlaced by remember { mutableStateOf(false) }
    val columnState = rememberLazyListState()
    val followedRowState = rememberLazyListState()
    val hazeState = rememberHazeState()
    var area by rememberSaveable { mutableStateOf(TvExplorationArea.Featured) }
    var carouselId by rememberSaveable { mutableStateOf<Int?>(null) }
    var carouselDirection by remember { mutableIntStateOf(1) }
    var focusedSubjectId by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastFollowedId by rememberSaveable { mutableStateOf<Int?>(null) }
    if (pagePlaced) focus.InitialFocus(
        focusedSubjectId?.takeIf { area != TvExplorationArea.Featured }?.let { TvExplorationCardKey(area, it) }
            ?: TvExplorationFocus.Details,
    )
    var detailsFocused by remember { mutableStateOf(false) }
    var pageFocused by remember { mutableStateOf(false) }
    var footerFocused by remember { mutableStateOf(false) }
    val heldCarouselKeys = remember { mutableSetOf<Key>() }
    val pageLifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycle by pageLifecycle.currentStateFlow.collectAsState()
    val density = LocalDensity.current

    val carouselItems = (0 until minOf(trendsPager.itemCount, TvExplorationDefaults.CarouselMaxItems))
        .mapNotNull { trendsPager.peek(it) }.distinctBy { it.bangumiId }
    val carouselIds = carouselItems.map { it.bangumiId }
    val selectedIndex = carouselIds.indexOf(carouselId).coerceAtLeast(0)
    val selected = carouselItems.getOrNull(selectedIndex)
    LaunchedEffect(carouselIds) { if (carouselId !in carouselIds) carouselId = carouselIds.firstOrNull() }

    LaunchedEffect(detailsFocused, carouselId, carouselIds, lifecycle) {
        if (!detailsFocused || carouselIds.size < 2 || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        delay(TvExplorationDefaults.CarouselAutoAdvanceMillis.toLong())
        carouselDirection = 1
        carouselId = nextFeaturedSubjectId(carouselIds, carouselId, 1)
    }

    val focusedSubject = when (area) {
        TvExplorationArea.Featured -> null
        TvExplorationArea.ContinueWatching -> followed.itemSnapshotList.items
            .firstOrNull { it.subjectInfo.subjectId == focusedSubjectId }?.subjectInfo
            ?.let { TvHeroSubject(it.subjectId, it.displayName, it.imageLarge) }

        TvExplorationArea.Recommendations -> recommendations.itemSnapshotList.items
            .filterIsInstance<RecommendedSubjectInfo>().firstOrNull { it.bangumiId == focusedSubjectId }
            ?.let { TvHeroSubject(it.bangumiId, it.nameCn, it.imageLarge) }
    }
    val featuredSubject = selected?.let { TvHeroSubject(it.bangumiId, it.nameCn, it.imageLarge) }
    val heroSubject = focusedSubject ?: featuredSubject
    val expanded = area == TvExplorationArea.Featured
    // Pages without Continue Watching retain the featured height and scroll it away directly.
    val compact = !expanded && followed.itemCount > 0
    val expandProgress by animateFloatAsState(
        if (compact) 0f else 1f,
        tween(TvExplorationDefaults.HeroTransitionMillis), label = "exploration-hero-transition",
    )
    val heightFraction = TvExplorationDefaults.HeroCollapsedFraction +
            (TvExplorationDefaults.HeroExpandedFraction - TvExplorationDefaults.HeroCollapsedFraction) * expandProgress

    LaunchedEffect(heroSubject) { heroSubject?.let { onIntent(TvExplorationIntent.ShowHero(it)) } }
    var backdropSubject by remember { mutableStateOf(heroSubject) }
    LaunchedEffect(heroSubject) {
        if (backdropSubject != null && !expanded) delay(TvExplorationDefaults.BackdropDebounceMillis)
        backdropSubject = heroSubject
    }
    val backdropUrl = backdropSubject?.let { media.backdropCache[it.subjectId] ?: it.imageUrl }

    fun returnToHero() {
        area = TvExplorationArea.Featured
        scope.launch {
            focus.requestPrepared(isRelevant = { area == TvExplorationArea.Featured }) {
                columnState.animateScrollToItem(0)
                TvExplorationFocus.Details
            }
        }
    }
    BackHandler(enabled = pageFocused && area != TvExplorationArea.Featured) { returnToHero() }

    BoxWithConstraints(
        modifier.fillMaxSize().testTag("tv-exploration")
            .onGloballyPositioned { pagePlaced = true }
            .onFocusChanged { pageFocused = it.hasFocus }
            // Navigation can attempt spatial focus during its return animation. Read the lifecycle
            // directly so the RESUMED focus request does not wait for the next recomposition.
            .focusProperties {
                onEnter = { if (!pageLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) cancelFocus() }
            }
            .focusGroup().semantics { stateDescription = area.name },
    ) {
        val rowWidth = maxWidth - TvExplorationDefaults.StartPadding - TvExplorationDefaults.EndPadding
        val columns = ((rowWidth + TvLandscapeCardDefaults.Spacing) /
                (TvLandscapeCardDefaults.Width + TvLandscapeCardDefaults.Spacing)).toInt().coerceAtLeast(1)
        val rows = buildList {
            if (followed.itemCount > 0) add(TvExplorationRow.ContinueWatching(followed))
            repeat((recommendations.itemCount + columns - 1) / columns) {
                add(TvExplorationRow.RecommendationGrid(recommendations, it, columns))
            }
        }
        val currentRows by rememberUpdatedState(rows)
        val currentArea by rememberUpdatedState(area)
        val currentSubjectId by rememberUpdatedState(focusedSubjectId)
        val currentFooterFocused by rememberUpdatedState(footerFocused)
        val footerState = when {
            recommendations.loadState.refresh is LoadState.Error -> recommendations.loadState.refresh
            recommendations.loadState.append is LoadState.Error -> recommendations.loadState.append
            followed.loadState.refresh is LoadState.Error -> followed.loadState.refresh
            followed.loadState.append is LoadState.Error -> followed.loadState.append
            recommendations.itemCount == 0 -> recommendations.loadState.refresh
            recommendations.loadState.append is LoadState.Loading -> recommendations.loadState.append
            else -> null
        }

        fun navigateToFooter() {
            if (footerState == null) return
            scope.launch {
                focus.requestPrepared {
                    columnState.animateScrollToItem(currentRows.size + 1)
                    TvExplorationFocus.FeedStatus
                }
            }
        }

        fun navigateToRow(target: Int, fromIndex: Int) {
            val row = currentRows.getOrNull(target) ?: run { navigateToFooter(); return }
            val index =
                if (row is TvExplorationRow.ContinueWatching) row.indexOfSubject(lastFollowedId).coerceAtLeast(0)
                else fromIndex.coerceIn(0, (row.count - 1).coerceAtLeast(0))
            val id = row.subjectIdAt(index) ?: return
            scope.launch {
                focus.requestPrepared(isRelevant = { currentRows.any { it.area == row.area && it.indexOfSubject(id) >= 0 } }) {
                    val destination = currentRows.firstOrNull { it.area == row.area && it.indexOfSubject(id) >= 0 }
                        ?: return@requestPrepared null
                    if (destination is TvExplorationRow.ContinueWatching) {
                        if (columnState.firstVisibleItemIndex != 0 || columnState.firstVisibleItemScrollOffset != 0) {
                            columnState.animateScrollToItem(0)
                        }
                        if (followedRowState.layoutInfo.visibleItemsInfo.none { it.key == id }) {
                            followedRowState.scrollToItem(destination.indexOfSubject(id))
                        }
                    } else {
                        val inset = with(density) {
                            ((if (destination.hasTitle) TvExplorationDefaults.RowHeaderHeight else 0.dp) -
                                    TvExplorationDefaults.RowAnchorInset).roundToPx()
                        }
                        columnState.animateScrollToItem(currentRows.indexOf(destination) + 1, inset)
                    }
                    TvExplorationCardKey(row.area, id)
                }
            }
        }

        LaunchedEffect(rows.firstOrNull()?.key) {
            if (area == TvExplorationArea.Featured) columnState.scrollToItem(0)
        }
        // A removed collection or refreshed grid must not leave focus on a recycled card.
        val followedIds = followed.itemSnapshotList.items.map { it.subjectInfo.subjectId }
        val recommendationIds =
            recommendations.itemSnapshotList.items.filterIsInstance<RecommendedSubjectInfo>().map { it.bangumiId }
        val cardWasFocused = focusedSubjectId?.let { focus.isFocused(TvExplorationCardKey(area, it)) } == true
        LaunchedEffect(followedIds, recommendationIds) {
            if (!cardWasFocused || focusedSubjectId == null || footerFocused || area == TvExplorationArea.Featured) return@LaunchedEffect
            val ids = if (area == TvExplorationArea.ContinueWatching) followedIds else recommendationIds
            if (focusedSubjectId !in ids) {
                val destination = rows.indexOfFirst { it.area == area }.takeIf { it >= 0 } ?: 0
                if (rows.isEmpty()) returnToHero() else navigateToRow(destination, 0)
            }
        }
        val footerWasFocused = focus.isFocused(TvExplorationFocus.FeedStatus)
        LaunchedEffect(footerState) {
            if (footerFocused && footerState == null) {
                footerFocused = false
                if (footerWasFocused) {
                    if (rows.isEmpty()) returnToHero() else navigateToRow(rows.lastIndex, 0)
                }
            }
        }
        val scrollProgress by remember(columnState) {
            derivedStateOf {
                val hero = columnState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" }
                if (hero == null) {
                    if (columnState.firstVisibleItemIndex > 0) 1f else 0f
                } else (-hero.offset.toFloat() / hero.size.coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
        TvMaterialTheme(colorScheme = TvMaterialTheme.colorScheme.copy(primary = TvExplorationDefaults.Content)) {
            TvExplorationPageLayout(
                viewportHeight = maxHeight, heroHeight = maxHeight * heightFraction,
                columnState = columnState, focus = focus,
                anchoredAtHero = { currentArea == TvExplorationArea.Featured || currentArea == TvExplorationArea.ContinueWatching },
                focusedRow = {
                    if (currentFooterFocused) null else currentRows.firstOrNull {
                        it.area == currentArea && it.indexOfSubject(
                            currentSubjectId,
                        ) >= 0
                    }
                },
                backdrop = {
                    TvExplorationBackdrop(
                        backdropSubject?.copy(imageUrl = backdropUrl.orEmpty()),
                        { scrollProgress }, it.testTag("tv-exploration-backdrop").hazeSource(hazeState),
                    )
                },
                hero = { heroModifier, minimumHeight ->
                    TvExplorationHero(
                        heroSubject, heroSubject?.let { media.infoCache[it.subjectId] },
                        followedSubject = followed.itemSnapshotList.items.firstOrNull {
                            area == TvExplorationArea.ContinueWatching && it.subjectInfo.subjectId == focusedSubjectId
                        },
                        loadState = trendsPager.loadState.refresh,
                        expanded = expanded, expandProgress = expandProgress,
                        minimumHeight = minimumHeight,
                        slideDirection = if (expanded) carouselDirection else 0,
                        carouselSize = carouselIds.size, carouselIndex = selectedIndex,
                        hazeState = hazeState,
                        onClickDetails = {
                            if (featuredSubject != null) onIntent(TvExplorationIntent.OpenSubject(featuredSubject))
                            else if (trendsPager.loadState.refresh is LoadState.Error) trendsPager.retry()
                            else if (trendsPager.loadState.refresh is LoadState.NotLoading) trendsPager.refresh()
                        },
                        onButtonFocusChanged = {
                            detailsFocused = it
                            if (it) {
                                area = TvExplorationArea.Featured; footerFocused = false
                            } else heldCarouselKeys.clear()
                        },
                        modifier = heroModifier,
                        buttonModifier = Modifier.then(
                            if (expanded) Modifier.tvFocusAnchor(
                                focus,
                                TvExplorationFocus.Details,
                            ) else Modifier,
                        )
                            .onPreviewKeyEvent { event ->
                                when (event.key) {
                                    Key.DirectionLeft, Key.DirectionRight -> {
                                        if (event.type == KeyEventType.KeyUp) heldCarouselKeys.remove(event.key)
                                        else if (event.type == KeyEventType.KeyDown && heldCarouselKeys.add(event.key)) {
                                            carouselDirection = if (event.key == Key.DirectionRight) 1 else -1
                                            carouselId = nextFeaturedSubjectId(
                                                carouselIds, carouselId,
                                                carouselDirection,
                                            )
                                        }
                                        true
                                    }

                                    Key.DirectionDown -> {
                                        if (event.type == KeyEventType.KeyDown) navigateToRow(0, 0)
                                        true
                                    }

                                    else -> false
                                }
                            },
                    )
                },
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    TvExplorationRowItem(
                        row, media, onIntent, focus, followedRowState, focusedSubjectId,
                        onCardFocused = { targetRow, subject ->
                            area = targetRow.area
                            footerFocused = false
                            focusedSubjectId = subject.subjectId
                            if (targetRow is TvExplorationRow.ContinueWatching) lastFollowedId = subject.subjectId
                        },
                        onNavigateVertical = { delta, from ->
                            if (index + delta < 0) returnToHero() else navigateToRow(index + delta, from)
                        },
                        modifier = Modifier.padding(start = TvExplorationDefaults.StartPadding),
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
                            .onFocusChanged {
                                if (it.isFocused) {
                                    area = TvExplorationArea.Recommendations; footerFocused = true
                                }
                            }
                            .onPreviewKeyEvent {
                                if (it.key == Key.DirectionUp) {
                                    if (it.type == KeyEventType.KeyDown) {
                                        if (rows.isEmpty()) returnToHero() else navigateToRow(rows.lastIndex, 0)
                                    }
                                    true
                                } else it.key == Key.DirectionDown
                            },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvExplorationPageLayout(
    viewportHeight: Dp, heroHeight: Dp, columnState: LazyListState, focus: TvFocusScope,
    anchoredAtHero: () -> Boolean, focusedRow: () -> TvExplorationRow?,
    backdrop: @Composable (Modifier) -> Unit, hero: @Composable (Modifier, Dp) -> Unit,
    rows: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scrollSpec = remember(columnState, density) {
        TvExplorationBringIntoViewSpec(
            columnState, anchoredAtHero, focusedRow,
            with(density) { TvExplorationDefaults.RowHeaderHeight.toPx() },
            with(density) { TvExplorationDefaults.RowAnchorInset.toPx() },
        )
    }
    Box(Modifier.fillMaxSize().background(TvExplorationDefaults.Background).tvFocusNavSignal(focus)) {
        backdrop(Modifier.fillMaxSize())
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec) {
            LazyColumn(
                Modifier.fillMaxSize().verticalFadingEdges(columnState).testTag("tv-exploration-scroll"),
                state = columnState, contentPadding = PaddingValues(bottom = viewportHeight),
                verticalArrangement = Arrangement.spacedBy(TvExplorationDefaults.RowGap),
            ) {
                item("hero") { hero(Modifier.fillMaxWidth(), heroHeight) }
                rows()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal class TvExplorationBringIntoViewSpec(
    private val columnState: LazyListState,
    private val anchoredAtHero: () -> Boolean,
    private val focusedRow: () -> TvExplorationRow?,
    private val headerHeightPx: Float,
    private val anchorInsetPx: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (anchoredAtHero()) {
            return columnState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" }?.offset?.toFloat() ?: 0f
        }
        val row = focusedRow()
        val layout = columnState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row?.key }
        val cardTop = if (layout != null) layout.offset + if (row?.hasTitle == true) headerHeightPx else 0f else offset
        return cardTop - anchorInsetPx
    }
}

private fun Modifier.verticalFadingEdges(state: LazyListState): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val height = TvExplorationDefaults.FadingEdgeHeight.toPx()
    if (state.canScrollBackward) drawRect(
        Brush.verticalGradient(listOf(Color.Black, Color.Transparent), 0f, height),
        blendMode = BlendMode.DstOut,
    )
    if (state.canScrollForward) drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black),
            size.height - height,
            size.height,
        ),
        blendMode = BlendMode.DstOut,
    )
}
