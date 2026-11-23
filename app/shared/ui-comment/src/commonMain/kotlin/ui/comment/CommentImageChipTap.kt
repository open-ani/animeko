/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.substring
import androidx.compose.ui.unit.IntSize

/**
 * 点击图片标签时光标的位置, 见 [imageChipTapCursor].
 *
 * @property chip 点击的图片标签.
 * @property atStart 光标在标签之前. 为 `false` 时在标签之后.
 */
@Immutable
internal data class ImageChipTapCursor(
    val chip: CommentImageChip,
    val atStart: Boolean,
) {
    /**
     * 光标在文本中的位置.
     */
    val cursor: Int get() = if (atStart) chip.range.start else chip.range.end

    /**
     * 标签的另一端在文本中的位置.
     */
    val otherEnd: Int get() = if (atStart) chip.range.end else chip.range.start
}

/**
 * 点击编辑框的 [position] 时, 光标应放在被点击的图片标签的哪一端. 不是点击图片标签时为 `null`, 光标的位置由 Compose 决定.
 *
 * Compose (`TextFieldSelectionState.placeCursorAtNearestOffset`) 先由文字布局取得点击处的位置 [proposedOffset],
 * 它落在替换后的标签文字内部时, 把光标放到两端中光标矩形离点击处较近的一端.
 * 自动换行处的光标矩形在下一行的行首, 所以标签紧挨自动换行处时会放错:
 * - 标签是一行最后的内容, 紧跟其后的文字换到了下一行. 点击标签右侧的空白或标签的右半边时, 取到的位置落在标签内部
 *   (Android 取这一行最后一个字符之前的位置), 而标签终点的光标矩形在下一行行首, 光标被放到标签之前;
 * - 一行以标签开头, 标签开头的 ZERO WIDTH SPACE 留在上一行末尾 (见 [encodeImageChipLabel]). 点击这一行的左边距时,
 *   标签起点的光标矩形在上一行末尾, 光标被放到标签之后.
 *
 * 这里以点击的那一行为准:
 * - 标签在这一行有可见的字符时, 点击处在这些字符的水平中点靠标签起点的一侧放在标签之前, 另一侧放在标签之后.
 *   起点在哪一侧由标签第一个和最后一个可见的字符 (两端的不换行空格) 的位置决定: 通常在左侧;
 *   从右向左的段落 (例如界面语言为阿拉伯语) 中标签前后是从右向左的文字时, 两端的不换行空格随段落方向排列, 起点在右侧;
 * - 标签在这一行只有不可见的字符 (例如留在上一行末尾的 ZERO WIDTH SPACE) 时, 这一行在标签可见的部分之前放在标签之前, 之后放在标签之后.
 *
 * [layout] 与 [display] 不一致时 (文字布局还没有更新) 返回 `null`.
 *
 * @param layout 编辑框的文字布局, 以显示的文字为准.
 * @param position 点击处在文字布局中的位置, 见 [CommentTextFieldLayout.textLayoutPosition].
 * @param proposedOffset Compose 根据点击处取得的显示文字中的位置.
 */
internal fun imageChipTapCursor(
    layout: TextLayoutResult,
    position: Offset,
    display: ImageChipDisplay,
    proposedOffset: Int = layout.getOffsetForPosition(position),
): ImageChipTapCursor? {
    val index = display.visualRanges.indexOfFirst { proposedOffset > it.start && proposedOffset < it.end }
    if (index < 0) return null
    val visual = display.visualRanges[index]
    val text = layout.layoutInput.text
    if (visual.end > text.length || text.substring(visual) != display.labels[index]) return null

    val line = layout.getLineForVerticalPosition(position.y)
    val chars = visual.start until visual.end
    val firstVisible = chars.firstOrNull { !text[it].isImageChipHiddenChar() } ?: return null
    val visibleOnLine = chars.filter { !text[it].isImageChipHiddenChar() && layout.getLineForOffset(it) == line }
    val atStart = if (visibleOnLine.isNotEmpty()) {
        val bounds = layout.lineBoundsOf(visual.start, visual.end) { it in visibleOnLine }.single()
        val startOnLeft = layout.getBoundingBox(visibleOnLine.first()).center.x <=
                layout.getBoundingBox(visibleOnLine.last()).center.x
        (position.x < bounds.center.x) == startOnLeft
    } else {
        line < layout.getLineForOffset(firstVisible)
    }
    return ImageChipTapCursor(display.chips[index], atStart)
}

/**
 * 点击图片标签时按 [imageChipTapCursor] 放置光标.
 *
 * 必须用在 `BasicTextField` 的 `modifier` 中. 这样它在文本框处理手势的节点之外, 在 [PointerEventPass.Main] 阶段晚于文本框收到同一个事件.
 * 文本框处理点击时先消费抬起事件, 再放置光标 (触摸和鼠标都是). 这里收到已被消费的抬起事件时检查光标是否被放在了被点击标签的另一端,
 * 是的话移到正确的一端. 两次修改在同一个事件中完成, 中间不会绘制.
 * 抬起事件没有被消费时文本框没有处理这次点击, 光标仍在点击之前的位置, 这里也不修改. 例如编辑框正在惯性滚动时,
 * 文本框的滚动在按下时就消费了事件, 这次点击只停止滚动; 外层的手势消费了移动时, 文本框也会取消这次点击.
 *
 * 这里不消费任何事件, 文本框的其他行为 (获得焦点、弹出键盘、显示光标手柄、双击选词、长按、拖动选择) 不变.
 * 双击、选词等得到的选区不是放在标签一端的光标, 不会被修改; Android 与桌面端的长按也是.
 * iOS 上长按文字时 Compose 用同样的就近规则放一个光标 (`moveCaretByLongPress`), 手指不动就抬起时抬起事件同样已被消费,
 * 这里同样会把它移到正确的一端; 长按后拖动由 Compose 的光标拖动处理 (移动事件被消费), 不在这里修正.
 * 只修改选区, 文字不变, 文本框不会因此隐藏光标手柄.
 *
 * @param textFieldLayout 用于取得文字布局, 以及把点击处换算到文字布局中.
 * @param scrollState 编辑框的纵向滚动状态.
 * @param chipDisplay 当前文本中的图片标签显示为什么文字.
 * @param enabled 编辑框是否可用. 不可用时文本框不放置光标, 这里也不修改选区.
 */
internal fun Modifier.imageChipTapCursorCorrection(
    state: TextFieldState,
    textFieldLayout: CommentTextFieldLayout,
    scrollState: ScrollState,
    chipDisplay: () -> ImageChipDisplay,
    enabled: Boolean = true,
): Modifier = this then ImageChipTapCursorElement(state, textFieldLayout, scrollState, chipDisplay, enabled)

private data class ImageChipTapCursorElement(
    val state: TextFieldState,
    val textFieldLayout: CommentTextFieldLayout,
    val scrollState: ScrollState,
    val chipDisplay: () -> ImageChipDisplay,
    val enabled: Boolean,
) : ModifierNodeElement<ImageChipTapCursorNode>() {
    override fun create() = ImageChipTapCursorNode(state, textFieldLayout, scrollState, chipDisplay, enabled)

    override fun update(node: ImageChipTapCursorNode) {
        node.state = state
        node.textFieldLayout = textFieldLayout
        node.scrollState = scrollState
        node.chipDisplay = chipDisplay
        node.enabled = enabled
    }
}

private class ImageChipTapCursorNode(
    var state: TextFieldState,
    var textFieldLayout: CommentTextFieldLayout,
    var scrollState: ScrollState,
    var chipDisplay: () -> ImageChipDisplay,
    var enabled: Boolean,
) : Modifier.Node(), PointerInputModifierNode {
    /**
     * 可能是一次点击的按下的指针. 与文本框一样, 指针抬起之前移出编辑框、出现第二个指针或移动被消费 (例如开始滚动或拖动选择) 时就不算点击.
     */
    private var pressedPointer: PointerId? = null

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        val changes = pointerEvent.changes
        val pressed = pressedPointer
        if (pressed == null) {
            val down = changes.singleOrNull() ?: return
            if (!down.changedToDownIgnoreConsumed()) return
            // 鼠标只处理左键, 右键打开菜单
            if (down.type == PointerType.Mouse && !pointerEvent.buttons.isPrimaryPressed) return
            pressedPointer = down.id
            return
        }
        val change = changes.singleOrNull()
        if (change == null || change.id != pressed) {
            pressedPointer = null
            return
        }
        when {
            change.changedToUpIgnoreConsumed() -> {
                pressedPointer = null
                // 文本框的点击 (以及 iOS 上长按放置光标) 都先消费抬起事件; 没有被消费时文本框没有放置光标
                if (change.isConsumed && !change.isOutside(bounds)) onTap(change.position)
            }

            change.isConsumed || change.isOutside(bounds) -> pressedPointer = null
        }
    }

    override fun onCancelPointerInput() {
        pressedPointer = null
    }

    override fun onDetach() {
        pressedPointer = null
    }

    private fun onTap(position: Offset) {
        if (!enabled) return
        val layout = textFieldLayout.textLayoutResult ?: return
        val layoutPosition = textFieldLayout.textLayoutPosition(
            requireLayoutCoordinates(),
            position,
            scrollState.value,
        ) ?: return
        val target = imageChipTapCursor(layout, layoutPosition, chipDisplay()) ?: return
        if (state.selection == TextRange(target.otherEnd)) {
            state.edit { selection = TextRange(target.cursor) }
        }
    }

    private fun PointerInputChange.isOutside(bounds: IntSize): Boolean =
        position.x < 0f || position.y < 0f || position.x > bounds.width || position.y > bounds.height
}
