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

import org.antlr.v4.kotlinruntime.tree.ParseTreeListener

/**
 * This interface defines a complete listener for a parse tree produced by [BBCodeParser].
 */
public interface BBCodeListener : ParseTreeListener {
    /**
     * Enter a parse tree produced by [BBCodeParser.file].
     *
     * @param ctx The parse tree
     */
    public fun enterFile(ctx: BBCodeParser.FileContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.file].
     *
     * @param ctx The parse tree
     */
    public fun exitFile(ctx: BBCodeParser.FileContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.section].
     *
     * @param ctx The parse tree
     */
    public fun enterSection(ctx: BBCodeParser.SectionContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.section].
     *
     * @param ctx The parse tree
     */
    public fun exitSection(ctx: BBCodeParser.SectionContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.element].
     *
     * @param ctx The parse tree
     */
    public fun enterElement(ctx: BBCodeParser.ElementContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.element].
     *
     * @param ctx The parse tree
     */
    public fun exitElement(ctx: BBCodeParser.ElementContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.plain].
     *
     * @param ctx The parse tree
     */
    public fun enterPlain(ctx: BBCodeParser.PlainContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.plain].
     *
     * @param ctx The parse tree
     */
    public fun exitPlain(ctx: BBCodeParser.PlainContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.b].
     *
     * @param ctx The parse tree
     */
    public fun enterB(ctx: BBCodeParser.BContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.b].
     *
     * @param ctx The parse tree
     */
    public fun exitB(ctx: BBCodeParser.BContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.i].
     *
     * @param ctx The parse tree
     */
    public fun enterI(ctx: BBCodeParser.IContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.i].
     *
     * @param ctx The parse tree
     */
    public fun exitI(ctx: BBCodeParser.IContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.u].
     *
     * @param ctx The parse tree
     */
    public fun enterU(ctx: BBCodeParser.UContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.u].
     *
     * @param ctx The parse tree
     */
    public fun exitU(ctx: BBCodeParser.UContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.s].
     *
     * @param ctx The parse tree
     */
    public fun enterS(ctx: BBCodeParser.SContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.s].
     *
     * @param ctx The parse tree
     */
    public fun exitS(ctx: BBCodeParser.SContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.code].
     *
     * @param ctx The parse tree
     */
    public fun enterCode(ctx: BBCodeParser.CodeContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.code].
     *
     * @param ctx The parse tree
     */
    public fun exitCode(ctx: BBCodeParser.CodeContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.mask].
     *
     * @param ctx The parse tree
     */
    public fun enterMask(ctx: BBCodeParser.MaskContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.mask].
     *
     * @param ctx The parse tree
     */
    public fun exitMask(ctx: BBCodeParser.MaskContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.quote].
     *
     * @param ctx The parse tree
     */
    public fun enterQuote(ctx: BBCodeParser.QuoteContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.quote].
     *
     * @param ctx The parse tree
     */
    public fun exitQuote(ctx: BBCodeParser.QuoteContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.size].
     *
     * @param ctx The parse tree
     */
    public fun enterSize(ctx: BBCodeParser.SizeContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.size].
     *
     * @param ctx The parse tree
     */
    public fun exitSize(ctx: BBCodeParser.SizeContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.color].
     *
     * @param ctx The parse tree
     */
    public fun enterColor(ctx: BBCodeParser.ColorContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.color].
     *
     * @param ctx The parse tree
     */
    public fun exitColor(ctx: BBCodeParser.ColorContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.bgm_sticker].
     *
     * @param ctx The parse tree
     */
    public fun enterBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.bgm_sticker].
     *
     * @param ctx The parse tree
     */
    public fun exitBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.text_stiker].
     *
     * @param ctx The parse tree
     */
    public fun enterText_stiker(ctx: BBCodeParser.Text_stikerContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.text_stiker].
     *
     * @param ctx The parse tree
     */
    public fun exitText_stiker(ctx: BBCodeParser.Text_stikerContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.url].
     *
     * @param ctx The parse tree
     */
    public fun enterUrl(ctx: BBCodeParser.UrlContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.url].
     *
     * @param ctx The parse tree
     */
    public fun exitUrl(ctx: BBCodeParser.UrlContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.url_named].
     *
     * @param ctx The parse tree
     */
    public fun enterUrl_named(ctx: BBCodeParser.Url_namedContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.url_named].
     *
     * @param ctx The parse tree
     */
    public fun exitUrl_named(ctx: BBCodeParser.Url_namedContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.img].
     *
     * @param ctx The parse tree
     */
    public fun enterImg(ctx: BBCodeParser.ImgContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.img].
     *
     * @param ctx The parse tree
     */
    public fun exitImg(ctx: BBCodeParser.ImgContext)

    /**
     * Enter a parse tree produced by [BBCodeParser.attribute_value].
     *
     * @param ctx The parse tree
     */
    public fun enterAttribute_value(ctx: BBCodeParser.Attribute_valueContext)

    /**
     * Exit a parse tree produced by [BBCodeParser.attribute_value].
     *
     * @param ctx The parse tree
     */
    public fun exitAttribute_value(ctx: BBCodeParser.Attribute_valueContext)

}
