/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject

import androidx.compose.runtime.mutableStateOf
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UICommentVote
import me.him188.ani.app.ui.comment.UIRichText
import me.him188.ani.app.ui.framework.AniComposeUiTest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

/** Exercise each screen's refresh Intent wiring with real Paging generations and shared vote overlays. */
internal fun AniComposeUiTest.assertCommentRefreshCleanup(mount: (CommentState, () -> Unit) -> Unit) {
    fun comment(id: Long, likes: Int, vote: UICommentVote? = null) = UIComment(
        id, "ani:$id", null, UIRichText(emptyList()), 0, emptyList(), emptyList(), 0, null,
        source = UICommentSource.ANI, likeCount = likes, selfVote = vote,
    )
    val initial = listOf(comment(1, 12), comment(2, 7))
    val refreshed = listOf(comment(1, 40, UICommentVote.LIKE), comment(2, 20))
    val source = AtomicReference<PagingSource<Int, UIComment>>()
    val generation = AtomicInteger()
    val refreshStarted = CompletableDeferred<Unit>()
    val refreshResult = CompletableDeferred<Unit>()
    val firstVoteSettled = CompletableDeferred<Unit>()
    val secondVoteStarted = CompletableDeferred<Unit>()
    val secondVoteResult = CompletableDeferred<Unit>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    try {
        val pager = Pager(PagingConfig(pageSize = 2, enablePlaceholders = false)) {
            val isRefresh = generation.getAndIncrement() > 0
            object : PagingSource<Int, UIComment>() {
                override fun getRefreshKey(state: PagingState<Int, UIComment>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UIComment> {
                    if (isRefresh) {
                        refreshStarted.complete(Unit)
                        refreshResult.await()
                    }
                    return LoadResult.Page(if (isRefresh) refreshed else initial, null, null)
                }
            }.also { source.set(it) }
        }.flow.cachedIn(scope)
        val shared = CommentState(pager, mutableStateOf(2), { _, _, _ -> }, scope,
            onSubmitCommentVote = { comment, _ ->
                if (comment.id == 1L) firstVoteSettled.complete(Unit) else {
                    secondVoteStarted.complete(Unit)
                    secondVoteResult.await()
                }
            })
        var refreshes = 0
        mount(shared) { refreshes++; shared.clearStaleOverlays() }
        waitUntil { refreshes == 1 }
        runOnIdle {
            shared.toggleVote(initial[0], UICommentVote.LIKE)
            shared.toggleVote(initial[1], UICommentVote.LIKE)
        }
        waitUntil { firstVoteSettled.isCompleted && secondVoteStarted.isCompleted }
        runOnIdle {
            assertEquals(13, shared.withOverlay(initial[0]).likeCount)
            assertEquals(8, shared.withOverlay(initial[1]).likeCount)
            source.get().invalidate()
        }
        waitUntil { refreshStarted.isCompleted }
        waitForIdle()
        runOnIdle { refreshResult.complete(Unit) }
        waitUntil { refreshes == 2 }
        runOnIdle {
            assertEquals(40, shared.withOverlay(refreshed[0]).likeCount)
            assertEquals(UICommentVote.LIKE, shared.withOverlay(refreshed[0]).selfVote)
            assertEquals(8, shared.withOverlay(refreshed[1]).likeCount)
            assertEquals(UICommentVote.LIKE, shared.withOverlay(refreshed[1]).selfVote)
        }
    } finally {
        scope.cancel()
    }
}
