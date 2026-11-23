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
import me.him188.ani.app.ui.foundation.text.DetectedUrl
import me.him188.ani.app.ui.foundation.text.findUrls

/**
 * 把用户粘贴进评论编辑框的网址转为 BBCode 链接.
 *
 * 用 [InputTransformation] 而不是包装剪贴板, 是因为输入法的剪贴板候选 (`commitText`) 不经过剪贴板, 只有这里能看到.
 * 程序化的修改 (工具栏、插入表情和图片) 不经过 [InputTransformation], 不会被处理.
 *
 * 只处理恰好一处修改, 规则见 [linkifyChange]. 转换与粘贴属于同一次修改, 撤销时一起撤销.
 */
internal object CommentPasteLinkifyTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (changes.changeCount != 1) return
        val linkified = linkifyChange(
            originalText = originalText,
            originalSelection = originalSelection,
            originalRange = changes.getOriginalRange(0),
            text = asCharSequence(),
            range = changes.getRange(0),
        ) ?: return
        replace(linkified.range.min, linkified.range.max, linkified.replacement)
        selection = TextRange(linkified.range.min + linkified.replacement.length)
    }
}

/**
 * 把文本中 [range] 的文字替换为 [replacement], 光标移到 [replacement] 中的 [cursor] 处 (默认在末尾).
 */
internal class TextReplacement(
    val range: TextRange,
    val replacement: String,
    val cursor: Int = replacement.length,
)

/**
 * 计算一处修改在自动识别链接后应该变成什么. 修改把 [originalText] 中 [originalRange] 的文字替换为了 [text] 中 [range] 的文字.
 *
 * 安卓输入法提交文字时, Compose 会去掉新旧文字相同的开头和结尾 (`TextFieldBuffer.imeReplace`), 记录的修改可能只是选区的一部分.
 * 例如选中 `here` 后从输入法剪贴板提交 `https://a.com/x`, 记录的修改是把 `ere` 替换为 `ttps://a.com/x`.
 * 修改位于选区 [originalSelection] 内时, 选区外的文字没有变化, 因此按整个选区被替换处理.
 *
 * @return 对 [text] 的替换, 光标应位于替换的文字之后. 不需要转换时为 `null`.
 * @see linkifyInsertion
 */
internal fun linkifyChange(
    originalText: CharSequence,
    originalSelection: TextRange,
    originalRange: TextRange,
    text: CharSequence,
    range: TextRange,
): TextReplacement? {
    val replacedSelection = !originalSelection.collapsed && originalRange in originalSelection
    val replacedRange: TextRange
    val insertedRange: TextRange
    if (replacedSelection) {
        replacedRange = originalSelection
        insertedRange = TextRange(
            originalSelection.min,
            originalSelection.max + (range.length - originalRange.length),
        )
    } else {
        replacedRange = originalRange
        insertedRange = range
    }
    val replacement = linkifyInsertion(
        textBefore = text.subSequence(0, insertedRange.min),
        inserted = text.substring(insertedRange),
        replaced = originalText.substring(replacedRange),
        replacedSelection = replacedSelection,
    ) ?: return null
    return TextReplacement(insertedRange, replacement)
}

/**
 * 计算一次插入在自动识别链接后应该变成什么.
 *
 * - 插入的文字没有 http(s) 网址时不处理. 逐字输入永远不会一次插入完整网址, 所以不影响打字.
 * - 输入法组字时会用更长的文字替换正在组的文字 (插入的文字以被替换的文字开头), 不处理.
 * - 插入点位于未闭合的 `[url=`、`[img=` 属性, 或 `[url]`、`[img]` 内容中: 插入的内容 (去掉首尾空白) 恰好是一个网址时,
 *   去掉空白并按所在位置编码, 例如在工具栏留下的 `[url=]文字[/url]` 的 `=` 后粘贴地址; 否则不处理.
 * - 插入点位于未闭合的 `[url=..]` 链接的文字中: 插入的内容 (去掉首尾空白) 恰好是一个网址时只编码网址, 保留首尾的空白,
 *   因为这里的空白是文字的一部分; 否则不处理.
 * - 插入点位于未闭合的 `[code]` 中时不处理.
 * - 用户选中了不含网址、链接和图片的文字, 粘贴的内容 (去掉首尾空白) 恰好是一个网址: 变为 `[url=网址]选中的文字[/url]`.
 * - 其他情况: 每个网址变为 `[url]网址[/url]`. 粘贴的内容里位于 `[url]`、`[url=`、`[img]` 或 `[code]` 中的网址
 *   (如 `[img]http..`、`[url="http..`) 保持原样.
 *
 * 网址在 BBCode 标签前结束, 例如 `[b]见 https://a.com[/b]` 中的网址是 `https://a.com`;
 * 后文没有对应闭标签的开标签不算标签, 例如 `https://a.com/?filter[b]=x` 整个是网址.
 * 网址中会破坏 BBCode 的字符按 [encodeUrlForBBCodeAttribute] / [encodeUrlForBBCodeContent] 编码.
 *
 * @param textBefore 插入点之前的全部文本.
 * @param inserted 新插入的文字.
 * @param replaced 被替换掉的原文字, 纯插入时为空.
 * @param replacedSelection 被替换的是否正是用户的选区 (而不是输入法正在组的文字).
 * @return 替换 [inserted] 的文字. 不需要转换时为 `null`.
 */
internal fun linkifyInsertion(
    textBefore: CharSequence,
    inserted: String,
    replaced: String,
    replacedSelection: Boolean,
): String? {
    if (replaced.isNotEmpty() && inserted.startsWith(replaced)) return null
    val urls = findUrlsBeforeBBCodeTags(inserted)
    if (urls.isEmpty()) return null
    val singleUrl = urls.singleOrNull()?.takeIf { it.url == inserted.trim() }

    when (rawUrlContextAt(textBefore)) {
        RawUrlContext.Attribute -> return singleUrl?.url?.let(::encodeUrlForBBCodeAttribute)?.takeIf { it != inserted }
        RawUrlContext.Content -> return singleUrl?.url?.let(::encodeUrlForBBCodeContent)?.takeIf { it != inserted }
        RawUrlContext.LinkText -> return singleUrl
            ?.let { inserted.replaceRange(it.range, encodeUrlForBBCodeContent(it.url)) }
            ?.takeIf { it != inserted }

        RawUrlContext.Code -> return null
        null -> {}
    }

    if (singleUrl != null && replacedSelection && replaced.isNotEmpty() && replaced.isPlainLinkText()) {
        return "[url=" + encodeUrlForBBCodeAttribute(singleUrl.url) + "]" + replaced + "[/url]"
    }

    val before = textBefore.toString()
    var cursor = 0
    var linked = false
    val result = StringBuilder()
    for (detected in urls) {
        val start = detected.range.first
        if (isInsideRawUrlContext(before + inserted.substring(0, start))) continue
        result.append(inserted, cursor, start)
        result.append("[url]").append(encodeUrlForBBCodeContent(detected.url)).append("[/url]")
        cursor = detected.range.last + 1
        linked = true
    }
    if (!linked) return null
    result.append(inserted, cursor, inserted.length)
    return result.toString()
}

/**
 * 可以作为 `[url=..]` 链接文字的选中内容: 不含网址, 也不含链接和图片标签.
 * 选中的是网址或链接时, 粘贴的网址应替换它, 而不是把旧网址当作新链接的文字.
 */
private fun String.isPlainLinkText(): Boolean =
    findUrls(this).isEmpty() && !contains("[url", ignoreCase = true) && !contains("[img", ignoreCase = true)

/**
 * 与 [findUrls] 相同, 但网址在 BBCode 标签前结束.
 *
 * [findUrls] 把 `[` 当作网址的一部分 (如 `?a[]=1`), 所以只在 `[` 开始一个标签时截断网址, 截断后剩下的部分仍会识别其中的网址.
 * 闭标签 (如 `[/b]`) 总是开始一个标签. 查询参数中常有与开标签相同的写法 (如 `?filter[b]=x`),
 * 所以开标签只有在后文有对应的闭标签时才开始一个标签.
 */
private fun findUrlsBeforeBBCodeTags(text: String): List<DetectedUrl> {
    val masked = text.toCharArray()
    for (tag in BBCodeTag.findAll(text)) {
        val (closingName, openingName, attributeName) = tag.destructured
        val name = openingName.ifEmpty { attributeName }
        val startsTag = closingName.isNotEmpty() || text.indexOf("[/$name]", tag.range.last + 1, ignoreCase = true) >= 0
        if (startsTag) masked[tag.range.first] = ' ' // 换成空白不改变长度, 网址的位置仍对应原文
    }
    return findUrls(masked.concatToString())
}

/**
 * BBCode 解析器支持的标签 (BBCode.g4, `[img=宽,高]` 由 `BBCode.kt` 处理). 匹配到的是以下之一:
 * - 闭标签, 第 1 组为标签名;
 * - 不带属性的开标签, 第 2 组为标签名;
 * - 带属性的开标签的开头 (如 `[url=`), 第 3 组为标签名.
 */
private val BBCodeTag = Regex(
    """\[(?:/(b|i|u|s|code|mask|quote|size|color|center|left|right|url|img)]""" +
            """|(b|i|u|s|code|mask|quote|center|left|right|url|img)]""" +
            """|(url|img|size|color)=)""",
    RegexOption.IGNORE_CASE,
)

/**
 * 插入点所在的、网址按原样使用的 BBCode 位置.
 */
internal enum class RawUrlContext {
    /**
     * `[url=` 或 `[img=` 之后、`]` 之前的属性值, 即链接或图片的地址.
     */
    Attribute,

    /**
     * `[url]..[/url]` 或 `[img]..[/img]` 的内容, 即链接或图片的地址.
     */
    Content,

    /**
     * `[url=..]..[/url]` 的内容, 即链接显示的文字.
     */
    LinkText,

    /**
     * `[code]..[/code]` 的内容.
     */
    Code,
}

/**
 * [textBefore] 末尾处于哪个未闭合的 `[url]`、`[url=`、`[img]`、`[img=` 或 `[code]` 中, 都不在时为 `null`.
 * 同时位于 `[code]` 和其他标签中时为 [RawUrlContext.Code], 因为代码中的文字都应原样保留.
 * 同时位于链接和图片中时按后打开的标签判断.
 */
internal fun rawUrlContextAt(textBefore: CharSequence): RawUrlContext? {
    // 最后一个开标签的位置, 它已闭合时为 -1
    fun lastUnclosed(close: String, vararg opens: String): Int {
        val lastOpen = opens.maxOf { textBefore.lastIndexOf(it, ignoreCase = true) }
        return if (lastOpen > textBefore.lastIndexOf(close, ignoreCase = true)) lastOpen else -1
    }
    if (lastUnclosed("[/code]", "[code]") >= 0) return RawUrlContext.Code
    val url = lastUnclosed("[/url]", "[url]", "[url=")
    val img = lastUnclosed("[/img]", "[img]", "[img=")
    val open = maxOf(url, img)
    if (open < 0) return null
    val hasAttribute = textBefore[open + "[url".length] == '='
    return when {
        hasAttribute && textBefore.indexOf(']', open) < 0 -> RawUrlContext.Attribute
        hasAttribute && open == url -> RawUrlContext.LinkText
        else -> RawUrlContext.Content
    }
}

/**
 * [textBefore] 末尾是否处于未闭合的 `[url]`、`[url=`、`[img]`、`[img=` 或 `[code]` 中. 这些位置的网址不包 `[url]`.
 *
 * @see rawUrlContextAt
 */
internal fun isInsideRawUrlContext(textBefore: CharSequence): Boolean = rawUrlContextAt(textBefore) != null
