/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.reviews

import me.him188.ani.app.ui.richtext.UIRichElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TvReviewPreviewTest {
    @Test
    fun `masked text and links never appear in preview semantics`() {
        val preview = reviewPreview(listOf(UIRichElement.AnnotatedText(listOf(
            UIRichElement.Annotated.Text("Visible", url = "https://example.com"),
            UIRichElement.Annotated.Text("Secret", mask = true, url = "https://example.com/spoiler"),
        ))), "Quote", "Image", "Hidden")
        val spans = preview.slice.filterIsInstance<UIRichElement.Annotated.Text>()
        assertEquals(listOf("Visible", "Hidden"), spans.map { it.content })
        assertFalse(spans.any { it.url != null || it.mask })
    }

    @Test
    fun `media and quotes keep readable placeholders instead of leaking nested content`() {
        val preview = reviewPreview(listOf(
            UIRichElement.Image("https://example.com/image.png", null),
            UIRichElement.Quote(listOf(UIRichElement.AnnotatedText(listOf(UIRichElement.Annotated.Text("Nested secret", mask = true))))),
        ), "Quote", "Image", "Hidden")
        assertEquals(listOf("Image", "Quote"), preview.slice.map { (it as UIRichElement.Annotated.Text).content })
    }
}
