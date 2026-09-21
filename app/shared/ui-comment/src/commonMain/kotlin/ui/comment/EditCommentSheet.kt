/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.rememberImeMaxHeight
import me.him188.ani.app.ui.foundation.widgets.ModalBottomImeAwareSheet
import me.him188.ani.app.ui.foundation.widgets.rememberModalBottomImeAwareSheetState

/**
 * 底部弹出的评论编辑器 (跟随 IME 高度), 供剧集评论与人物/角色评论共用.
 *
 * 调用方在展示前应先调用 [CommentEditorState.startEdit] 设定发送目标, 并在 [onDismiss] 里调用 [CommentEditorState.cancelSend].
 *
 * @param onSendComplete 发送成功且 sheet 已关闭后回调, 调用方可据此刷新列表.
 */
@Composable
fun EditCommentSheet(
    state: CommentEditorState,
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
