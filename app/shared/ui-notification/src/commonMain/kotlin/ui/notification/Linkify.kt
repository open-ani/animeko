/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

/**
 * 纯文本中的一个 http(s) 网址.
 *
 * @property range 网址在原文中的位置.
 */
@Immutable
data class DetectedUrl(
    val range: IntRange,
    val url: String,
)

/**
 * 网址到空白或中日韩标点 (全角括号、逗号、句号等) 为止. 公告和系统消息多为中文, 网址后常紧跟全角标点.
 */
private val UrlPattern = Regex("https?://[^\\s<>\"'\u3000-\u303F\uFF00-\uFFEF]+", RegexOption.IGNORE_CASE)

/**
 * 找出 [text] 中的 http(s) 网址.
 *
 * 网址末尾的句末标点 (`.`, `,`, `!` 等) 和没有配对的右括号不算作网址, 例如 `见 https://example.com/a).` 中的网址为
 * `https://example.com/a`.
 */
fun findUrls(text: String): List<DetectedUrl> = UrlPattern.findAll(text).mapNotNull { match ->
    val start = match.range.first
    var end = match.range.last + 1 // exclusive
    while (end > start) {
        val shouldTrim = when (text[end - 1]) {
            '.', ',', ';', ':', '!', '?' -> true
            ')' -> text.count(start, end, '(') < text.count(start, end, ')')
            ']' -> text.count(start, end, '[') < text.count(start, end, ']')
            '}' -> text.count(start, end, '{') < text.count(start, end, '}')
            else -> false
        }
        if (!shouldTrim) break
        end--
    }
    val url = text.substring(start, end)
    if (url.substringAfter("://").isEmpty()) return@mapNotNull null
    DetectedUrl(start until end, url)
}.toList()

private fun String.count(start: Int, end: Int, char: Char): Int {
    var count = 0
    for (i in start until end) {
        if (this[i] == char) count++
    }
    return count
}

/**
 * 把 [text] 中的网址转为可点击的链接, 其余文本原样保留.
 *
 * @param onClickUrl 点击链接时调用. 为 `null` 时由平台默认的 `UriHandler` 打开.
 */
fun linkifyText(
    text: String,
    linkStyles: TextLinkStyles? = null,
    onClickUrl: ((String) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (detected in findUrls(text)) {
        append(text, cursor, detected.range.first)
        val listener = onClickUrl?.let { onClick -> LinkInteractionListener { onClick(detected.url) } }
        withLink(LinkAnnotation.Url(detected.url, linkStyles, listener)) {
            append(detected.url)
        }
        cursor = detected.range.last + 1
    }
    append(text, cursor, text.length)
}
