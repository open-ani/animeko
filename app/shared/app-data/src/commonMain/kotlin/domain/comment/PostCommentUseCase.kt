/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.comment

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

interface PostCommentUseCase : UseCase {
    suspend operator fun invoke(context: CommentContext, content: String): CommentSendResult
}

class PostCommentUseCaseImpl(
    private val turnstileState: TurnstileState,
    private val commentService: BangumiCommentService,
    private val turnstileContext: CoroutineContext = Dispatchers.Main,
) : PostCommentUseCase {
    private val logger = logger<PostCommentUseCase>()

    override suspend operator fun invoke(context: CommentContext, content: String): CommentSendResult {
        val turnstileResult = try {
            withContext(turnstileContext) {
                turnstileState.reload()
            }
            merge(
                turnstileState.tokenFlow.map { TurnstileResult.Ok(it) },
                turnstileState.webErrorFlow.map { TurnstileResult.Error(it) }
            ).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CommentSendResult.UnknownError(e.toString())
        }
        
        val token = when (turnstileResult) {
            is TurnstileResult.Error -> {
                return when (turnstileResult.cause) {
                    is TurnstileState.Error.Network -> 
                        CommentSendResult.TurnstileError.Network(turnstileResult.cause.code)
                    else -> CommentSendResult.TurnstileError.Unknown(turnstileResult.cause.code)
                }
            }
            is TurnstileResult.Ok -> turnstileResult.token
        }

        try {
            commentService.postEpisodeComment(context, content, token)
            return CommentSendResult.Ok
        } catch (e: Exception) {
            val delegateEx = RepositoryException.wrapOrThrowCancellation(e)
            
            logger.error(delegateEx) { "Failed to post comment, see exception" }
            return if (delegateEx is RepositoryUnknownException) {
                CommentSendResult.UnknownError(e.toString())
            } else {
                CommentSendResult.NetworkError
            }
        }
    }
}

private sealed class TurnstileResult {
    class Ok(val token: String) : TurnstileResult()
    class Error(val cause: TurnstileState.Error) : TurnstileResult()
}

@Immutable
sealed interface CommentSendResult {
    sealed class Error : CommentSendResult

    sealed class TurnstileError : Error() {
        @Immutable
        data class Network(val code: Int) : TurnstileError()

        @Immutable
        data class Unknown(val code: Int) : TurnstileError()
    }

    data object NetworkError : Error()
    
    class UnknownError(val message: String) : Error()

    data object Ok : CommentSendResult
}

private suspend fun BangumiCommentService.postEpisodeComment(
    context: CommentContext,
    content: String,
    turnstileToken: String
) {
    when (context) {
        is CommentContext.Episode ->
            postEpisodeComment(context.episodeId, content, turnstileToken, null)

        is CommentContext.EpisodeReply ->
            postEpisodeComment(context.episodeId, content, turnstileToken, context.commentId)

        is CommentContext.SubjectReview -> error("unreachable on postEpisodeComment")
    }
}