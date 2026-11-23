/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange

/**
 * 给编辑过程中出现的每个图片标签原文 ([CommentImageChip.source]) 分配一个编号, 编号写在标签显示的文字中 (见 [encodeImageChipLabel]).
 *
 * Compose 复制和剪切时把编辑框显示的文字 (而不是 `TextFieldState` 中的文本) 放到剪贴板. 显示的文字中只有 "图片 N",
 * 靠这个编号才能还原出原来的 BBCode, 见 [decodeImageChipLabels].
 *
 * 编号只增不减: 剪切后标签已不在文本中, 但写入剪贴板时仍要能还原. 只在主线程使用.
 */
internal class ImageChipRegistry {
    private val ids = HashMap<String, Int>()
    private val sources = ArrayList<String>()

    fun idOf(source: String): Int = ids.getOrPut(source) {
        sources.add(source)
        sources.lastIndex
    }

    fun sourceOf(id: Int): String? = sources.getOrNull(id)
}

/**
 * 图片标签在编辑框中显示的文字: 开头是为图标留出的空位, 然后是 [label] (例如 "图片 1"), 两端各有 [IMAGE_CHIP_LABEL_PADDING] 个
 * 不换行空格作为标签背景的边距, 末尾隐藏着编号 [id].
 *
 * 图标的空位是 [IMAGE_CHIP_ICON_SLOT] 个不换行空格, 之后再有一个作为图标与文字的间距. 图标由 [drawImageChipBackgrounds]
 * 画在空位占据的范围 ([ImageChipIconRange]) 内. 空位的宽度随字体变化, 图标按空位缩放.
 *
 * 标签内部不会换行: 空格换成不换行空格, 两个字符之间可能换行时 (如中文) 插入 U+2060 WORD JOINER.
 * 标签之前总是可以换行: 最前面是一个 U+200B ZERO WIDTH SPACE, 它之前不能换行, 之后总能换行, 所以换行处落在它与标签之间,
 * 它本身留在上一行的末尾 (宽度为零, 不绘制背景). 否则连续的标签 (例如一次选择多张图片) 之间以及标签与紧贴在它前面的文字之间
 * 没有换行的机会, 一行放不下时只能在某个标签中间强行换行.
 * 它放在开头而不是末尾: 点击自动换行的一行的末尾时, Android 取这一行最后一个字符之前的位置. 放在末尾时这个位置落在标签内部,
 * 光标会被放到较近的标签开头; 放在开头时这个位置正好在两个标签之间. 标签紧挨自动换行处的其他点击见 [imageChipTapCursor].
 *
 * 隐藏的编号由 Unicode 中默认不显示 (Default_Ignorable_Code_Point) 的格式字符组成, 宽度为零, 用户不会输入它们:
 * U+2063 INVISIBLE SEPARATOR 标记显示文字的开头和结尾, 中间以 U+2062 INVISIBLE TIMES (0) 和 U+2064 INVISIBLE PLUS (1)
 * 写出编号的二进制. 这三个字符的换行属性是 AL, 与相邻的字母、数字和不换行空格之间不能换行, 也不参与字形连接.
 * 开头和结尾的标记还让输入法不会把标签的文字与相邻的单词当作一个词.
 */
internal fun encodeImageChipLabel(label: String, id: Int): String = buildString {
    append(ZERO_WIDTH_SPACE)
    append(LABEL_MARK)
    repeat(IMAGE_CHIP_LABEL_PADDING + IMAGE_CHIP_ICON_SLOT + 1) { append(NBSP) }
    val visible = label.replace(' ', NBSP)
    for ((index, char) in visible.withIndex()) {
        if (index > 0) {
            val previous = visible[index - 1]
            if (previous != NBSP && char != NBSP && (previous.code >= 0x80 || char.code >= 0x80)) {
                append(WORD_JOINER)
            }
        }
        append(char)
    }
    repeat(IMAGE_CHIP_LABEL_PADDING) { append(NBSP) }
    for (bit in id.toString(2)) {
        append(if (bit == '1') BIT_ONE else BIT_ZERO)
    }
    append(LABEL_MARK)
}

/**
 * 标签文字两端各有几个不换行空格作为背景的边距.
 */
internal const val IMAGE_CHIP_LABEL_PADDING = 2

/**
 * 标签文字开头的边距之后为图标留出的不换行空格数.
 */
internal const val IMAGE_CHIP_ICON_SLOT = 4

/**
 * [encodeImageChipLabel] 生成的标签文字中图标空位的范围, 相对于标签文字的开头 (ZERO WIDTH SPACE 和开头标记之后).
 */
internal val ImageChipIconRange: TextRange =
    TextRange(2 + IMAGE_CHIP_LABEL_PADDING, 2 + IMAGE_CHIP_LABEL_PADDING + IMAGE_CHIP_ICON_SLOT)

/**
 * [encodeImageChipLabel] 生成的标签文字中连续的不可见字符的范围.
 *
 * 这些字符本身宽度为零, 但文字样式中的字间距 (`letterSpacing`) 仍会加在每个字符上, 显示时要对这些范围把字间距设为零.
 */
internal fun imageChipHiddenRanges(label: String): List<TextRange> = buildList {
    var start = -1
    for ((index, char) in label.withIndex()) {
        val hidden = char.isImageChipHiddenChar()
        if (hidden && start < 0) start = index
        if (!hidden && start >= 0) {
            add(TextRange(start, index))
            start = -1
        }
    }
    if (start >= 0) add(TextRange(start, label.length))
}

/**
 * 是否是 [encodeImageChipLabel] 在标签文字中加入的不可见字符.
 */
internal fun Char.isImageChipHiddenChar(): Boolean =
    this == LABEL_MARK || this == BIT_ZERO || this == BIT_ONE || this == WORD_JOINER || this == ZERO_WIDTH_SPACE

/**
 * 把 [text] 中 [encodeImageChipLabel] 生成的标签文字还原为图片标签原文 ([sourceOf] 根据编号查找).
 * 找不到编号对应的原文时保留标签上可见的文字 (如 "图片 1").
 *
 * 不完整的标签文字 (见 [imageChipLabelTailLength]) 中的隐藏字符, 连同紧挨着它们的不换行空格和标签中的 U+2060 WORD JOINER 都去掉,
 * 标签的隐藏字符不会进入评论的文本. 不含隐藏标记时原样返回.
 *
 * 用于复制和剪切时写入剪贴板的文字, 以及粘贴或输入法提交的文字中混入的标签文字.
 */
internal fun decodeImageChipLabels(text: String, sourceOf: (id: Int) -> String?): String {
    if (text.none { it == LABEL_MARK || it == BIT_ZERO || it == BIT_ONE }) return text
    val decoded = EncodedLabelPattern.replace(text) { match ->
        val bits = match.groupValues[2]
        val id = if (bits.length <= MAX_ID_BITS) {
            bits.fold(0) { acc, bit -> acc * 2 + (if (bit == BIT_ONE) 1 else 0) }
        } else {
            -1
        }
        sourceOf(id) ?: match.groupValues[1].replace(WORD_JOINER.toString(), "").replace(NBSP, ' ').trim()
    }
    return StrayLabelCharsPattern.replace(decoded, "").filterNot { it == WORD_JOINER }
}

/**
 * 输入法提交的文字 [text] 是否以某个标签的显示文字被截掉开头之后剩下的部分开始: 编号和结尾标记之前没有开头标记.
 *
 * 输入法把隐藏字符当作单词的一部分时, 在标签之后输入, 它的组合文字可能从标签的隐藏字符开始. Compose 把组合范围扩展到整个标签,
 * 输入法提交的文字却只有标签的末尾.
 *
 * @return 这部分的长度, 不是时为 0.
 */
internal fun imageChipLabelTailLength(text: CharSequence): Int =
    LabelTailPattern.find(text)?.value?.length ?: 0

/**
 * 输入法提交的文字 [text] 是否以某个标签的显示文字被截掉结尾之后剩下的部分结束: 最后一个开头标记之后没有编号和结尾标记.
 * 与 [imageChipLabelTailLength] 相对, 发生在标签之前输入时.
 *
 * @return 这部分 (包括开头标记之前的 ZERO WIDTH SPACE) 在 [text] 中的起点, 不是时为 `-1`.
 */
internal fun imageChipLabelHeadStart(text: CharSequence): Int {
    val mark = text.lastIndexOf(LABEL_MARK)
    if (mark < 0) return -1
    // 结尾标记紧跟在编号之后; 开头标记之后是不换行空格
    if (mark > 0 && (text[mark - 1] == BIT_ZERO || text[mark - 1] == BIT_ONE)) return -1
    if (mark + 1 < text.length && text[mark + 1] != NBSP) return -1
    for (index in mark + 1 until text.length) {
        if (text[index] == BIT_ZERO || text[index] == BIT_ONE) return -1
    }
    return if (mark > 0 && text[mark - 1] == ZERO_WIDTH_SPACE) mark - 1 else mark
}

/**
 * [text] 中是否有 [encodeImageChipLabel] 加入的隐藏字符, 即是否混入了 (可能不完整的) 标签文字.
 */
internal fun containsImageChipLabelChars(text: CharSequence): Boolean =
    text.any { it == LABEL_MARK || it == BIT_ZERO || it == BIT_ONE || it == WORD_JOINER }

private const val LABEL_MARK = '\u2063'
private const val BIT_ZERO = '\u2062'
private const val BIT_ONE = '\u2064'
private const val WORD_JOINER = '\u2060'
private const val ZERO_WIDTH_SPACE = '\u200B'
private const val NBSP = '\u00A0'
private const val MAX_ID_BITS = 30

private val EncodedLabelPattern = Regex("\u200B?\u2063([^\u2063]*?)([\u2062\u2064]+)\u2063")

/**
 * 不完整的标签文字中剩下的隐藏字符, 以及紧挨着它们的 ZERO WIDTH SPACE 和不换行空格 (标签背景的边距).
 */
private val StrayLabelCharsPattern = Regex("\u00A0*\u200B?[\u2062\u2063\u2064]+\u00A0*")

private val LabelTailPattern = Regex("^[^\u2063]*?[\u2062\u2064]+\u2063")

/**
 * 评论文本中的图片标签替换为显示的文字 ([labels]) 之后的样子, 以及文本与显示的文字之间的位置换算.
 *
 * 编辑框的文字布局 (`TextLayoutResult`) 以显示的文字为准, 而 `TextFieldState.selection` 是文本中的位置.
 * 把文本中的位置传给文字布局之前 (例如计算选区工具栏的位置、绘制标签背景) 要先用 [toVisual] 换算.
 * [CommentImageChipOutputTransformation] 做的替换与这里一致, 所以换算结果与 Compose 的一致.
 *
 * @property chips 文本中的图片标签, 见 [parseImageChips].
 * @property labels 每个图片标签显示的文字.
 */
@Immutable
internal class ImageChipDisplay(
    val chips: List<CommentImageChip>,
    val labels: List<String>,
) {
    /**
     * 每个图片标签显示的文字在显示文本中的范围.
     */
    val visualRanges: List<TextRange> = buildList(chips.size) {
        var delta = 0
        for ((index, chip) in chips.withIndex()) {
            val start = chip.range.start + delta
            add(TextRange(start, start + labels[index].length))
            delta += labels[index].length - chip.range.length
        }
    }

    /**
     * 显示文本比原文本长出的字符数.
     */
    val lengthDelta: Int = chips.indices.sumOf { labels[it].length - chips[it].range.length }

    /**
     * 文本中的位置 [offset] 在显示文本中的位置. 位于标签内部时取标签的起点.
     */
    fun toVisual(offset: Int): Int = toVisual(offset, roundUp = false)

    /**
     * 文本中的范围 [range] 在显示文本中的范围. 端点位于标签内部时向外扩展到整个标签, 方向不变.
     */
    fun toVisual(range: TextRange): TextRange {
        if (range.collapsed) return TextRange(toVisual(range.start))
        val min = toVisual(range.min, roundUp = false)
        val max = toVisual(range.max, roundUp = true)
        return if (range.reversed) TextRange(max, min) else TextRange(min, max)
    }

    /**
     * 显示文本中的位置 [visualOffset] 对应的文本范围. 位于标签显示的文字内部时为整个标签, 否则为一个位置.
     */
    fun toUnderlying(visualOffset: Int): TextRange {
        var delta = 0
        for ((index, chip) in chips.withIndex()) {
            val visual = visualRanges[index]
            when {
                visualOffset <= visual.start -> return TextRange(visualOffset - delta)
                visualOffset < visual.end -> return chip.range
            }
            delta += labels[index].length - chip.range.length
        }
        return TextRange(visualOffset - delta)
    }

    private fun toVisual(offset: Int, roundUp: Boolean): Int {
        var delta = 0
        for ((index, chip) in chips.withIndex()) {
            when {
                offset <= chip.range.start -> return offset + delta
                offset < chip.range.end -> {
                    val visual = visualRanges[index]
                    return if (roundUp) visual.end else visual.start
                }
            }
            delta += labels[index].length - chip.range.length
        }
        return offset + delta
    }
}

/**
 * 计算 [text] 中的图片标签显示为什么文字.
 *
 * @param label 序号为 N 的图片的标签文字, 例如 "图片 N".
 */
internal fun buildImageChipDisplay(
    text: CharSequence,
    registry: ImageChipRegistry,
    label: (number: Int) -> String,
): ImageChipDisplay {
    val chips = parseImageChips(text)
    return ImageChipDisplay(
        chips,
        chips.map { encodeImageChipLabel(label(it.number), registry.idOf(it.source)) },
    )
}
