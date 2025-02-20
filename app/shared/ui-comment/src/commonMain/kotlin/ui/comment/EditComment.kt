/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.domain.comment.TurnstileState
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.isImeVisible
import me.him188.ani.app.ui.foundation.text.ProvideContentColor

/**
 * 评论编辑.
 *
 * @param stickerPanelHeight 表情面板的高度，调用者可以提供 IME 高度来配合实现沉浸效果
 *
 * @see EditCommentScaffold
 */
@Composable
fun EditComment(
    state: CommentEditorState,
    turnstileState: TurnstileState,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    stickerPanelHeight: Dp = EditCommentDefaults.MinStickerHeight.dp,
    onSendComplete: () -> Unit = { },
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val sendingComment by state.sending.collectAsStateWithLifecycle(false)

    val imeVisible = isImeVisible()
    var previousImeVisible by remember { mutableStateOf(false) }
    SideEffect {
        if (!previousImeVisible && imeVisible) {
            state.toggleStickerPanelState(false)
        }
        previousImeVisible = imeVisible
    }

    EditCommentScaffold(
        previewing = state.previewing,
        modifier = modifier,
        title = {
            state.panelTitle?.let { EditCommentDefaults.Title(it) }
        },
        actionRow = {
            EditCommentDefaults.ActionRow(
                sendTarget = state.currentSendTarget,
                previewing = state.previewing,
                sending = sendingComment,
                onClickBold = { state.wrapSelectionWith("[b][/b]", 3) },
                onClickItalic = { state.wrapSelectionWith("[i][/i]", 3) },
                onClickUnderlined = { state.wrapSelectionWith("[u][/u]", 3) },
                onClickStrikethrough = { state.wrapSelectionWith("[s][/s]", 3) },
                onClickMask = { state.wrapSelectionWith("[mask][/mask]", 6) },
                onClickImage = { state.wrapSelectionWith("[img][/img]", 5) },
                onClickUrl = { state.wrapSelectionWith("[url=][/url]", 5) },
                onClickEmoji = {
                    state.toggleStickerPanelState()
                    if (state.showStickerPanel) keyboard?.hide()
                },
                onPreview = {
                    keyboard?.hide()
                    state.toggleStickerPanelState(false)
                    state.togglePreview()
                },
                onSend = {
                    keyboard?.hide()
                    state.toggleStickerPanelState(false)
                    scope.launch {
                        if (state.send()) onSendComplete()
                    }
                },
            )

            if (state.showStickerPanel) {
                EditCommentDefaults.StickerSelector(
                    list = state.stickers,
                    modifier = Modifier.fillMaxWidth()
                        .height(max(EditCommentDefaults.MinStickerHeight.dp, stickerPanelHeight)),
                    onClickItem = { stickerId ->
                        val inserted = "(bgm$stickerId)"
                        state.insertTextAt(inserted, inserted.length)
                    },
                )
            }
        },
        captcha = {
            if (!sendingComment) return@EditCommentScaffold
            Turnstile(turnstileState, Modifier.wrapContentSize())
        },
        expanded = state.expandButtonState,
        onClickExpand = { state.editExpanded = it },
    ) { previewing ->
        Column {
            ProvideContentColor(MaterialTheme.colorScheme.onSurface) {
                if (previewing) {
                    LaunchedEffect(Unit) { state.renderPreview() }
                    EditCommentDefaults.Preview(
                        content = state.previewContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .ifThen(state.editExpanded) { fillMaxHeight() }
                            .animateContentSize(),
                        contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                    )
                } else {
                    EditCommentDefaults.CommentTextField(
                        value = state.content,
                        enabled = !sendingComment,
                        maxLines = if (state.editExpanded) Int.MAX_VALUE else 3,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .fillMaxWidth()
                            .ifThen(state.editExpanded) { fillMaxHeight() }
                            .animateContentSize(),
                        onValueChange = { state.setContent(it) },
                        interactionSource = remember { MutableInteractionSource() },
                    )
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                }
                AniAnimatedVisibility(
                    visible = state.sendResult is CommentSendResult.Error,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = (state.sendResult as? CommentSendResult.Error)
                            ?.let { renderCommentSendError(it) }
                            ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun renderCommentSendError(result: CommentSendResult.Error): String {
    return when (result) {
        is CommentSendResult.TurnstileError.Network -> "验证码加载失败：网络错误(${result.code})"
        is CommentSendResult.TurnstileError.Unknown -> "验证码加载失败：未知错误(${result.code})"
        CommentSendResult.NetworkError -> "发送失败：网络错误"
        is CommentSendResult.UnknownError -> "发送失败，请附带日志反馈此问题\n${result.message}"
    }
}

/**
 * 评论编辑 Scaffold
 *
 * @param previewing 是否正在 preview
 * @param actionRow 操作按钮, 进行富文本编辑和评论发送. see [EditCommentDefaults.ActionRow].
 * @param expanded 展开按钮状态, 为 `null` 时不显示按钮.
 * @param onClickExpand 点击展开按钮时触发该点击事件.
 * @param title 评论编辑标题, 一般显示 正在为哪个对象发送评论. see [EditCommentDefaults.Title].
 * @param content 评论编辑框. see [EditCommentDefaults.CommentTextField].
 * @param captcha 发送评论时的验证码交互.
 */
@Composable
fun EditCommentScaffold(
    previewing: Boolean,
    actionRow: @Composable ColumnScope.() -> Unit,
    onClickExpand: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    title: (@Composable () -> Unit)? = null,
    captcha: @Composable () -> Unit = {},
    contentColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.(previewing: Boolean) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (title != null) {
                Box(Modifier.weight(1.0f)) {
                    title()
                }
            }
            if (expanded != null) {
                EditCommentDefaults.ActionButton(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    enabled = true,
                    onClick = { onClickExpand(!expanded) },
                )
            }

        }

        ProvideContentColor(contentColor) {
            Crossfade(
                targetState = previewing,
                modifier = Modifier.weight(1.0f, fill = false),
            ) { previewing ->
                content(previewing)
            }
            
        }

        captcha()
        
        Column {
            actionRow()
        }

    }
}