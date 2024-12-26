/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.annotation.UiThread
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.him188.ani.app.tools.MonoTasker
import org.jetbrains.compose.resources.DrawableResource
import kotlin.coroutines.CoroutineContext

@Stable
class CommentEditorState(
    showExpandEditCommentButton: Boolean,
    initialEditExpanded: Boolean,
    panelTitle: State<String?>,
    stickers: State<List<EditCommentSticker>>,
    private val richTextRenderer: suspend (String) -> UIRichText,
    private val onSend: suspend (target: CommentContext, content: String) -> Boolean,
    backgroundScope: CoroutineScope,
) {
    private val editor = CommentEditorTextState("")

    private val sendTasker = MonoTasker(backgroundScope)

    val panelTitle by panelTitle

    var currentSendTarget: CommentContext? by mutableStateOf(null)
        private set
    val sending get() = sendTasker.isRunning

    val content get() = editor.textField
    var previewing by mutableStateOf(false)
        private set
    var previewContent: UIRichText? by mutableStateOf(null)
        private set

    var editExpanded: Boolean by mutableStateOf(initialEditExpanded)
    val expandButtonState by derivedStateOf { if (!showExpandEditCommentButton) null else editExpanded }

    var showStickerPanel: Boolean by mutableStateOf(false)
        private set
    val stickers by stickers

    /**
     * 连续开关为同一个评论的编辑框将保存编辑内容和编辑框状态
     */
    fun startEdit(newTarget: CommentContext) {
        if (newTarget != currentSendTarget) {
            editor.override(TextFieldValue(""))
        }
        currentSendTarget = newTarget
        previewing = false
        previewContent = null
        editExpanded = false
    }

    fun toggleStickerPanelState(desired: Boolean? = null) {
        showStickerPanel = desired ?: !showStickerPanel
    }

    fun setContent(value: TextFieldValue) {
        editor.override(value)
    }

    /**
     * @see CommentEditorTextState.wrapSelectionWith
     */
    fun wrapSelectionWith(value: String, secondSliceIndex: Int) {
        editor.wrapSelectionWith(value, secondSliceIndex)
    }

    /**
     * @see CommentEditorTextState.insertTextAt
     */
    fun insertTextAt(value: String, cursorOffset: Int = value.length) {
        editor.insertTextAt(value, cursorOffset)
    }

    fun togglePreview() {
        previewing = !previewing
    }

    @UiThread
    suspend fun renderPreview() {
        previewContent = null
        val rendered = richTextRenderer(content.text)
        previewContent = rendered
    }

    /**
     * @return `true` if comment was sent successfully
     */
    suspend fun send(
        context: CoroutineContext = Dispatchers.Default
    ): Boolean {
        val target = currentSendTarget
        val content = editor.textField.text

        editExpanded = false

        val result = sendTasker.async(context) {
            checkNotNull(target)
            onSend(target, content)
        }.await()

        editor.override(TextFieldValue(""))
        return result
    }

    fun cancelSend() {
        sendTasker.cancel()
    }
}

@Immutable
data class EditCommentSticker(
    val id: Int,
    val drawableRes: DrawableResource?,
)

/**
 * 评论发送的对象，在 [CommentEditorState.onSend] 需要提供。
 */
@Immutable
sealed interface CommentContext {
    /**
     * 剧集评论
     */
    data class Episode(val subjectId: Int, val episodeId: Int) : CommentContext

    /**
     * 条目吐槽箱
     */
    data class SubjectReview(val subjectId: Int) : CommentContext

    /**
     * 回复某个人的评论
     */
    data class Reply(val commentId: Int) : CommentContext
}