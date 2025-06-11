/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.bbcode

import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.tree.ErrorNode

data class RichText(
    val elements: List<RichElement>,
)

object BBCode {
    fun parse(text: String): RichText {
        val lexer = BBCodeLexer(CharStreams.fromString(text))
        lexer.removeErrorListeners() // it prints to stderr
        val file = BBCodeParser(CommonTokenStream(lexer)).run {
            removeErrorListeners()
            file()
        }
        file.section().element()
        return RichText(file.toElements())
    }
}

private data class Context(
    val size: Int = RichElement.Text.DEFAULT_SIZE,
    val color: String? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isCode: Boolean = false,
    val isMask: Boolean = false,
    val jumpUrl: String? = null,
)

private fun ParserRuleContext.toElements(initialContext: Context = Context()): List<RichElement> {
    val builder = ElementBuilder(initialContext)
    accept(AstToRichElementVisitor(builder))
    return builder.build()
}

private class ElementBuilder(
    initialContext: Context
) {
    private val contexts = mutableListOf(initialContext)

    private val context get() = contexts.last()

    private inline fun pushContext(new: Context.() -> Context) {
        contexts.add(contexts.last().new())
    }

    private fun popContext() {
        // Caused by: java.lang.NoSuchMethodError: No interface method removeLast()Ljava/lang/Object; in class Ljava/util/List; or its super classes (declaration of 'java.util.List' appears in /apex/com.android.art/javalib/core-oj.jar)
        if (contexts.isEmpty()) throw NoSuchElementException("List is empty.") else contexts.removeAt(contexts.lastIndex)
    }

    inline fun withContext(new: Context.() -> Context, block: () -> Unit) {
        pushContext(new)
        block()
        popContext()
    }

    private val elements = mutableListOf<RichElement>()

    fun emitText(text: String) {
        val context = context
        elements.add(
            RichElement.Text(
                text,
                size = context.size,
                color = context.color,
                bold = context.isBold,
                italic = context.isItalic,
                underline = context.isUnderline,
                strikethrough = context.isStrikethrough,
                mask = context.isMask,
                code = context.isCode,
                jumpUrl = context.jumpUrl,
            ),
        )
    }

    fun emitQuote(content: List<RichElement>) {
        val context = context
        elements.add(
            RichElement.Quote(
                RichText(content),
                jumpUrl = context.jumpUrl,
            ),
        )
    }

    fun emitBangumiSticker(id: Int) {
        val context = context
        elements.add(
            RichElement.BangumiSticker(
                id,
                jumpUrl = context.jumpUrl,
            ),
        )
    }

    fun emitKanmoji(id: String) {
        val context = context
        elements.add(
            RichElement.Kanmoji(
                id,
                jumpUrl = context.jumpUrl,
            ),
        )
    }

    fun emitImage(url: String, width: Int? = null, height: Int? = null) {
        val context = context
        elements.add(
            RichElement.Image(
                url,
                width,
                height,
                jumpUrl = context.jumpUrl,
            ),
        )
    }

    fun build(): List<RichElement> = elements
}

private class AstToRichElementVisitor(
    private val builder: ElementBuilder = ElementBuilder(Context())
) : BBCodeBaseVisitor<Unit>() {
    override fun defaultResult(): Unit = Unit

    override fun visitB(ctx: BBCodeParser.BContext) {
        builder.withContext({ copy(isBold = true) }) {
            visitChildren(ctx)
        }
    }

    override fun visitI(ctx: BBCodeParser.IContext) {
        builder.withContext({ copy(isItalic = true) }) {
            visitChildren(ctx)
        }
    }

    override fun visitU(ctx: BBCodeParser.UContext) {
        builder.withContext({ copy(isUnderline = true) }) {
            visitChildren(ctx)
        }
    }

    override fun visitS(ctx: BBCodeParser.SContext) {
        builder.withContext({ copy(isStrikethrough = true) }) {
            visitChildren(ctx)
        }
    }

    override fun visitCode(ctx: BBCodeParser.CodeContext) {
        builder.withContext({ copy(isCode = true) }) {
            visitChildren(ctx)
        }
    }

    override fun visitMask(ctx: BBCodeParser.MaskContext) {
        builder.withContext({ copy(isMask = true) }) {
            visitChildren(ctx)
        }
    }

    override fun visitSize(ctx: BBCodeParser.SizeContext) {
        builder.withContext({ copy(size = ctx.value?.text?.toIntOrNull() ?: RichElement.Text.DEFAULT_SIZE) }) {
            visitChildren(ctx)
        }
    }

    override fun visitColor(ctx: BBCodeParser.ColorContext) {
        builder.withContext({ copy(color = ctx.value?.text) }) {
            visitChildren(ctx)
        }
    }

    override fun visitQuote(ctx: BBCodeParser.QuoteContext) {
        val content = ctx.content
        builder.emitQuote(content?.toElements().orEmpty())
    }

    override fun visitBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext) {
        if (ctx.id == null) {
            builder.emitText(ctx.text)
        } else {
            builder.emitBangumiSticker(ctx.id?.text?.toInt() ?: 0)
        }
    }

    override fun visitText_stiker(ctx: BBCodeParser.Text_stikerContext) {
        builder.emitKanmoji(ctx.text)
    }

    override fun visitUrl(ctx: BBCodeParser.UrlContext) {
        builder.withContext({ copy(jumpUrl = ctx.href?.text) }) {
            visitChildren(ctx)
        }
    }

    override fun visitUrl_named(ctx: BBCodeParser.Url_namedContext) {
        builder.withContext(
            {
                copy(
                    jumpUrl = ctx.attribute_value().QUOTED()?.text?.removeSurrounding("\"")
                        ?: ctx.attribute_value().TEXT()?.text,
                )
            },
        ) {
            visitChildren(ctx)
        }
    }

    override fun visitPlain(ctx: BBCodeParser.PlainContext) {
        builder.emitText(ctx.text)
    }

    override fun visitErrorNode(node: ErrorNode) {
    }

    override fun visitImg(ctx: BBCodeParser.ImgContext) {
        val text = ctx.text
        val content = text.substringAfter(']').substringBeforeLast('[')
        val (w, h) = Regex("""(?i)\[img=(\d+),(\d+)]""").find(text)
            ?.destructured?.let { it.component1().toIntOrNull() to it.component2().toIntOrNull() } ?: (null to null)

        builder.emitImage(content, w, h)
    }

}
