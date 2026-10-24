/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.text

import androidx.compose.runtime.Immutable

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
 * 网址到空白、`<` `>` `"` `'` 或下列中文常用标点为止. 公告、系统消息和评论多为中文, 网址后常紧跟这些标点.
 *
 * - U+2014–U+2015: 破折号 `—`
 * - U+2018–U+201F: 引号 `‘’“”` 等
 * - U+2026: 省略号 `…`
 * - U+3000–U+303F: 中日韩符号和标点, 如全角空格、`。`、`、`、`「」`、`【】`
 * - U+FF00–U+FFEF: 全角与半角字符, 如 `（）`、`，`、`：`
 *
 * 这些标点很少以未编码的形式出现在网址中. 网址后直接跟着的汉字仍算作网址的一部分, 因为网址中可能有未编码的汉字
 * (如维基百科的条目名).
 */
private val UrlPattern = Regex(
    "https?://[^\\s<>\"'\u2014-\u2015\u2018-\u201F\u2026\u3000-\u303F\uFF00-\uFFEF]+",
    RegexOption.IGNORE_CASE,
)

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
