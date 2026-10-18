/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.substring
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_image_chip
import org.jetbrains.compose.resources.stringResource

/**
 * 图片标签的状态, 决定标签的颜色和图标.
 */
internal enum class CommentImageChipStatus {
    /**
     * 已有图片地址.
     */
    Ready,

    /**
     * 待上传的图片正在上传或等待上传.
     */
    Uploading,

    /**
     * 待上传的图片已上传, 服务器正在处理.
     */
    Processing,

    /**
     * 待上传的图片上传失败, 或占位标签不属于本次编辑 (例如粘贴进来的).
     */
    Failed,
}

/**
 * 图片标签的颜色: 背景 [containerColor] 由 [drawImageChipBackgrounds] 绘制在文字下方, 文字和图标的颜色为 [contentColor].
 */
@Immutable
internal data class CommentImageChipColors(
    val containerColor: Color,
    val contentColor: Color,
)

/**
 * 每种状态的图片标签的颜色.
 */
@Immutable
internal data class CommentImageChipStyle(
    val ready: CommentImageChipColors,
    val uploading: CommentImageChipColors,
    val processing: CommentImageChipColors,
    val failed: CommentImageChipColors,
) {
    fun colors(status: CommentImageChipStatus): CommentImageChipColors = when (status) {
        CommentImageChipStatus.Ready -> ready
        CommentImageChipStatus.Uploading -> uploading
        CommentImageChipStatus.Processing -> processing
        CommentImageChipStatus.Failed -> failed
    }

    companion object {
        /**
         * 标签是编辑框底色上的一层淡淡的色调, 文字和图标用对应的强调色: 可用的图片用主色, 失败的用错误色, 还不能用的用中性色.
         * 色调预先叠加到编辑框的底色 (`surface`) 上得到不透明的颜色, 缩略图上的序号用同一组颜色.
         */
        @Composable
        fun default(): CommentImageChipStyle {
            val colorScheme = MaterialTheme.colorScheme
            return remember(colorScheme) {
                val surface = colorScheme.surface
                fun tint(color: Color, alpha: Float) = color.copy(alpha = alpha).compositeOver(surface)
                // 正在上传和正在处理对用户来说都是 "还不能用", 标签颜色相同; 缩略图上的进度指示区分两者
                val pending = CommentImageChipColors(
                    containerColor = tint(colorScheme.onSurface, 0.08f),
                    contentColor = colorScheme.onSurfaceVariant,
                )
                CommentImageChipStyle(
                    ready = CommentImageChipColors(
                        containerColor = tint(colorScheme.primary, 0.12f),
                        contentColor = colorScheme.primary,
                    ),
                    uploading = pending,
                    processing = pending,
                    failed = CommentImageChipColors(
                        containerColor = tint(colorScheme.error, 0.12f),
                        contentColor = colorScheme.error,
                    ),
                )
            }
        }
    }
}

/**
 * 图片标签开头的图标, 画在标签文字为它留出的空位中 (见 [ImageChipIconRange]).
 */
@Immutable
internal class CommentImageChipIcons(
    private val image: Painter,
    private val brokenImage: Painter,
) {
    fun of(status: CommentImageChipStatus): Painter =
        if (status == CommentImageChipStatus.Failed) brokenImage else image

    companion object {
        @Composable
        fun default(): CommentImageChipIcons {
            val image = rememberVectorPainter(Icons.Outlined.Image)
            val brokenImage = rememberVectorPainter(Icons.Outlined.BrokenImage)
            return remember(image, brokenImage) { CommentImageChipIcons(image, brokenImage) }
        }
    }
}

/**
 * 图片标签的文字, 例如序号为 1 时为 "图片 1".
 */
@Composable
internal fun rememberImageChipLabel(): (number: Int) -> String {
    val template = stringResource(Lang.comment_image_chip, ImageChipNumberPlaceholder)
    return remember(template) {
        { number -> template.replace(ImageChipNumberPlaceholder, number.toString()) }
    }
}

private const val ImageChipNumberPlaceholder = ""

/**
 * 标签文字的样式: 比正文小, 中等字重, 让标签像一个嵌在文字中的小标签而不是一段高亮的文字. 颜色按状态另加.
 */
internal val ImageChipLabelSpanStyle = SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)

/**
 * 把评论文本中的每个图片标签显示为 "图片 N" 标签 (见 [encodeImageChipLabel]), 文字以 [ImageChipLabelSpanStyle] 和 [style] 中对应状态的颜色显示.
 * 标签的背景和图标由 [drawImageChipBackgrounds] 绘制. 标签中不可见字符的字间距为零 (见 [imageChipHiddenRanges]), 所以标签与只有可见文字时一样宽.
 *
 * Compose 不会把光标放进替换后的文字中间, 选区和删除也以整个标签为单位, 见 [CommentImageChipInputTransformation].
 * 点击标签时光标放在哪一端见 [imageChipTapCursor].
 *
 * @param status 图片标签的状态. 可以读取快照状态, 状态变化时重新显示.
 */
internal class CommentImageChipOutputTransformation(
    private val registry: ImageChipRegistry,
    private val label: (number: Int) -> String,
    private val status: (CommentImageChip) -> CommentImageChipStatus,
    private val style: CommentImageChipStyle,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val display = buildImageChipDisplay(asCharSequence(), registry, label)
        if (display.chips.isEmpty()) return
        // 从后往前替换, 前面的标签在文本中的位置不变
        for (index in display.chips.indices.reversed()) {
            val chip = display.chips[index]
            replace(chip.range.start, chip.range.end, display.labels[index])
        }
        for ((index, chip) in display.chips.withIndex()) {
            val visual = display.visualRanges[index]
            addStyle(
                ImageChipLabelSpanStyle.copy(color = style.colors(status(chip)).contentColor),
                visual.start,
                visual.end,
            )
            for (hidden in imageChipHiddenRanges(display.labels[index])) {
                addStyle(ImageChipHiddenCharStyle, visual.start + hidden.start, visual.start + hidden.end)
            }
        }
    }
}

/**
 * 图片标签中不可见字符的样式, 见 [imageChipHiddenRanges].
 */
internal val ImageChipHiddenCharStyle = SpanStyle(letterSpacing = 0.sp)

/**
 * 在编辑框的文字下方绘制图片标签的背景和图标. 在包裹编辑框内部文字区域 (可滚动的视口) 的布局中调用, 超出视口的部分不绘制.
 *
 * 背景是一个两端全圆的胶囊, 比文字行矮一些, 按标签中可见的字符 (文字和不换行空格) 在每一行占据的范围绘制, 见 [lineBoundsOf].
 * 标签被迫换行时 (编辑框比标签还窄) 每行分别绘制. 图标画在标签开头的空位 ([ImageChipIconRange]) 中央, 空位被换行拆开时不画.
 * [layout] 与 [display] 不一致时 (文字布局还没有更新) 跳过不一致的标签.
 *
 * @param layout 编辑框的文字布局, 以显示的文字为准.
 * @param scrollOffset 编辑框的纵向滚动值. 文字向上偏移这个值显示.
 */
internal fun DrawScope.drawImageChipBackgrounds(
    layout: TextLayoutResult,
    display: ImageChipDisplay,
    scrollOffset: Int,
    style: CommentImageChipStyle,
    icons: CommentImageChipIcons,
    status: (CommentImageChip) -> CommentImageChipStatus,
) {
    if (display.chips.isEmpty()) return
    val layoutText = layout.layoutInput.text
    val horizontalInset = ImageChipBackgroundHorizontalInset.toPx()
    val verticalInset = ImageChipBackgroundVerticalInset.toPx()
    val iconPadding = ImageChipIconPadding.toPx()
    clipRect {
        for ((index, chip) in display.chips.withIndex()) {
            val visual = display.visualRanges[index]
            if (visual.end > layoutText.length || layoutText.substring(visual) != display.labels[index]) continue
            val chipStatus = status(chip)
            val colors = style.colors(chipStatus)
            val lines = layout.lineBoundsOf(visual.start, visual.end) { !layoutText[it].isImageChipHiddenChar() }
            for (bounds in lines) {
                val height = (bounds.height - 2 * verticalInset).coerceAtLeast(0f)
                drawRoundRect(
                    color = colors.containerColor,
                    topLeft = Offset(bounds.left + horizontalInset, bounds.top + verticalInset - scrollOffset),
                    size = Size((bounds.width - 2 * horizontalInset).coerceAtLeast(0f), height),
                    cornerRadius = CornerRadius(height / 2),
                )
            }

            val slot = layout.lineBoundsOf(
                visual.start + ImageChipIconRange.start,
                visual.start + ImageChipIconRange.end,
            ).singleOrNull() ?: continue
            val iconSize = minOf(slot.width, slot.height - 2 * (verticalInset + iconPadding))
            if (iconSize <= 0f) continue
            translate(slot.center.x - iconSize / 2, slot.center.y - iconSize / 2 - scrollOffset) {
                with(icons.of(chipStatus)) {
                    draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(colors.contentColor))
                }
            }
        }
    }
}

/**
 * 背景比标签文字左右各窄一点, 相邻的两个标签之间留出空隙.
 */
private val ImageChipBackgroundHorizontalInset = 1.dp

/**
 * 背景比文字行上下各矮一点, 上下两行的标签之间留出空隙.
 */
private val ImageChipBackgroundVerticalInset = 1.5.dp

/**
 * 图标与背景上下边缘的距离.
 */
private val ImageChipIconPadding = 2.5.dp
