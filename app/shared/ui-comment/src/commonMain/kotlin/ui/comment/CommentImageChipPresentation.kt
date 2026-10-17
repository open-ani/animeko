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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * 图片标签的状态, 决定标签的颜色.
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
 * 图片标签的颜色: 背景 [containerColor] 由 [drawImageChipBackgrounds] 绘制在文字下方, 文字颜色为 [contentColor].
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
        @Composable
        fun default(): CommentImageChipStyle {
            val colorScheme = MaterialTheme.colorScheme
            return remember(colorScheme) {
                // 正在上传和正在处理对用户来说都是 "还不能用", 标签颜色相同; 缩略图上的进度指示区分两者
                val pending = CommentImageChipColors(
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                CommentImageChipStyle(
                    ready = CommentImageChipColors(
                        containerColor = colorScheme.secondaryContainer,
                        contentColor = colorScheme.onSecondaryContainer,
                    ),
                    uploading = pending,
                    processing = pending,
                    failed = CommentImageChipColors(
                        containerColor = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer,
                    ),
                )
            }
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

private const val ImageChipNumberPlaceholder = "\uE000"

/**
 * 把评论文本中的每个图片标签显示为 "图片 N" 标签 (见 [encodeImageChipLabel]), 文字以 [style] 中对应状态的颜色加粗显示.
 * 标签的背景由 [drawImageChipBackgrounds] 绘制. 标签中不可见字符的字间距为零 (见 [imageChipHiddenRanges]), 所以标签与只有可见文字时一样宽.
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
                SpanStyle(color = style.colors(status(chip)).contentColor, fontWeight = FontWeight.Medium),
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
 * 在编辑框的文字下方绘制图片标签的圆角背景. 在包裹编辑框内部文字区域 (可滚动的视口) 的布局中调用, 超出视口的部分不绘制.
 *
 * 背景按标签中可见的字符 (文字和两侧的不换行空格) 在每一行占据的范围绘制, 见 [lineBoundsOf]. 标签被迫换行时 (编辑框比标签还窄)
 * 每行分别绘制. [layout] 与 [display] 不一致时 (文字布局还没有更新) 跳过不一致的标签.
 *
 * @param layout 编辑框的文字布局, 以显示的文字为准.
 * @param scrollOffset 编辑框的纵向滚动值. 文字向上偏移这个值显示.
 */
internal fun DrawScope.drawImageChipBackgrounds(
    layout: TextLayoutResult,
    display: ImageChipDisplay,
    scrollOffset: Int,
    containerColor: (CommentImageChip) -> Color,
) {
    if (display.chips.isEmpty()) return
    val layoutText = layout.layoutInput.text
    val horizontalInset = ImageChipBackgroundHorizontalInset.toPx()
    val verticalInset = ImageChipBackgroundVerticalInset.toPx()
    val cornerRadius = CornerRadius(ImageChipBackgroundCornerRadius.toPx())
    clipRect {
        for ((index, chip) in display.chips.withIndex()) {
            val visual = display.visualRanges[index]
            if (visual.end > layoutText.length || layoutText.substring(visual) != display.labels[index]) continue
            val color = containerColor(chip)
            val lines = layout.lineBoundsOf(visual.start, visual.end) { !layoutText[it].isImageChipHiddenChar() }
            for (bounds in lines) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(bounds.left + horizontalInset, bounds.top + verticalInset - scrollOffset),
                    size = Size(
                        (bounds.width - 2 * horizontalInset).coerceAtLeast(0f),
                        (bounds.height - 2 * verticalInset).coerceAtLeast(0f),
                    ),
                    cornerRadius = cornerRadius,
                )
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
private val ImageChipBackgroundVerticalInset = 1.dp

private val ImageChipBackgroundCornerRadius = 4.dp
