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

        /**
         * 一枚表情. [resource] 与 [imageUrl] 至少有一个非空, 都为空的代码应当在映射阶段
         * 退化成 [Text] (原样显示 `(bgm999)`), 而不是渲染成一块看不见的空白.
         */
        data class Sticker(
            /**
             * 完整的 BBCode 形态, 如 `"(bgm38)"` / `"(musume_06)"` / `"(=A=)"`.
             * 同时是行内内容的 id 和 [StickerSizes] 的键.
             */
            val id: String,
            /** 随包的图. 只有最早那 125 张有 ([me.him188.ani.app.ui.comment.BangumiCommentSticker]). */
            val resource: DrawableResource?,
            /** 图片站上的地址, 随包没图时按它现拉, 见 [me.him188.ani.app.ui.comment.BangumiStickers]. */
            val imageUrl: String? = null,
            override val url: String? = null,
        ) : Annotated
    }

    data class AnnotatedText(val slice: List<Annotated>, val maxLine: Int? = null) : UIRichElement

    data class Quote(val content: List<UIRichElement>) : UIRichElement

    data class Image(val imageUrl: String, val jumpUrl: String?) : UIRichElement
}
