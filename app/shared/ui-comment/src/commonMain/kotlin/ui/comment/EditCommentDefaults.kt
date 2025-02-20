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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.theme.looming
import me.him188.ani.app.ui.richtext.RichText
import org.jetbrains.compose.resources.painterResource

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
        Text(text = "发送评论", modifier, softWrap = false)
    }

    @Composable
    fun CommentTextField(
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        enabled: Boolean = true,
        maxLines: Int = Int.MAX_VALUE,
        modifier: Modifier = Modifier,
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
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            textStyle = MaterialTheme.typography.bodyMedium.merge(
                fontSize = 15.5.sp,
                color = LocalContentColor.current.looming(),
            ),
            shape = shape,
            enabled = enabled,
            maxLines = maxLines,
            placeholder = placeholder,
            colors = colors,
            interactionSource = interactionSource,
        )
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
                            text = "渲染中...",
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
        hasIndication: Boolean = true
    ) {
        val icon = @Composable {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
        if (hasIndication) IconButton(
            modifier = modifier,
            enabled = enabled,
            onClick = onClick,
            content = icon,
        ) else IconButton(
            modifier = modifier,
            enabled = enabled,
            onClick = onClick,
            content = icon,
            indication = null,
        )
    }

    @Composable
    fun ActionRow(
        sendTarget: 
        CommentContext?,
        onClickBold: () -> Unit,
        onClickItalic: () -> Unit,
        onClickUnderlined: () -> Unit,
        onClickStrikethrough: () -> Unit,
        onClickMask: () -> Unit,
        onClickImage: () -> Unit,
        onClickUrl: () -> Unit,
        onClickEmoji: () -> Unit,
        onSend: () -> Unit,
        onPreview: () -> Unit,
        modifier: Modifier = Modifier,
        previewing: Boolean = false,
        sending: Boolean = false,
    ) {
        // maybe we can extract the layout
        val size = ActionButtonSize.dp
        var actionRowExpanded by rememberSaveable { mutableStateOf(false) }
        val expandableActionWidth by animateDpAsState(if (actionRowExpanded) size else 0.dp)

        val actionEnabled by derivedStateOf { !sending && !previewing }

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
                    contentDescription = "添加表情",
                    onClick = onClickEmoji,
                    modifier = Modifier.size(size),
                    enabled = actionEnabled,
                )
                if (actionRowExpanded) {
                    ActionButton(
                        imageVector = Icons.Outlined.FormatBold,
                        contentDescription = "加粗",
                        onClick = onClickBold,
                        modifier = Modifier.size(height = size, width = expandableActionWidth),
                        enabled = actionEnabled,
                    )
                    ActionButton(
                        imageVector = Icons.Outlined.FormatItalic,
                        contentDescription = "斜体",
                        onClick = onClickItalic,
                        modifier = Modifier.size(height = size, width = expandableActionWidth),
                        enabled = actionEnabled,
                    )
                    ActionButton(
                        imageVector = Icons.Outlined.FormatUnderlined,
                        contentDescription = "下划线",
                        onClick = onClickUnderlined,
                        modifier = Modifier.size(height = size, width = expandableActionWidth),
                        enabled = actionEnabled,
                    )
                    ActionButton(
                        imageVector = Icons.Outlined.FormatStrikethrough,
                        contentDescription = "删除线",
                        onClick = onClickStrikethrough,
                        modifier = Modifier.size(height = size, width = expandableActionWidth),
                        enabled = actionEnabled,
                    )
                }
                ActionButton(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = "遮罩",
                    onClick = onClickMask,
                    modifier = Modifier.size(size),
                    enabled = actionEnabled,
                )
                ActionButton(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "图片",
                    onClick = onClickImage,
                    modifier = Modifier.size(size),
                    enabled = actionEnabled,
                )
                if (actionRowExpanded) {
                    ActionButton(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = "链接",
                        onClick = onClickUrl,
                        modifier = Modifier.size(height = size, width = expandableActionWidth),
                        enabled = actionEnabled,
                    )
                }
                // 最后一个按钮不要有 ripple effect，因为有动画，看起来比较奇怪
                ActionButton(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "更多评论编辑功能",
                    enabled = true,
                    onClick = { actionRowExpanded = true },
                    modifier = Modifier.size(height = size, width = size - expandableActionWidth),
                    hasIndication = false,
                )

                Row(modifier = Modifier.layoutId(ActionRowPrimaryAction)) {
                    TextButton(
                        onClick = onPreview,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Text(text = if (previewing) "编辑" else "预览")
                    }
                    OutlinedButton(
                        onClick = onSend,
                        enabled = !sending && sendTarget != null,
                        modifier = Modifier.padding(start = 8.dp).animateContentSize(),
                    ) {
                        Crossfade(targetState = sending) {
                            if (!it) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(text = "发送", textAlign = TextAlign.Center)
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        modifier = Modifier.size(24.dp),
                                        contentDescription = null,
                                    )
                                }
                            } else {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    color = LocalContentColor.current,
                                )
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