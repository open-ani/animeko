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
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.client.apis.CommentsAniApi
import me.him188.ani.client.models.AniCommentImageInfo
import me.him188.ani.client.models.AniCommentImageStatus
import me.him188.ani.utils.coroutines.runCatchingCancellable
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext

/**
 * 上传评论里插入的图片到 Ani 服务器.
 *
 * 服务器校验图片的格式与大小后, 在后台把图片转为 WebP 保存. 评论正文只能以 `[img]<url>[/img]` 引用转换好的图片:
 * 结果为 [CommentImageUploadResult.Ready] 时得到的地址可以直接写进 BBCode; 为 [CommentImageUploadResult.Processing] 时
 * 用 [GetCommentImageStatusUseCase] 查询, 转换完成后才能引用.
 */
interface UploadCommentImageUseCase : UseCase {
    /**
     * @param image 图片文件的原始内容. 调用方应先检查大小不超过 [MAX_IMAGE_SIZE_BYTES], 超出时不必读取文件;
     * 不是 [CommentImageFormat] 中的格式时应先转码.
     */
    suspend operator fun invoke(image: ByteArray): CommentImageUploadResult

    companion object {
        /**
         * 评论图片的最大字节数 (10MB), 客户端预检与服务器限制一致.
         */
        const val MAX_IMAGE_SIZE_BYTES: Long = 10L * 1024 * 1024

        /**
         * 服务器保存图片时等比缩小到短边不超过 [MAX_SHORT_SIDE], 长边不超过 [MAX_LONG_SIDE], 不放大.
         * 客户端自行转码时按同样的规则缩小, 不上传比服务器保存的更大的图.
         */
        const val MAX_SHORT_SIDE: Int = 2048

        /**
         * @see MAX_SHORT_SIDE
         */
        const val MAX_LONG_SIDE: Int = 8192
    }
}

/**
 * 登录状态、大小和文件头在本地先检查一遍, 明显会被拒绝的上传不发请求. 服务器仍会完整校验, 以服务器的结果为准.
 */
class UploadCommentImageUseCaseImpl(
    private val commentsApi: ApiInvoker<CommentsAniApi>,
    private val sessionStateProvider: SessionStateProvider,
    private val context: CoroutineContext = Dispatchers.Default,
) : UploadCommentImageUseCase {
    private val logger = logger<UploadCommentImageUseCase>()

    override suspend fun invoke(image: ByteArray): CommentImageUploadResult {
        if (!hasSession()) {
            return CommentImageUploadResult.NotLoggedIn
        }
        if (image.size > UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES) {
            return CommentImageUploadResult.TooLarge
        }
        if (!CommentImageFormat.isSupported(image)) {
            return CommentImageUploadResult.InvalidFormat
        }

        return try {
            val response = withContext(context) {
                commentsApi.invoke {
                    // 请求体是图片的原始字节. 生成的参数类型 OctetByteArray 会被序列化成 JSON 字符串,
                    // client/build.gradle.kts 的 fixGeneratedOpenApi 把它替换成了 OutgoingContent
                    uploadCommentImage(
                        object : OutgoingContent.ByteArrayContent() {
                            override fun bytes(): ByteArray = image
                        },
                    ).body()
                }
            }
            response.toUploadResult()
        } catch (e: ClientRequestException) {
            val status = e.response.status
            // 服务器的错误响应是纯文本. 同一状态码有不同原因时, 正文中带有错误码
            val body = runCatchingCancellable { e.response.bodyAsText() }.getOrDefault("")
            when {
                status == HttpStatusCode.Unauthorized -> CommentImageUploadResult.NotLoggedIn
                status == HttpStatusCode.Forbidden -> CommentImageUploadResult.Forbidden
                status == HttpStatusCode.PayloadTooLarge -> CommentImageUploadResult.TooLarge
                status == HttpStatusCode.UnprocessableEntity -> CommentImageUploadResult.InvalidFormat
                status == HttpStatusCode.TooManyRequests && ERROR_PENDING_LIMIT in body ->
                    CommentImageUploadResult.PendingLimit

                status == HttpStatusCode.TooManyRequests -> CommentImageUploadResult.RateLimited
                // 服务器正在删除之前上传过但没有被使用的同一张图片, 稍后重试即可
                status == HttpStatusCode.Conflict && ERROR_RETRY in body -> CommentImageUploadResult.NetworkError
                else -> {
                    logger.error(e) { "Failed to upload comment image, see exception" }
                    CommentImageUploadResult.UnknownError(body.ifBlank { status.toString() })
                }
            }
        } catch (e: ServerResponseException) {
            if (e.response.status == HttpStatusCode.ServiceUnavailable) {
                // 服务器暂时不能处理图片 (响应正文为 COMMENT_IMAGE_UNAVAILABLE), 或服务器本身暂时不可用. 都可以稍后重新上传
                logger.warn(e) { "Comment image service is unavailable" }
                CommentImageUploadResult.ProcessingFailed
            } else {
                logger.error(e) { "Failed to upload comment image, see exception" }
                CommentImageUploadResult.NetworkError
            }
        } catch (e: Exception) {
            val delegateEx = RepositoryException.wrapOrThrowCancellation(e)

            logger.error(delegateEx) { "Failed to upload comment image, see exception" }
            if (delegateEx is RepositoryUnknownException) {
                CommentImageUploadResult.UnknownError(e.toString())
            } else {
                CommentImageUploadResult.NetworkError
            }
        }
    }

    /**
     * 只有确定没有保存的登录 (游客, 或已退出登录) 时才不发请求. 有登录但刷新 token 失败时仍然发送, 由服务器判断.
     */
    private suspend fun hasSession(): Boolean {
        return when (val state = sessionStateProvider.stateFlow.first()) {
            is SessionState.Valid -> true
            is SessionState.Invalid -> state.reason != InvalidSessionReason.NO_TOKEN
        }
    }

    private companion object {
        const val ERROR_PENDING_LIMIT = "COMMENT_IMAGE_PENDING_LIMIT"
        const val ERROR_RETRY = "COMMENT_IMAGE_RETRY"
    }
}

/**
 * 客户端以响应中的状态为准, 不看 HTTP 状态码. 上传接口不会返回 [AniCommentImageStatus.FAILED], 万一返回时按失败的原因处理.
 */
private fun AniCommentImageInfo.toUploadResult(): CommentImageUploadResult = when (status) {
    AniCommentImageStatus.READY -> CommentImageUploadResult.Ready(url)
    AniCommentImageStatus.PROCESSING -> CommentImageUploadResult.Processing(id, url)
    AniCommentImageStatus.FAILED -> failureCode.toCommentImageError()
}

/**
 * [UploadCommentImageUseCase] 的结果.
 */
@Immutable
sealed interface CommentImageUploadResult {
    /**
     * 图片已经转换好, 可以在评论中引用.
     *
     * @property url 图片的地址, 用于 `[img]<url>[/img]`.
     */
    data class Ready(val url: String) : CommentImageUploadResult

    /**
     * 服务器已收到图片, 正在后台转换. 用 [GetCommentImageStatusUseCase] 查询 [id] 的状态, 转换完成后才能在评论中引用.
     *
     * @property id 图片的标识, 用于查询状态.
     * @property url 转换完成后图片的地址.
     */
    data class Processing(val id: String, val url: String) : CommentImageUploadResult

    sealed interface Error : CommentImageUploadResult {
        /**
         * 重新上传同一张图片是否可能成功. 图片本身不被接受 (太大, 格式不支持或无法解码) 时不能重试.
         */
        val isRetryable: Boolean get() = this != TooLarge && this != InvalidFormat
    }

    /**
     * 图片超过 [UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES].
     */
    data object TooLarge : Error

    /**
     * 不是服务器支持的图片格式, 文件无法解码, 或像素过多.
     */
    data object InvalidFormat : Error

    /**
     * 没有登录 Ani 账号, 或登录已失效 (服务器返回 401).
     */
    data object NotLoggedIn : Error

    /**
     * 账号当前不能上传图片, 例如被封禁 (服务器返回 403).
     */
    data object Forbidden : Error

    /**
     * 上传过于频繁.
     */
    data object RateLimited : Error

    /**
     * 已上传但还没有被评论引用的图片太多. 发布引用这些图片的评论, 或等服务器删除过期未用的图片后才能继续上传.
     * 重新上传其中已有的图片不受此限制.
     */
    data object PendingLimit : Error

    /**
     * 网络错误, 或服务器暂时无法处理. 稍后重试即可.
     */
    data object NetworkError : Error

    /**
     * 服务器暂时无法转换这张图片, 或转换没有完成. 重新上传同一张图片即可重试.
     */
    data object ProcessingFailed : Error

    data class UnknownError(val message: String) : Error
}

/**
 * 服务器接受的评论图片格式: JPEG, PNG, WebP, GIF. 与服务器一样只看文件头.
 *
 * 其他格式 (例如 iOS 相机拍摄的 HEIC 照片) 服务器会拒绝, 需要先在客户端转码.
 */
object CommentImageFormat {
    /**
     * @param head 图片文件的内容, 至少包含开头的 12 个字节.
     */
    fun isSupported(head: ByteArray): Boolean = isJpeg(head) || isPng(head) || isWebp(head) || isGif(head)

    private fun isJpeg(data: ByteArray): Boolean {
        // JPEG signature: 0xFF 0xD8 0xFF
        return data.size >= 3 && data[0] == 0xFF.toByte() &&
                data[1] == 0xD8.toByte() &&
                data[2] == 0xFF.toByte()
    }

    private fun isPng(data: ByteArray): Boolean {
        // PNG signature: 137 80 78 71 13 10 26 10
        return data.size >= 8 && data[0] == 137.toByte() &&
                data[1] == 'P'.code.toByte() &&
                data[2] == 'N'.code.toByte() &&
                data[3] == 'G'.code.toByte() &&
                data[4] == 13.toByte() &&
                data[5] == 10.toByte() &&
                data[6] == 26.toByte() &&
                data[7] == 10.toByte()
    }

    private fun isWebp(data: ByteArray): Boolean {
        // WebP signature: "RIFF" + 4 bytes + "WEBP"
        return data.size >= 12 && data[0] == 'R'.code.toByte() &&
                data[1] == 'I'.code.toByte() &&
                data[2] == 'F'.code.toByte() &&
                data[3] == 'F'.code.toByte() &&
                data[8] == 'W'.code.toByte() &&
                data[9] == 'E'.code.toByte() &&
                data[10] == 'B'.code.toByte() &&
                data[11] == 'P'.code.toByte()
    }

    private fun isGif(data: ByteArray): Boolean {
        // GIF signature: "GIF87a" or "GIF89a"
        return data.size >= 6 && data[0] == 'G'.code.toByte() &&
                data[1] == 'I'.code.toByte() &&
                data[2] == 'F'.code.toByte() &&
                data[3] == '8'.code.toByte() &&
                (data[4] == '7'.code.toByte() || data[4] == '9'.code.toByte()) &&
                data[5] == 'a'.code.toByte()
    }
}
