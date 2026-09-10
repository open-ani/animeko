/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.comments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_empty_title
import me.him188.ani.app.ui.lang.comment_load_failed
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.leanback.ui.episode.components.TvPlayerPanelList
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

/**
 * Sidebar list content. The host supplies independent scroll state and owns navigation.
 */
@Composable
internal fun TvPlayerComments(
    comments: LazyPagingItems<EpisodeComment>?,
    entryAnchorModifier: Modifier,
    onClickComment: (EpisodeComment, Int) -> Unit,
    commentAnchor: (EpisodeComment) -> Modifier,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val listModifier = Modifier.fillMaxWidth()

    fun anchorFor(index: Int) = if (index == 0) entryAnchorModifier else Modifier

    Column(modifier) {
        val refresh = comments?.loadState?.refresh
        if (comments == null || comments.itemCount == 0 && refresh is LoadState.Loading) {
            TvPlayerPanelList(
                listModifier.testTag("tv-comments-loading").progressSemantics(), Modifier,
                empty = false, state = listState,
            ) {
                items(3) { index ->
                    TvCommentCardPlaceholder(Modifier.testTag("tv-comment-placeholder-$index"))
                }
            }
            return@Column
        }
        if (refresh is LoadState.Error) {
            TvCommentLoadError(refresh, entryAnchorModifier.testTag("tv-comments-retry"), comments::retry)
            if (comments.itemCount == 0) return@Column
        }
        TvPlayerPanelList(
            listModifier, Modifier,
            empty = comments.itemCount == 0,
            emptyText = stringResource(Lang.comment_empty_title),
            state = listState,
        ) {
            items(comments.itemCount, key = { comments.peek(it)?.stableId ?: "placeholder-$it" }) { index ->
                val comment = comments[index]
                if (comment == null) {
                    TvCommentCardPlaceholder(Modifier.testTag("tv-comment-placeholder-$index").progressSemantics())
                } else {
                    TvCommentCard(comment, modifier = (if (refresh is LoadState.Error) Modifier else anchorFor(index)).then(commentAnchor(comment))) {
                        onClickComment(comment, index)
                    }
                }
            }
            when (val append = comments.loadState.append) {
                is LoadState.Loading -> item {
                    TvCommentCardPlaceholder(Modifier.testTag("tv-comments-append-loading").progressSemantics())
                }
                is LoadState.Error -> item {
                    TvCommentLoadError(append, Modifier.testTag("tv-comments-append-retry"), comments::retry)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun TvCommentLoadError(error: LoadState.Error, modifier: Modifier, onRetry: () -> Unit) {
    TvOptionRow(
        stringResource(Lang.comment_load_failed), value = stringResource(Lang.settings_mediasource_retry), modifier = modifier,
        supportingText = renderLoadErrorMessage(LoadError.fromException(error.error)),
        onClick = onRetry,
    )
}
