/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.comment

import androidx.compose.runtime.Immutable
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.app.data.network.AniPersonCommentService
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.utils.coroutines.runCatchingCancellable
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

interface PostCommentUseCase : UseCase {
    suspend operator fun invoke(context: CommentContext, content: String): CommentSendResult

    companion object {
        /**
         * 一条评论最多引用的图片数, 与服务器的限制一致. 超出时服务器返回 [CommentSendResult.TooManyImages].
         */
        const val MAX_IMAGES: Int = 10
    }
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
            return delegateEx.toCommentImageError()
                ?: if (delegateEx is RepositoryUnknownException) {
                    CommentSendResult.UnknownError(e.toString())
                } else {
                    CommentSendResult.NetworkError
                }
        }
    }

    /**
     * 服务器拒绝评论引用的图片时返回 422, 纯文本响应正文中带有错误码.
     *
     * 协程的异常栈恢复 (调试模式下启用) 跨过 `withContext` 时会把异常复制一份并以原异常为 cause,
     * 因此沿 cause 链查找 [ClientRequestException], 而不是只看直接的 cause.
     */
    private suspend fun RepositoryException.toCommentImageError(): CommentSendResult.Error? {
        val response = generateSequence<Throwable>(this) { it.cause }
            .firstNotNullOfOrNull { it as? ClientRequestException }
            ?.response
            ?: return null
        if (response.status != HttpStatusCode.UnprocessableEntity) return null
        val body = runCatchingCancellable { response.bodyAsText() }.getOrDefault("")
        return when {
            ERROR_IMAGE_TOO_MANY in body -> CommentSendResult.TooManyImages
            ERROR_IMAGE_INVALID in body -> CommentSendResult.InvalidImage
            ERROR_IMAGE_PROCESSING in body -> CommentSendResult.ImageProcessing
            else -> null
        }
    }

    private companion object {
        const val ERROR_IMAGE_INVALID = "COMMENT_IMAGE_INVALID"
        const val ERROR_IMAGE_TOO_MANY = "COMMENT_IMAGE_TOO_MANY"
        const val ERROR_IMAGE_PROCESSING = "COMMENT_IMAGE_PROCESSING"
    }
}

@Immutable
sealed interface CommentSendResult {
    sealed class Error : CommentSendResult

    data object NetworkError : Error()

    /**
     * 评论引用的图片不存在, 已被删除, 或不是当前账号上传的. 上传后一直没有发布的图片会被服务器删除.
     *
     * 重新上传同一张图片会得到相同的地址, 草稿中的图片随之恢复, 无需修改评论内容.
     */
    data object InvalidImage : Error()

    /**
     * 评论引用的图片超过服务器允许的数量 ([PostCommentUseCase.MAX_IMAGES]).
     */
    data object TooManyImages : Error()

    /**
     * 评论引用的图片还在服务器上转换. 转换完成后再发送即可.
     */
    data object ImageProcessing : Error()
    
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
