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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.app.data.network.AniPersonCommentService
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
    private val commentService: AniEpisodeCommentService,
    private val personCommentService: AniPersonCommentService,
    private val context: CoroutineContext = Dispatchers.Main,
) : PostCommentUseCase {
    private val logger = logger<PostCommentUseCase>()

    override suspend operator fun invoke(context: CommentContext, content: String): CommentSendResult {
        try {
            withContext(this.context) {
                when (context) {
                    is CommentContext.Episode, is CommentContext.EpisodeReply ->
                        commentService.postEpisodeComment(context, content)

                    is CommentContext.PersonComment ->
                        personCommentService.createComment(context.target, content)

                    is CommentContext.PersonCommentReply ->
                        personCommentService.createReply(context.target, context.commentId, content)

                    is CommentContext.SubjectReview -> error("SubjectReview is not posted through PostCommentUseCase")
                }
            }
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

@Immutable
sealed interface CommentSendResult {
    sealed class Error : CommentSendResult

    data object NetworkError : Error()
    
    class UnknownError(val message: String) : Error()

    data object Ok : CommentSendResult
}

private suspend fun AniEpisodeCommentService.postEpisodeComment(
    context: CommentContext,
    content: String,
) {
    when (context) {
        is CommentContext.Episode ->
            createEpisodeComment(context.episodeId, content)

        is CommentContext.EpisodeReply ->
            createEpisodeReply(context.episodeId, context.commentId, content)

        else -> error("unreachable on postEpisodeComment: $context")
    }
}
