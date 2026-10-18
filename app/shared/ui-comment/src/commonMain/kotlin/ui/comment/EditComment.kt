/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.then
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.comment.PostCommentUseCase
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.isImeVisible
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.transformTextOnWrite
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.utils.platform.isAndroid
import me.him188.ani.utils.platform.isMobile

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
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    stickerPanelHeight: Dp = EditCommentDefaults.MinStickerHeight.dp,
    onSendComplete: () -> Unit = { },
    onCloseRequest: () -> Unit = { },
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val sendingComment by state.sending.collectAsStateWithLifecycle(false)

    // 上传失败、发送失败等提示用 toast, 不占编辑器的位置
    val toaster = LocalToaster.current
    LaunchedEffect(state, toaster) {
        state.notices.collect { toaster.toast(it.text().load()) }
    }

    val imeVisible = isImeVisible()
    var previousImeVisible by remember { mutableStateOf(false) }
    SideEffect {
        if (!previousImeVisible && imeVisible) {
            state.toggleStickerPanelState(false)
        }
        previousImeVisible = imeVisible
    }

    val isMobile = LocalPlatform.current.isMobile()
    val imagePickerType = if (isMobile) {
        // 系统的照片选择器
        FileKitType.Image
    } else {
        // 桌面端的 FileKitType.Image 只能选 png、jpg、jpeg、gif 和 bmp: 不能选 WebP, 而 BMP 服务器不接受
        FileKitType.File(extensions = DesktopImageExtensions)
    }
    // 移动端的照片选择器自己限制可选的数量. 桌面端的文件对话框不能限制, 超出的部分由 addImages 舍去并提示
    val maxPickedImages = if (isMobile) state.remainingImageSlots.coerceIn(1, PostCommentUseCase.MAX_IMAGES) else null
    val imagePicker = rememberFilePickerLauncher(
        type = imagePickerType,
        mode = FileKitMode.Multiple(maxItems = maxPickedImages),
    ) { files ->
        if (files.isNullOrEmpty()) return@rememberFilePickerLauncher
        scope.launch {
            val images = withContext(Dispatchers.IO) {
                files.map { file ->
                    val size = try {
                        file.size()
                    } catch (e: Exception) {
                        // 读取内容后再检查大小
                        null
                    }
                    CommentImageFile(size, readBytes = { file.readBytes() })
                }
            }
            state.addImages(images)
        }
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
                uploadingImage = state.uploadingImages,
                onClickEmoji = {
                    state.toggleStickerPanelState()
                    if (state.showStickerPanel) keyboard?.hide()
                },
                onClickUploadImage = {
                    if (state.remainingImageSlots > 0) imagePicker.launch() else state.notifyImageLimitReached()
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
                        state.insertTextAtCursor("(bgm$stickerId)")
                    },
                )
            }
        },
        expanded = state.expandButtonState,
        onClickExpand = { state.editExpanded = it },
        onClickClose = onCloseRequest,
    ) { previewing ->
        Column {
            ProvideContentColor(MaterialTheme.colorScheme.onSurface) {
                if (previewing) {
                    LaunchedEffect(Unit) { state.renderPreview() }
                    EditCommentDefaults.Preview(
                        content = state.previewContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 展开时占满高度
                            .ifThen(state.editExpanded) { weight(1f) }
                            .animateContentSize(),
                        contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                    )
                } else {
                    val textFieldLayout = remember { CommentTextFieldLayout() }
                    val textScrollState = rememberScrollState()
                    val textFieldInteractionSource = remember { MutableInteractionSource() }
                    val textFieldFocused by textFieldInteractionSource.collectIsFocusedAsState()

                    val chipLabel = rememberImageChipLabel()
                    val chipStyle = CommentImageChipStyle.default()
                    val chipIcons = CommentImageChipIcons.default()
                    val chipDisplay by remember(state, chipLabel) {
                        derivedStateOf {
                            buildImageChipDisplay(state.textFieldState.text, state.imageChipRegistry, chipLabel)
                        }
                    }
                    val outputTransformation = remember(state, chipLabel, chipStyle) {
                        CommentImageChipOutputTransformation(
                            registry = state.imageChipRegistry,
                            label = chipLabel,
                            status = state::imageChipStatus,
                            style = chipStyle,
                        )
                    }
                    val inputTransformation = remember(state) {
                        state.imageChipInputTransformation.then(CommentPasteLinkifyTransformation)
                    }

                    // 复制和剪切时 Compose 把显示的 "图片 N" 放到剪贴板, 写入前还原为图片的 BBCode
                    val platformClipboard = LocalClipboard.current
                    val clipboard = remember(platformClipboard, state) {
                        platformClipboard.transformTextOnWrite { text ->
                            state.decodeImageChipLabels(text).takeIf { it != text }
                        }
                    }
                    val toolbarRequests = remember { CommentTextToolbarRequests() }
                    val clipboardActions = if (LocalPlatform.current.isAndroid()) {
                        remember(state, clipboard, scope) {
                            CommentClipboardActions(
                                onCut = { scope.launch { state.cutSelection(clipboard) } },
                                onCopy = { scope.launch { state.copySelection(clipboard) } },
                                onPaste = { scope.launch { state.paste(clipboard) } },
                                onSelectAll = { state.selectAll() },
                            )
                        }
                    } else {
                        null
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 展开时占满图片缩略图以外的高度
                            .ifThen(state.editExpanded) { weight(1f) }
                            .animateContentSize()
                            .onGloballyPositioned { textFieldLayout.onHostPositioned(it) },
                    ) {
                        CompositionLocalProvider(
                            LocalClipboard provides clipboard,
                            LocalTextContextMenuToolbarProvider provides if (clipboardActions != null) {
                                toolbarRequests
                            } else {
                                LocalTextContextMenuToolbarProvider.current
                            },
                        ) {
                            EditCommentDefaults.CommentTextField(
                                state = state.textFieldState,
                                enabled = !sendingComment,
                                lineLimits = if (state.editExpanded) ExpandedLineLimits else CompactLineLimits,
                                inputTransformation = inputTransformation,
                                outputTransformation = outputTransformation,
                                onTextLayout = { textFieldLayout.onTextLayout(it) },
                                textAreaModifier = Modifier
                                    .onGloballyPositioned { textFieldLayout.onTextPositioned(it) }
                                    .drawBehind {
                                        val layout = textFieldLayout.textLayoutResult ?: return@drawBehind
                                        drawImageChipBackgrounds(
                                            layout, chipDisplay, textScrollState.value, chipStyle, chipIcons,
                                        ) { state.imageChipStatus(it) }
                                    },
                                scrollState = textScrollState,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .fillMaxWidth()
                                    .ifThen(state.editExpanded) { fillMaxHeight() }
                                    .testTag(EditCommentTestTags.TextField)
                                    .imageChipTapCursorCorrection(
                                        state = state.textFieldState,
                                        textFieldLayout = textFieldLayout,
                                        scrollState = textScrollState,
                                        chipDisplay = { chipDisplay },
                                        enabled = !sendingComment,
                                    ),
                                interactionSource = textFieldInteractionSource,
                            )
                        }

                        EditCommentDefaults.ActionButton(
                            imageVector = if (state.editExpanded)
                                Icons.Default.CloseFullscreen
                            else
                                Icons.Default.OpenInFull,
                            enabled = true,
                            onClick = { state.editExpanded = !state.editExpanded },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                        )

                        CommentFormattingToolbarHost(
                            state = state,
                            textFieldLayout = textFieldLayout,
                            textScrollState = textScrollState,
                            chipDisplay = { chipDisplay },
                            enabled = textFieldFocused && !state.previewing && !sendingComment,
                            clipboardActions = clipboardActions,
                            cursorToolbarRequested = { toolbarRequests.requested },
                        )
                    }

                    CommentImageStripHost(
                        state = state,
                        onSelectImage = { number ->
                            focusRequester.requestFocus()
                            state.selectImageChip(number)
                        },
                    )

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                }
            }
        }
    }
}

/**
 * 编辑框获得焦点且选中了文字时, 在选区旁显示 [CommentFormattingToolbarPopup]. 选区完全滚出编辑框的可见区域时不显示.
 *
 * 有 [clipboardActions] 时 (Android), 文本框请求显示文字操作工具栏 ([cursorToolbarRequested]) 且选区为空时,
 * 在光标旁显示只有粘贴和全选的工具栏.
 *
 * 单独作为一个可重组的作用域, 选区和滚动位置的变化只重组这里.
 *
 * @param chipDisplay 当前文本中的图片标签显示为什么文字, 用于把选区换算到文字布局中.
 */
@Composable
private fun CommentFormattingToolbarHost(
    state: CommentEditorState,
    textFieldLayout: CommentTextFieldLayout,
    textScrollState: ScrollState,
    chipDisplay: () -> ImageChipDisplay,
    enabled: Boolean,
    clipboardActions: CommentClipboardActions?,
    cursorToolbarRequested: () -> Boolean,
) {
    if (!enabled) return
    val selection = state.textFieldState.selection
    if (selection.collapsed && (clipboardActions == null || !cursorToolbarRequested())) return
    val visualSelection = chipDisplay().toVisual(selection)
    val bounds = textFieldLayout.selectionBounds(visualSelection, textScrollState.value) ?: return
    CommentFormattingToolbarPopup(
        selectionBounds = bounds,
        activeFormats = state.activeFormats,
        linkActive = state.linkActive,
        onToggleFormat = { state.toggleFormat(it) },
        onToggleLink = { state.toggleLink() },
        clipboardActions = clipboardActions,
        showFormatting = !selection.collapsed,
    )
}

/**
 * 评论中有图片时在编辑框下方显示 [CommentImageStrip].
 *
 * - 点击缩略图选中编辑框中对应的图片标签 ([onSelectImage]), 选区工具栏随之出现, 可以剪切、复制这张图片;
 *   上传或处理失败且可以重试的图片则重新上传.
 * - 点击删除按钮从评论中删除这一处图片.
 *
 * 单独作为一个可重组的作用域, 编辑文字时只有图片变化才重组这里.
 *
 * @param onSelectImage 选中第 N 张图片. 调用方负责让编辑框获得焦点.
 */
@Composable
private fun CommentImageStripHost(
    state: CommentEditorState,
    onSelectImage: (number: Int) -> Unit,
) {
    val images = state.imageThumbnails
    if (images.isEmpty()) return
    CommentImageStrip(
        images = images,
        onClick = { image ->
            val uploadId = image.uploadId
            if (image.canRetry && uploadId != null) {
                state.retryImageUpload(uploadId)
            } else {
                onSelectImage(image.number)
            }
        },
        onRemove = { state.removeImageChip(it.number) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(EditCommentTestTags.ImageStrip),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * 桌面端选择图片时可选的扩展名, 即服务器接受的格式.
 */
private val DesktopImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

private val CompactLineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3)
private val ExpandedLineLimits = TextFieldLineLimits.MultiLine()

object EditCommentTestTags {
    const val TextField = "EditComment:textField"
    const val FormattingToolbar = "EditComment:formattingToolbar"
    const val ImageStrip = "EditComment:imageStrip"
}

/**
 * 评论编辑 Scaffold
 *
 * @param previewing 是否正在 preview
 * @param actionRow 操作按钮, 插入表情、上传图片和发送评论. see [EditCommentDefaults.ActionRow].
 * @param expanded 展开按钮状态, 为 `null` 时不显示按钮.
 * @param onClickExpand 点击展开按钮时触发该点击事件.
 * @param title 评论编辑标题, 一般显示 正在为哪个对象发送评论. see [EditCommentDefaults.Title].
 * @param content 评论编辑框. see [EditCommentDefaults.CommentTextField].
 */
@Composable
fun EditCommentScaffold(
    previewing: Boolean,
    actionRow: @Composable ColumnScope.() -> Unit,
    onClickExpand: (Boolean) -> Unit,
    onClickClose: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    title: (@Composable () -> Unit)? = null,
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
                    imageVector = Icons.Default.Close,
                    enabled = true,
                    onClick = onClickClose,
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

        Column {
            actionRow()
        }

    }
}
