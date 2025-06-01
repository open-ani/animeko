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

import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.tree.ErrorNode
import org.antlr.v4.kotlinruntime.tree.TerminalNode

/**
 * This class provides an empty implementation of [BBCodeListener],
 * which can be extended to create a listener which only needs to handle a subset
 * of the available methods.
 */
public open class BBCodeBaseListener : BBCodeListener {
    /**
     * The default implementation does nothing.
     */
    override fun enterFile(ctx: BBCodeParser.FileContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitFile(ctx: BBCodeParser.FileContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterSection(ctx: BBCodeParser.SectionContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitSection(ctx: BBCodeParser.SectionContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterElement(ctx: BBCodeParser.ElementContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitElement(ctx: BBCodeParser.ElementContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterPlain(ctx: BBCodeParser.PlainContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitPlain(ctx: BBCodeParser.PlainContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterB(ctx: BBCodeParser.BContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitB(ctx: BBCodeParser.BContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterI(ctx: BBCodeParser.IContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitI(ctx: BBCodeParser.IContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterU(ctx: BBCodeParser.UContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitU(ctx: BBCodeParser.UContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterS(ctx: BBCodeParser.SContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitS(ctx: BBCodeParser.SContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterCode(ctx: BBCodeParser.CodeContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitCode(ctx: BBCodeParser.CodeContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterMask(ctx: BBCodeParser.MaskContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitMask(ctx: BBCodeParser.MaskContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterQuote(ctx: BBCodeParser.QuoteContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitQuote(ctx: BBCodeParser.QuoteContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterSize(ctx: BBCodeParser.SizeContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitSize(ctx: BBCodeParser.SizeContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterColor(ctx: BBCodeParser.ColorContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitColor(ctx: BBCodeParser.ColorContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitBgm_sticker(ctx: BBCodeParser.Bgm_stickerContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterText_stiker(ctx: BBCodeParser.Text_stikerContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitText_stiker(ctx: BBCodeParser.Text_stikerContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterUrl(ctx: BBCodeParser.UrlContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitUrl(ctx: BBCodeParser.UrlContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterUrl_named(ctx: BBCodeParser.Url_namedContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitUrl_named(ctx: BBCodeParser.Url_namedContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterImg(ctx: BBCodeParser.ImgContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitImg(ctx: BBCodeParser.ImgContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterAttribute_value(ctx: BBCodeParser.Attribute_valueContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitAttribute_value(ctx: BBCodeParser.Attribute_valueContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun enterEveryRule(ctx: ParserRuleContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun exitEveryRule(ctx: ParserRuleContext) {}

    /**
     * The default implementation does nothing.
     */
    override fun visitTerminal(node: TerminalNode) {}

    /**
     * The default implementation does nothing.
     */
    override fun visitErrorNode(node: ErrorNode) {}
}
