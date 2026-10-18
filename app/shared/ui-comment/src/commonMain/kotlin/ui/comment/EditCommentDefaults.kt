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
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.theme.looming
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_add_emoji
import me.him188.ani.app.ui.lang.comment_edit
import me.him188.ani.app.ui.lang.comment_preview
import me.him188.ani.app.ui.lang.comment_rendering
import me.him188.ani.app.ui.lang.comment_send
import me.him188.ani.app.ui.lang.comment_send_comment
import me.him188.ani.app.ui.lang.comment_upload_image
import me.him188.ani.app.ui.richtext.RichText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

object EditCommentDefaults {
    @Suppress("ConstPropertyName")
    const val ActionButtonSize: Int = 48

    @Suppress("ConstPropertyName")
    private const val ActionRowPrimaryAction: String = "primaryAction"

    @Suppress("ConstPropertyName")
    const val MinStickerHeight: Int = 192

    @Composable
    fun Title(text: String) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }

    @Composable
    fun CommentTextFieldPlaceholder(modifier: Modifier = Modifier) {
        Text(text = stringResource(Lang.comment_send_comment), modifier, softWrap = false)
    }

    /**
     * 评论编辑框, 外观与 Material3 `OutlinedTextField` 相同.
     *
     * 用 [BasicTextField] 加 [OutlinedTextFieldDefaults.decorator] 组合而不是直接使用 `OutlinedTextField`, 是为了在内部文字区域上加
     * [textAreaModifier]: 选区工具栏要知道文字区域的位置, 才能把 [onTextLayout] 给出的坐标换算到编辑框外; 图片标签的背景也画在这里.
     *
     * @param lineLimits 收起时为最多 3 行的 [TextFieldLineLimits.MultiLine], 展开时不限行数并由 [modifier] 填满高度.
     * @param onTextLayout 见 [BasicTextField]. 布局结果的坐标以文字区域左上角为原点, 显示时再向上偏移 [scrollState] 的值.
     * 有 [outputTransformation] 时布局的是转换后显示的文字.
     * @param textAreaModifier 用于内部文字区域 (可滚动的视口) 的 [Modifier], 在其中绘制的内容位于文字下方.
     */
    @Composable
    fun CommentTextField(
        state: TextFieldState,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        lineLimits: TextFieldLineLimits = TextFieldLineLimits.MultiLine(),
        inputTransformation: InputTransformation? = null,
        outputTransformation: OutputTransformation? = null,
        onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
        textAreaModifier: Modifier = Modifier,
        scrollState: ScrollState = rememberScrollState(),
        shape: Shape = MaterialTheme.shapes.medium,
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
        colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        placeholder: @Composable (() -> Unit)? = null
    ) {
        val decorator = OutlinedTextFieldDefaults.decorator(
            state = state,
            enabled = enabled,
            lineLimits = lineLimits,
            outputTransformation = outputTransformation,
            interactionSource = interactionSource,
            placeholder = placeholder,
            colors = colors,
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = enabled,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = colors,
                    shape = shape,
                )
            },
        )
        CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
            BasicTextField(
                state = state,
                modifier = modifier.defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = OutlinedTextFieldDefaults.MinHeight,
                ),
                enabled = enabled,
                inputTransformation = inputTransformation,
                outputTransformation = outputTransformation,
                textStyle = MaterialTheme.typography.bodyMedium.merge(
                    fontSize = 15.5.sp,
                    color = LocalContentColor.current.looming(),
                ),
                lineLimits = lineLimits,
                onTextLayout = onTextLayout,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(colors.cursorColor),
                scrollState = scrollState,
                decorator = { innerTextField ->
                    decorator.Decoration {
                        Box(
                            modifier = textAreaModifier,
                            propagateMinConstraints = true,
                        ) {
                            innerTextField()
                        }
                    }
                },
            )
        }
    }

    @Composable
    fun Preview(
        content: UIRichText?,
        modifier: Modifier = Modifier,
        contentPadding: PaddingValues = PaddingValues(0.dp)
    ) {
        Surface(
            modifier = modifier.placeholder(content == null),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            LazyColumn {
                if (content == null) {
                    item {
                        Text(
                            text = stringResource(Lang.comment_rendering),
                            modifier = Modifier.padding(contentPadding),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    item {
                        RichText(
                            elements = content.elements,
                            modifier = Modifier.padding(contentPadding),
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ActionButton(
        imageVector: ImageVector,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        contentDescription: String? = null,
        enabled: Boolean = true,
        iconSize: Dp = 22.dp,
    ) {
        IconButton(
            modifier = modifier,
            enabled = enabled,
            onClick = onClick,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }

    /**
     * 编辑框下方的操作行: 表情、上传图片, 以及靠右的预览和发送. 宽度不够时换行, 预览和发送始终靠右.
     *
     * 加粗等格式操作在选中文字后由 [CommentFormattingToolbar] 提供. 上传中的图片在编辑框下方的 [CommentImageStrip] 中显示进度,
     * 上传期间仍可以继续添加图片.
     *
     * @param uploadingImage 正在上传图片, 或上传的图片正在服务器上处理. 这期间不能发送.
     */
    @Composable
    fun ActionRow(
        sendTarget: CommentContext?,
        onClickEmoji: () -> Unit,
        onClickUploadImage: () -> Unit,
        onSend: () -> Unit,
        onPreview: () -> Unit,
        modifier: Modifier = Modifier,
        previewing: Boolean = false,
        sending: Boolean = false,
        uploadingImage: Boolean = false,
    ) {
        // maybe we can extract the layout
        val size = ActionButtonSize.dp

        val actionEnabled by derivedStateOf { !sending && !previewing }
        val addEmojiText = stringResource(Lang.comment_add_emoji)
        val uploadImageText = stringResource(Lang.comment_upload_image)
        val editText = stringResource(Lang.comment_edit)
        val previewText = stringResource(Lang.comment_preview)
        val sendText = stringResource(Lang.comment_send)

        // Custom FlowRow which supports a right-aligned element.
        Layout(
            modifier = modifier.then(Modifier.fillMaxWidth().animateContentSize()),
            measurePolicy = { measurables, rawConstraints ->
                val constraints = rawConstraints.copy(minWidth = 0)
                val containerWidth = constraints.maxWidth
                val primaryAction = measurables.find { it.layoutId == ActionRowPrimaryAction }
                val textActions = measurables.filter { it.layoutId != ActionRowPrimaryAction }

                var currentAccumulatedWidth = 0
                val currentList = mutableListOf<Placeable>()

                val textPlaceables = textActions.foldIndexed(mutableListOf<List<Placeable>>()) { i, acc, curr ->
                    val placeable = curr.measure(constraints)

                    // 当前一行显示不下了，需要放到下一行
                    if (currentAccumulatedWidth + placeable.width > containerWidth) {
                        acc.add(currentList.toList())
                        currentList.clear()
                        currentAccumulatedWidth = 0
                    }

                    currentList.add(placeable)
                    currentAccumulatedWidth += placeable.width

                    if (i == textActions.lastIndex && currentList.isNotEmpty()) {
                        acc.add(currentList.toList())
                    }

                    acc
                }

                val primaryActionPlaceable = primaryAction?.measure(constraints)
                val primaryActionWidth = primaryActionPlaceable?.width ?: 0
                val primaryActionHeight = primaryActionPlaceable?.height ?: 0

                val shouldAppendNewLine =
                    primaryActionWidth + (textPlaceables.lastOrNull()?.sumOf { it.width } ?: 0) > containerWidth

                layout(
                    width = constraints.maxWidth,
                    height = textPlaceables.sumOf { it.maxOf { p -> p.height } } +
                            if (shouldAppendNewLine) primaryActionHeight else 0,
                ) {
                    var currentX = 0
                    var currentY = 0
                    var lastLineMaxHeight = 0
                    textPlaceables.forEachIndexed { i, line ->
                        val lineMaxHeight = maxOf(
                            line.maxOf { it.height },
                            if (!shouldAppendNewLine) primaryActionHeight else 0,
                        )

                        line.forEach { p ->
                            // center vertically
                            p.placeRelative(currentX, currentY + (lineMaxHeight - p.height) / 2)
                            currentX += p.width
                        }

                        currentX = 0
                        currentY += lineMaxHeight
                        if (i == textPlaceables.lastIndex) {
                            lastLineMaxHeight = lineMaxHeight
                        }
                    }
                    val adjustedYOffset = if (shouldAppendNewLine) 0 else {
                        -(lastLineMaxHeight + primaryActionHeight) / 2
                    }
                    primaryActionPlaceable?.placeRelative(
                        x = containerWidth - primaryActionWidth,
                        y = currentY + adjustedYOffset,
                    )
                }
            },
            content = {
                ActionButton(
                    imageVector = Icons.Outlined.SentimentSatisfied,
                    contentDescription = addEmojiText,
                    onClick = onClickEmoji,
                    modifier = Modifier.size(size),
                    enabled = actionEnabled,
                )
                ActionButton(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = uploadImageText,
                    onClick = onClickUploadImage,
                    modifier = Modifier.size(size),
                    enabled = actionEnabled,
                )

                Row(modifier = Modifier.layoutId(ActionRowPrimaryAction)) {
                    TextButton(
                        onClick = onPreview,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Text(text = if (previewing) editText else previewText)
                    }
                    OutlinedButton(
                        onClick = onSend,
                        enabled = !sending && !uploadingImage && sendTarget != null,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        // 发送中显示进度圈. 文字始终参与布局, 按钮宽度不随状态变化
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = sendText,
                                modifier = Modifier.alpha(0f).clearAndSetSemantics {},
                                textAlign = TextAlign.Center,
                            )
                            Crossfade(targetState = sending) {
                                if (!it) {
                                    Text(text = sendText, textAlign = TextAlign.Center)
                                } else {
                                    CircularProgressIndicator(
                                        Modifier.size(20.dp),
                                        color = LocalContentColor.current,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    @Composable
    fun StickerSelector(
        list: List<EditCommentSticker>,
        onClickItem: (id: Int) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val previewing = LocalIsPreviewing.current
        val scrollState = rememberScrollState()

        FlowRow(
            modifier = modifier.verticalScroll(scrollState),
            horizontalArrangement = Arrangement.Start,
        ) {
            list.forEach { sticker ->
                IconButton(onClick = { onClickItem(sticker.id) }) {
                    if (previewing || sticker.drawableRes == null) {
                        Icon(
                            Icons.Outlined.SentimentSatisfied,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Image(
                            painterResource(sticker.drawableRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
