/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.comment.CommentImageUploadResult
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_image_processing
import me.him188.ani.app.ui.lang.comment_image_remove
import me.him188.ani.app.ui.lang.comment_image_retry
import me.him188.ani.app.ui.lang.comment_image_uploading
import org.jetbrains.compose.resources.stringResource

/**
 * 编辑框下方的一张图片缩略图, 对应文本中的一个图片标签 ([CommentImageChip]).
 *
 * @property number 图片的序号, 与编辑框中 "图片 N" 标签的序号相同.
 * @property url 显示的图片地址. 还不可用 (正在上传、正在处理或失败) 的图片为 `null`.
 * @property uploadId 待上传图片的本地标识, 见 [CommentImageChip.uploadId].
 * @property error 上传失败的原因. 不属于本次编辑的占位标签 (例如粘贴进来的) 没有原因, 为 `null`.
 * @property canRetry 上传失败且可以重试.
 */
@Immutable
internal data class CommentImageThumbnail(
    val number: Int,
    val status: CommentImageChipStatus,
    val url: String?,
    val uploadId: String?,
    val error: CommentImageUploadResult.Error?,
    val canRetry: Boolean,
)

/**
 * 评论中图片的缩略图, 按图片在评论中出现的顺序排成一行, 放不下时可以横向滚动.
 *
 * 每张缩略图左上角是序号, 颜色与编辑框中的图片标签相同 ([style]); 右上角是删除按钮.
 * 正在上传和正在服务器上处理的显示进度 (两者颜色不同), 失败的显示错误图标, 可以重试时显示重试图标.
 * 正在处理的图片不在本地解码显示预览: 原图可能非常大.
 *
 * @param onClick 点击缩略图 (删除按钮以外的部分).
 * @param onRemove 点击删除按钮.
 */
@Composable
internal fun CommentImageStrip(
    images: List<CommentImageThumbnail>,
    onClick: (CommentImageThumbnail) -> Unit,
    onRemove: (CommentImageThumbnail) -> Unit,
    modifier: Modifier = Modifier,
    style: CommentImageChipStyle = CommentImageChipStyle.default(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val label = rememberImageChipLabel()
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (image in images) {
            CommentImageThumbnailItem(
                image = image,
                label = label(image.number),
                colors = style.colors(image.status),
                onClick = { onClick(image) },
                onRemove = { onRemove(image) },
            )
        }
    }
}

@Composable
private fun CommentImageThumbnailItem(
    image: CommentImageThumbnail,
    label: String,
    colors: CommentImageChipColors,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val retryText = stringResource(Lang.comment_image_retry)
    val stateText = when (image.status) {
        CommentImageChipStatus.Ready -> null
        CommentImageChipStatus.Uploading -> stringResource(Lang.comment_image_uploading)
        CommentImageChipStatus.Processing -> stringResource(Lang.comment_image_processing)
        CommentImageChipStatus.Failed -> image.error?.let { renderImageUploadError(it) }
    }
    Box(
        Modifier
            .size(ThumbnailSize)
            .clip(ThumbnailShape)
            .background(
                if (image.status == CommentImageChipStatus.Failed) {
                    colorScheme.errorContainer
                } else {
                    colorScheme.surfaceContainerHighest
                },
            )
            .clickable(
                onClickLabel = if (image.canRetry) retryText else null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                if (stateText != null) stateDescription = stateText
            },
    ) {
        when (image.status) {
            CommentImageChipStatus.Ready -> {
                var loadFailed by remember(image.url) { mutableStateOf(false) }
                AsyncImage(
                    model = image.url,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    onLoading = { loadFailed = false },
                    onSuccess = { loadFailed = false },
                    onError = { loadFailed = true },
                    contentScale = ContentScale.Crop,
                )
                if (loadFailed) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
            }

            CommentImageChipStatus.Uploading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(24.dp),
                strokeWidth = 2.dp,
            )

            // 与上传时的进度区分: 图片已经在服务器上, 换成次要颜色并显示轨道
            CommentImageChipStatus.Processing -> CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(24.dp),
                color = colorScheme.secondary,
                strokeWidth = 2.dp,
                trackColor = colorScheme.secondaryContainer,
            )

            CommentImageChipStatus.Failed -> Icon(
                imageVector = if (image.canRetry) Icons.Rounded.Refresh else Icons.Outlined.ErrorOutline,
                contentDescription = if (image.canRetry) retryText else null,
                modifier = Modifier.align(Alignment.Center).size(24.dp),
                tint = colorScheme.onErrorContainer,
            )
        }

        // 序号已在缩略图的描述 ("图片 N") 中
        Text(
            text = image.number.toString(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(colors.containerColor, BadgeShape)
                .padding(horizontal = 5.dp)
                .clearAndSetSemantics {},
            color = colors.contentColor,
            style = MaterialTheme.typography.labelSmall,
        )

        val removeText = stringResource(Lang.comment_image_remove, image.number)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(RemoveButtonTouchSize)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = removeText },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(RemoveButtonSize)
                    .background(colorScheme.scrim.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

private val ThumbnailSize = 64.dp
private val ThumbnailShape = RoundedCornerShape(8.dp)
private val BadgeShape = RoundedCornerShape(4.dp)
private val RemoveButtonSize = 20.dp

/**
 * 删除按钮可以点击的范围, 比按钮本身大一些, 方便触摸.
 */
private val RemoveButtonTouchSize = 28.dp

@Preview
@Composable
private fun PreviewCommentImageStrip() {
    ProvideCompositionLocalsForPreview {
        CommentImageStrip(
            images = listOf(
                CommentImageThumbnail(1, CommentImageChipStatus.Ready, "https://example.com/1.webp", null, null, false),
                CommentImageThumbnail(2, CommentImageChipStatus.Uploading, null, "a-2", null, false),
                CommentImageThumbnail(5, CommentImageChipStatus.Processing, null, "a-5", null, false),
                CommentImageThumbnail(
                    3, CommentImageChipStatus.Failed, null, "a-3",
                    CommentImageUploadResult.NetworkError, canRetry = true,
                ),
                CommentImageThumbnail(
                    4, CommentImageChipStatus.Failed, null, "a-4",
                    CommentImageUploadResult.TooLarge, canRetry = false,
                ),
            ),
            onClick = {},
            onRemove = {},
            contentPadding = PaddingValues(16.dp),
        )
    }
}
