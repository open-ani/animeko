/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.comments

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.comment.CommentImageStatusResult
import me.him188.ani.app.domain.comment.CommentImageUploadResult
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.EditComment
import me.him188.ani.app.ui.comment.EditCommentSheet
import me.him188.ani.app.ui.comment.EditCommentDefaults
import me.him188.ani.app.ui.comment.EditCommentSticker
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberBackgroundScope

@Composable
fun EpisodeEditCommentSheet(
    state: CommentEditorState,
    onDismiss: () -> Unit,
    onSendComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EditCommentSheet(
        state = state,
        onDismiss = onDismiss,
        onSendComplete = onSendComplete,
        modifier = modifier,
    )
}

@Preview
@Composable
fun PreviewEditComment() {
    ProvideCompositionLocalsForPreview {
        EditComment(state = rememberPreviewCommentEditorState())
    }
}

@Preview
@Composable
fun PreviewEditCommentWithImages() {
    ProvideCompositionLocalsForPreview {
        EditComment(
            state = rememberPreviewCommentEditorState(
                initialText = "看这个 [img]https://example.com/1.webp[/img] 还有 [img]https://example.com/2.webp[/img]",
            ),
        )
    }
}

@Composable
private fun rememberPreviewCommentEditorState(initialText: String = ""): CommentEditorState {
    val scope = rememberBackgroundScope()
    return remember {
        CommentEditorState(
            showExpandEditCommentButton = true,
            initialEditExpanded = false,
            panelTitle = mutableStateOf("评论：我心里危险的东西 第二季"),
            stickers = mutableStateOf(
                (0..64)
                    .map { EditCommentSticker(it, null) }
                    .toList(),
            ),
            onSend = { _, _ -> CommentSendResult.Ok },
            onUploadImage = { CommentImageUploadResult.Ready("https://example.com/image.webp") },
            onCheckImageStatus = { CommentImageStatusResult.Ok(emptyMap()) },
            richTextRenderer = {
                withContext(Dispatchers.Default) {
                    with(CommentMapperContext) { parseBBCode(it) }
                }
            },
            backgroundScope = scope.backgroundScope,
        ).apply {
            textFieldState.setTextAndPlaceCursorAtEnd(initialText)
        }
    }
}

@Preview
@Composable
fun PreviewEditCommentStickerPanel() {
    ProvideCompositionLocalsForPreview {
        EditCommentDefaults.StickerSelector(
            list = (0..64)
                .map { EditCommentSticker(it, null) }
                .toList(),
            onClickItem = { },
        )
    }
}
