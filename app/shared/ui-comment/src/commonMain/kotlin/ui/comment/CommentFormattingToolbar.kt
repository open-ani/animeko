/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.awaitCancellation
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_bold
import me.him188.ani.app.ui.lang.comment_copy
import me.him188.ani.app.ui.lang.comment_cut
import me.him188.ani.app.ui.lang.comment_italic
import me.him188.ani.app.ui.lang.comment_link
import me.him188.ani.app.ui.lang.comment_mask
import me.him188.ani.app.ui.lang.comment_paste
import me.him188.ani.app.ui.lang.comment_select_all
import me.him188.ani.app.ui.lang.comment_strikethrough
import me.him188.ani.app.ui.lang.comment_underline
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 选中文字后显示在选区旁的格式工具栏 (加粗、斜体、下划线、删除线、遮罩、链接).
 *
 * 有 [clipboardActions] 时 (Android) 工具栏前部还有剪切、复制和粘贴, 因为 Android 的评论面板中不显示系统的文字操作菜单.
 * [showFormatting] 为 `false` 时只显示剪贴板操作, 用于在光标处粘贴, 见 [CommentTextToolbarRequests].
 *
 * 工具栏是一个不可获得焦点的 [Popup], 显示与否完全由调用方根据编辑框的焦点和选区决定, 所以点击编辑框不会把它关掉.
 * 位置跟随选区:
 * - 桌面端优先放在选区上方, 上方放不下时放到下方;
 * - 移动端优先放在选区下方并让出选区手柄的位置. 拖动选区手柄时放大镜显示在所在行的上方, iOS 的系统编辑菜单也在选区上方,
 *   放在下方与两者都不重叠. Android 的评论面板中不显示系统的文字操作菜单, 上方只有放大镜.
 *
 * 这个 [Popup] 在单独的层 (桌面端和 iOS) 或窗口 (Android) 中, 有自己的焦点, 其中的焦点请求不会把焦点从编辑框移走,
 * 所以点击按钮时编辑框的焦点和选区保持不变. 按钮另外设为不可获得焦点作为防御: 如果按钮与编辑框处在同一个焦点体系中,
 * 桌面端按下按钮时会请求焦点, 编辑框失去焦点会清除选区.
 *
 * @param selectionBounds 选区在调用处所在布局中的范围, 单位为像素. 见 [CommentTextFieldLayout.selectionBounds].
 */
@Composable
internal fun CommentFormattingToolbarPopup(
    selectionBounds: Rect,
    activeFormats: Set<CommentFormat>,
    linkActive: Boolean,
    onToggleFormat: (CommentFormat) -> Unit,
    onToggleLink: () -> Unit,
    clipboardActions: CommentClipboardActions? = null,
    showFormatting: Boolean = true,
) {
    val density = LocalDensity.current
    val preferAbove = !LocalPlatform.current.isMobile()
    val positionProvider = remember(selectionBounds, preferAbove, density) {
        with(density) {
            CommentFormattingToolbarPositionProvider(
                selectionBounds = selectionBounds,
                preferAbove = preferAbove,
                spacing = ToolbarSpacing.roundToPx(),
                belowClearance = if (preferAbove) 0 else SelectionHandleClearance.roundToPx(),
            )
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        CommentFormattingToolbar(
            activeFormats = activeFormats,
            linkActive = linkActive,
            onToggleFormat = onToggleFormat,
            onToggleLink = onToggleLink,
            clipboardActions = clipboardActions,
            showFormatting = showFormatting,
        )
    }
}

/**
 * 选区工具栏中的剪贴板操作, 见 [CommentFormattingToolbarPopup].
 */
@Immutable
internal class CommentClipboardActions(
    val onCut: () -> Unit,
    val onCopy: () -> Unit,
    val onPaste: () -> Unit,
    val onSelectAll: () -> Unit,
)

/**
 * 选区工具栏的内容.
 *
 * 有剪贴板操作时按钮更小 (36dp), 两组按钮之间有分隔线, 在 360dp 宽的手机上也能放下; 再窄时可以横向滚动.
 *
 * @param showFormatting 显示格式按钮, 即选区不为空. 为 `false` 时剪贴板操作只有粘贴和全选.
 */
@Composable
internal fun CommentFormattingToolbar(
    activeFormats: Set<CommentFormat>,
    linkActive: Boolean,
    onToggleFormat: (CommentFormat) -> Unit,
    onToggleLink: () -> Unit,
    modifier: Modifier = Modifier,
    clipboardActions: CommentClipboardActions? = null,
    showFormatting: Boolean = true,
) {
    val buttonSize = if (clipboardActions != null) CompactToolbarButtonSize else ToolbarButtonSize
    Surface(
        modifier = modifier.testTag(EditCommentTestTags.FormattingToolbar),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (clipboardActions != null) 0.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (clipboardActions != null) {
                if (showFormatting) {
                    ToolbarButton(
                        icon = Icons.Outlined.ContentCut,
                        contentDescription = stringResource(Lang.comment_cut),
                        size = buttonSize,
                        onClick = clipboardActions.onCut,
                    )
                    ToolbarButton(
                        icon = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(Lang.comment_copy),
                        size = buttonSize,
                        onClick = clipboardActions.onCopy,
                    )
                }
                ToolbarButton(
                    icon = Icons.Outlined.ContentPaste,
                    contentDescription = stringResource(Lang.comment_paste),
                    size = buttonSize,
                    onClick = clipboardActions.onPaste,
                )
                if (showFormatting) {
                    VerticalDivider(Modifier.height(20.dp).padding(horizontal = 4.dp))
                } else {
                    ToolbarButton(
                        icon = Icons.Outlined.SelectAll,
                        contentDescription = stringResource(Lang.comment_select_all),
                        size = buttonSize,
                        onClick = clipboardActions.onSelectAll,
                    )
                }
            }
            if (showFormatting) {
                for (format in CommentFormat.entries) {
                    FormattingButton(
                        icon = format.icon,
                        contentDescription = stringResource(format.label),
                        checked = format in activeFormats,
                        size = buttonSize,
                        onClick = { onToggleFormat(format) },
                    )
                }
                FormattingButton(
                    icon = Icons.Outlined.Link,
                    contentDescription = stringResource(Lang.comment_link),
                    checked = linkActive,
                    size = buttonSize,
                    onClick = onToggleLink,
                )
            }
        }
    }
}

@Composable
private fun FormattingButton(
    icon: ImageVector,
    contentDescription: String,
    checked: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = Modifier
            .focusProperties { canFocus = false }
            .size(size),
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .focusProperties { canFocus = false }
            .size(size),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

private val CommentFormat.icon: ImageVector
    get() = when (this) {
        CommentFormat.Bold -> Icons.Outlined.FormatBold
        CommentFormat.Italic -> Icons.Outlined.FormatItalic
        CommentFormat.Underline -> Icons.Outlined.FormatUnderlined
        CommentFormat.Strikethrough -> Icons.Outlined.FormatStrikethrough
        CommentFormat.Mask -> Icons.Outlined.VisibilityOff
    }

private val CommentFormat.label: StringResource
    get() = when (this) {
        CommentFormat.Bold -> Lang.comment_bold
        CommentFormat.Italic -> Lang.comment_italic
        CommentFormat.Underline -> Lang.comment_underline
        CommentFormat.Strikethrough -> Lang.comment_strikethrough
        CommentFormat.Mask -> Lang.comment_mask
    }

/**
 * 工具栏与选区之间的距离.
 */
private val ToolbarSpacing: Dp = 8.dp

private val ToolbarButtonSize: Dp = 40.dp
private val CompactToolbarButtonSize: Dp = 36.dp

/**
 * 移动端选区手柄悬挂在选区下方的高度, 工具栏放在下方时要让开.
 */
private val SelectionHandleClearance: Dp = 28.dp

/**
 * 把工具栏放在选区上方或下方, 放不下时换到另一侧, 并水平居中于选区.
 *
 * 水平方向限制在锚点布局的左右边缘之间 (比锚点宽时居中于锚点), 与屏幕边缘至少保持锚点自身的边距, 阴影不会被截掉.
 * 只限制在窗口内不够: Android 的评论面板窗口不限制布局范围 (`FLAG_LAYOUT_NO_LIMITS`), `Popup` 得到的窗口大小可能超出屏幕.
 * 结果最后仍限制在窗口内.
 *
 * 居中对齐与布局方向无关, 工具栏内按钮的顺序由 [Row] 按布局方向排列.
 *
 * @param selectionBounds 选区相对于锚点布局 (`Popup` 所在的布局) 的范围, 单位为像素.
 * @param preferAbove 优先放在选区上方.
 * @param spacing 工具栏与选区之间的距离.
 * @param belowClearance 放在选区下方时额外留出的距离 (移动端的选区手柄).
 */
internal class CommentFormattingToolbarPositionProvider(
    private val selectionBounds: Rect,
    private val preferAbove: Boolean,
    private val spacing: Int,
    private val belowClearance: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val selectionTop = anchorBounds.top + selectionBounds.top.roundToInt()
        val selectionBottom = anchorBounds.top + selectionBounds.bottom.roundToInt()
        val above = selectionTop - spacing - popupContentSize.height
        val below = selectionBottom + spacing + belowClearance
        val fitsAbove = above >= 0
        val fitsBelow = below + popupContentSize.height <= windowSize.height
        val y = if (preferAbove) {
            if (fitsAbove || !fitsBelow) above else below
        } else {
            if (fitsBelow || !fitsAbove) below else above
        }

        val width = popupContentSize.width
        val x = if (width <= anchorBounds.width) {
            val centerX = anchorBounds.left + selectionBounds.center.x
            (centerX - width / 2f).roundToInt().coerceIn(anchorBounds.left, anchorBounds.right - width)
        } else {
            anchorBounds.left - (width - anchorBounds.width) / 2
        }

        return IntOffset(
            x.coerceIn(0, max(0, windowSize.width - width)),
            y.coerceIn(0, max(0, windowSize.height - popupContentSize.height)),
        )
    }
}

/**
 * 记录评论编辑框的文字布局与位置, 用来把选区换算成宿主布局 (放置工具栏 [Popup] 的布局) 中的坐标, 以及把点击处换算到文字布局中.
 *
 * 文字布局 ([TextLayoutResult]) 的坐标以编辑框内部文字区域的左上角为原点; 多行编辑框在纵向滚动时文字向上偏移滚动值.
 * 文字区域在宿主布局中的位置由两者的 [LayoutCoordinates] 计算.
 *
 * 文字布局的是显示的文字, 其中的图片标签已替换为 "图片 N". 文本 (`TextFieldState`) 中的位置要先用 [ImageChipDisplay.toVisual] 换算.
 */
@Stable
internal class CommentTextFieldLayout {
    private var getTextLayoutResult: () -> TextLayoutResult? by mutableStateOf({ null })
    private var textOffsetInHost: Offset by mutableStateOf(Offset.Unspecified)
    private var textViewportSize: IntSize by mutableStateOf(IntSize.Zero)

    private var hostCoordinates: LayoutCoordinates? = null
    private var textCoordinates: LayoutCoordinates? = null

    /**
     * 编辑框的 `onTextLayout`.
     */
    fun onTextLayout(getResult: () -> TextLayoutResult?) {
        getTextLayoutResult = getResult
    }

    /**
     * 最新的文字布局, 尚未布局时为 `null`.
     */
    val textLayoutResult: TextLayoutResult? get() = getTextLayoutResult()

    /**
     * 宿主布局的位置.
     */
    fun onHostPositioned(coordinates: LayoutCoordinates) {
        hostCoordinates = coordinates
        updateTextOffset()
    }

    /**
     * 编辑框内部文字区域 (可滚动的视口) 的位置.
     */
    fun onTextPositioned(coordinates: LayoutCoordinates) {
        textCoordinates = coordinates
        textViewportSize = coordinates.size
        updateTextOffset()
    }

    private fun updateTextOffset() {
        val host = hostCoordinates ?: return
        val text = textCoordinates ?: return
        if (!host.isAttached || !text.isAttached) return
        textOffsetInHost = host.localPositionOf(text, Offset.Zero)
    }

    /**
     * 选区在宿主布局中的可见范围, 单位为像素. 选区部分滚出可见区域时, 滚出的部分会被截掉.
     *
     * @param visualSelection 选区在显示的文字中的范围, 见 [ImageChipDisplay.toVisual].
     * @param scrollOffset 编辑框的纵向滚动值 (`ScrollState.value`).
     * @return 布局尚未完成, 或选区完全滚出可见区域时为 `null`.
     */
    fun selectionBounds(visualSelection: TextRange, scrollOffset: Int): Rect? {
        val layout = getTextLayoutResult() ?: return null
        val origin = textOffsetInHost.takeIf { it.isSpecified } ?: return null
        val text = layout.layoutInput.text
        val selectionStart = visualSelection.min.coerceIn(0, text.length)
        // 图片标签中的隐藏字符宽度为零, 不计入. 标签开头的 ZERO WIDTH SPACE 可能留在上一行的末尾
        var start = selectionStart
        var end = visualSelection.max.coerceIn(0, text.length)
        while (start < end && text[start].isImageChipHiddenChar()) start++
        while (end > start && text[end - 1].isImageChipHiddenChar()) end--
        val bounds = when {
            start == end -> layout.getCursorRect(selectionStart)
            layout.getLineForOffset(start) == layout.getLineForOffset(end - 1) ->
                layout.lineBoundsOf(start, end) { !text[it].isImageChipHiddenChar() }.single()

            else -> {
                // 跨行的选区横向占满各行, 只需修正底部, 原因见 lineBoundsOf
                val path = layout.getPathForRange(start, end).getBounds()
                path.copy(bottom = minOf(path.bottom, layout.getLineBottom(layout.getLineForOffset(end - 1))))
            }
        }
        val width = textViewportSize.width.toFloat()
        val height = textViewportSize.height.toFloat()
        val top = bounds.top - scrollOffset
        val bottom = bounds.bottom - scrollOffset
        if (bottom <= 0f || top >= height) return null
        val visible = Rect(
            left = bounds.left.coerceIn(0f, width),
            top = top.coerceIn(0f, height),
            right = bounds.right.coerceIn(0f, width),
            bottom = bottom.coerceIn(0f, height),
        )
        return visible.translate(origin)
    }

    /**
     * 编辑框中的点击处 [position] 在文字布局中的位置, 用于判断点击了哪个字符.
     *
     * 与 Compose 处理点击时一样, 文字区域 (可滚动的视口) 之外的位置 (例如编辑框的内边距中) 先移到文字区域的边缘.
     *
     * @param coordinates [position] 所在的布局, 例如编辑框本身.
     * @param scrollOffset 编辑框的纵向滚动值 (`ScrollState.value`).
     * @return 文字区域尚未布局时为 `null`.
     */
    fun textLayoutPosition(coordinates: LayoutCoordinates, position: Offset, scrollOffset: Int): Offset? {
        val text = textCoordinates?.takeIf { it.isAttached } ?: return null
        if (!coordinates.isAttached) return null
        val local = text.localPositionOf(coordinates, position)
        return Offset(
            x = local.x.coerceIn(0f, text.size.width.toFloat()),
            y = local.y.coerceIn(0f, text.size.height.toFloat()) + scrollOffset,
        )
    }
}

/**
 * 文字 [start] 到 [end] (不含) 在每一行中占据的范围, 坐标与文字布局相同, 按行的顺序排列. 只计入 [include] 的字符,
 * 一行中没有这样的字符时这一行不出现在结果中.
 *
 * 逐个字符取 [TextLayoutResult.getBoundingBox] 再按行合并, 而不用 `getPathForRange(start, end).getBounds()`:
 * Android 上终点恰好是下一行的行首 (在这里自动换行) 时, `getPathForRange` 把终点当作下一行的位置,
 * 路径包含这一行右侧的空白以及下一行, 合起来是一个占满宽度的两行高的矩形.
 * 宽度为零的字符应当排除: 桌面端和 iOS 取不到它们自己的范围时会取到其后字符的范围, 可能在下一行.
 */
internal inline fun TextLayoutResult.lineBoundsOf(
    start: Int,
    end: Int,
    include: (offset: Int) -> Boolean = { true },
): List<Rect> {
    val result = mutableListOf<Rect>()
    var currentLine = -1
    var current = Rect.Zero
    for (offset in start until end) {
        if (!include(offset)) continue
        val line = getLineForOffset(offset)
        val box = getBoundingBox(offset)
        if (line != currentLine) {
            if (currentLine >= 0) result.add(current)
            currentLine = line
            current = box
        } else {
            current = Rect(
                left = minOf(current.left, box.left),
                top = minOf(current.top, box.top),
                right = maxOf(current.right, box.right),
                bottom = maxOf(current.bottom, box.bottom),
            )
        }
    }
    if (currentLine >= 0) result.add(current)
    return result
}

/**
 * 文本框请求显示文字操作工具栏的状态, 作为 `LocalTextContextMenuToolbarProvider` 提供给评论编辑框.
 *
 * Android 上 Compose 默认启用新的文字操作菜单 (`ComposeFoundationFlags.isNewContextMenuEnabled`). 文本框在点按光标手柄、
 * 长按空白处、长按选中文字等时机请求显示工具栏, 系统的实现在评论面板中显示不出来.
 * 这里只记录请求, 由 [CommentFormattingToolbarPopup] 显示: 选区为空时只有粘贴和全选, 这是用触摸在光标处粘贴的方式.
 * 选区不为空时编辑框获得焦点就会显示工具栏, 与这个请求无关.
 *
 * 桌面端和 iOS 默认不启用新的菜单, 不会请求; 那里使用系统或 Compose 自带的菜单.
 */
@Stable
internal class CommentTextToolbarRequests : TextContextMenuProvider {
    private var activeRequests by mutableIntStateOf(0)

    /**
     * 文本框正在请求显示工具栏.
     */
    val requested: Boolean get() = activeRequests > 0

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        activeRequests++
        try {
            awaitCancellation()
        } finally {
            activeRequests--
        }
    }
}
