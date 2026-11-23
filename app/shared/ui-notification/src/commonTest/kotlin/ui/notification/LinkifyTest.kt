/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.ui.text.LinkAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkifyTest {
    @Test
    fun `linkified text keeps all characters and links urls`() {
        val text = "前 https://myani.org/a。后\n第二行 http://b.org"
        val annotated = linkifyText(text)
        assertEquals(text, annotated.text)
        val links = annotated.getLinkAnnotations(0, text.length)
        assertEquals(
            listOf("https://myani.org/a", "http://b.org"),
            links.map { (it.item as LinkAnnotation.Url).url },
        )
        assertEquals(
            listOf("https://myani.org/a", "http://b.org"),
            links.map { text.substring(it.start, it.end) },
        )
    }

    @Test
    fun `text without urls has no links`() {
        val annotated = linkifyText("没有链接")
        assertEquals("没有链接", annotated.text)
        assertEquals(emptyList(), annotated.getLinkAnnotations(0, annotated.length))
    }
}
