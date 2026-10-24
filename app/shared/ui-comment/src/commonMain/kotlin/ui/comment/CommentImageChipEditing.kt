/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.substring

/**
 * 让用户的编辑把图片标签当作一个整体: 编辑要么保留整个标签, 要么删除整个标签. 规则见 [guardImageChipChange].
 *
 * 编辑框中的手势、方向键和退格键都在显示的文字上操作, Compose 不会把光标放进替换后的文字中间,
 * 删除标签显示文字中的一个字符时也会删除整个标签. 这里处理其余的情况:
 * - 安卓输入法提交文字时, Compose 去掉新旧文字相同的开头和结尾 (`imeReplace`), 记录的修改可能只是标签的一部分;
 * - 无障碍服务或测试通过语义设置的选区 (以文本为准) 可能落在标签内部;
 * - 粘贴或输入法提交的文字中混入了标签显示的文字 ([decodeLabels] 把它们还原为图片标签);
 * - 输入法把标签的隐藏字符当作单词的一部分, 组合文字从标签内部开始或结束, 提交的文字中只有标签显示文字的一部分.
 *
 * 程序化的修改 (`TextFieldState.edit`) 不经过这里, 由 [CommentEditorState] 自己保证不拆开标签.
 *
 * 应排在 [CommentPasteLinkifyTransformation] 之前: 识别链接时看到的应当是还原后的 BBCode.
 * 标签显示文字中的隐藏字符不是网址的结束符, 先识别链接的话, 紧贴在网址后的标签文字会被并入网址; 还原为 `[img]` 标签后,
 * 网址在标签前结束, 图片标签中的地址也不会被包上 `[url]`.
 */
internal class CommentImageChipInputTransformation(
    private val decodeLabels: (String) -> String,
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val changeCount = changes.changeCount
        if (changeCount > 0) {
            // 多处修改时合并为一处: 修改之间的文字没有变化, 合并后的范围在新旧文本中仍然对应
            var originalStart = Int.MAX_VALUE
            var originalEnd = Int.MIN_VALUE
            var start = Int.MAX_VALUE
            var end = Int.MIN_VALUE
            for (i in 0 until changeCount) {
                val originalRange = changes.getOriginalRange(i)
                val range = changes.getRange(i)
                originalStart = minOf(originalStart, originalRange.min)
                originalEnd = maxOf(originalEnd, originalRange.max)
                start = minOf(start, range.min)
                end = maxOf(end, range.max)
            }
            val replacement = guardImageChipChange(
                originalText = originalText,
                originalSelection = originalSelection,
                originalRange = TextRange(originalStart, originalEnd),
                text = asCharSequence(),
                range = TextRange(start, end),
                widenToSelection = changeCount == 1,
                decodeLabels = decodeLabels,
            )
            if (replacement != null) {
                replace(replacement.range.min, replacement.range.max, replacement.replacement)
                selection = TextRange(replacement.range.min + replacement.cursor)
            }
        }
        val snapped = snapSelectionToImageChips(asCharSequence(), selection)
        if (snapped != selection) selection = snapped
    }
}

/**
 * 计算一处修改为了保持图片标签完整应该变成什么. 修改把 [originalText] 中 [originalRange] 的文字替换为了 [text] 中 [range] 的文字.
 *
 * - [widenToSelection] 为 `true`、选区 [originalSelection] 不为空且修改位于选区内时, 按整个选区被替换处理, 原因见 [linkifyChange].
 *   例如选中一个图片标签后从输入法剪贴板提交另一个图片标签, 记录的修改只是两个地址不同的部分, 但实际是替换了整个标签.
 * - 插入的文字中的标签显示文字用 [decodeLabels] 还原. 输入法的组合文字从标签内部开始或结束时, Compose 把组合范围扩展到整个标签,
 *   提交的文字中却只有标签显示文字的一部分 (见 [imageChipLabelTailLength] 和 [imageChipLabelHeadStart]):
 *   - 开头的残片还原为修改范围开头的图片标签, 结尾的残片还原为修改范围结尾的图片标签;
 *   - 修改覆盖了整个图片标签, 插入的文字中有标签的隐藏字符, 还原后却没有这个标签时, 保留这个标签.
 *   输入法只是在复述它读到的显示文字, 用户并没有删除图片.
 * - 修改的一端位于某个标签内部时:
 *   - 纯插入 (没有删除文字): 插入点移到该标签之后, 标签保持不变;
 *   - 只删除了标签的一部分: 删除整个标签;
 *   - 替换了标签的一部分: 删除整个标签, 只保留插入的文字. 但插入的文字与标签剩余的部分仍组成完整的图片标签时保留它们,
 *     这是输入法去掉相同开头和结尾后的样子.
 *
 * @return 对 [text] 的替换. 不需要调整时为 `null`.
 */
internal fun guardImageChipChange(
    originalText: CharSequence,
    originalSelection: TextRange,
    originalRange: TextRange,
    text: CharSequence,
    range: TextRange,
    widenToSelection: Boolean,
    decodeLabels: (String) -> String,
): TextReplacement? {
    var originalStart = originalRange.min
    var originalEnd = originalRange.max
    var start = range.min
    var end = range.max
    if (widenToSelection && !originalSelection.collapsed && originalRange in originalSelection) {
        val lengthDelta = range.length - originalRange.length
        originalStart = originalSelection.min
        originalEnd = originalSelection.max
        start = originalStart
        end = originalEnd + lengthDelta
    }

    val inserted = text.substring(start, end)
    val chips = parseImageChips(originalText)
    val startChip = chips.firstOrNull { it.contains(originalStart) }
    val endChip = chips.firstOrNull { it.contains(originalEnd) }

    if (startChip == null && endChip == null) {
        val decoded = decodeImageChipFragments(inserted, chips, originalStart, originalEnd, decodeLabels)
        if (decoded.replacement == inserted) return null
        return TextReplacement(TextRange(start, end), decoded.replacement, decoded.cursor)
    }

    val decoded = decodeLabels(inserted)
    if (originalStart == originalEnd) {
        // 纯插入, 两端在同一个标签内. 在新文本中这个标签被插入的文字分成两段
        val chip = checkNotNull(startChip)
        return TextReplacement(
            TextRange(chip.range.start, chip.range.end + inserted.length),
            chip.source + decoded,
        )
    }

    val widenedStart = startChip?.range?.start ?: originalStart
    val widenedEnd = endChip?.range?.end ?: originalEnd
    // 两端所在标签在修改范围外的部分. 在新文本中它们分别紧贴在插入的文字两侧
    val prefix = originalText.substring(widenedStart, originalStart)
    val suffix = originalText.substring(originalEnd, widenedEnd)
    val result = if (decoded.isEmpty()) {
        ""
    } else {
        val candidate = prefix + decoded + suffix
        val candidateChips = parseImageChips(candidate)
        val keepPrefix = prefix.isNotEmpty() && candidateChips.any { it.range.start == 0 }
        val keepSuffix = suffix.isNotEmpty() && candidateChips.any { it.range.end == candidate.length }
        (if (keepPrefix) prefix else "") + decoded + (if (keepSuffix) suffix else "")
    }
    val replaceRange = TextRange(widenedStart, end + suffix.length)
    if (text.substring(replaceRange) == result) return null
    return TextReplacement(replaceRange, result)
}

/**
 * 还原覆盖了 [originalStart] 到 [originalEnd] 的修改所插入的文字 [inserted], 包括其中不完整的标签显示文字. 规则见 [guardImageChipChange].
 * 光标位于用户输入的文字之后, 在还原到结尾一侧的标签之前.
 *
 * @param chips 修改前的文本中的图片标签.
 * @return 还原后的文字. [TextReplacement.range] 无意义.
 */
private fun decodeImageChipFragments(
    inserted: String,
    chips: List<CommentImageChip>,
    originalStart: Int,
    originalEnd: Int,
    decodeLabels: (String) -> String,
): TextReplacement {
    val covered = chips.filter { it.range.start >= originalStart && it.range.end <= originalEnd }
    if (covered.isEmpty() || !containsImageChipLabelChars(inserted)) {
        return TextReplacement(TextRange.Zero, decodeLabels(inserted))
    }

    var middle = inserted
    var head = ""
    var tail = ""
    val leadingChip = covered.first().takeIf { it.range.start == originalStart }
    val trailingChip = covered.last().takeIf { it.range.end == originalEnd }
    if (leadingChip != null) {
        val length = imageChipLabelTailLength(middle)
        if (length > 0) {
            head = leadingChip.source
            middle = middle.substring(length)
        }
    }
    if (trailingChip != null && (trailingChip !== leadingChip || head.isEmpty())) {
        val start = imageChipLabelHeadStart(middle)
        if (start >= 0) {
            tail = trailingChip.source
            middle = middle.substring(0, start)
        }
    }
    val decoded = decodeLabels(head + middle + tail)
    var cursor = decoded.length - (if (tail.isEmpty()) 0 else decodeLabels(tail).length)

    // 其余没有还原出来的标签保留在原来的一侧
    val kept = StringBuilder()
    val restored = StringBuilder(decoded)
    var searchFrom = 0
    for (chip in covered) {
        // 与插入的文字一样还原, 例如已上传成功的占位标签替换为图片
        val source = decodeLabels(chip.source)
        val found = restored.indexOf(source, searchFrom)
        if (found >= 0) {
            searchFrom = found + source.length
        } else if (chip.range.start == originalStart) {
            kept.append(source)
            cursor += source.length
        } else {
            restored.append(source)
            searchFrom = restored.length
        }
    }
    return TextReplacement(TextRange.Zero, kept.append(restored).toString(), cursor)
}

/**
 * 把位于图片标签内部的选区端点移出标签: 光标移到较近的一端 (距离相同时移到之后), 选区向外扩展到整个标签, 方向不变.
 */
internal fun snapSelectionToImageChips(text: CharSequence, selection: TextRange): TextRange {
    val chips = parseImageChips(text)
    if (chips.isEmpty()) return selection
    if (selection.collapsed) {
        val chip = chips.firstOrNull { it.contains(selection.start) } ?: return selection
        val toStart = selection.start - chip.range.start
        val toEnd = chip.range.end - selection.start
        return TextRange(if (toStart < toEnd) chip.range.start else chip.range.end)
    }
    val min = chips.firstOrNull { it.contains(selection.min) }?.range?.start ?: selection.min
    val max = chips.firstOrNull { it.contains(selection.max) }?.range?.end ?: selection.max
    return if (selection.reversed) TextRange(max, min) else TextRange(min, max)
}

/**
 * 选中的文字. 选区端点位于图片标签内部时包含整个标签.
 */
internal fun CommentText.selectedText(): String = text.substring(snapSelectionToImageChips(text, selection))

/**
 * 删除选中的文字, 光标位于删除处. 选区端点位于图片标签内部时删除整个标签.
 */
internal fun CommentText.deleteSelection(): CommentText {
    val range = snapSelectionToImageChips(text, selection)
    return CommentText(text.substring(0, range.min) + text.substring(range.max), TextRange(range.min))
}

/**
 * 用粘贴的文字 [pasted] 替换选区, 光标移到插入的文字之后. 效果与在编辑框中粘贴相同:
 * 先用 [decodeLabels] 还原其中的标签显示文字, 再按 [linkifyInsertion] 识别网址; 选区端点位于图片标签内部时替换整个标签.
 */
internal fun CommentText.paste(pasted: String, decodeLabels: (String) -> String): CommentText {
    val range = snapSelectionToImageChips(text, selection)
    val textBefore = text.substring(0, range.min)
    val decoded = decodeLabels(pasted)
    val inserted = linkifyInsertion(
        textBefore = textBefore,
        inserted = decoded,
        replaced = text.substring(range),
        replacedSelection = !range.collapsed,
    ) ?: decoded
    return CommentText(textBefore + inserted + text.substring(range.max), TextRange(range.min + inserted.length))
}
