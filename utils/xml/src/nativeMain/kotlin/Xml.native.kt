/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.xml

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parseSource
import com.fleeksoft.ksoup.parser.Parser
import com.fleeksoft.ksoup.select.QueryParser
import kotlinx.io.Source

actual object Xml {
    actual fun parse(string: String, baseUrl: String): Document {
        return Ksoup.parse(string, baseUri = baseUrl, parser = Parser.xmlParser())
    }

    actual fun parse(source: Source, baseUrl: String): Document {
        return Ksoup.parseSource(source, parser = Parser.xmlParser(), baseUri = baseUrl)
    }

    actual fun parse(string: String): Document {
        return Ksoup.parse(string, parser = Parser.xmlParser())
    }

    actual fun parse(source: Source): Document {
        return Ksoup.parseSource(source, parser = Parser.xmlParser(), baseUri = "")
    }
}

actual object QueryParser {
    @Throws(IllegalStateException::class)
    actual fun parseSelector(selector: String): Evaluator = QueryParser.parse(selector)
}

actual object Html {
    actual fun parse(string: String): Document {
        return Ksoup.parse(string, Parser.htmlParser())
    }

    actual fun parse(
        string: String,
        baseUrl: String,
    ): Document {
        return Ksoup.parse(string, baseUri = baseUrl, parser = Parser.htmlParser())
    }

    actual fun parse(source: Source): Document {
        return Ksoup.parseSource(source, baseUri = "", parser = Parser.htmlParser())
    }

    actual fun parse(
        source: Source,
        baseUrl: String,
    ): Document {
        return Ksoup.parseSource(source, baseUri = baseUrl, parser = Parser.htmlParser())
    }
}