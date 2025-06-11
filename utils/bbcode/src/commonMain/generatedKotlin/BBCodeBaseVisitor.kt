/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// Generated from /Users/general_k1ng/Documents/project/animeko/utils/bbcode/BBCode.g4 by ANTLR 4.13.1
package me.him188.ani.utils.bbcode

import org.antlr.v4.kotlinruntime.tree.AbstractParseTreeVisitor

/**
 * This class provides an empty implementation of [BBCodeVisitor],
 * which can be extended to create a visitor which only needs to handle a subset
 * of the available methods.
 *
 * @param T The return type of the visit operation.
 *   Use [Unit] for operations with no return type
 */
public abstract class BBCodeBaseVisitor<T> : AbstractParseTreeVisitor<T>(), BBCodeVisitor<T> {
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitFile(ctx: BBCodeParser.FileContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitSection(ctx: BBCodeParser.SectionContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitElement(ctx: BBCodeParser.ElementContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitPlain(ctx: BBCodeParser.PlainContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitB(ctx: BBCodeParser.BContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitI(ctx: BBCodeParser.IContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitU(ctx: BBCodeParser.UContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitS(ctx: BBCodeParser.SContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitCode(ctx: BBCodeParser.CodeContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitMask(ctx: BBCodeParser.MaskContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitQuote(ctx: BBCodeParser.QuoteContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitSize(ctx: BBCodeParser.SizeContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitColor(ctx: BBCodeParser.ColorContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitText_stiker(ctx: BBCodeParser.Text_stikerContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitUrl(ctx: BBCodeParser.UrlContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitUrl_named(ctx: BBCodeParser.Url_namedContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitImg(ctx: BBCodeParser.ImgContext): T {
        return this.visitChildren(ctx)
    }
    /**
     * The default implementation returns the result of calling [visitChildren] on [ctx].
     */
    override fun visitAttribute_value(ctx: BBCodeParser.Attribute_valueContext): T {
        return this.visitChildren(ctx)
    }
}
