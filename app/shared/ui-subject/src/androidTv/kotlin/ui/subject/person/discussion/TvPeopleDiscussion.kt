/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person.discussion

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UICommentVote
import me.him188.ani.app.ui.comment.CommentReportReason
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_empty_title
import me.him188.ani.app.ui.lang.comment_review_hidden
import me.him188.ani.app.ui.lang.foundation_anonymous
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.people_bangumi_unavailable
import me.him188.ani.app.ui.lang.people_discussion
import me.him188.ani.app.ui.lang.person_details_comments_count
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.layout.TvModalOverlay
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionModal
import me.him188.ani.leanback.ui.subject.components.TvDetailsReadingArea
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.components.detailsRevealMasks
import me.him188.ani.leanback.ui.subject.components.detailsRedactMasks
import me.him188.ani.leanback.ui.subject.details.TvDetailsAction
import me.him188.ani.leanback.ui.subject.details.formatCount
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey
import me.him188.ani.leanback.ui.subject.presentation.detailsFocusFallback
import me.him188.ani.leanback.ui.subject.reviews.TvReviewBringIntoViewSpec
import me.him188.ani.leanback.ui.subject.reviews.TvReviewCard
import me.him188.ani.leanback.ui.subject.reviews.TvReviewLoading
import me.him188.ani.leanback.ui.subject.reviews.TvReviewScrollbar
import me.him188.ani.leanback.ui.subject.reviews.tvReviewEdges
import org.jetbrains.compose.resources.stringResource

internal fun peopleDiscussionCount(items: LazyPagingItems<UIComment>, incomplete: Boolean = false): String =
    peopleDiscussionCount(items.itemCount, items.loadState.refresh is LoadState.NotLoading,
        items.loadState.append.endOfPaginationReached && items.loadState.append is LoadState.NotLoading, incomplete)

internal fun peopleDiscussionCount(loaded: Int, refreshed: Boolean, ended: Boolean, incomplete: Boolean): String = when {
    refreshed && ended && !incomplete -> formatCount(loaded)
    loaded > 0 -> "${formatCount(loaded)}+"
    else -> "—"
}

/** One window-local modal surface for both the paged list and the selected comment. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TvPeopleDiscussion(
    state: TvPeopleDiscussionState,
    comments: LazyPagingItems<UIComment>,
    commentPresentation: (UIComment) -> UIComment,
    onAction: (TvPeopleDiscussionAction) -> Unit,
    onVote: (UIComment, UICommentVote) -> Unit,
    onReport: (UIComment, CommentReportReason) -> Unit,
    onOpenOriginal: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    var laidOut by remember { mutableStateOf(false) }
    val page = state.page
    val generation = state.generation
    val currentState by rememberUpdatedState(state)
    var expectedFocus by remember { mutableStateOf<String?>(null) }
    var entryPending by remember { mutableStateOf(true) }
    var entryNavigation by remember { mutableStateOf(focus.userNavGeneration) }
    var previousKeys by remember { mutableStateOf(emptyList<String>()) }
    val error = (comments.loadState.refresh as? LoadState.Error) ?: (comments.loadState.append as? LoadState.Error)
    val itemKeys = comments.itemSnapshotList.items.map { "comment:${it.stableId}" }
    val keys = itemKeys + when {
        itemKeys.isEmpty() -> listOf("status")
        error != null -> listOf("retry")
        else -> emptyList()
    }
    val currentKeys by rememberUpdatedState(keys)
    val readerScroll = rememberSaveable(state.commentId, saver = ScrollState.Saver) { ScrollState(0) }
    var revealed by rememberSaveable(state.commentId) { mutableStateOf(false) }
    val selected = comments.itemSnapshotList.items.find { it.stableId == state.commentId }?.let(commentPresentation)
    val hidden = stringResource(Lang.comment_review_hidden)
    val elements = selected?.content?.elements.orEmpty().let {
        if (revealed) it.detailsRevealMasks() else it.detailsRedactMasks(hidden)
    }

    suspend fun restore(target: String?, previous: List<String> = emptyList(), animate: Boolean = false) {
        val expectedGeneration = currentState.generation
        focus.requestPrepared(isRelevant = { currentState.generation == expectedGeneration }) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { laidOut && window.isWindowFocused }.first { it }
            val key = if (currentState.page == TvPeopleDiscussionPage.List)
                detailsFocusFallback(target, previous, currentKeys, "status") else target ?: "body"
            expectedFocus = key
            if (currentState.page == TvPeopleDiscussionPage.List) {
                val index = currentKeys.indexOf(key)
                if (index >= 0 && list.layoutInfo.visibleItemsInfo.none { it.key == key }) list.scrollToItem(index)
                if (animate && index >= 0) {
                    val distance = snapshotFlow {
                        val layout = list.layoutInfo
                        layout.visibleItemsInfo.firstOrNull { it.key == key && it.index == currentKeys.indexOf(key) }?.let {
                            it.offset + it.size / 2f - (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                        }
                    }.first { it != null }
                    focus.request(TvDetailsKey(key))
                    list.animateScrollBy(checkNotNull(distance))
                    return@requestPrepared null
                }
            }
            TvDetailsKey(key)
        }
    }
    LaunchedEffect(generation) {
        entryPending = true
        entryNavigation = focus.userNavGeneration
        restore(when (page) {
            TvPeopleDiscussionPage.List -> state.commentFocus
            TvPeopleDiscussionPage.Report -> "reason:SPAM"
            else -> "body"
        }, previousKeys)
    }
    val beforeUpdate = state.commentFocus
    LaunchedEffect(keys) {
        if (page == TvPeopleDiscussionPage.List && beforeUpdate != null && beforeUpdate !in keys) restore(beforeUpdate, previousKeys)
        // Preserve the removed comment's old position while its full text is open.
        if (page == TvPeopleDiscussionPage.List || beforeUpdate == null || beforeUpdate in keys) previousKeys = keys
    }
    fun anchor(key: String) = Modifier.tvFocusAnchor(focus, TvDetailsKey(key)).onFocusChanged {
        if (it.isFocused && page == TvPeopleDiscussionPage.List) {
            if (entryPending && focus.userNavGeneration == entryNavigation && key != expectedFocus) return@onFocusChanged
            entryPending = false
            onAction(TvPeopleDiscussionAction.FocusComment(key))
        }
    }.testTag("tv-people-discussion-$key")
    fun move(delta: Int) {
        val index = keys.indexOf(currentState.commentFocus)
        keys.getOrNull(index + delta)?.let { scope.launch { restore(it, animate = true) } }
    }
    val discussionTitle = stringResource(Lang.people_discussion)
    val author = selected?.author?.nickname?.takeIf { it.isNotBlank() } ?: stringResource(Lang.foundation_anonymous)
    val subtitle = if (page == TvPeopleDiscussionPage.List)
        state.name + " · " + stringResource(Lang.person_details_comments_count, peopleDiscussionCount(comments, state.bangumiUnavailable))
    else selected?.let { (if (it.source == UICommentSource.BANGUMI) "Bangumi" else "Animeko") + " · " + formatDateTime(it.createdAt) }

    TvModalOverlay(onClose = { onAction(TvPeopleDiscussionAction.Close) }, background = {},
        modifier = Modifier.tvFocusNavSignal(focus).onGloballyPositioned { laidOut = true }.testTag("tv-people-discussion")) {
        TvOptionModal(if (page == TvPeopleDiscussionPage.List) discussionTitle else author,
            modifier = Modifier.testTag("tv-people-discussion-surface"), subtitle = subtitle,
            footer = if (page == TvPeopleDiscussionPage.Comment && selected != null) ({
                TvPeopleCommentActions(selected, elements, revealed, { revealed = !revealed },
                    actionModifier = { key ->
                        Modifier.tvFocusAnchor(focus, TvDetailsKey("action:$key"))
                            .tvFocusLink(focus, up = TvDetailsKey("body"))
                    },
                    onVote = { onVote(selected, it) }, onOpenOriginal = onOpenOriginal, onOpenUrl = onOpenUrl,
                    onReport = { onAction(TvPeopleDiscussionAction.ShowReport) },
                    onImage = { onAction(TvPeopleDiscussionAction.ShowImage(it)) })
            }) else null,
        ) {
            when (page) {
                TvPeopleDiscussionPage.List -> {
                    if (state.bangumiUnavailable) Text(stringResource(Lang.people_bangumi_unavailable), color = TvSubjectDetailsDefaults.SecondaryContent)
                    CompositionLocalProvider(LocalBringIntoViewSpec provides TvReviewBringIntoViewSpec) {
                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(state = list, contentPadding = PaddingValues(2.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize().padding(end = 14.dp).tvReviewEdges(list)
                                    .tvFocusHotkey(focus, Key.DirectionUp) { move(-1) }
                                    .tvFocusHotkey(focus, Key.DirectionDown) { move(1) }
                                    .testTag("tv-people-discussion-list")) {
                                items(comments.itemCount, key = { "comment:${comments.peek(it)?.stableId ?: "placeholder:$it"}" }) { index ->
                                    comments[index]?.let { raw ->
                                        TvReviewCard(commentPresentation(raw), anchor("comment:${raw.stableId}"), showRating = false) {
                                            onAction(TvPeopleDiscussionAction.OpenComment(raw.stableId))
                                        }
                                    }
                                }
                                if (itemKeys.isEmpty() || error != null) item(keys.last()) {
                                    when {
                                        error != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(renderLoadErrorMessage(LoadError.fromException(error.error)), color = TvSubjectDetailsDefaults.SecondaryContent)
                                            TvDetailsAction(stringResource(Lang.settings_mediasource_retry), Icons.Rounded.Refresh, comments::retry, anchor(keys.last()))
                                        }
                                        comments.loadState.refresh is LoadState.Loading -> TvReviewLoading(anchor("status").focusable())
                                        else -> Text(stringResource(Lang.comment_empty_title), anchor("status").padding(vertical = 24.dp).focusable(),
                                            color = TvSubjectDetailsDefaults.SecondaryContent)
                                    }
                                } else if (comments.loadState.append is LoadState.Loading) item("loading") {
                                    Text(stringResource(Lang.foundation_loading), color = TvSubjectDetailsDefaults.SecondaryContent)
                                }
                            }
                            TvReviewScrollbar(list, Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight())
                        }
                    }
                }
                TvPeopleDiscussionPage.Comment -> {
                    TvDetailsReadingArea(anchor("body").fillMaxSize(), readerScroll) {
                        if (selected == null) Text(stringResource(Lang.comment_empty_title), color = TvSubjectDetailsDefaults.SecondaryContent)
                        else RichText(elements, interactionEnabled = false, color = TvSubjectDetailsDefaults.Content)
                    }
                }
                TvPeopleDiscussionPage.Report -> TvPeopleReport(selected?.stableId, state.reportBusy,
                    onSubmit = { reason -> selected?.let { onReport(it, reason) } }, anchor = ::anchor)
                TvPeopleDiscussionPage.Image -> Box(anchor("body").fillMaxSize().focusable()) {
                    AsyncImage(state.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}
