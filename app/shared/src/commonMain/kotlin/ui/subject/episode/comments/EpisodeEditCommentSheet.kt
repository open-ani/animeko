/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.comments

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.domain.comment.TurnstileState
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.EditComment
import me.him188.ani.app.ui.comment.EditCommentDefaults
import me.him188.ani.app.ui.comment.EditCommentSticker
import me.him188.ani.app.ui.comment.createPreviewTurnstileState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.rememberImeMaxHeight
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.app.ui.foundation.widgets.ModalBottomImeAwareSheet
import me.him188.ani.app.ui.foundation.widgets.rememberModalBottomImeAwareSheetState

@Composable
fun EpisodeEditCommentSheet(
    state: CommentEditorState,
    turnstileState: TurnstileState,
    onDismiss: () -> Unit,
    onSendComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomImeAwareSheetState()

    val contentPadding = 16.dp
    val imePresentMaxHeight by rememberImeMaxHeight()

    ModalBottomImeAwareSheet(
        state = sheetState,
        onDismiss = onDismiss,
        modifier = Modifier
            .navigationBarsPadding()
            .ifThen(!state.showStickerPanel) { imePadding() },
    ) {
        EditComment(
            state = state,
            turnstileState = turnstileState,
            onCloseRequest = onDismiss,
            modifier = modifier
                .ifThen(state.editExpanded) { statusBarsPadding() }
                .ifThen(!state.editExpanded) { padding(top = contentPadding) }
                .padding(contentPadding),
            stickerPanelHeight = with(density) { imePresentMaxHeight.toDp() },
            focusRequester = focusRequester,
            onSendComplete = {
                sheetState.close()
                onSendComplete()
            },
        )
    }
}

@Preview
@Composable
fun PreviewEditComment() {
    ProvideCompositionLocalsForPreview {
        val scope = rememberBackgroundScope()
        EditComment(
            state = remember {
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
                    richTextRenderer = {
                        withContext(Dispatchers.Default) {
                            with(CommentMapperContext) { parseBBCode(it) }
                        }
                    },
                    backgroundScope = scope.backgroundScope,
                )
            },
            turnstileState = remember { createPreviewTurnstileState() },
        )
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
