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
import me.him188.ani.app.ui.foundation.text.findUrls

/**
 * 评论编辑框的文本与选区.
 *
 * 格式化、插入等编辑操作都是 `CommentText -> CommentText` 的纯函数, 由 [CommentEditorState] 在一次
 * `TextFieldState.edit` 里应用, 因此每个操作是一个撤销步骤.
 */
@Immutable
internal data class CommentText(
    val text: String,
    val selection: TextRange = TextRange(text.length),
)

/**
 * 选区工具栏可以切换的 BBCode 格式. 标签不区分大小写, `[B]` 与 `[b]` 等价.
 */
enum class CommentFormat(
    internal val openTag: String,
    internal val closeTag: String,
) {
    Bold("[b]", "[/b]"),
    Italic("[i]", "[/i]"),
    Underline("[u]", "[/u]"),
    Strikethrough("[s]", "[/s]"),

    /**
     * 剧透遮罩
     */
    Mask("[mask]", "[/mask]"),
}

/**
 * 选区被 [format] 包裹时去掉标签, 否则用标签包裹选区.
 *
 * 两种情况都保持原来的文字处于选中状态, 所以工具栏不会消失, 可以连续叠加格式. 选区为空时插入一对空标签, 光标位于两者之间.
 *
 * @see isFormatActive
 */
internal fun CommentText.toggleFormat(format: CommentFormat): CommentText {
    findFormatWrapping(format)?.let { return unwrap(it) }
    return wrap(format.openTag, format.closeTag)
}

/**
 * 选区是否已经被 [format] 包裹: 标签紧贴在选区两侧, 或选中的文字本身以开标签开头、以闭标签结尾.
 * 两个标签之间的同类标签必须配对, 例如选中 `[b]x[/b] [b]y[/b]` 不算已加粗.
 */
internal fun CommentText.isFormatActive(format: CommentFormat): Boolean = findFormatWrapping(format) != null

/**
 * 选区已是链接时去掉链接, 否则把选区变成链接.
 *
 * - 选中的文字 (去掉首尾空白后) 恰好是一个 http(s) 网址: 变为 `[url]网址[/url]`, 网址保持选中.
 * - 其他文字: 变为 `[url=]文字[/url]`, 光标放在 `=` 之后, 等用户输入或粘贴地址.
 * - 已是链接 (`[url]..[/url]` 或 `[url=..]..[/url]`): 去掉标签, 只保留链接文字并选中.
 */
internal fun CommentText.toggleLink(): CommentText {
    findLinkWrapping()?.let { return unwrap(it) }

    val min = selection.min
    val max = selection.max
    val selected = text.substring(min, max)
    val trimmed = selected.trim()
    val url = findUrls(trimmed).singleOrNull()?.url?.takeIf { it == trimmed }
    if (url != null) {
        val urlStart = min + (selected.length - selected.trimStart().length)
        val urlEnd = max - (selected.length - selected.trimEnd().length)
        val encoded = encodeUrlForBBCodeContent(url)
        val innerStart = urlStart + URL_OPEN.length
        return CommentText(
            text.substring(0, urlStart) + URL_OPEN + encoded + URL_CLOSE + text.substring(urlEnd),
            TextRange(innerStart, innerStart + encoded.length),
        )
    }

    return CommentText(
        text.substring(0, min) + URL_NAMED_OPEN_PREFIX + "]" + selected + URL_CLOSE + text.substring(max),
        TextRange(min + URL_NAMED_OPEN_PREFIX.length),
    )
}

/**
 * 选区是否已经是链接.
 *
 * @see toggleLink
 */
internal fun CommentText.isLinkActive(): Boolean = findLinkWrapping() != null

/**
 * 在光标处插入 [value], 光标移到插入的文字之后. 有选区时插入到选区末尾, 不会删除选中的文字.
 * 光标位于图片标签内部时插入到标签之后.
 */
internal fun CommentText.insertAtCursor(value: String): CommentText {
    val at = parseImageChips(text).firstOrNull { it.contains(selection.max) }?.range?.end ?: selection.max
    return CommentText(text.substring(0, at) + value + text.substring(at), TextRange(at + value.length))
}

/**
 * 在光标处插入图片 `[img]url[/img]`.
 *
 * @see insertAtCursor
 */
internal fun CommentText.insertImage(url: String): CommentText =
    insertAtCursor(IMG_OPEN + encodeUrlForBBCodeContent(url) + IMG_CLOSE)

/**
 * 文本中是否已有显示 [url] 的图片 `[img]url[/img]`. 标签不区分大小写, 地址区分大小写.
 *
 * @see insertImage
 */
internal fun CommentText.containsImage(url: String): Boolean {
    val encoded = encodeUrlForBBCodeContent(url)
    if (encoded.isEmpty()) return false
    var index = text.indexOf(encoded)
    while (index >= 0) {
        if (text.matchesAt(index - IMG_OPEN.length, IMG_OPEN) && text.matchesAt(index + encoded.length, IMG_CLOSE)) {
            return true
        }
        index = text.indexOf(encoded, index + 1)
    }
    return false
}

/**
 * 去掉没有地址的链接 `[url=]文字[/url]` 和 `[url=""]文字[/url]` 的标签, 只保留文字. 链接文字中还有链接标签时不处理.
 *
 * [toggleLink] 先留下空地址等用户填写, 而 BBCode 不显示空地址链接中的文字. 发送和预览前用它处理评论, 用户没有填写地址时文字不会丢失.
 */
internal fun removeEmptyLinks(text: String): String = EmptyLinkPattern.replace(text) { it.groupValues[1] }

private val EmptyLinkPattern = Regex("""\[url=(?:"")?\]((?:(?!\[/?url)[\s\S])*?)\[/url\]""", RegexOption.IGNORE_CASE)

/**
 * 编码网址中会破坏 `[url]..[/url]`、`[img]..[/img]` 内容的字符 (`[` 与 `]`).
 */
internal fun encodeUrlForBBCodeContent(url: String): String =
    url.replace("[", "%5B").replace("]", "%5D")

/**
 * 编码网址中会破坏 `[url=..]` 属性的字符. BBCode.g4 中未加引号的属性值是 `TEXT`, 不能包含 `[` `]` `(` `)`.
 */
internal fun encodeUrlForBBCodeAttribute(url: String): String =
    encodeUrlForBBCodeContent(url).replace("(", "%28").replace(")", "%29")

private const val URL_OPEN = "[url]"
private const val URL_NAMED_OPEN_PREFIX = "[url="
private const val URL_CLOSE = "[/url]"
private const val IMG_OPEN = "[img]"
private const val IMG_CLOSE = "[/img]"

/**
 * 一对标签在文本中的位置: 开标签 `[openStart, openEnd)`, 闭标签 `[closeStart, closeEnd)`.
 */
private class TagPair(
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int,
)

/**
 * 去掉 [pair] 的两个标签, 并选中标签之间的文字.
 */
private fun CommentText.unwrap(pair: TagPair): CommentText {
    val inner = text.substring(pair.openEnd, pair.closeStart)
    return CommentText(
        text.substring(0, pair.openStart) + inner + text.substring(pair.closeEnd),
        TextRange(pair.openStart, pair.openStart + inner.length),
    )
}

private fun CommentText.wrap(open: String, close: String): CommentText {
    val min = selection.min
    val max = selection.max
    return CommentText(
        text.substring(0, min) + open + text.substring(min, max) + close + text.substring(max),
        TextRange(min + open.length, max + open.length),
    )
}

private fun CommentText.findFormatWrapping(format: CommentFormat): TagPair? {
    val open = format.openTag
    val close = format.closeTag
    val min = selection.min
    val max = selection.max

    // 标签紧贴在选区两侧
    if (text.matchesAt(min - open.length, open) && text.matchesAt(max, close) &&
        text.isBalanced(min, max, open, close)
    ) {
        return TagPair(min - open.length, min, max, max + close.length)
    }

    // 选中的文字本身带着标签
    if (max - min >= open.length + close.length &&
        text.matchesAt(min, open) && text.matchesAt(max - close.length, close) &&
        text.isBalanced(min + open.length, max - close.length, open, close)
    ) {
        return TagPair(min, min + open.length, max - close.length, max)
    }
    return null
}

private fun CommentText.findLinkWrapping(): TagPair? {
    val min = selection.min
    val max = selection.max

    // 标签紧贴在选区两侧
    if (text.matchesAt(max, URL_CLOSE) && !text.containsUrlTag(min, max)) {
        if (text.matchesAt(min - URL_OPEN.length, URL_OPEN)) {
            return TagPair(min - URL_OPEN.length, min, max, max + URL_CLOSE.length)
        }
        val namedStart = text.findNamedUrlOpenEndingAt(min)
        if (namedStart >= 0) {
            return TagPair(namedStart, min, max, max + URL_CLOSE.length)
        }
    }

    // 选中的文字本身带着标签
    if (max - min >= URL_OPEN.length + URL_CLOSE.length && text.matchesAt(max - URL_CLOSE.length, URL_CLOSE)) {
        val closeStart = max - URL_CLOSE.length
        val openEnd = when {
            text.matchesAt(min, URL_OPEN) -> min + URL_OPEN.length
            text.matchesAt(min, URL_NAMED_OPEN_PREFIX) -> {
                val bracket = text.indexOf(']', min + URL_NAMED_OPEN_PREFIX.length)
                if (bracket in 0 until closeStart &&
                    text.indexOf('[', min + URL_NAMED_OPEN_PREFIX.length).let { it < 0 || it > bracket }
                ) bracket + 1 else -1
            }

            else -> -1
        }
        if (openEnd in 0..closeStart && !text.containsUrlTag(openEnd, closeStart)) {
            return TagPair(min, openEnd, closeStart, max)
        }
    }
    return null
}

/**
 * 找到恰好在 [end] 结束的 `[url=...]`, 返回它的起始位置; 没有时返回 -1.
 */
private fun String.findNamedUrlOpenEndingAt(end: Int): Int {
    if (end < 1 || this[end - 1] != ']') return -1
    val start = lastIndexOf(URL_NAMED_OPEN_PREFIX, startIndex = end - 1, ignoreCase = true)
    if (start < 0) return -1
    // 属性值里不能再有方括号, 否则这个 `]` 不属于该开标签
    for (i in start + URL_NAMED_OPEN_PREFIX.length until end - 1) {
        if (this[i] == '[' || this[i] == ']') return -1
    }
    return start
}

private fun String.containsUrlTag(start: Int, end: Int): Boolean {
    val region = substring(start, end)
    return region.contains("[url", ignoreCase = true) || region.contains(URL_CLOSE, ignoreCase = true)
}

private fun String.matchesAt(index: Int, tag: String): Boolean =
    index >= 0 && regionMatches(index, tag, 0, tag.length, ignoreCase = true)

/**
 * `[start, end)` 中的 [open] 与 [close] 是否两两配对.
 */
private fun String.isBalanced(start: Int, end: Int, open: String, close: String): Boolean {
    var depth = 0
    var i = start
    while (i < end) {
        when {
            i + open.length <= end && matchesAt(i, open) -> {
                depth++
                i += open.length
            }

            i + close.length <= end && matchesAt(i, close) -> {
                depth--
                if (depth < 0) return false
                i += close.length
            }

            else -> i++
        }
    }
    return depth == 0
}
