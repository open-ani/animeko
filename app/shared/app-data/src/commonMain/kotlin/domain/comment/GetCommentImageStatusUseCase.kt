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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.client.apis.CommentsAniApi
import me.him188.ani.client.models.AniCommentImageFailureCode
import me.him188.ani.client.models.AniCommentImageInfo
import me.him188.ani.client.models.AniCommentImageStatus
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext

/**
 * 查询上传后正在服务器上转换的评论图片 ([CommentImageUploadResult.Processing]) 的状态.
 */
interface GetCommentImageStatusUseCase : UseCase {
    /**
     * @param ids 上传时得到的图片标识 [CommentImageUploadResult.Processing.id].
     * @return 每个标识的状态. 请求失败时为 [CommentImageStatusResult.Unavailable], 调用方稍后再查询即可.
     */
    suspend operator fun invoke(ids: List<String>): CommentImageStatusResult
}

/**
 * 标识较多时分成几次请求.
 */
class GetCommentImageStatusUseCaseImpl(
    private val commentsApi: ApiInvoker<CommentsAniApi>,
    private val context: CoroutineContext = Dispatchers.Default,
) : GetCommentImageStatusUseCase {
    private val logger = logger<GetCommentImageStatusUseCase>()

    override suspend fun invoke(ids: List<String>): CommentImageStatusResult {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return CommentImageStatusResult.Ok(emptyMap())
        return try {
            val statuses = withContext(context) {
                buildMap {
                    for (chunk in distinct.chunked(MAX_IDS_PER_REQUEST)) {
                        val response = commentsApi.invoke { getCommentImages(chunk).body() }
                        for (image in response.images) put(image.id, image.toProcessingStatus())
                    }
                }
            }
            CommentImageStatusResult.Ok(statuses)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 调用方会继续查询, 失败只记为警告
            logger.warn(e) { "Failed to get comment image status" }
            CommentImageStatusResult.Unavailable
        }
    }

    private companion object {
        /**
         * 服务器一次最多查询的图片数, 与一条评论最多引用的图片数相同.
         */
        const val MAX_IDS_PER_REQUEST = PostCommentUseCase.MAX_IMAGES
    }
}

/**
 * [GetCommentImageStatusUseCase] 的结果.
 */
@Immutable
sealed interface CommentImageStatusResult {
    /**
     * @property statuses 图片标识到状态. 服务器没有返回的标识不在其中.
     */
    data class Ok(val statuses: Map<String, CommentImageProcessingStatus>) : CommentImageStatusResult

    /**
     * 网络错误或服务器暂时不可用, 没有得到状态. 图片的状态没有变化, 稍后再查询即可.
     */
    data object Unavailable : CommentImageStatusResult
}

/**
 * 一张上传的评论图片在服务器上的状态.
 */
@Immutable
sealed interface CommentImageProcessingStatus {
    /**
     * 正在转换.
     */
    data object Processing : CommentImageProcessingStatus

    /**
     * 已经转换好, 可以在评论中引用.
     *
     * @property url 图片的地址, 用于 `[img]<url>[/img]`.
     */
    data class Ready(val url: String) : CommentImageProcessingStatus

    /**
     * 转换失败, 或服务器上没有这张图片. [error] 可以重试 ([CommentImageUploadResult.Error.isRetryable]) 时, 重新上传同一张图片.
     */
    data class Failed(val error: CommentImageUploadResult.Error) : CommentImageProcessingStatus
}

private fun AniCommentImageInfo.toProcessingStatus(): CommentImageProcessingStatus = when (status) {
    AniCommentImageStatus.PROCESSING -> CommentImageProcessingStatus.Processing
    AniCommentImageStatus.READY -> CommentImageProcessingStatus.Ready(url)
    AniCommentImageStatus.FAILED -> CommentImageProcessingStatus.Failed(failureCode.toCommentImageError())
}

/**
 * 服务器给出的转换失败原因. 图片本身无法使用时不能重试; 其他原因 (包括服务器上没有这张图片) 重新上传即可.
 */
internal fun AniCommentImageFailureCode?.toCommentImageError(): CommentImageUploadResult.Error = when (this) {
    AniCommentImageFailureCode.UNSUPPORTED_IMAGE,
    AniCommentImageFailureCode.TOO_LARGE -> CommentImageUploadResult.InvalidFormat

    AniCommentImageFailureCode.INTERNAL,
    AniCommentImageFailureCode.NOT_FOUND,
    null -> CommentImageUploadResult.ProcessingFailed
}
