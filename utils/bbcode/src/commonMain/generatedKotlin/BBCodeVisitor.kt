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

import org.antlr.v4.kotlinruntime.tree.ParseTreeVisitor

/**
 * This interface defines a complete generic visitor for a parse tree produced by [BBCodeParser].
 *
 * @param T The return type of the visit operation.
 *   Use [Unit] for operations with no return type
 */
public interface BBCodeVisitor<T> : ParseTreeVisitor<T> {
    /**
     * Visit a parse tree produced by [BBCodeParser.file].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitFile(ctx: BBCodeParser.FileContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.section].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitSection(ctx: BBCodeParser.SectionContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.element].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitElement(ctx: BBCodeParser.ElementContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.plain].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitPlain(ctx: BBCodeParser.PlainContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.b].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitB(ctx: BBCodeParser.BContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.i].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitI(ctx: BBCodeParser.IContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.u].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitU(ctx: BBCodeParser.UContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.s].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitS(ctx: BBCodeParser.SContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.code].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitCode(ctx: BBCodeParser.CodeContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.mask].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitMask(ctx: BBCodeParser.MaskContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.quote].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitQuote(ctx: BBCodeParser.QuoteContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.size].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitSize(ctx: BBCodeParser.SizeContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.color].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitColor(ctx: BBCodeParser.ColorContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.bgm_sticker].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.text_stiker].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitText_stiker(ctx: BBCodeParser.Text_stikerContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.url].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitUrl(ctx: BBCodeParser.UrlContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.url_named].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitUrl_named(ctx: BBCodeParser.Url_namedContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.img].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitImg(ctx: BBCodeParser.ImgContext): T

    /**
     * Visit a parse tree produced by [BBCodeParser.attribute_value].
     *
     * @param ctx The parse tree
     * @return The visitor result
     */
    public fun visitAttribute_value(ctx: BBCodeParser.Attribute_valueContext): T

}
