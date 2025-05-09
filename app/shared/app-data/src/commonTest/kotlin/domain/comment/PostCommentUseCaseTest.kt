/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.comment

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.subject.SubjectComment
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.datasources.api.paging.Paged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostCommentUseCaseTest {
    @Test
    fun `send succeed`() = runTest {
        val commentService = createCommentService { }        
        val turnstileState = createTurnstileState()

        val sender = PostCommentUseCaseImpl(
            turnstileState,
            commentService,
            turnstileContext = coroutineContext,
        )

        val result = sender(commentContext, COMMENT_CONTENT)
        assertIs<CommentSendResult.Ok>(result, "Should send succeeded.")
    }
    
    @Test
    fun `turnstile token - web view network error`() = runTest {
        val commentService = createCommentService { }        
        val turnstileState = createTurnstileState(
            tokenFlow = { },
            errorFlow = { emit(TurnstileState.Error.Network(TURNSTILE_ERROR_CODE)) },
        )

        val sender = PostCommentUseCaseImpl(
            turnstileState,
            commentService,
            turnstileContext = coroutineContext,
        )

        val result = sender(commentContext, COMMENT_CONTENT)
        assertIs<CommentSendResult.TurnstileError.Network>(result, "Should cause turnstile web view network error.")
        assertEquals(TURNSTILE_ERROR_CODE, result.code)
    }

    @Test
    fun `turnstile token - web view unknown error`() = runTest {
        val commentService = createCommentService { }
        val turnstileState = createTurnstileState(
            tokenFlow = { },
            errorFlow = { emit(TurnstileState.Error.Unknown(TURNSTILE_ERROR_CODE)) },
        )

        val sender = PostCommentUseCaseImpl(
            turnstileState,
            commentService,
            turnstileContext = coroutineContext,
        )

        val result = sender(commentContext, COMMENT_CONTENT)
        assertIs<CommentSendResult.TurnstileError.Unknown>(result, "Should cause turnstile web view unknown error.")
                assertEquals(TURNSTILE_ERROR_CODE, result.code)
    }
    
    @Test
    fun `turnstile token - unknown error`() = runTest {
        val commentService = createCommentService { }
        val turnstileState = createTurnstileState(
            errorFlow = { throw IllegalStateException() },
        )

        val sender = PostCommentUseCaseImpl(
            turnstileState,
            commentService,
            turnstileContext = coroutineContext,
        )

        val result = sender(commentContext, COMMENT_CONTENT)
        assertIs<CommentSendResult.UnknownError>(result, "Exception in turnstile token should cause unknown error.")
            }
    
    @Test
    fun `comment service - network error`() = runTest {
        val commentService = createCommentService { throw IOException() }
        val turnstileState = createTurnstileState()

        val sender = PostCommentUseCaseImpl(
            turnstileState,
            commentService,
            turnstileContext = coroutineContext,
        )

        val result = sender(commentContext, COMMENT_CONTENT)
        assertIs<CommentSendResult.NetworkError>(result, "Should cause network error.")
    }
    
    @Test
    fun `comment service - unknown error`() = runTest {
        val commentService = createCommentService { throw IllegalStateException() }
        val turnstileState = createTurnstileState()

        val sender = PostCommentUseCaseImpl(
            turnstileState,
            commentService,
            turnstileContext = coroutineContext,
        )

        val result = sender(commentContext, COMMENT_CONTENT)
        assertIs<CommentSendResult.UnknownError>(result, "Should cause unknown error.")
    }
    
    
    private fun createCommentService(
        onPostEpisodeComment: () -> Unit = { }
    ): BangumiCommentService {
        return object : BangumiCommentService {
            override suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int): Paged<SubjectComment>? {
                error("unreachable test")
            }

            override suspend fun getSubjectEpisodeComments(episodeId: Int): List<EpisodeComment>? {
                error("unreachable test")
            }

            override suspend fun postEpisodeComment(
                episodeId: Int,
                content: String,
                cfTurnstileResponse: String,
                replyToCommentId: Int?
            ) {
                onPostEpisodeComment()
            }
        }
    }
    
    private fun createTurnstileState(
        tokenFlow: suspend FlowCollector<String>.() -> Unit = { emit(TURNSTILE_TOKEN) },
        errorFlow: suspend FlowCollector<TurnstileState.Error>.() -> Unit = { },
    ): TurnstileState {
        val deferred = CompletableDeferred<Unit>()
        
        return object : TurnstileState {
            override val url: String = ""
            override val tokenFlow: Flow<String> = flow { 
                deferred.await()
                tokenFlow()
            }
            override val webErrorFlow: Flow<TurnstileState.Error> = flow {
                deferred.await()
                errorFlow()
            }

            override fun reload() {
                deferred.complete(Unit)
            }

            override fun cancel() { }

        }
    }
    
    private companion object {
        private const val TURNSTILE_TOKEN = "test-cf-turnstile-token-114514"
        private val commentContext = CommentContext.Episode(1, 2)
        private const val COMMENT_CONTENT = "小祈好可爱我要死了(bgm38)"
        private const val TURNSTILE_ERROR_CODE = 114514
    }
}