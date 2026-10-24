/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import me.him188.ani.app.ui.foundation.text.findUrls

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
