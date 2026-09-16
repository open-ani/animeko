/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_continue_watching
import me.him188.ani.app.ui.lang.exploration_for_you
import me.him188.ani.leanback.ui.foundation.focus.TvAnchoredBringIntoViewSpec
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.widgets.TvLandscapeCard
import me.him188.ani.leanback.ui.foundation.widgets.TvLandscapeCardDefaults
import org.jetbrains.compose.resources.stringResource

/** Card identity survives pagination, reordering and changes to the number of grid columns. */
internal data class TvExplorationCardKey(val area: TvExplorationArea, val subjectId: Int) : TvFocusKey

internal sealed class TvExplorationRow(val key: String, val area: TvExplorationArea, val hasTitle: Boolean) {
    abstract val count: Int
    abstract fun subjectIdAt(index: Int): Int?
    fun indexOfSubject(id: Int?): Int = (0 until count).firstOrNull { subjectIdAt(it) == id } ?: -1

    class ContinueWatching(val items: LazyPagingItems<FollowedSubjectInfo>) :
        TvExplorationRow("followed", TvExplorationArea.ContinueWatching, true) {
        override val count get() = items.itemCount
        override fun subjectIdAt(index: Int) =
            if (index in 0 until count) items.peek(index)?.subjectInfo?.subjectId else null
    }

    class RecommendationGrid(val items: LazyPagingItems<RecommendedItemInfo>, val rowIndex: Int, val columns: Int) :
        TvExplorationRow("rec-$rowIndex", TvExplorationArea.Recommendations, rowIndex == 0) {
        val start = rowIndex * columns
        override val count get() = (items.itemCount - start).coerceIn(0, columns)
        override fun subjectIdAt(index: Int) = if (index in 0 until count) {
            (items.peek(start + index) as? RecommendedSubjectInfo)?.bangumiId
        } else null
    }
}

internal fun tmdbBackdropCardUrl(url: String): String = url.replace("/t/p/w1280/", "/t/p/w780/")

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvExplorationRowItem(
    row: TvExplorationRow,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    focus: TvFocusScope,
    followedRowState: LazyListState,
    focusedSubjectId: Int?,
    onCardFocused: (TvExplorationRow, TvHeroSubject) -> Unit,
    onNavigateVertical: (delta: Int, fromIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val verticalNav = Modifier.onPreviewKeyEvent { event ->
        val delta = when (event.key) {
            Key.DirectionUp -> -1; Key.DirectionDown -> 1; else -> return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown) onNavigateVertical(
            delta,
            row.indexOfSubject(focusedSubjectId).coerceAtLeast(0),
        )
        true
    }
    Column(modifier.fillMaxWidth().testTag("tv-exploration-row-${row.key}")) {
        if (row.hasTitle) {
            Box(Modifier.height(TvExplorationDefaults.RowHeaderHeight).padding(top = 4.dp)) {
                Text(
                    stringResource(if (row is TvExplorationRow.ContinueWatching) Lang.exploration_continue_watching else Lang.exploration_for_you),
                    color = TvExplorationDefaults.Content,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                )
            }
        }
        when (row) {
            is TvExplorationRow.ContinueWatching -> {
                val startAligned = remember { TvAnchoredBringIntoViewSpec() }
                CompositionLocalProvider(LocalBringIntoViewSpec provides startAligned) {
                    LazyRow(
                        state = followedRowState,
                        modifier = verticalNav.fillMaxWidth().onPreviewKeyEvent { event ->
                            val delta = when (event.key) {
                                Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return@onPreviewKeyEvent false
                            }
                            val current = row.indexOfSubject(focusedSubjectId).coerceAtLeast(0)
                            if (current == 0 && delta < 0) return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyDown) {
                                val target = (current + delta).coerceIn(0, (row.count - 1).coerceAtLeast(0))
                                val id = row.subjectIdAt(target) ?: return@onPreviewKeyEvent true
                                scope.launch {
                                    focus.requestPrepared {
                                        if (followedRowState.layoutInfo.visibleItemsInfo.none { it.key == id }) {
                                            followedRowState.animateScrollToItem(target)
                                        }
                                        TvExplorationCardKey(row.area, id)
                                    }
                                }
                            }
                            true
                        },
                        horizontalArrangement = Arrangement.spacedBy(TvExplorationDefaults.CardSpacing),
                        contentPadding = PaddingValues(end = TvExplorationDefaults.EndPadding, bottom = 6.dp),
                    ) {
                        items(row.count, key = { row.subjectIdAt(it) ?: "followed-placeholder-$it" }) { index ->
                            val item = row.items[index] ?: return@items
                            val info = item.subjectInfo
                            val subject = TvHeroSubject(info.subjectId, info.displayName, info.imageLarge)
                            CardMediaEffect(subject, item.subjectCollectionInfo, onIntent)
                            TvLandscapeCard(
                                title = subject.title,
                                imageUrl = media.backdropCache[subject.subjectId]?.let(::tmdbBackdropCardUrl)
                                    ?: subject.imageUrl,
                                width = TvExplorationDefaults.ContinueCardWidth, showTitle = false,
                                onClick = {
                                    val episodeId = item.subjectProgressInfo.nextEpisodeIdToPlay
                                    if (episodeId != null) onIntent(
                                        TvExplorationIntent.ContinueWatching(
                                            subject,
                                            episodeId,
                                        ),
                                    )
                                    else onIntent(TvExplorationIntent.OpenSubject(subject))
                                },
                                onLongClick = { onIntent(TvExplorationIntent.OpenSubject(subject)) },
                                onFocused = { onCardFocused(row, subject) },
                                memoryId = "exploration-followed-${subject.subjectId}",
                                modifier = Modifier.testTag("tv-exploration-followed-${subject.subjectId}")
                                    .tvFocusAnchor(focus, TvExplorationCardKey(row.area, subject.subjectId)),
                            )
                        }
                    }
                }
            }

            is TvExplorationRow.RecommendationGrid -> Row(
                verticalNav.fillMaxWidth().padding(end = TvExplorationDefaults.EndPadding),
                horizontalArrangement = Arrangement.spacedBy(TvLandscapeCardDefaults.Spacing),
            ) {
                repeat(row.columns) { column ->
                    val index = row.start + column
                    val item = if (index < row.items.itemCount) row.items[index] as? RecommendedSubjectInfo else null
                    if (item == null) Spacer(Modifier.weight(1f)) else key(item.bangumiId) {
                        val subject = TvHeroSubject(item.bangumiId, item.nameCn, item.imageLarge)
                        CardMediaEffect(subject, null, onIntent)
                        TvLandscapeCard(
                            imageUrl = media.backdropCache[subject.subjectId]?.let(::tmdbBackdropCardUrl)
                                ?: subject.imageUrl,
                            title = subject.title, width = null,
                            onClick = { onIntent(TvExplorationIntent.OpenSubject(subject)) },
                            onFocused = { onCardFocused(row, subject) },
                            memoryId = "exploration-rec-${subject.subjectId}",
                            modifier = Modifier.weight(1f)
                                .testTag("tv-exploration-rec-${subject.subjectId}")
                                .tvFocusAnchor(focus, TvExplorationCardKey(row.area, subject.subjectId))
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.DirectionRight && column == row.count - 1) true else false
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardMediaEffect(
    subject: TvHeroSubject,
    collection: SubjectCollectionInfo?,
    onIntent: (TvExplorationIntent) -> Unit
) {
    LaunchedEffect(subject.subjectId, collection) {
        onIntent(
            TvExplorationIntent.CardVisible(
                subject.subjectId,
                collection,
            ),
        )
    }
}
