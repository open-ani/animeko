/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.richtext

import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource

sealed interface UIRichElement {
    sealed interface Annotated {
        val url: String?

        data class Text(
            val content: String,
            val size: Float = RichTextDefaults.FontSize,
            val color: Color = Color.Unspecified,

            val italic: Boolean = false,
            val underline: Boolean = false,
            val strikethrough: Boolean = false,
            val bold: Boolean = false,

            val mask: Boolean = false,
            val code: Boolean = false,

            override val url: String? = null
        ) : Annotated

        data class Sticker(
            val id: String,
            val resource: DrawableResource?,
            override val url: String? = null
        ) : Annotated
    }

    data class AnnotatedText(val slice: List<Annotated>, val maxLine: Int? = null) : UIRichElement

    data class Quote(val content: List<UIRichElement>) : UIRichElement

    data class Image(val imageUrl: String, val jumpUrl: String?) : UIRichElement
}
