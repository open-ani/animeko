/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.domain.comment.CommentImageProcessingStatus
import me.him188.ani.app.domain.comment.CommentImageStatusResult
import me.him188.ani.app.domain.comment.CommentImageUploadResult
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_copy
import me.him188.ani.app.ui.lang.comment_cut
import me.him188.ani.app.ui.lang.comment_image_chip
import me.him188.ani.app.ui.lang.comment_paste
import me.him188.ani.app.ui.lang.comment_select_all
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.awt.datatransfer.Clipboard as AwtClipboard
import java.awt.datatransfer.StringSelection
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 评论编辑框中的图片标签: 显示、整体编辑与剪贴板.
 */
@OptIn(TestOnly::class, ExperimentalComposeUiApi::class)
class EditCommentImageChipTest {
    private class Editor(
        val state: CommentEditorState,
        val chipStyle: CommentImageChipStyle,
    )

    private fun ComposeUiTest.setEditor(
        modifier: Modifier = Modifier,
        topSpacing: Int = 160,
        clipboard: Clipboard? = null,
        platform: Platform? = null,
        textInputInterceptor: PlatformTextInputInterceptor? = null,
        onUploadImage: suspend (ByteArray) -> CommentImageUploadResult = { CommentImageUploadResult.Ready(IMAGE_URL) },
        onCheckImageStatus: suspend (List<String>) -> CommentImageStatusResult = { CommentImageStatusResult.Unavailable },
    ): Editor {
        lateinit var editor: Editor
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val chipStyle = CommentImageChipStyle.default()
                editor = remember {
                    Editor(
                        CommentEditorState(
                            showExpandEditCommentButton = false,
                            initialEditExpanded = false,
                            panelTitle = stateOf(null),
                            stickers = stateOf(emptyList()),
                            richTextRenderer = { UIRichText(emptyList()) },
                            onSend = { _, _ -> CommentSendResult.Ok },
                            onUploadImage = onUploadImage,
                            onCheckImageStatus = onCheckImageStatus,
                            backgroundScope = scope,
                        ).apply {
                            startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
                        },
                        chipStyle,
                    )
                }
                CompositionLocalProvider(
                    LocalClipboard provides (clipboard ?: LocalClipboard.current),
                    LocalPlatform provides (platform ?: LocalPlatform.current),
                ) {
                    Box(modifier.size(width = 480.dp, height = 720.dp)) {
                        Column {
                            Spacer(Modifier.height(topSpacing.dp))
                            if (textInputInterceptor != null) {
                                InterceptPlatformTextInput(textInputInterceptor) { EditComment(state = editor.state) }
                            } else {
                                EditComment(state = editor.state)
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        return editor
    }

    private fun ComposeUiTest.textField() = onNodeWithTag(EditCommentTestTags.TextField)
    private fun ComposeUiTest.toolbar() = onNodeWithTag(EditCommentTestTags.FormattingToolbar)

    private val CommentEditorState.text get() = textFieldState.text.toString()

    private fun string(resource: StringResource) = runBlocking { getString(resource) }

    /**
     * 序号为 [number] 的图片标签上可见的文字, 例如 "图片 1".
     */
    private fun chipLabel(number: Int) = runBlocking { getString(Lang.comment_image_chip, number) }

    /**
     * 编辑框显示的文字 (经过 [CommentImageChipOutputTransformation]).
     */
    private fun ComposeUiTest.displayedText(): String =
        textField().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /**
     * 去掉隐藏字符, 不换行空格换成空格, 即用户看到的文字.
     */
    private fun String.visible(): String = filterNot { it in HiddenChars }.replace('\u00A0', ' ')

    private fun ComposeUiTest.textLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = textField().fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action?.invoke(results) == true, "text layout is not available")
        return results.single()
    }

    /**
     * 显示的文字中第一个图片标签的范围.
     */
    private fun String.firstLabelRange(): TextRange {
        val mark = indexOf(LABEL_MARK)
        val start = if (getOrNull(mark - 1) == ZERO_WIDTH_SPACE) mark - 1 else mark
        return TextRange(start, indexOf(LABEL_MARK, mark + 1) + 1)
    }

    /**
     * 显示的文字中第一个图片标签开头的不换行空格 (背景的边距) 的位置.
     */
    private fun String.firstLabelPadding(): Int = indexOf('\u00A0', firstLabelRange().start)

    /**
     * 显示的文字中第一个图片标签可见的部分在文字布局中的范围. 标签只占一行.
     */
    private fun ComposeUiTest.firstLabelBounds(): Rect {
        val layout = textLayoutResult()
        val text = layout.layoutInput.text.text
        val label = text.firstLabelRange()
        return layout.lineBoundsOf(label.start, label.end) { !text[it].isImageChipHiddenChar() }.single()
    }

    /**
     * 依次尝试不同长度的前缀, 把文本设为第一个让图片标签处于 [matches] 描述的位置的文本: 三个空行、前缀、一个图片标签和紧跟其后的中文.
     * 空行让标签所在的行低于编辑框右上角的展开按钮. 光标在文本末尾.
     *
     * @param matches 参数为文字布局和标签在显示的文字中的范围.
     */
    private fun ComposeUiTest.setTextWithChipAtWrap(
        editor: Editor,
        matches: (layout: TextLayoutResult, label: TextRange) -> Boolean,
    ): CommentImageChip {
        for (length in 1..80) {
            val text = "\n\n\n" + "一".repeat(length) + "[img]$IMAGE_URL[/img]中文测试"
            runOnIdle { editor.state.textFieldState.setTextAndPlaceCursorAtEnd(text) }
            waitForIdle()
            val layout = textLayoutResult()
            if (matches(layout, layout.layoutInput.text.text.firstLabelRange())) {
                return editor.state.imageChips.single()
            }
        }
        error("No prefix produces the expected line breaks")
    }

    /**
     * 分别用触摸和鼠标点击编辑框中的 [position] (以编辑框左上角为原点), 光标都应在 [expected]. 每次点击前光标移到文本末尾.
     */
    private fun ComposeUiTest.assertTapPlacesCursor(
        editor: Editor,
        position: Offset,
        expected: Int,
    ) {
        for (mouse in listOf(false, true)) {
            runOnIdle { editor.state.textFieldState.edit { selection = TextRange(length) } }
            // 与上一次点击间隔足够长, 不会被当作双击
            mainClock.advanceTimeBy(1_000)
            if (mouse) {
                textField().performMouseInput { click(position) }
            } else {
                textField().performTouchInput { click(position) }
            }
            waitForIdle()
            assertEquals(
                TextRange(expected),
                editor.state.textFieldState.selection,
                "${if (mouse) "click" else "tap"} at $position",
            )
        }
    }

    private fun ComposeUiTest.pressShortcut(key: Key) {
        textField().performKeyInput { withKeyDown(ShortcutModifier) { pressKey(key) } }
        waitForIdle()
    }

    /**
     * 剪贴板的读写在 IO 线程上进行, [ComposeUiTest.waitForIdle] 不会等待它们.
     */
    private fun ComposeUiTest.waitUntilEquals(expected: String?, actual: () -> String?) {
        waitUntil(timeoutMillis = 5_000) { actual() == expected }
        assertEquals(expected, actual())
    }

    @Test
    fun `image tag is shown as a chip label instead of the url`() = runAniComposeUiTest {
        val editor = setEditor()
        textField().performTextInput("look [img]$IMAGE_URL[/img] ok")
        waitForIdle()

        assertEquals("look [img]$IMAGE_URL[/img] ok", editor.state.text)
        val displayed = displayedText()
        assertFalse(IMAGE_URL in displayed, displayed)
        assertEquals("look  ${chipLabel(1)}  ok", displayed.visible())
    }

    @Test
    fun `chips are numbered by order`() = runAniComposeUiTest {
        setEditor()
        textField().performTextInput("[img]https://a.com/1.png[/img] and [IMG=1,2]https://a.com/2.png[/IMG]")
        waitForIdle()
        assertEquals(" ${chipLabel(1)}  and  ${chipLabel(2)} ", displayedText().visible())
    }

    @Test
    fun `cursor cannot be placed inside a chip`() = runAniComposeUiTest {
        val editor = setEditor()
        textField().performTextInput("a [img]$IMAGE_URL[/img] b")
        val chip = editor.state.imageChips.single()

        // 以文本为准的选区 (语义操作) 移到较近的一端
        textField().performTextInputSelection(TextRange(chip.range.start + 3))
        waitForIdle()
        assertEquals(TextRange(chip.range.start), editor.state.textFieldState.selection)
        textField().performTextInputSelection(TextRange(chip.range.end - 3))
        waitForIdle()
        assertEquals(TextRange(chip.range.end), editor.state.textFieldState.selection)

        // 点击标签中间
        val layout = textLayoutResult()
        val label = displayedText().firstLabelRange()
        val box = layout.getBoundingBox((label.start + label.end) / 2)
        val position = Offset(
            ContentPaddingStartPx(density.density) + box.center.x,
            ContentPaddingTopPx(density.density) + box.center.y,
        )
        textField().performMouseInput { click(position) }
        waitForIdle()
        val selection = editor.state.textFieldState.selection
        assertTrue(
            selection == TextRange(chip.range.start) || selection == TextRange(chip.range.end),
            "cursor $selection should be at an end of ${chip.range}",
        )

        // 输入的文字不会落在标签内
        textField().performTextInputSelection(TextRange(chip.range.end - 3))
        textField().performTextInput("x")
        waitForIdle()
        assertEquals("a [img]$IMAGE_URL[/img]x b", editor.state.text)
    }

    @Test
    fun `tapping beside a chip that ends a wrapped line places the cursor after it`() = runAniComposeUiTest {
        val editor = setEditor()
        runOnIdle { editor.state.editExpanded = true }
        // 标签是一行最后的内容, 紧跟其后的中文换到下一行
        val chip = setTextWithChipAtWrap(editor) { layout, label ->
            layout.getLineForOffset(label.end) == layout.getLineForOffset(label.end - 1) + 1 &&
                    layout.getLineForOffset(label.start + 1) == layout.getLineForOffset(label.end - 1)
        }
        val bounds = firstLabelBounds()
        val y = ContentPaddingTopPx(density.density) + bounds.center.y
        val labelLeft = ContentPaddingStartPx(density.density) + bounds.left

        // 这一行右侧的边距
        val fieldWidth = textField().fetchSemanticsNode().size.width
        assertTapPlacesCursor(editor, Offset(fieldWidth - ContentPaddingStartPx(density.density) / 2, y), chip.range.end)
        // 标签的右半边和左半边
        assertTapPlacesCursor(editor, Offset(labelLeft + bounds.width * 3 / 4, y), chip.range.end)
        assertTapPlacesCursor(editor, Offset(labelLeft + bounds.width / 4, y), chip.range.start)
    }

    @Test
    fun `tapping the start of a line that begins with a chip places the cursor before it`() = runAniComposeUiTest {
        val editor = setEditor()
        runOnIdle { editor.state.editExpanded = true }
        // 标签开头的 ZERO WIDTH SPACE 留在上一行末尾, 标签可见的部分在下一行的开头
        val chip = setTextWithChipAtWrap(editor) { layout, label ->
            layout.getLineForOffset(label.start + 1) == layout.getLineForOffset(label.start) + 1
        }
        val bounds = firstLabelBounds()
        val y = ContentPaddingTopPx(density.density) + bounds.center.y
        val labelLeft = ContentPaddingStartPx(density.density) + bounds.left

        // 这一行左侧的边距
        assertTapPlacesCursor(editor, Offset(ContentPaddingStartPx(density.density) / 2, y), chip.range.start)
        assertTapPlacesCursor(editor, Offset(labelLeft + bounds.width / 4, y), chip.range.start)
        assertTapPlacesCursor(editor, Offset(labelLeft + bounds.width * 3 / 4, y), chip.range.end)

        // 双击仍然选中整个标签
        mainClock.advanceTimeBy(1_000)
        textField().performMouseInput { doubleClick(Offset(labelLeft + bounds.width / 4, y)) }
        waitForIdle()
        assertEquals(chip.range, editor.state.textFieldState.selection)
    }

    @Test
    fun `tapping a chip at the start of a line accounts for the scroll position`() = runAniComposeUiTest {
        val editor = setEditor()
        // 收起的编辑框最多显示 3 行, 光标在文本末尾, 编辑框滚动到最后一行, 即标签所在的行
        val chip = setTextWithChipAtWrap(editor) { layout, label ->
            layout.getLineForOffset(label.start + 1) == layout.getLineForOffset(label.start) + 1
        }
        val layout = textLayoutResult()
        val bounds = firstLabelBounds()
        val line = layout.getLineForVerticalPosition(bounds.center.y)
        assertEquals(layout.lineCount - 1, line)
        val fieldHeight = textField().fetchSemanticsNode().size.height
        val viewportHeight = fieldHeight - ContentPaddingTopPx(density.density) - ContentPaddingBottomPx(density.density)
        val scroll = (layout.getLineBottom(line) - viewportHeight).coerceAtLeast(0f)
        assertTrue(scroll > layout.getLineBottom(0), "the text field should scroll: $scroll")

        val y = ContentPaddingTopPx(density.density) + bounds.center.y - scroll
        val labelLeft = ContentPaddingStartPx(density.density) + bounds.left
        assertTapPlacesCursor(editor, Offset(ContentPaddingStartPx(density.density) / 2, y), chip.range.start)
        assertTapPlacesCursor(editor, Offset(labelLeft + bounds.width * 3 / 4, y), chip.range.end)
    }

    /**
     * 把文本设为收起的编辑框放不下的多行文字, 最后一行中有一个图片标签, 光标在标签之后.
     * 编辑框滚动到底部, 返回标签可见的部分的左半边中的一点 (以编辑框左上角为原点). 点击这里时光标应放在标签之前.
     */
    private fun ComposeUiTest.setTextWithChipOnLastLine(editor: Editor): Offset {
        runOnIdle {
            editor.state.textFieldState.setTextAndPlaceCursorAtEnd("\n\n\n\n一二三 [img]$IMAGE_URL[/img] 四五")
        }
        waitForIdle()
        val chip = editor.state.imageChips.single()
        runOnIdle { editor.state.textFieldState.edit { selection = TextRange(chip.range.end) } }
        waitForIdle()

        val layout = textLayoutResult()
        val bounds = firstLabelBounds()
        val line = layout.getLineForVerticalPosition(bounds.center.y)
        assertEquals(layout.lineCount - 1, line)
        val fieldHeight = textField().fetchSemanticsNode().size.height
        val viewportHeight = fieldHeight - ContentPaddingTopPx(density.density) - ContentPaddingBottomPx(density.density)
        val scroll = (layout.getLineBottom(line) - viewportHeight).coerceAtLeast(0f)
        assertTrue(scroll > layout.getLineBottom(0), "the text field should scroll: $scroll")
        return Offset(
            ContentPaddingStartPx(density.density) + bounds.left + bounds.width / 4,
            ContentPaddingTopPx(density.density) + bounds.center.y - scroll,
        )
    }

    @Test
    fun `tapping a chip to stop a fling leaves the cursor where it was`() = runAniComposeUiTest {
        val editor = setEditor()
        val leftHalf = setTextWithChipOnLastLine(editor)
        val chip = editor.state.imageChips.single()

        // 编辑框已经滚动到底部, 向上快速滑动不会移动文字, 但开始了惯性滚动. 冻结时钟让惯性滚动停在进行中
        mainClock.autoAdvance = false
        textField().performTouchInput {
            swipeWithVelocity(Offset(centerX, bottom - 4f), Offset(centerX, top + 4f), endVelocity = 4000f)
        }
        assertEquals(TextRange(chip.range.end), editor.state.textFieldState.selection)

        // 惯性滚动时文本框的滚动在按下时就消费了事件, 这次点击只停止滚动, 光标不动
        textField().performTouchInput { click(leftHalf) }
        assertEquals(TextRange(chip.range.end), editor.state.textFieldState.selection)

        // 对照: 滚动停止后同样的点击把光标放在标签之前
        mainClock.autoAdvance = true
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        textField().performTouchInput { click(leftHalf) }
        waitForIdle()
        assertEquals(TextRange(chip.range.start), editor.state.textFieldState.selection)
    }

    @Test
    fun `tap ignored by the text field leaves the cursor where it was`() = runAniComposeUiTest {
        // 外层在 Initial 阶段消费按下事件, 与惯性滚动时文本框的滚动一样. 文本框因此不处理这次点击
        var consumeDowns = false
        val editor = setEditor(
            modifier = Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (consumeDowns) event.changes.forEach { if (it.changedToDown()) it.consume() }
                    }
                }
            },
        )
        val leftHalf = setTextWithChipOnLastLine(editor)
        val chip = editor.state.imageChips.single()

        consumeDowns = true
        for (mouse in listOf(false, true)) {
            mainClock.advanceTimeBy(1_000)
            if (mouse) {
                textField().performMouseInput { click(leftHalf) }
            } else {
                textField().performTouchInput { click(leftHalf) }
            }
            waitForIdle()
            assertEquals(TextRange(chip.range.end), editor.state.textFieldState.selection, if (mouse) "click" else "tap")
        }

        // 对照: 文本框处理点击时光标放在标签之前
        consumeDowns = false
        assertTapPlacesCursor(editor, leftHalf, chip.range.start)
    }

    @Test
    fun `backspace after a chip and delete before it remove the whole chip`() = runAniComposeUiTest {
        val editor = setEditor()
        textField().performTextInput("a [img]$IMAGE_URL[/img]")
        textField().performKeyInput { pressKey(Key.Backspace) }
        waitForIdle()
        assertEquals("a ", editor.state.text)

        textField().performTextInput("[img]$IMAGE_URL[/img]")
        textField().performTextInputSelection(TextRange(2))
        textField().performKeyInput { pressKey(Key.Delete) }
        waitForIdle()
        assertEquals("a ", editor.state.text)
    }

    @Test
    fun `copying a selected chip puts its bbcode on the clipboard`() = runAniComposeUiTest {
        val clipboard = FakeClipboard()
        val editor = setEditor(clipboard = clipboard)
        textField().performTextInput("look [img]$IMAGE_URL[/img] ok")
        val chip = editor.state.imageChips.single()

        textField().performTextInputSelection(chip.range)
        pressShortcut(Key.C)
        waitUntilEquals("[img]$IMAGE_URL[/img]") { clipboard.text() }

        textField().performTextInputSelection(TextRange(0, editor.state.text.length))
        pressShortcut(Key.C)
        waitUntilEquals("look [img]$IMAGE_URL[/img] ok") { clipboard.text() }
        assertEquals("look [img]$IMAGE_URL[/img] ok", editor.state.text)
    }

    @Test
    fun `cutting a chip and pasting it elsewhere moves it`() = runAniComposeUiTest {
        val clipboard = FakeClipboard()
        val editor = setEditor(clipboard = clipboard)
        textField().performTextInput("a [img]$IMAGE_URL[/img] b")
        val chip = editor.state.imageChips.single()

        textField().performTextInputSelection(chip.range)
        pressShortcut(Key.X)
        assertEquals("a  b", editor.state.text)
        waitUntilEquals("[img]$IMAGE_URL[/img]") { clipboard.text() }

        textField().performTextInputSelection(TextRange(editor.state.text.length))
        pressShortcut(Key.V)
        waitUntilEquals("a  b[img]$IMAGE_URL[/img]") { editor.state.text }
        waitForIdle()
        assertEquals("a  b ${chipLabel(1)} ", displayedText().visible())
    }

    @Test
    fun `toolbar is placed above the selected line when images come before the selection`() = runAniComposeUiTest {
        val editor = setEditor(topSpacing = 160)
        // 第一行是图片, 文本中的位置比显示的文字靠后很多. 不换算时 "ab" 的位置会落在第三行
        textField().performTextInput("[img]$IMAGE_URL[/img]\nab\ncdefghijklmnopqrstuvwxyz")
        val start = editor.state.text.indexOf("ab")
        textField().performTextInputSelection(TextRange(start, start + 2))
        waitForIdle()

        val layout = textLayoutResult()
        assertEquals(1, layout.getLineForOffset(displayedText().indexOf("ab")))
        val field = textField().fetchSemanticsNode().boundsInWindow
        val toolbar = toolbar().fetchSemanticsNode().boundsInWindow
        val expectedBottom = field.top + ContentPaddingTopPx(density.density) + layout.getLineTop(1) -
                with(density) { ToolbarSpacing.toPx() }
        assertEquals(expectedBottom, toolbar.bottom, absoluteTolerance = PositionTolerancePx, "toolbar $toolbar")
    }

    @Test
    fun `hidden characters in the chip label have no width`() = runAniComposeUiTest {
        setEditor()
        textField().performTextInput("[img]$IMAGE_URL[/img]")
        waitForIdle()

        val layout = textLayoutResult()
        val displayed = layout.layoutInput.text.text
        val label = displayed.firstLabelRange()
        val chipWidth = layout.getPathForRange(label.start, label.end).getBounds().width

        val measurer = TextMeasurer(
            layout.layoutInput.fontFamilyResolver,
            layout.layoutInput.density,
            layout.layoutInput.layoutDirection,
        )
        // 与 CommentImageChipOutputTransformation 相同的样式. 编辑框的文字样式有字间距, 不可见字符的字间距为零
        fun width(text: String): Float {
            val annotated = buildAnnotatedString {
                append(text)
                addStyle(SpanStyle(fontWeight = FontWeight.Medium), 0, text.length)
                for (hidden in imageChipHiddenRanges(text)) {
                    addStyle(ImageChipHiddenCharStyle, hidden.start, hidden.end)
                }
            }
            return measurer.measure(annotated, layout.layoutInput.style)
                .getPathForRange(0, text.length).getBounds().width
        }

        val plainLabel = "\u00A0" + chipLabel(1).replace(' ', '\u00A0') + "\u00A0"
        assertEquals(width(plainLabel), chipWidth, absoluteTolerance = 0.5f)
        assertEquals(width(plainLabel), width(displayed.substring(label.start, label.end)), absoluteTolerance = 0.5f)
        assertEquals(width(plainLabel), width(encodeImageChipLabel(chipLabel(1), 123456)), absoluteTolerance = 0.5f)
    }

    @Test
    fun `chip label never breaks across lines`() = runAniComposeUiTest {
        lateinit var measurer: TextMeasurer
        setContent { measurer = rememberTextMeasurer() }
        waitForIdle()

        val style = TextStyle(fontSize = 16.sp)
        val prefix = "一二三四五六七八九十"
        // 宽度刚好放下前缀和标签的第一个字: 标签内部可以换行时第二个字会换到下一行
        val width = measurer.measure("$prefix\u00A0图", style).size.width + 2
        val constraints = Constraints(maxWidth = width)

        val label = encodeImageChipLabel("图片 1", 5)
        val layout = measurer.measure(prefix + label, style, constraints = constraints)
        // 标签开头的 ZERO WIDTH SPACE 之后可以换行, 它留在上一行; 从开头标记起不换行
        assertEquals(
            layout.getLineForOffset(prefix.length + 1),
            layout.getLineForOffset(prefix.length + label.length - 1),
        )

        // 对照: 中文字符之间不插入 WORD JOINER 时会在标签中间换行
        val unjoined = "$prefix\u00A0图片\u00A01\u00A0"
        val unjoinedLayout = measurer.measure(unjoined, style, constraints = constraints)
        assertNotEquals(
            unjoinedLayout.getLineForOffset(prefix.length + 1),
            unjoinedLayout.getLineForOffset(prefix.length + 2),
        )
    }

    @Test
    fun `chip background is drawn behind the label`() = runAniComposeUiTest {
        val editor = setEditor()
        textField().performTextInput("[img]$IMAGE_URL[/img] ")
        waitForIdle()

        val layout = textLayoutResult()
        // 标签开头的不换行空格处没有字形, 只有背景
        val padding = layout.getBoundingBox(displayedText().firstLabelPadding())
        val image = textField().captureToImage().toPixelMap()
        val pixel = image[
            (ContentPaddingStartPx(density.density) + padding.center.x).roundToInt(),
            (ContentPaddingTopPx(density.density) + padding.center.y).roundToInt(),
        ]
        assertColorClose(editor.chipStyle.ready.containerColor, pixel)
    }

    @Test
    fun `pending and failed chips have their own colors`() = runAniComposeUiTest {
        val firstAttempt = CompletableDeferred<CommentImageUploadResult>()
        var attempts = 0
        val editor = setEditor(
            onUploadImage = {
                if (++attempts == 1) firstAttempt.await() else CommentImageUploadResult.Ready(IMAGE_URL)
            },
        )
        runOnIdle { editor.state.addImages(listOf(CommentImageFile(PNG.size.toLong()) { PNG })) }
        waitForIdle()
        val uploadId = checkNotNull(editor.state.imageChips.single().uploadId)
        assertEquals(" ${chipLabel(1)} ", displayedText().visible())

        fun chipBackground(): Color {
            val layout = textLayoutResult()
            val padding = layout.getBoundingBox(displayedText().firstLabelPadding())
            return textField().captureToImage().toPixelMap()[
                (ContentPaddingStartPx(density.density) + padding.center.x).roundToInt(),
                (ContentPaddingTopPx(density.density) + padding.center.y).roundToInt(),
            ]
        }
        assertColorClose(editor.chipStyle.uploading.containerColor, chipBackground())

        firstAttempt.complete(CommentImageUploadResult.NetworkError)
        waitUntil(timeoutMillis = 5_000) { editor.state.imageUploadError != null }
        waitForIdle()
        assertColorClose(editor.chipStyle.failed.containerColor, chipBackground())

        runOnIdle { editor.state.retryImageUpload(uploadId) }
        waitUntil(timeoutMillis = 5_000) { editor.state.text == "[img]$IMAGE_URL[/img]" }
        waitForIdle()
        assertColorClose(editor.chipStyle.ready.containerColor, chipBackground())
    }

    @Test
    fun `processing chip has the pending color until the image is ready`() = runAniComposeUiTest {
        val ready = AtomicBoolean(false)
        val editor = setEditor(
            onUploadImage = { CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL) },
            onCheckImageStatus = { ids ->
                val status = if (ready.get()) {
                    CommentImageProcessingStatus.Ready(IMAGE_URL)
                } else {
                    CommentImageProcessingStatus.Processing
                }
                CommentImageStatusResult.Ok(ids.associateWith { status })
            },
        )
        runOnIdle { editor.state.addImages(listOf(CommentImageFile(PNG.size.toLong()) { PNG })) }
        waitUntil(timeoutMillis = 5_000) {
            editor.state.imageChips.singleOrNull()?.let { editor.state.imageChipStatus(it) } ==
                    CommentImageChipStatus.Processing
        }
        waitForIdle()
        assertEquals(" ${chipLabel(1)} ", displayedText().visible())

        fun chipBackground(): Color {
            val layout = textLayoutResult()
            val padding = layout.getBoundingBox(displayedText().firstLabelPadding())
            return textField().captureToImage().toPixelMap()[
                (ContentPaddingStartPx(density.density) + padding.center.x).roundToInt(),
                (ContentPaddingTopPx(density.density) + padding.center.y).roundToInt(),
            ]
        }
        assertColorClose(editor.chipStyle.processing.containerColor, chipBackground())

        ready.set(true)
        waitUntil(timeoutMillis = 10_000) { editor.state.text == "[img]$IMAGE_URL[/img]" }
        waitForIdle()
        assertEquals(" ${chipLabel(1)} ", displayedText().visible())
        assertColorClose(editor.chipStyle.ready.containerColor, chipBackground())
    }

    @Test
    fun `android toolbar copies cuts and pastes bbcode`() = runAniComposeUiTest {
        val clipboard = FakeClipboard()
        val editor = setEditor(clipboard = clipboard, platform = Platform.Android(Arch.ARMV8A))
        textField().performTextInput("a [img]$IMAGE_URL[/img] b")
        val chip = editor.state.imageChips.single()

        textField().performTextInputSelection(chip.range)
        waitForIdle()
        toolbar().assertIsDisplayed()
        onNodeWithContentDescription(string(Lang.comment_select_all)).assertDoesNotExist()
        onNodeWithContentDescription(string(Lang.comment_copy)).performMouseInput { click() }
        waitUntilEquals("[img]$IMAGE_URL[/img]") { clipboard.text() }
        waitForIdle()
        // 与桌面端一样, 复制后选区保持不变
        assertEquals(chip.range, editor.state.textFieldState.selection)
        toolbar().assertIsDisplayed()

        onNodeWithContentDescription(string(Lang.comment_cut)).performMouseInput { click() }
        waitForIdle()
        assertEquals("a  b", editor.state.text)

        // 粘贴替换选中的 "b"
        textField().performTextInputSelection(TextRange(3, 4))
        waitForIdle()
        onNodeWithContentDescription(string(Lang.comment_paste)).performMouseInput { click() }
        waitUntilEquals("a  [img]$IMAGE_URL[/img]") { editor.state.text }
        assertEquals(TextRange(editor.state.text.length), editor.state.textFieldState.selection)
    }

    @Test
    fun `android cursor toolbar requested by the text field offers paste`() = runAniComposeUiTest {
        // Android 上默认启用新的文字操作菜单, 桌面端默认不启用
        val previous = ComposeFoundationFlags.isNewContextMenuEnabled
        ComposeFoundationFlags.isNewContextMenuEnabled = true
        try {
            val clipboard = FakeClipboard()
            runBlocking { clipboard.setClipEntryText("[img]$IMAGE_URL[/img]") }
            val editor = setEditor(clipboard = clipboard, platform = Platform.Android(Arch.ARMV8A))
            textField().performTextInput("hi")
            waitForIdle()
            toolbar().assertDoesNotExist()

            // 长按空白处, 文本框把光标放到行末并请求显示工具栏
            textField().performTouchInput { longClick(Offset(width * 0.6f, ContentPaddingTopPx(density) + 8f)) }
            waitForIdle()
            assertEquals(TextRange(2), editor.state.textFieldState.selection)
            toolbar().assertIsDisplayed()
            onNodeWithContentDescription(string(Lang.comment_cut)).assertDoesNotExist()
            onNodeWithContentDescription(string(Lang.comment_select_all)).assertExists()

            onNodeWithContentDescription(string(Lang.comment_paste)).performMouseInput { click() }
            waitUntilEquals("hi[img]$IMAGE_URL[/img]") { editor.state.text }
        } finally {
            ComposeFoundationFlags.isNewContextMenuEnabled = previous
        }
    }

    @Test
    fun `android cursor toolbar does not come back after select all and copy`() = runAniComposeUiTest {
        val previous = ComposeFoundationFlags.isNewContextMenuEnabled
        ComposeFoundationFlags.isNewContextMenuEnabled = true
        try {
            val clipboard = FakeClipboard()
            val editor = setEditor(clipboard = clipboard, platform = Platform.Android(Arch.ARMV8A))
            textField().performTextInput("hi [img]$IMAGE_URL[/img] ok")
            waitForIdle()

            // 长按空白处显示光标处的工具栏, 全选后复制
            textField().performTouchInput { longClick(Offset(width * 0.8f, ContentPaddingTopPx(density) + 8f)) }
            waitForIdle()
            onNodeWithContentDescription(string(Lang.comment_select_all)).performMouseInput { click() }
            waitForIdle()
            assertEquals(TextRange(0, editor.state.text.length), editor.state.textFieldState.selection)
            onNodeWithContentDescription(string(Lang.comment_copy)).performMouseInput { click() }
            waitUntilEquals("hi [img]$IMAGE_URL[/img] ok") { clipboard.text() }
            waitForIdle()
            // 选区和完整的工具栏保持不变, 没有变成光标处只有粘贴和全选的工具栏
            assertEquals(TextRange(0, editor.state.text.length), editor.state.textFieldState.selection)
            onNodeWithContentDescription(string(Lang.comment_select_all)).assertDoesNotExist()
            onNodeWithContentDescription(string(Lang.comment_cut)).assertIsDisplayed()

            // 点击文字取消选区后不显示工具栏
            textField().performTouchInput { click(Offset(width * 0.1f, ContentPaddingTopPx(density) + 8f)) }
            waitForIdle()
            assertTrue(editor.state.textFieldState.selection.collapsed)
            toolbar().assertDoesNotExist()
        } finally {
            ComposeFoundationFlags.isNewContextMenuEnabled = previous
        }
    }

    @Test
    fun `a run of chips breaks between chips and never inside one`() = runAniComposeUiTest {
        lateinit var measurer: TextMeasurer
        setContent { measurer = rememberTextMeasurer() }
        waitForIdle()

        val style = TextStyle(fontSize = 16.sp)
        val prefix = "behind the hill"
        // 一次选择多张图片时, 标签之间没有空格
        val labels = (1..10).map { encodeImageChipLabel("图片 $it", it) }
        val text = prefix + labels.joinToString("")
        val constraints = Constraints(maxWidth = measurer.measure(labels.last(), style).size.width * 4)
        val layout = measurer.measure(text, style, constraints = constraints)

        var start = prefix.length
        val lines = labels.map { label ->
            // 标签开头的 ZERO WIDTH SPACE 之后可以换行, 它可能留在上一行; 从开头标记起不换行
            val lines = layout.getLineForOffset(start + 1) to layout.getLineForOffset(start + label.length - 1)
            // 背景只画在标签可见的部分所在的那一行
            val backgrounds = layout.lineBoundsOf(start, start + label.length) { !text[it].isImageChipHiddenChar() }
            assertEquals(1, backgrounds.size, "chip at $start: $backgrounds")
            assertTrue(backgrounds.single().top >= layout.getLineTop(lines.first) - 0.5f, "chip at $start")
            start += label.length
            lines
        }
        for ((index, line) in lines.withIndex()) {
            assertEquals(line.first, line.second, "chip ${index + 1} should not break across lines: $lines")
        }
        assertTrue(lines.last().first > lines.first().first, "chips should wrap: $lines")
    }

    @Test
    fun `background of a chip wrapped at its end covers only its own line`() = runAniComposeUiTest {
        lateinit var measurer: TextMeasurer
        setContent { measurer = rememberTextMeasurer() }
        waitForIdle()

        val style = TextStyle(fontSize = 16.sp)
        val prefix = "一二三"
        val label = encodeImageChipLabel("图片 1", 3)
        // 宽度刚好放下前缀和标签, 紧跟在标签后的中文换到下一行
        val width = measurer.measure(prefix + label, style).size.width + 2
        val text = prefix + label + "四五六七八九"
        val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = width))
        val start = prefix.length
        val end = start + label.length
        assertEquals(0, layout.getLineForOffset(end - 1))
        assertEquals(1, layout.getLineForOffset(end))

        val lines = layout.lineBoundsOf(start, end) { !text[it].isImageChipHiddenChar() }
        assertEquals(1, lines.size, "$lines")
        val bounds = lines.single()
        assertTrue(bounds.top >= layout.getLineTop(0) - 0.5f && bounds.bottom <= layout.getLineBottom(0) + 0.5f, "$bounds")
        assertTrue(bounds.left >= layout.getBoundingBox(start - 1).right - 0.5f, "$bounds")
        assertTrue(bounds.right <= width, "$bounds")
    }

    @Test
    fun `finished upload waits for the input method composition to end`() = runAniComposeUiTest {
        var inputRequest: PlatformTextInputMethodRequest? = null
        val response = CompletableDeferred<CommentImageUploadResult>()
        val editor = setEditor(
            textInputInterceptor = { request, nextHandler ->
                inputRequest = request
                nextHandler.startInputMethod(request)
            },
            onUploadImage = { response.await() },
        )
        textField().performTextInput("hi ")
        runOnIdle { editor.state.addImages(listOf(CommentImageFile(PNG.size.toLong()) { PNG })) }
        waitUntil(timeoutMillis = 5_000) { inputRequest != null }
        val tag = pendingImageTag(checkNotNull(editor.state.imageChips.single().uploadId))
        val request = checkNotNull(inputRequest)

        // 输入法正在组合拼音
        runOnIdle { request.editText { setComposingText("ni", 1) } }
        waitForIdle()
        val composing = "hi ${tag}ni"
        assertEquals(composing, editor.state.text)
        assertEquals(TextRange(composing.length - 2, composing.length), editor.state.textFieldState.composition)

        response.complete(CommentImageUploadResult.Ready(IMAGE_URL))
        waitUntil(timeoutMillis = 5_000) { !editor.state.uploadingImages }
        waitForIdle()
        // 图片已可用, 文本等到组合结束才修改, 组合不被打断
        assertEquals(CommentImageChipStatus.Ready, editor.state.imageChipStatus(editor.state.imageChips.single()))
        assertEquals(composing, editor.state.text)
        assertEquals(TextRange(composing.length - 2, composing.length), editor.state.textFieldState.composition)

        runOnIdle { request.editText { commitText("你", 1) } }
        waitUntilEquals("hi [img]$IMAGE_URL[/img]你") { editor.state.text }
    }

    private fun assertColorClose(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < 0.03f &&
                abs(expected.green - actual.green) < 0.03f &&
                abs(expected.blue - actual.blue) < 0.03f
        assertTrue(close, "expected $expected but was $actual")
    }

    /**
     * 只在内存中保存内容的剪贴板. 平台剪贴板 ([nativeClipboard]) 是一个独立的 AWT 剪贴板, Compose 用它判断能否粘贴.
     */
    private class FakeClipboard : Clipboard {
        private val awtClipboard = AwtClipboard("test")

        override suspend fun getClipEntry(): ClipEntry? = awtClipboard.getContents(null)?.let { ClipEntry(it) }

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            awtClipboard.setContents(clipEntry?.asAwtTransferable ?: StringSelection(""), null)
        }

        @Deprecated("Use platform-specific extension to get platform reference")
        override val nativeClipboard: NativeClipboard get() = awtClipboard

        fun text(): String? = runBlocking { getClipEntryText() }
    }

    private companion object {
        const val IMAGE_URL = "https://img.myani.org/comments/1.webp"
        const val IMAGE_ID = "1111111111111111111111111111111111111111111111111111111111111111"
        val PNG = byteArrayOf(137.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
        const val LABEL_MARK = '\u2063'
        const val ZERO_WIDTH_SPACE = '\u200B'
        const val HiddenChars = "\u200B\u2060\u2062\u2063\u2064"

        /**
         * 编辑框的内边距. [EditCommentDefaults.CommentTextField] 没有标签, 使用无标签时的默认值.
         */
        val ContentPadding = OutlinedTextFieldDefaults.contentPaddingWithoutLabel()

        fun ContentPaddingStartPx(density: Float) = ContentPadding.calculateLeftPadding(LayoutDirection.Ltr).value * density
        fun ContentPaddingTopPx(density: Float) = ContentPadding.calculateTopPadding().value * density
        fun ContentPaddingBottomPx(density: Float) = ContentPadding.calculateBottomPadding().value * density

        val ToolbarSpacing = 8.dp
        const val PositionTolerancePx = 2f

        /**
         * 复制、剪切和粘贴快捷键的修饰键, 与 Compose 桌面端的按键映射一致.
         */
        val ShortcutModifier = if (System.getProperty("os.name") == "Mac OS X") Key.MetaLeft else Key.CtrlLeft
    }
}
