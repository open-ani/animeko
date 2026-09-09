/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_empty_title
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.subject_details_review_title
import me.him188.ani.app.ui.lang.subject_details_reviews_count
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.layout.TvModalOverlay
import me.him188.ani.leanback.ui.foundation.layout.tvModalUnderlay
import me.him188.ani.leanback.ui.subject.TvSubjectDetailsContentState
import me.him188.ani.leanback.ui.subject.components.TvDetailsBackdrop
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsPanel
import me.him188.ani.leanback.ui.subject.presentation.detailsFocusFallback
import me.him188.ani.leanback.ui.subject.reviews.TvReviewBringIntoViewSpec
import me.him188.ani.leanback.ui.subject.reviews.TvReviewCard
import me.him188.ani.leanback.ui.subject.reviews.TvReviewDefaults
import me.him188.ani.leanback.ui.subject.reviews.TvReviewLoading
import me.him188.ani.leanback.ui.subject.reviews.TvReviewOverview
import me.him188.ani.leanback.ui.subject.reviews.TvReviewScrollbar
import me.him188.ani.leanback.ui.subject.reviews.tvReviewEdges
import org.jetbrains.compose.resources.stringResource

/** Fixed overview and an independently paged review list, retained beneath nested overlays. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TvSubjectComments(
    details: TvSubjectDetailsContentState,
    comments: LazyPagingItems<UIComment>,
    backdrop: String,
    panel: TvDetailsPanel,
    active: Boolean,
    onFocused: (String) -> Unit,
    onClose: () -> Unit,
    onRating: () -> Unit,
    onComment: (UIComment) -> Unit,
    ratingBoundsModifier: Modifier,
) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    val inputMode = LocalInputModeManager.current
    var laidOut by remember(active) { mutableStateOf(false) }
    val isActive by rememberUpdatedState(active)
    var entryFocusPending by remember { mutableStateOf(true) }
    var entryTarget by remember { mutableStateOf(panel.focusedItem) }
    var entryNavigation by remember { mutableStateOf(focus.userNavGeneration) }
    var lastReview by rememberSaveable { mutableStateOf<String?>(null) }
    var previousKeys by remember { mutableStateOf(emptyList<String>()) }
    val error = (comments.loadState.refresh as? LoadState.Error) ?: (comments.loadState.append as? LoadState.Error)
    val reviewKeys = comments.itemSnapshotList.items.map { "review:${it.stableId}" }
    val keys = reviewKeys + when {
        reviewKeys.isEmpty() -> listOf("review-status")
        error != null -> listOf("review-retry")
        else -> emptyList()
    }
    val currentKeys by rememberUpdatedState(keys)

    suspend fun restore(target: String?, previous: List<String> = emptyList(), animate: Boolean = false) {
        focus.requestPrepared(isRelevant = { isActive }) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { laidOut && window.isWindowFocused }.first { it }
            val selected = if (target == "review-rating") target else
                detailsFocusFallback(target, previous, currentKeys, "review-status")
            if (entryFocusPending) entryTarget = selected
            val index = currentKeys.indexOf(selected)
            if (index >= 0) {
                if (list.layoutInfo.visibleItemsInfo.none { it.key == selected }) {
                    if (animate) list.animateScrollToItem(index) else list.scrollToItem(index)
                }
                val distance = snapshotFlow {
                    val layout = list.layoutInfo
                    // Paging can update its keys before LazyColumn applies the new item layout.
                    // Restore only after that layout, so removing a focused card cannot undo it.
                    layout.visibleItemsInfo.firstOrNull {
                        it.key == selected && it.index == currentKeys.indexOf(selected)
                    }?.let {
                        it.offset + it.size / 2f - (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                    }
                }.first { it != null }
                // The scroll range naturally clamps the first and last reviews to the list edges.
                if (animate) {
                    // Focus moves immediately; a new direction cancels this animation and continues
                    // from the current card, rather than waiting for the previous scroll to finish.
                    focus.request(TvDetailsKey(selected))
                    list.animateScrollBy(checkNotNull(distance))
                    return@requestPrepared null
                }
                list.scrollBy(checkNotNull(distance))
            }
            TvDetailsKey(selected)
        }
    }

    LaunchedEffect(active) {
        // Arm while the page is covered, before Compose restores focus as the child disappears.
        entryFocusPending = true
        entryTarget = panel.focusedItem
        entryNavigation = focus.userNavGeneration
        if (active) {
            // Pointer entry can leave Android in touch mode, which rejects the initial remote focus.
            inputMode.requestInputMode(InputMode.Keyboard)
            restore(panel.focusedItem, previousKeys)
        }
    }
    val focusedBeforeUpdate = panel.focusedItem
    LaunchedEffect(keys) {
        if (active && focusedBeforeUpdate != null && focusedBeforeUpdate != "review-rating" && focusedBeforeUpdate !in keys) {
            restore(focusedBeforeUpdate, previousKeys)
        }
        previousKeys = keys
    }
    fun anchor(key: String) = Modifier.tvFocusAnchor(focus, TvDetailsKey(key))
        .onFocusChanged {
            if (it.isFocused && isActive) {
                // Removing a child modal may temporarily focus the first card before restoration.
                // Preserve the remembered review unless this is the prepared entry or user navigation.
                if (entryFocusPending && focus.userNavGeneration == entryNavigation && key != entryTarget) {
                    return@onFocusChanged
                }
                entryFocusPending = false
                if (key.startsWith("review:")) lastReview = key
                onFocused(key)
            }
        }.testTag("tv-details-$key")
    fun move(delta: Int) {
        val index = keys.indexOf(panel.focusedItem)
        keys.getOrNull(index + delta)?.let { scope.launch { restore(it, animate = true) } }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides TvReviewBringIntoViewSpec) {
        TvModalOverlay(
            active = active,
            onClose = { if (isActive) onClose() },
            modifier = Modifier.tvModalUnderlay(!active).tvFocusNavSignal(focus)
                .then(if (active) Modifier.onGloballyPositioned { laidOut = true } else Modifier)
                .testTag("tv-reviews-page"),
            background = { TvDetailsBackdrop(backdrop, { 1f }, crossfade = false) },
        ) {
            Column(Modifier.fillMaxSize().padding(
                horizontal = TvSubjectDetailsDefaults.HorizontalPadding,
                vertical = TvReviewDefaults.VerticalPadding,
            )) {
                Text(stringResource(Lang.subject_details_review_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    color = TvSubjectDetailsDefaults.Content, modifier = Modifier.testTag("tv-comments-title"))
                Text(details.info.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = TvSubjectDetailsDefaults.SecondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp).testTag("tv-review-subject-title"))
                Spacer(Modifier.height(28.dp))
                BoxWithConstraints(Modifier.weight(1f)) {
                    val gap = if (maxWidth < 650.dp) 24.dp else TvReviewDefaults.ColumnGap
                    val overviewWidth = ((maxWidth - gap) * .35f).coerceAtMost(TvReviewDefaults.OverviewWidth)
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Column(Modifier.width(overviewWidth).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                            TvReviewOverview(details.info.ratingInfo, Modifier.fillMaxWidth().weight(1f))
                            Spacer(Modifier.height(16.dp))
                            TvDetailsRatingAction(
                                details.selfRating.score, onRating,
                                modifier = anchor("review-rating").fillMaxWidth()
                                    .tvFocusHotkey(focus, Key.DirectionRight) { scope.launch { restore(lastReview, previousKeys) } }
                                    .tvFocusHotkey(focus, Key.DirectionLeft) {}
                                    .tvFocusHotkey(focus, Key.DirectionUp) {}
                                    .tvFocusHotkey(focus, Key.DirectionDown) {},
                                compact = false, boundsModifier = ratingBoundsModifier.fillMaxWidth(),
                                available = details.collectionType != UnifiedCollectionType.NOT_COLLECTED,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            val count = details.commentCount ?: comments.itemCount.takeIf {
                                comments.loadState.refresh is LoadState.NotLoading && comments.loadState.append.endOfPaginationReached
                            }
                            val countText = count?.let(::formatCount)
                                ?: comments.itemCount.takeIf { it > 0 }?.let { "${formatCount(it)}+" } ?: "—"
                            Text(stringResource(Lang.subject_details_reviews_count, countText),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                color = TvSubjectDetailsDefaults.SecondaryContent,
                                modifier = Modifier.padding(bottom = 16.dp).testTag("tv-review-count"))
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                LazyColumn(
                                    state = list,
                                    contentPadding = PaddingValues(2.dp),
                                    verticalArrangement = Arrangement.spacedBy(TvReviewDefaults.CardGap),
                                    modifier = Modifier.fillMaxSize().padding(end = 14.dp).tvReviewEdges(list)
                                        .tvFocusHotkey(focus, Key.DirectionLeft to TvDetailsKey("review-rating"))
                                        .tvFocusHotkey(focus, Key.DirectionRight) {}
                                        .tvFocusHotkey(focus, Key.DirectionUp) { move(-1) }
                                        .tvFocusHotkey(focus, Key.DirectionDown) { move(1) }
                                        .testTag("tv-review-list"),
                                ) {
                                    items(comments.itemCount, key = { "review:${comments.peek(it)?.stableId ?: "placeholder:$it"}" }) { index ->
                                        comments[index]?.let { raw ->
                                            val comment = details.commentPresentation(raw)
                                            TvReviewCard(comment, anchor("review:${comment.stableId}")) { onComment(comment) }
                                        }
                                    }
                                    if (reviewKeys.isEmpty() || error != null) {
                                        val key = if (reviewKeys.isEmpty()) "review-status" else "review-retry"
                                        item(key) {
                                            if (error != null) {
                                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                    Text(renderLoadErrorMessage(LoadError.fromException(error.error)),
                                                        color = TvSubjectDetailsDefaults.SecondaryContent)
                                                    TvDetailsAction(stringResource(Lang.settings_mediasource_retry), Icons.Rounded.Refresh,
                                                        comments::retry, anchor(key))
                                                }
                                            } else if (comments.loadState.refresh is LoadState.Loading) {
                                                TvReviewLoading(anchor(key).focusable())
                                            } else {
                                                Text(stringResource(Lang.comment_empty_title),
                                                    modifier = anchor(key).fillMaxWidth().padding(vertical = 32.dp).focusable(),
                                                    color = TvSubjectDetailsDefaults.SecondaryContent)
                                            }
                                        }
                                    } else if (comments.loadState.append is LoadState.Loading) {
                                        item("review-loading") { Text(stringResource(Lang.foundation_loading), color = TvSubjectDetailsDefaults.SecondaryContent) }
                                    }
                                }
                                TvReviewScrollbar(list, Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
    }
}
