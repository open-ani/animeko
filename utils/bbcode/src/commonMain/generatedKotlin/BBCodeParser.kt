/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// Generated from /Users/stageguard/Projects/animeko/utils/bbcode/BBCode.g4 by ANTLR 4.13.1
package me.him188.ani.utils.bbcode

import com.strumenta.antlrkotlin.runtime.JsName
import org.antlr.v4.kotlinruntime.NoViableAltException
import org.antlr.v4.kotlinruntime.Parser
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.RuleContext
import org.antlr.v4.kotlinruntime.RuntimeMetaData
import org.antlr.v4.kotlinruntime.Token
import org.antlr.v4.kotlinruntime.TokenStream
import org.antlr.v4.kotlinruntime.Vocabulary
import org.antlr.v4.kotlinruntime.VocabularyImpl
import org.antlr.v4.kotlinruntime.atn.ATN
import org.antlr.v4.kotlinruntime.atn.ATN.Companion.INVALID_ALT_NUMBER
import org.antlr.v4.kotlinruntime.atn.ATNDeserializer
import org.antlr.v4.kotlinruntime.atn.ParserATNSimulator
import org.antlr.v4.kotlinruntime.atn.PredictionContextCache
import org.antlr.v4.kotlinruntime.dfa.DFA
import org.antlr.v4.kotlinruntime.tree.ParseTreeListener
import org.antlr.v4.kotlinruntime.tree.ParseTreeVisitor
import org.antlr.v4.kotlinruntime.tree.TerminalNode
import kotlin.jvm.JvmField

@Suppress(
    // This is required as we are using a custom JsName alias that is not recognized by the IDE.
    // No name clashes will happen tho.
    "JS_NAME_CLASH",
    "UNUSED_VARIABLE",
    "ClassName",
    "FunctionName",
    "LocalVariableName",
    "ConstPropertyName",
    "ConvertSecondaryConstructorToPrimary",
    "CanBeVal",
)
public open class BBCodeParser(input: TokenStream) : Parser(input) {
    private companion object {
        init {
            RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.runtimeVersion)
        }

        private const val SERIALIZED_ATN: String =
            "\u0004\u0001\u0056\u00bb\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002\u0008\u0007\u0008\u0002\u0009\u0007\u0009\u0002\u000a\u0007\u000a\u0002\u000b\u0007\u000b\u0002\u000c\u0007\u000c\u0002\u000d\u0007\u000d\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0004\u0001\u0031\u0008\u0001\u000b\u0001\u000c\u0001\u0032\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002\u0047\u0008\u0002\u0001\u0003\u0004\u0003\u004a\u0008\u0003\u000b\u0003\u000c\u0003\u004b\u0001\u0004\u0001\u0004\u0003\u0004\u0050\u0008\u0004\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0003\u0005\u0056\u0008\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0003\u0006\u005c\u0008\u0006\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007\u0003\u0007\u0062\u0008\u0007\u0001\u0007\u0001\u0007\u0001\u0008\u0001\u0008\u0003\u0008\u0068\u0008\u0008\u0001\u0008\u0001\u0008\u0001\u0009\u0001\u0009\u0003\u0009\u006e\u0008\u0009\u0001\u0009\u0001\u0009\u0001\u000a\u0001\u000a\u0003\u000a\u0074\u0008\u000a\u0001\u000a\u0001\u000a\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000b\u007c\u0008\u000b\u0001\u000b\u0001\u000b\u0001\u000c\u0001\u000c\u0001\u000c\u0001\u000c\u0003\u000c\u0084\u0008\u000c\u0001\u000c\u0001\u000c\u0001\u000d\u0001\u000d\u0003\u000d\u008a\u0008\u000d\u0001\u000d\u0001\u000d\u0001\u000e\u0001\u000e\u0003\u000e\u0090\u0008\u000e\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0003\u000f\u0096\u0008\u000f\u0001\u000f\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0001\u0012\u0001\u0012\u0003\u0012\u00a2\u0008\u0012\u0001\u0012\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0003\u0013\u00aa\u0008\u0013\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0004\u0014\u00b1\u0008\u0014\u000b\u0014\u000c\u0014\u00b2\u0001\u0014\u0001\u0014\u0001\u0015\u0001\u0015\u0003\u0015\u00b9\u0008\u0015\u0001\u0015\u0000\u0000\u0016\u0000\u0002\u0004\u0006\u0008\u000a\u000c\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c\u001e\u0020\u0022\u0024\u0026\u0028\u002a\u0000\u0021\u0003\u0000\u0001\u0007\u0054\u0054\u0056\u0056\u0001\u0000\u0008\u0009\u0001\u0000\u000a\u000b\u0001\u0000\u000c\u000d\u0001\u0000\u000e\u000f\u0001\u0000\u0010\u0011\u0001\u0000\u0012\u0013\u0001\u0000\u0014\u0015\u0001\u0000\u0016\u0017\u0001\u0000\u0018\u0019\u0001\u0000\u001a\u001b\u0001\u0000\u001c\u001d\u0001\u0000\u001e\u001f\u0001\u0000\u0020\u0021\u0001\u0000\u0022\u0023\u0001\u0000\u0024\u0025\u0001\u0000\u0005\u0005\u0001\u0000\u0026\u0027\u0001\u0000\u0028\u0029\u0001\u0000\u002a\u002b\u0001\u0000\u002c\u002d\u0001\u0000\u002e\u002f\u0001\u0000\u0030\u0031\u0001\u0000\u0032\u0033\u0001\u0000\u0034\u0035\u0001\u0000\u0036\u0037\u0001\u0000\u0038\u0039\u0001\u0000\u003a\u0049\u0001\u0000\u004a\u004b\u0001\u0000\u004c\u004d\u0001\u0000\u004e\u004f\u0001\u0000\u0050\u0051\u0001\u0000\u0052\u0053\u00c7\u0000\u002c\u0001\u0000\u0000\u0000\u0002\u0030\u0001\u0000\u0000\u0000\u0004\u0046\u0001\u0000\u0000\u0000\u0006\u0049\u0001\u0000\u0000\u0000\u0008\u004d\u0001\u0000\u0000\u0000\u000a\u0053\u0001\u0000\u0000\u0000\u000c\u0059\u0001\u0000\u0000\u0000\u000e\u005f\u0001\u0000\u0000\u0000\u0010\u0065\u0001\u0000\u0000\u0000\u0012\u006b\u0001\u0000\u0000\u0000\u0014\u0071\u0001\u0000\u0000\u0000\u0016\u0077\u0001\u0000\u0000\u0000\u0018\u007f\u0001\u0000\u0000\u0000\u001a\u0087\u0001\u0000\u0000\u0000\u001c\u008d\u0001\u0000\u0000\u0000\u001e\u0093\u0001\u0000\u0000\u0000\u0020\u0099\u0001\u0000\u0000\u0000\u0022\u009d\u0001\u0000\u0000\u0000\u0024\u009f\u0001\u0000\u0000\u0000\u0026\u00a5\u0001\u0000\u0000\u0000\u0028\u00ad\u0001\u0000\u0000\u0000\u002a\u00b8\u0001\u0000\u0000\u0000\u002c\u002d\u0003\u0002\u0001\u0000\u002d\u002e\u0005\u0000\u0000\u0001\u002e\u0001\u0001\u0000\u0000\u0000\u002f\u0031\u0003\u0004\u0002\u0000\u0030\u002f\u0001\u0000\u0000\u0000\u0031\u0032\u0001\u0000\u0000\u0000\u0032\u0030\u0001\u0000\u0000\u0000\u0032\u0033\u0001\u0000\u0000\u0000\u0033\u0003\u0001\u0000\u0000\u0000\u0034\u0047\u0003\u0008\u0004\u0000\u0035\u0047\u0003\u000a\u0005\u0000\u0036\u0047\u0003\u000c\u0006\u0000\u0037\u0047\u0003\u000e\u0007\u0000\u0038\u0047\u0003\u0010\u0008\u0000\u0039\u0047\u0003\u0024\u0012\u0000\u003a\u0047\u0003\u0026\u0013\u0000\u003b\u0047\u0003\u0028\u0014\u0000\u003c\u0047\u0003\u0014\u000a\u0000\u003d\u0047\u0003\u0016\u000b\u0000\u003e\u0047\u0003\u0018\u000c\u0000\u003f\u0047\u0003\u0012\u0009\u0000\u0040\u0047\u0003\u001a\u000d\u0000\u0041\u0047\u0003\u001c\u000e\u0000\u0042\u0047\u0003\u001e\u000f\u0000\u0043\u0047\u0003\u0020\u0010\u0000\u0044\u0047\u0003\u0022\u0011\u0000\u0045\u0047\u0003\u0006\u0003\u0000\u0046\u0034\u0001\u0000\u0000\u0000\u0046\u0035\u0001\u0000\u0000\u0000\u0046\u0036\u0001\u0000\u0000\u0000\u0046\u0037\u0001\u0000\u0000\u0000\u0046\u0038\u0001\u0000\u0000\u0000\u0046\u0039\u0001\u0000\u0000\u0000\u0046\u003a\u0001\u0000\u0000\u0000\u0046\u003b\u0001\u0000\u0000\u0000\u0046\u003c\u0001\u0000\u0000\u0000\u0046\u003d\u0001\u0000\u0000\u0000\u0046\u003e\u0001\u0000\u0000\u0000\u0046\u003f\u0001\u0000\u0000\u0000\u0046\u0040\u0001\u0000\u0000\u0000\u0046\u0041\u0001\u0000\u0000\u0000\u0046\u0042\u0001\u0000\u0000\u0000\u0046\u0043\u0001\u0000\u0000\u0000\u0046\u0044\u0001\u0000\u0000\u0000\u0046\u0045\u0001\u0000\u0000\u0000\u0047\u0005\u0001\u0000\u0000\u0000\u0048\u004a\u0007\u0000\u0000\u0000\u0049\u0048\u0001\u0000\u0000\u0000\u004a\u004b\u0001\u0000\u0000\u0000\u004b\u0049\u0001\u0000\u0000\u0000\u004b\u004c\u0001\u0000\u0000\u0000\u004c\u0007\u0001\u0000\u0000\u0000\u004d\u004f\u0007\u0001\u0000\u0000\u004e\u0050\u0003\u0002\u0001\u0000\u004f\u004e\u0001\u0000\u0000\u0000\u004f\u0050\u0001\u0000\u0000\u0000\u0050\u0051\u0001\u0000\u0000\u0000\u0051\u0052\u0007\u0002\u0000\u0000\u0052\u0009\u0001\u0000\u0000\u0000\u0053\u0055\u0007\u0003\u0000\u0000\u0054\u0056\u0003\u0002\u0001\u0000\u0055\u0054\u0001\u0000\u0000\u0000\u0055\u0056\u0001\u0000\u0000\u0000\u0056\u0057\u0001\u0000\u0000\u0000\u0057\u0058\u0007\u0004\u0000\u0000\u0058\u000b\u0001\u0000\u0000\u0000\u0059\u005b\u0007\u0005\u0000\u0000\u005a\u005c\u0003\u0002\u0001\u0000\u005b\u005a\u0001\u0000\u0000\u0000\u005b\u005c\u0001\u0000\u0000\u0000\u005c\u005d\u0001\u0000\u0000\u0000\u005d\u005e\u0007\u0006\u0000\u0000\u005e\u000d\u0001\u0000\u0000\u0000\u005f\u0061\u0007\u0007\u0000\u0000\u0060\u0062\u0003\u0002\u0001\u0000\u0061\u0060\u0001\u0000\u0000\u0000\u0061\u0062\u0001\u0000\u0000\u0000\u0062\u0063\u0001\u0000\u0000\u0000\u0063\u0064\u0007\u0008\u0000\u0000\u0064\u000f\u0001\u0000\u0000\u0000\u0065\u0067\u0007\u0009\u0000\u0000\u0066\u0068\u0003\u0002\u0001\u0000\u0067\u0066\u0001\u0000\u0000\u0000\u0067\u0068\u0001\u0000\u0000\u0000\u0068\u0069\u0001\u0000\u0000\u0000\u0069\u006a\u0007\u000a\u0000\u0000\u006a\u0011\u0001\u0000\u0000\u0000\u006b\u006d\u0007\u000b\u0000\u0000\u006c\u006e\u0003\u0002\u0001\u0000\u006d\u006c\u0001\u0000\u0000\u0000\u006d\u006e\u0001\u0000\u0000\u0000\u006e\u006f\u0001\u0000\u0000\u0000\u006f\u0070\u0007\u000c\u0000\u0000\u0070\u0013\u0001\u0000\u0000\u0000\u0071\u0073\u0007\u000d\u0000\u0000\u0072\u0074\u0003\u0002\u0001\u0000\u0073\u0072\u0001\u0000\u0000\u0000\u0073\u0074\u0001\u0000\u0000\u0000\u0074\u0075\u0001\u0000\u0000\u0000\u0075\u0076\u0007\u000e\u0000\u0000\u0076\u0015\u0001\u0000\u0000\u0000\u0077\u0078\u0007\u000f\u0000\u0000\u0078\u0079\u0005\u0054\u0000\u0000\u0079\u007b\u0007\u0010\u0000\u0000\u007a\u007c\u0003\u0002\u0001\u0000\u007b\u007a\u0001\u0000\u0000\u0000\u007b\u007c\u0001\u0000\u0000\u0000\u007c\u007d\u0001\u0000\u0000\u0000\u007d\u007e\u0007\u0011\u0000\u0000\u007e\u0017\u0001\u0000\u0000\u0000\u007f\u0080\u0007\u0012\u0000\u0000\u0080\u0081\u0005\u0056\u0000\u0000\u0081\u0083\u0007\u0010\u0000\u0000\u0082\u0084\u0003\u0002\u0001\u0000\u0083\u0082\u0001\u0000\u0000\u0000\u0083\u0084\u0001\u0000\u0000\u0000\u0084\u0085\u0001\u0000\u0000\u0000\u0085\u0086\u0007\u0013\u0000\u0000\u0086\u0019\u0001\u0000\u0000\u0000\u0087\u0089\u0007\u0014\u0000\u0000\u0088\u008a\u0003\u0002\u0001\u0000\u0089\u0088\u0001\u0000\u0000\u0000\u0089\u008a\u0001\u0000\u0000\u0000\u008a\u008b\u0001\u0000\u0000\u0000\u008b\u008c\u0007\u0015\u0000\u0000\u008c\u001b\u0001\u0000\u0000\u0000\u008d\u008f\u0007\u0016\u0000\u0000\u008e\u0090\u0003\u0002\u0001\u0000\u008f\u008e\u0001\u0000\u0000\u0000\u008f\u0090\u0001\u0000\u0000\u0000\u0090\u0091\u0001\u0000\u0000\u0000\u0091\u0092\u0007\u0017\u0000\u0000\u0092\u001d\u0001\u0000\u0000\u0000\u0093\u0095\u0007\u0018\u0000\u0000\u0094\u0096\u0003\u0002\u0001\u0000\u0095\u0094\u0001\u0000\u0000\u0000\u0095\u0096\u0001\u0000\u0000\u0000\u0096\u0097\u0001\u0000\u0000\u0000\u0097\u0098\u0007\u0019\u0000\u0000\u0098\u001f\u0001\u0000\u0000\u0000\u0099\u009a\u0007\u001a\u0000\u0000\u009a\u009b\u0005\u0054\u0000\u0000\u009b\u009c\u0005\u0002\u0000\u0000\u009c\u0021\u0001\u0000\u0000\u0000\u009d\u009e\u0007\u001b\u0000\u0000\u009e\u0023\u0001\u0000\u0000\u0000\u009f\u00a1\u0007\u001c\u0000\u0000\u00a0\u00a2\u0003\u0006\u0003\u0000\u00a1\u00a0\u0001\u0000\u0000\u0000\u00a1\u00a2\u0001\u0000\u0000\u0000\u00a2\u00a3\u0001\u0000\u0000\u0000\u00a3\u00a4\u0007\u001d\u0000\u0000\u00a4\u0025\u0001\u0000\u0000\u0000\u00a5\u00a6\u0007\u001e\u0000\u0000\u00a6\u00a7\u0003\u002a\u0015\u0000\u00a7\u00a9\u0005\u0005\u0000\u0000\u00a8\u00aa\u0003\u0002\u0001\u0000\u00a9\u00a8\u0001\u0000\u0000\u0000\u00a9\u00aa\u0001\u0000\u0000\u0000\u00aa\u00ab\u0001\u0000\u0000\u0000\u00ab\u00ac\u0007\u001d\u0000\u0000\u00ac\u0027\u0001\u0000\u0000\u0000\u00ad\u00ae\u0007\u001f\u0000\u0000\u00ae\u00b0\u0005\u0005\u0000\u0000\u00af\u00b1\u0003\u0006\u0003\u0000\u00b0\u00af\u0001\u0000\u0000\u0000\u00b1\u00b2\u0001\u0000\u0000\u0000\u00b2\u00b0\u0001\u0000\u0000\u0000\u00b2\u00b3\u0001\u0000\u0000\u0000\u00b3\u00b4\u0001\u0000\u0000\u0000\u00b4\u00b5\u0007\u0020\u0000\u0000\u00b5\u0029\u0001\u0000\u0000\u0000\u00b6\u00b9\u0005\u0055\u0000\u0000\u00b7\u00b9\u0005\u0056\u0000\u0000\u00b8\u00b6\u0001\u0000\u0000\u0000\u00b8\u00b7\u0001\u0000\u0000\u0000\u00b9\u002b\u0001\u0000\u0000\u0000\u0013\u0032\u0046\u004b\u004f\u0055\u005b\u0061\u0067\u006d\u0073\u007b\u0083\u0089\u008f\u0095\u00a1\u00a9\u00b2\u00b8"

        private val ATN = ATNDeserializer().deserialize(SERIALIZED_ATN.toCharArray())

        private val DECISION_TO_DFA = Array(ATN.numberOfDecisions) {
            DFA(ATN.getDecisionState(it)!!, it)
        }

        private val SHARED_CONTEXT_CACHE = PredictionContextCache()
        private val RULE_NAMES: Array<String> = arrayOf(
            "file", "section", "element", "plain", "b", "i", "u", "s", "code",
            "mask", "quote", "size", "color", "center", "left", "right",
            "bgm_sticker", "text_stiker", "url", "url_named", "img", "attribute_value"
        )

        private val LITERAL_NAMES: Array<String?> = arrayOf(
            null, "'('", "')'", "'['", "'/'", "']'", "'='", "','", "'[b]'",
            "'[B]'", "'[/b]'", "'[/B]'", "'[i]'", "'[I]'", "'[/i]'", "'[/I]'",
            "'[u]'", "'[U]'", "'[/u]'", "'[/U]'", "'[s]'", "'[S]'", "'[/s]'",
            "'[/S]'", "'[code]'", "'[CODE]'", "'[/code]'", "'[/CODE]'",
            "'[mask]'", "'[MASK]'", "'[/mask]'", "'[/MASK]'", "'[quote]'",
            "'[QUOTE]'", "'[/quote]'", "'[/QUOTE]'", "'[size='", "'[SIZE='",
            "'[/size]'", "'[/SIZE]'", "'[color='", "'[COLOR='", "'[/color]'",
            "'[/COLOR]'", "'[center]'", "'[CENTER]'", "'[/center]'", "'[/CENTER]'",
            "'[left]'", "'[LEFT]'", "'[/left]'", "'[/LEFT]'", "'[right]'",
            "'[RIGHT]'", "'[/right]'", "'[/RIGHT]'", "'(bgm'", "'(BGM'",
            "'(=A=)'", "'(=w=)'", "'(-w=)'", "'(S_S)'", "'(=v=)'", "'(@_@)'",
            "'(=W=)'", "'(TAT)'", "'(T_T)'", "'(='=)'", "'(=3=)'", "'(= =')'",
            "'(=///=)'", "'(=.,=)'", "'(:P)'", "'(LOL)'", "'[url]'", "'[URL]'",
            "'[/url]'", "'[/URL]'", "'[url='", "'[URL='", "'[img'", "'[IMG'",
            "'[/img]'", "'[/IMG]'"
        )

        private val SYMBOLIC_NAMES: Array<String?> = arrayOf(
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, "NUMBER", "QUOTED", "TEXT"
        )

        private val VOCABULARY = VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES)

        private val TOKEN_NAMES: Array<String> = Array(SYMBOLIC_NAMES.size) {
            VOCABULARY.getLiteralName(it)
                ?: VOCABULARY.getSymbolicName(it)
                ?: "<INVALID>"
        }
    }

    public object Tokens {
        public const val EOF: Int = -1
        public const val T__0: Int = 1
        public const val T__1: Int = 2
        public const val T__2: Int = 3
        public const val T__3: Int = 4
        public const val T__4: Int = 5
        public const val T__5: Int = 6
        public const val T__6: Int = 7
        public const val T__7: Int = 8
        public const val T__8: Int = 9
        public const val T__9: Int = 10
        public const val T__10: Int = 11
        public const val T__11: Int = 12
        public const val T__12: Int = 13
        public const val T__13: Int = 14
        public const val T__14: Int = 15
        public const val T__15: Int = 16
        public const val T__16: Int = 17
        public const val T__17: Int = 18
        public const val T__18: Int = 19
        public const val T__19: Int = 20
        public const val T__20: Int = 21
        public const val T__21: Int = 22
        public const val T__22: Int = 23
        public const val T__23: Int = 24
        public const val T__24: Int = 25
        public const val T__25: Int = 26
        public const val T__26: Int = 27
        public const val T__27: Int = 28
        public const val T__28: Int = 29
        public const val T__29: Int = 30
        public const val T__30: Int = 31
        public const val T__31: Int = 32
        public const val T__32: Int = 33
        public const val T__33: Int = 34
        public const val T__34: Int = 35
        public const val T__35: Int = 36
        public const val T__36: Int = 37
        public const val T__37: Int = 38
        public const val T__38: Int = 39
        public const val T__39: Int = 40
        public const val T__40: Int = 41
        public const val T__41: Int = 42
        public const val T__42: Int = 43
        public const val T__43: Int = 44
        public const val T__44: Int = 45
        public const val T__45: Int = 46
        public const val T__46: Int = 47
        public const val T__47: Int = 48
        public const val T__48: Int = 49
        public const val T__49: Int = 50
        public const val T__50: Int = 51
        public const val T__51: Int = 52
        public const val T__52: Int = 53
        public const val T__53: Int = 54
        public const val T__54: Int = 55
        public const val T__55: Int = 56
        public const val T__56: Int = 57
        public const val T__57: Int = 58
        public const val T__58: Int = 59
        public const val T__59: Int = 60
        public const val T__60: Int = 61
        public const val T__61: Int = 62
        public const val T__62: Int = 63
        public const val T__63: Int = 64
        public const val T__64: Int = 65
        public const val T__65: Int = 66
        public const val T__66: Int = 67
        public const val T__67: Int = 68
        public const val T__68: Int = 69
        public const val T__69: Int = 70
        public const val T__70: Int = 71
        public const val T__71: Int = 72
        public const val T__72: Int = 73
        public const val T__73: Int = 74
        public const val T__74: Int = 75
        public const val T__75: Int = 76
        public const val T__76: Int = 77
        public const val T__77: Int = 78
        public const val T__78: Int = 79
        public const val T__79: Int = 80
        public const val T__80: Int = 81
        public const val T__81: Int = 82
        public const val T__82: Int = 83
        public const val NUMBER: Int = 84
        public const val QUOTED: Int = 85
        public const val TEXT: Int = 86
    }

    public object Rules {
        public const val File: Int = 0
        public const val Section: Int = 1
        public const val Element: Int = 2
        public const val Plain: Int = 3
        public const val B: Int = 4
        public const val I: Int = 5
        public const val U: Int = 6
        public const val S: Int = 7
        public const val Code: Int = 8
        public const val Mask: Int = 9
        public const val Quote: Int = 10
        public const val Size: Int = 11
        public const val Color: Int = 12
        public const val Center: Int = 13
        public const val Left: Int = 14
        public const val Right: Int = 15
        public const val Bgm_sticker: Int = 16
        public const val Text_stiker: Int = 17
        public const val Url: Int = 18
        public const val Url_named: Int = 19
        public const val Img: Int = 20
        public const val Attribute_value: Int = 21
    }

    override var interpreter: ParserATNSimulator =
        @Suppress("LeakingThis")
        ParserATNSimulator(this, ATN, DECISION_TO_DFA, SHARED_CONTEXT_CACHE)

    override val grammarFileName: String =
        "BBCode.g4"

    @Deprecated("Use vocabulary instead", replaceWith = ReplaceWith("vocabulary"))
    override val tokenNames: Array<String> =
        TOKEN_NAMES

    override val ruleNames: Array<String> =
        RULE_NAMES

    override val atn: ATN =
        ATN

    override val vocabulary: Vocabulary =
        VOCABULARY

    override val serializedATN: String =
        SERIALIZED_ATN

    /* Named actions */

    /* Funcs */
    public open class FileContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.File

        public fun section(): SectionContext = getRuleContext(SectionContext::class, 0)!!
        public fun EOF(): TerminalNode = getToken(Tokens.EOF, 0)!!

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterFile(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitFile(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitFile(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun file(): FileContext {
        var _localctx = FileContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 0, Rules.File)

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 44
            section()

            this.state = 45
            match(Tokens.EOF)

        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class SectionContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Section

        public fun element(): List<ElementContext> = getRuleContexts(ElementContext::class)
        public fun element(i: Int): ElementContext? = getRuleContext(ElementContext::class, i)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterSection(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitSection(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitSection(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun section(): SectionContext {
        var _localctx = SectionContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 2, Rules.Section)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 48
            errorHandler.sync(this)
            _la = _input.LA(1)

            do {
                this.state = 47
                element()

                this.state = 50
                errorHandler.sync(this)
                _la = _input.LA(1)
            } while ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L))
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class ElementContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Element

        public fun b(): BContext? = getRuleContext(BContext::class, 0)
        public fun i(): IContext? = getRuleContext(IContext::class, 0)
        public fun u(): UContext? = getRuleContext(UContext::class, 0)
        public fun s(): SContext? = getRuleContext(SContext::class, 0)
        public fun code(): CodeContext? = getRuleContext(CodeContext::class, 0)
        public fun url(): UrlContext? = getRuleContext(UrlContext::class, 0)
        public fun url_named(): Url_namedContext? = getRuleContext(Url_namedContext::class, 0)
        public fun img(): ImgContext? = getRuleContext(ImgContext::class, 0)
        public fun quote(): QuoteContext? = getRuleContext(QuoteContext::class, 0)
        public fun size(): SizeContext? = getRuleContext(SizeContext::class, 0)
        public fun color(): ColorContext? = getRuleContext(ColorContext::class, 0)
        public fun mask(): MaskContext? = getRuleContext(MaskContext::class, 0)
        public fun center(): CenterContext? = getRuleContext(CenterContext::class, 0)
        public fun left(): LeftContext? = getRuleContext(LeftContext::class, 0)
        public fun right(): RightContext? = getRuleContext(RightContext::class, 0)
        public fun bgm_sticker(): Bgm_stickerContext? = getRuleContext(Bgm_stickerContext::class, 0)
        public fun text_stiker(): Text_stikerContext? = getRuleContext(Text_stikerContext::class, 0)
        public fun plain(): PlainContext? = getRuleContext(PlainContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterElement(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitElement(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitElement(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun element(): ElementContext {
        var _localctx = ElementContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 4, Rules.Element)

        try {
            this.state = 70
            errorHandler.sync(this)

            when (_input.LA(1)) {
                Tokens.T__7, Tokens.T__8 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 1)
                    this.state = 52
                    b()

                }Tokens.T__11, Tokens.T__12 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 2)
                    this.state = 53
                    i()

                }Tokens.T__15, Tokens.T__16 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 3)
                    this.state = 54
                    u()

                }Tokens.T__19, Tokens.T__20 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 4)
                    this.state = 55
                    s()

                }Tokens.T__23, Tokens.T__24 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 5)
                    this.state = 56
                    code()

                }Tokens.T__73, Tokens.T__74 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 6)
                    this.state = 57
                    url()

                }Tokens.T__77, Tokens.T__78 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 7)
                    this.state = 58
                    url_named()

                }Tokens.T__79, Tokens.T__80 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 8)
                    this.state = 59
                    img()

                }Tokens.T__31, Tokens.T__32 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 9)
                    this.state = 60
                    quote()

                }Tokens.T__35, Tokens.T__36 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 10)
                    this.state = 61
                    size()

                }Tokens.T__39, Tokens.T__40 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 11)
                    this.state = 62
                    color()

                }Tokens.T__27, Tokens.T__28 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 12)
                    this.state = 63
                    mask()

                }Tokens.T__43, Tokens.T__44 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 13)
                    this.state = 64
                    center()

                }Tokens.T__47, Tokens.T__48 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 14)
                    this.state = 65
                    left()

                }Tokens.T__51, Tokens.T__52 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 15)
                    this.state = 66
                    right()

                }Tokens.T__55, Tokens.T__56 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 16)
                    this.state = 67
                    bgm_sticker()

                }Tokens.T__57, Tokens.T__58, Tokens.T__59, Tokens.T__60, Tokens.T__61, Tokens.T__62, Tokens.T__63, Tokens.T__64, Tokens.T__65, Tokens.T__66, Tokens.T__67, Tokens.T__68, Tokens.T__69, Tokens.T__70, Tokens.T__71, Tokens.T__72 -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 17)
                    this.state = 68
                    text_stiker()

                }Tokens.T__0, Tokens.T__1, Tokens.T__2, Tokens.T__3, Tokens.T__4, Tokens.T__5, Tokens.T__6, Tokens.NUMBER, Tokens.TEXT -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 18)
                    this.state = 69
                    plain()

                }
                else -> throw NoViableAltException(this)
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class PlainContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Plain

        public fun TEXT(): List<TerminalNode> = getTokens(Tokens.TEXT)
        public fun TEXT(i: Int): TerminalNode? = getToken(Tokens.TEXT, i)
        public fun NUMBER(): List<TerminalNode> = getTokens(Tokens.NUMBER)
        public fun NUMBER(i: Int): TerminalNode? = getToken(Tokens.NUMBER, i)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterPlain(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitPlain(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitPlain(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun plain(): PlainContext {
        var _localctx = PlainContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 6, Rules.Plain)
        var _la: Int

        try {
            var _alt: Int
            enterOuterAlt(_localctx, 1)
            this.state = 73
            errorHandler.sync(this)
            _alt = 1

            do {
                when (_alt) {
                    1 -> {
                        this.state = 72
                        _la = _input.LA(1)

                        if (!((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and 254L) != 0L) || _la == Tokens.NUMBER || _la == Tokens.TEXT)) {
                            errorHandler.recoverInline(this)
                        }
                        else {
                            if (_input.LA(1) == Tokens.EOF) {
                                isMatchedEOF = true
                            }

                            errorHandler.reportMatch(this)
                            consume()
                        }
                    }
                    else -> throw NoViableAltException(this)
                }

                this.state = 75
                errorHandler.sync(this)
                _alt = interpreter.adaptivePredict(_input, 2, context)
            } while (_alt != 2 && _alt != INVALID_ALT_NUMBER)
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class BContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.B

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterB(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitB(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitB(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun b(): BContext {
        var _localctx = BContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 8, Rules.B)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 77
            _la = _input.LA(1)

            if (!(_la == Tokens.T__7 || _la == Tokens.T__8)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 79
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 78
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 81
            _la = _input.LA(1)

            if (!(_la == Tokens.T__9 || _la == Tokens.T__10)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class IContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.I

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterI(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitI(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitI(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun i(): IContext {
        var _localctx = IContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 10, Rules.I)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 83
            _la = _input.LA(1)

            if (!(_la == Tokens.T__11 || _la == Tokens.T__12)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 85
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 84
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 87
            _la = _input.LA(1)

            if (!(_la == Tokens.T__13 || _la == Tokens.T__14)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class UContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.U

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterU(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitU(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitU(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun u(): UContext {
        var _localctx = UContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 12, Rules.U)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 89
            _la = _input.LA(1)

            if (!(_la == Tokens.T__15 || _la == Tokens.T__16)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 91
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 90
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 93
            _la = _input.LA(1)

            if (!(_la == Tokens.T__17 || _la == Tokens.T__18)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class SContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.S

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterS(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitS(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitS(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun s(): SContext {
        var _localctx = SContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 14, Rules.S)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 95
            _la = _input.LA(1)

            if (!(_la == Tokens.T__19 || _la == Tokens.T__20)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 97
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 96
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 99
            _la = _input.LA(1)

            if (!(_la == Tokens.T__21 || _la == Tokens.T__22)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class CodeContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Code

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterCode(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitCode(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitCode(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun code(): CodeContext {
        var _localctx = CodeContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 16, Rules.Code)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 101
            _la = _input.LA(1)

            if (!(_la == Tokens.T__23 || _la == Tokens.T__24)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 103
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 102
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 105
            _la = _input.LA(1)

            if (!(_la == Tokens.T__25 || _la == Tokens.T__26)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class MaskContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Mask

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterMask(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitMask(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitMask(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun mask(): MaskContext {
        var _localctx = MaskContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 18, Rules.Mask)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 107
            _la = _input.LA(1)

            if (!(_la == Tokens.T__27 || _la == Tokens.T__28)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 109
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 108
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 111
            _la = _input.LA(1)

            if (!(_la == Tokens.T__29 || _la == Tokens.T__30)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class QuoteContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Quote

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterQuote(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitQuote(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitQuote(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun quote(): QuoteContext {
        var _localctx = QuoteContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 20, Rules.Quote)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 113
            _la = _input.LA(1)

            if (!(_la == Tokens.T__31 || _la == Tokens.T__32)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 115
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 114
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 117
            _la = _input.LA(1)

            if (!(_la == Tokens.T__33 || _la == Tokens.T__34)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class SizeContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Size

        @JvmField @JsName("value$") public var value: Token? = null
        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun NUMBER(): TerminalNode = getToken(Tokens.NUMBER, 0)!!
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterSize(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitSize(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitSize(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun size(): SizeContext {
        var _localctx = SizeContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 22, Rules.Size)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 119
            _la = _input.LA(1)

            if (!(_la == Tokens.T__35 || _la == Tokens.T__36)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 120
            _token = match(Tokens.NUMBER)
            _localctx.value = _token

            this.state = 121
            _la = _input.LA(1)

            if (!(_la == Tokens.T__4)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 123
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 122
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 125
            _la = _input.LA(1)

            if (!(_la == Tokens.T__37 || _la == Tokens.T__38)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class ColorContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Color

        @JvmField @JsName("value$") public var value: Token? = null
        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun TEXT(): TerminalNode = getToken(Tokens.TEXT, 0)!!
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterColor(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitColor(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitColor(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun color(): ColorContext {
        var _localctx = ColorContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 24, Rules.Color)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 127
            _la = _input.LA(1)

            if (!(_la == Tokens.T__39 || _la == Tokens.T__40)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 128
            _token = match(Tokens.TEXT)
            _localctx.value = _token

            this.state = 129
            _la = _input.LA(1)

            if (!(_la == Tokens.T__4)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 131
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 130
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 133
            _la = _input.LA(1)

            if (!(_la == Tokens.T__41 || _la == Tokens.T__42)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class CenterContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Center

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterCenter(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitCenter(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitCenter(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun center(): CenterContext {
        var _localctx = CenterContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 26, Rules.Center)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 135
            _la = _input.LA(1)

            if (!(_la == Tokens.T__43 || _la == Tokens.T__44)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 137
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 136
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 139
            _la = _input.LA(1)

            if (!(_la == Tokens.T__45 || _la == Tokens.T__46)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class LeftContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Left

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterLeft(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitLeft(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitLeft(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun left(): LeftContext {
        var _localctx = LeftContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 28, Rules.Left)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 141
            _la = _input.LA(1)

            if (!(_la == Tokens.T__47 || _la == Tokens.T__48)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 143
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 142
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 145
            _la = _input.LA(1)

            if (!(_la == Tokens.T__49 || _la == Tokens.T__50)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class RightContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Right

        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterRight(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitRight(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitRight(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun right(): RightContext {
        var _localctx = RightContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 30, Rules.Right)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 147
            _la = _input.LA(1)

            if (!(_la == Tokens.T__51 || _la == Tokens.T__52)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 149
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 148
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 151
            _la = _input.LA(1)

            if (!(_la == Tokens.T__53 || _la == Tokens.T__54)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class Bgm_stickerContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Bgm_sticker

        @JvmField @JsName("id$") public var id: Token? = null
        public fun NUMBER(): TerminalNode = getToken(Tokens.NUMBER, 0)!!

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterBgm_sticker(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitBgm_sticker(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitBgm_sticker(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun bgm_sticker(): Bgm_stickerContext {
        var _localctx = Bgm_stickerContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 32, Rules.Bgm_sticker)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 153
            _la = _input.LA(1)

            if (!(_la == Tokens.T__55 || _la == Tokens.T__56)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 154
            _token = match(Tokens.NUMBER)
            _localctx.id = _token

            this.state = 155
            match(Tokens.T__1)

        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class Text_stikerContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Text_stiker


        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterText_stiker(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitText_stiker(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitText_stiker(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun text_stiker(): Text_stikerContext {
        var _localctx = Text_stikerContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 34, Rules.Text_stiker)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 157
            _la = _input.LA(1)

            if (!(((((_la - 58)) and 0x3f.inv()) == 0 && ((1L shl (_la - 58)) and 65535L) != 0L))) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class UrlContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Url

        @JvmField @JsName("href$") public var href: PlainContext? = null
        public fun plain(): PlainContext? = getRuleContext(PlainContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterUrl(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitUrl(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitUrl(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun url(): UrlContext {
        var _localctx = UrlContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 36, Rules.Url)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 159
            _la = _input.LA(1)

            if (!(_la == Tokens.T__73 || _la == Tokens.T__74)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 161
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and 254L) != 0L) || _la == Tokens.NUMBER || _la == Tokens.TEXT) {
                this.state = 160
                _ctx = plain()
                _localctx.href = _ctx

            }
            this.state = 163
            _la = _input.LA(1)

            if (!(_la == Tokens.T__75 || _la == Tokens.T__76)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class Url_namedContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Url_named

        @JvmField @JsName("href$") public var href: Attribute_valueContext? = null
        @JvmField @JsName("content$") public var content: SectionContext? = null
        public fun attribute_value(): Attribute_valueContext = getRuleContext(Attribute_valueContext::class, 0)!!
        public fun section(): SectionContext? = getRuleContext(SectionContext::class, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterUrl_named(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitUrl_named(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitUrl_named(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun url_named(): Url_namedContext {
        var _localctx = Url_namedContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 38, Rules.Url_named)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 165
            _la = _input.LA(1)

            if (!(_la == Tokens.T__77 || _la == Tokens.T__78)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 166
            _ctx = attribute_value()
            _localctx.href = _ctx

            this.state = 167
            match(Tokens.T__4)

            this.state = 169
            errorHandler.sync(this)
            _la = _input.LA(1)

            if ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and -57646075230342146L) != 0L) || ((((_la - 64)) and 0x3f.inv()) == 0 && ((1L shl (_la - 64)) and 5492735L) != 0L)) {
                this.state = 168
                _ctx = section()
                _localctx.content = _ctx

            }
            this.state = 171
            _la = _input.LA(1)

            if (!(_la == Tokens.T__75 || _la == Tokens.T__76)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class ImgContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Img

        @JvmField @JsName("content$") public var content: PlainContext? = null
        public fun plain(): List<PlainContext> = getRuleContexts(PlainContext::class)
        public fun plain(i: Int): PlainContext? = getRuleContext(PlainContext::class, i)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterImg(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitImg(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitImg(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun img(): ImgContext {
        var _localctx = ImgContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 40, Rules.Img)
        var _la: Int

        try {
            enterOuterAlt(_localctx, 1)
            this.state = 173
            _la = _input.LA(1)

            if (!(_la == Tokens.T__79 || _la == Tokens.T__80)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
            this.state = 174
            match(Tokens.T__4)

            this.state = 176
            errorHandler.sync(this)
            _la = _input.LA(1)

            do {
                this.state = 175
                _ctx = plain()
                _localctx.content = _ctx

                this.state = 178
                errorHandler.sync(this)
                _la = _input.LA(1)
            } while ((((_la) and 0x3f.inv()) == 0 && ((1L shl _la) and 254L) != 0L) || _la == Tokens.NUMBER || _la == Tokens.TEXT)
            this.state = 180
            _la = _input.LA(1)

            if (!(_la == Tokens.T__81 || _la == Tokens.T__82)) {
                errorHandler.recoverInline(this)
            }
            else {
                if (_input.LA(1) == Tokens.EOF) {
                    isMatchedEOF = true
                }

                errorHandler.reportMatch(this)
                consume()
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }

    public open class Attribute_valueContext : ParserRuleContext {
        override val ruleIndex: Int = Rules.Attribute_value

        @JvmField @JsName("quoted$") public var quoted: Token? = null
        @JvmField @JsName("unquoted$") public var unquoted: Token? = null
        public fun QUOTED(): TerminalNode? = getToken(Tokens.QUOTED, 0)
        public fun TEXT(): TerminalNode? = getToken(Tokens.TEXT, 0)

        public constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState) {
        }

        override fun enterRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.enterAttribute_value(this)
            }
        }

        override fun exitRule(listener: ParseTreeListener) {
            if (listener is BBCodeListener) {
                listener.exitAttribute_value(this)
            }
        }

        override fun <T> accept(visitor: ParseTreeVisitor<out T>): T {
            return if (visitor is BBCodeVisitor) {
                visitor.visitAttribute_value(this)
            } else {
                visitor.visitChildren(this)
            }
        }
    }


    public fun attribute_value(): Attribute_valueContext {
        var _localctx = Attribute_valueContext(context, state)
        var _token: Token?
        var _ctx: RuleContext?

        enterRule(_localctx, 42, Rules.Attribute_value)

        try {
            this.state = 184
            errorHandler.sync(this)

            when (_input.LA(1)) {
                Tokens.QUOTED -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 1)
                    this.state = 182
                    _token = match(Tokens.QUOTED)
                    _localctx.quoted = _token

                }Tokens.TEXT -> /*LL1AltBlock*/ {
                    enterOuterAlt(_localctx, 2)
                    this.state = 183
                    _token = match(Tokens.TEXT)
                    _localctx.unquoted = _token

                }
                else -> throw NoViableAltException(this)
            }
        }
        catch (re: RecognitionException) {
            _localctx.exception = re
            errorHandler.reportError(this, re)
            errorHandler.recover(this, re)
        }
        finally {
            exitRule()
        }

        return _localctx
    }
}
