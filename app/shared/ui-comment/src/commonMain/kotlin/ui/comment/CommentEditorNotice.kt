/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.runtime.Composable
import me.him188.ani.app.domain.comment.CommentImageUploadResult
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.domain.comment.PostCommentUseCase
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_image_limit_reached
import me.him188.ani.app.ui.lang.comment_image_processing_failed
import me.him188.ani.app.ui.lang.comment_image_upload_already_in_comment
import me.him188.ani.app.ui.lang.comment_image_upload_failed_network
import me.him188.ani.app.ui.lang.comment_image_upload_failed_unknown
import me.him188.ani.app.ui.lang.comment_image_upload_forbidden
import me.him188.ani.app.ui.lang.comment_image_upload_invalid_format
import me.him188.ani.app.ui.lang.comment_image_upload_not_logged_in
import me.him188.ani.app.ui.lang.comment_image_upload_pending_limit
import me.him188.ani.app.ui.lang.comment_image_upload_rate_limited
import me.him188.ani.app.ui.lang.comment_image_upload_too_large
import me.him188.ani.app.ui.lang.comment_send_blocked_failed_images
import me.him188.ani.app.ui.lang.comment_send_failed_image_processing
import me.him188.ani.app.ui.lang.comment_send_failed_invalid_image
import me.him188.ani.app.ui.lang.comment_send_failed_network
import me.him188.ani.app.ui.lang.comment_send_failed_too_many_images
import me.him188.ani.app.ui.lang.comment_send_failed_unknown
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * 评论编辑器的一次性提示, 界面以 toast 显示, 见 [CommentEditorState.notices].
 *
 * 提示只在事情发生时出现一次, 不反映之后的状态: 失败的图片在缩略图上持续显示失败, 发送按钮在图片可用前持续不可用.
 */
sealed interface CommentEditorNotice {
    /**
     * 评论中的一张图片上传或处理失败. 图片留在评论中, 可以重试或删除.
     */
    data class ImageUploadFailed(val error: CommentImageUploadResult.Error) : CommentEditorNotice

    /**
     * 评论发送失败.
     */
    data class SendFailed(val error: CommentSendResult.Error) : CommentEditorNotice

    /**
     * 评论中有上传失败的图片, 没有发送.
     */
    data object SendBlockedByFailedImages : CommentEditorNotice

    /**
     * 评论中的图片已达上限 ([PostCommentUseCase.MAX_IMAGES]), 没有添加更多图片.
     */
    data object ImageLimitReached : CommentEditorNotice

    /**
     * 上传成功的图片已经在评论中, 没有再插入一次.
     */
    data object ImageAlreadyInComment : CommentEditorNotice
}

/**
 * 一条多语言文字及其格式化参数. 在组合中用 [resolve] 取得, 在协程中用 [load] 取得.
 */
internal class LocalizedText(
    private val resource: StringResource,
    private vararg val args: Any,
) {
    @Composable
    fun resolve(): String = stringResource(resource, *args)

    suspend fun load(): String = getString(resource, *args)
}

internal fun CommentEditorNotice.text(): LocalizedText = when (this) {
    is CommentEditorNotice.ImageUploadFailed -> error.text()
    is CommentEditorNotice.SendFailed -> when (val error = error) {
        CommentSendResult.NetworkError -> LocalizedText(Lang.comment_send_failed_network)
        CommentSendResult.InvalidImage -> LocalizedText(Lang.comment_send_failed_invalid_image)
        CommentSendResult.TooManyImages -> LocalizedText(Lang.comment_send_failed_too_many_images)
        CommentSendResult.ImageProcessing -> LocalizedText(Lang.comment_send_failed_image_processing)
        is CommentSendResult.UnknownError -> LocalizedText(Lang.comment_send_failed_unknown, error.message)
    }

    CommentEditorNotice.SendBlockedByFailedImages -> LocalizedText(Lang.comment_send_blocked_failed_images)
    CommentEditorNotice.ImageLimitReached -> LocalizedText(Lang.comment_image_limit_reached, PostCommentUseCase.MAX_IMAGES)
    CommentEditorNotice.ImageAlreadyInComment -> LocalizedText(Lang.comment_image_upload_already_in_comment)
}

/**
 * 图片上传失败原因的说明. 缩略图的状态描述和 toast 都用它.
 */
internal fun CommentImageUploadResult.Error.text(): LocalizedText = when (this) {
    CommentImageUploadResult.TooLarge -> LocalizedText(Lang.comment_image_upload_too_large)
    CommentImageUploadResult.InvalidFormat -> LocalizedText(Lang.comment_image_upload_invalid_format)
    CommentImageUploadResult.NotLoggedIn -> LocalizedText(Lang.comment_image_upload_not_logged_in)
    CommentImageUploadResult.Forbidden -> LocalizedText(Lang.comment_image_upload_forbidden)
    CommentImageUploadResult.RateLimited -> LocalizedText(Lang.comment_image_upload_rate_limited)
    CommentImageUploadResult.PendingLimit -> LocalizedText(Lang.comment_image_upload_pending_limit)
    CommentImageUploadResult.NetworkError -> LocalizedText(Lang.comment_image_upload_failed_network)
    CommentImageUploadResult.ProcessingFailed -> LocalizedText(Lang.comment_image_processing_failed)
    is CommentImageUploadResult.UnknownError -> LocalizedText(Lang.comment_image_upload_failed_unknown, message)
}
