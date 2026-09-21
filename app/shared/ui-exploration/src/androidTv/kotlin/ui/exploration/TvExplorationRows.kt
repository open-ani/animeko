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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_continue_watching
import me.him188.ani.app.ui.lang.exploration_for_you
import me.him188.ani.app.ui.lang.exploration_trending
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.leanback.ui.foundation.focus.tvCardFocusBorder
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.leanback.ui.foundation.widgets.TvLandscapeCard
import me.him188.ani.leanback.ui.foundation.widgets.TvLandscapeCardDefaults
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal data class TvExplorationCardKey(val area: TvExplorationArea, val subjectId: Int) : TvFocusKey

internal data class TvHomeCard(
    val subject: TvHeroSubject,
    val collection: SubjectCollectionInfo? = null,
    val episodeId: Int? = null,
)

internal sealed class TvExplorationRow(val key: String, val area: TvExplorationArea, val title: StringResource) {
    abstract val count: Int
    abstract fun card(index: Int, load: Boolean = false): TvHomeCard?
    fun subjectIdAt(index: Int) = card(index)?.subject?.subjectId
    fun indexOfSubject(id: Int?) = (0 until count).firstOrNull { subjectIdAt(it) == id } ?: -1

    class ContinueWatching(val items: LazyPagingItems<FollowedSubjectInfo>) :
        TvExplorationRow("followed", TvExplorationArea.ContinueWatching, Lang.exploration_continue_watching) {
        override val count get() = items.itemCount
        override fun card(index: Int, load: Boolean): TvHomeCard? {
            if (index !in 0 until count) return null
            val item = (if (load) items[index] else items.peek(index)) ?: return null
            val info = item.subjectInfo
            return TvHomeCard(
                TvHeroSubject(info.subjectId, info.displayName, info.imageLarge),
                item.subjectCollectionInfo, item.subjectProgressInfo.nextEpisodeIdToPlay,
            )
        }
    }

    class RecommendationGrid(
        val items: LazyPagingItems<RecommendedItemInfo>,
        val indices: List<Int>,
        val rowIndex: Int,
        val columns: Int,
    ) : TvExplorationRow("rec-$rowIndex", TvExplorationArea.Recommendations, Lang.exploration_for_you) {
        override val count get() = (indices.size - rowIndex * columns).coerceIn(0, columns)
        override fun card(index: Int, load: Boolean): TvHomeCard? {
            if (index !in 0 until count) return null
            val sourceIndex = indices[rowIndex * columns + index]
            val item = (if (load) items[sourceIndex] else items.peek(sourceIndex)) as? RecommendedSubjectInfo ?: return null
            return TvHomeCard(TvHeroSubject(item.bangumiId, item.nameCn, item.imageLarge))
        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvExplorationRowItem(
    row: TvExplorationRow,
    immersive: Boolean,
    selected: Boolean,
    featuredProgress: Float,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    focus: TvFocusScope,
    rowState: LazyListState,
    focusedSubjectId: Int?,
    onCardFocused: (TvExplorationRow, TvHeroSubject) -> Unit,
    onNavigateVertical: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val emphasis by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(250, easing = ExplorationPanelEasing), label = "home-row-title",
    )
    val headerHeight = if (immersive) 33.dp + 11.dp * (1f - featuredProgress) else 36.dp + 20.dp * emphasis
    val topPadding = 12.dp
    val horizontalSpec = remember(density) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val start = with(density) { TvExplorationDefaults.StartPadding.toPx() }
                // Leanback's horizontal keyline keeps the selected poster at the left edge.
                return offset - start
            }
        }
    }
    Column(
        modifier.fillMaxWidth().testTag("tv-exploration-row-${row.key}")
            .onPreviewKeyEvent { event ->
                val delta = when (event.key) {
                    Key.DirectionUp -> -1
                    Key.DirectionDown -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) onNavigateVertical(delta, row.indexOfSubject(focusedSubjectId).coerceAtLeast(0))
                true
            },
    ) {
        if (row !is TvExplorationRow.RecommendationGrid || row.rowIndex == 0) Box(
            Modifier.fillMaxWidth().height((headerHeight - topPadding).coerceAtLeast(0.dp))
                .padding(start = TvExplorationDefaults.StartPadding),
            contentAlignment = Alignment.BottomStart,
        ) {
            val titleScale = (16f + 10f * emphasis) / 26f
            Text(
                stringResource(row.title),
                Modifier.testTag("tv-exploration-row-title-${row.key}")
                    .wrapContentHeight(Alignment.Bottom, unbounded = true).graphicsLayer {
                        scaleX = titleScale; scaleY = titleScale
                        translationY = if (immersive) 0f else -16.dp.toPx() * emphasis
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
                color = TvExplorationDefaults.Content, fontSize = 26.sp, lineHeight = 32.sp, maxLines = 1,
            )
        }
        if (row is TvExplorationRow.RecommendationGrid) {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = TvExplorationDefaults.StartPadding, end = TvExplorationDefaults.EndPadding,
                    top = topPadding, bottom = 4.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvLandscapeCardDefaults.Spacing),
            ) {
                repeat(row.columns) { index ->
                    val card = row.card(index, load = true)
                    if (card == null) Spacer(Modifier.weight(1f)) else key(card.subject.subjectId) {
                        val subject = card.subject
                        val anchor = TvExplorationCardKey(row.area, subject.subjectId)
                        LaunchedEffect(subject.subjectId) {
                            onIntent(TvExplorationIntent.CardVisible(subject.subjectId))
                        }
                        TvLandscapeCard(
                            imageUrl = media.backdropCache[subject.subjectId] ?: subject.imageUrl,
                            title = subject.title, width = null,
                            onClick = { onIntent(TvExplorationIntent.OpenSubject(subject)) },
                            onFocused = { onCardFocused(row, subject) },
                            memoryId = "exploration-rec-${subject.subjectId}",
                            modifier = Modifier.weight(1f).testTag("tv-exploration-rec-${subject.subjectId}")
                                .tvFocusAnchor(focus, anchor)
                                .onGloballyPositioned { focus.onAnchorAttached(anchor) }
                                .onPreviewKeyEvent { event ->
                                    val delta = when (event.key) {
                                        Key.DirectionLeft -> -1
                                        Key.DirectionRight -> 1
                                        else -> return@onPreviewKeyEvent false
                                    }
                                    if (index == 0 && delta < 0) return@onPreviewKeyEvent false
                                    if (event.type == KeyEventType.KeyDown) {
                                        row.subjectIdAt(index + delta)?.let {
                                            focus.request(TvExplorationCardKey(row.area, it))
                                        }
                                    }
                                    true
                                },
                        )
                    }
                }
            }
        } else CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalSpec) {
            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                    val delta = when (event.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        else -> return@onPreviewKeyEvent false
                    }
                    val current = row.indexOfSubject(focusedSubjectId).coerceAtLeast(0)
                    if (current == 0 && delta < 0) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        val target = (current + delta).coerceIn(0, (row.count - 1).coerceAtLeast(0))
                        val id = row.subjectIdAt(target) ?: return@onPreviewKeyEvent true
                        scope.launch {
                            focus.requestPrepared {
                                if (rowState.layoutInfo.visibleItemsInfo.none { it.key == id }) {
                                    rowState.animateScrollToItem(target)
                                }
                                TvExplorationCardKey(row.area, id)
                            }
                        }
                    }
                    true
                },
                contentPadding = PaddingValues(
                    start = TvExplorationDefaults.StartPadding, end = TvExplorationDefaults.EndPadding,
                    top = topPadding, bottom = 12.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvExplorationDefaults.CardSpacing),
            ) {
                items(row.count, key = { row.subjectIdAt(it) ?: "${row.key}-placeholder-$it" }) { index ->
                    val card = row.card(index, load = true) ?: return@items
                    val subject = card.subject
                    LaunchedEffect(subject.subjectId, card.collection) {
                        onIntent(TvExplorationIntent.CardVisible(subject.subjectId, card.collection))
                    }
                    HomePoster(
                        card = card,
                        imageUrl = media.backdropCache[subject.subjectId] ?: subject.imageUrl,
                        onClick = {
                            val episodeId = card.episodeId
                            if (episodeId != null) onIntent(TvExplorationIntent.ContinueWatching(subject, episodeId))
                            else onIntent(TvExplorationIntent.OpenSubject(subject))
                        },
                        onLongClick = { onIntent(TvExplorationIntent.OpenSubject(subject)) },
                        onFocused = { onCardFocused(row, subject) },
                        modifier = Modifier.testTag("tv-exploration-${row.key}-${subject.subjectId}")
                            .tvFocusAnchor(focus, TvExplorationCardKey(row.area, subject.subjectId))
                            .onGloballyPositioned {
                                // Lazy composition can attach the focus node before it is placed.
                                // Re-announce readiness on layout so a pending request can complete.
                                focus.onAnchorAttached(TvExplorationCardKey(row.area, subject.subjectId))
                            }
                            .tvFocusMemorable("exploration-${row.key}-${subject.subjectId}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePoster(
    card: TvHomeCard,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val width = TvExplorationDefaults.ImmersiveCardWidth
    val height = TvExplorationDefaults.ImmersiveCardHeight
    val radius = 12.dp
    Column(Modifier.width(width)) {
        Box(
            modifier.width(width).height(height)
                .semantics { contentDescription = card.subject.title }
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button, onClick = onClick, onLongClick = onLongClick,
                )
                .tvCardFocusBorder(focused, RoundedCornerShape(radius + TvFocusDefaults.RingInset)),
        ) {
            AsyncImage(
                imageUrl, null,
                Modifier.fillMaxSize().padding(TvFocusDefaults.RingInset)
                    .clip(RoundedCornerShape(radius)).background(TvExplorationDefaults.Background),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
