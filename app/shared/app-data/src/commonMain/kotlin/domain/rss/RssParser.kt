/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.rss

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import me.him188.ani.utils.xml.Element

class RssParser(
    private val includeOrigin: Boolean,
) {
    companion object {
        private val parserWithOrigin = RssParser(true)
        private val parserWithoutOrigin = RssParser(true)

        fun parse(document: Element, includeOrigin: Boolean): RssChannel? {
            val parser = if (includeOrigin) {
                parserWithOrigin
            } else {
                parserWithoutOrigin
            }
            return parser.parse(document)
        }
    }

    fun parse(document: Element): RssChannel? {
        if (document.childNodeSize() == 0) {
            return null
        }
        if (document.tagName() == "#root") {
            document.getElementsByTag("channel").firstOrNull()?.let {
                return parseImpl(it)
            }
        }
        if (document.tagName() == "channel") {
            return parseImpl(document)
        }
        document.childNodes()
            .find { it is Element && it.tagName() == "channel" }
            ?.let {
                return parseImpl(it as Element)
            }

        return null
    }

    private fun parseImpl(element: Element): RssChannel {
        val children = element.childNodes().asSequence().filterIsInstance<Element>()
        return RssChannel(
            title = children.findTagText("title").orEmpty(),
            description = children.findTagText("description").orEmpty(),
            link = children.findTagText("link").orEmpty(),
            ttl = children.findTagText("ttl")?.toIntOrNull() ?: 0,
            items = children.filter { it.tagName() == "item" }.map { parseItem(it) }.toList(),
            origin = if (includeOrigin) element else null,
        )
    }

    private fun parseItem(element: Element): RssItem {
        val children = element.childNodes().asSequence().filterIsInstance<Element>()
        return RssItem(
            title = children.findTagText("title").orEmpty(),
            description = children.findTagText("description").orEmpty(),
            pubDate = children.findTagText("pubDate").orEmpty().let {
                // java.time.format.DateTimeParseException: Text 'Sun, 25 Feb 2024 08:32:16 -0800' could not be parsed at index 0
                RssParser_parseTime(it)
            },
            link = children.findTagText("link").orEmpty(),
            guid = children.findTagText("guid").orEmpty(),
            enclosure = children.find { it.tagName() == "enclosure" }?.let { parseEnclosure(it) },
            origin = if (includeOrigin) element else null,
        )
    }

    private fun parseEnclosure(element: Element): RssEnclosure {
        return RssEnclosure(
            url = element.attr("url"),
            type = element.attr("type"),
            length = element.attr("length").toLongOrNull() ?: 0,
        )
    }

    private fun Sequence<Element>.findTagText(name: String): String? {
        return find { it.tagName() == name }?.text()
    }
}

@Suppress("FunctionName")
internal expect fun RssParser_parseTime(text: String): LocalDateTime?


@OptIn(FormatStringsInDatetimeFormats::class)
private val FORMATTER = LocalDateTime.Format { byUnicodePattern("dd MM yyyy HH:mm:ss") }

@Suppress("FunctionName")
internal fun RssParser_parseTimeUsingKtx(text: String): LocalDateTime? {
    val text2 = text.substringAfter(", ").let {
        when {
            "Jan" in it -> it.replace("Jan", "01")
            "Feb" in it -> it.replace("Feb", "02")
            "Mar" in it -> it.replace("Mar", "03")
            "Apr" in it -> it.replace("Apr", "04")
            "May" in it -> it.replace("May", "05")
            "Jun" in it -> it.replace("Jun", "06")
            "Jul" in it -> it.replace("Jul", "07")
            "Aug" in it -> it.replace("Aug", "08")
            "Sep" in it -> it.replace("Sep", "09")
            "Oct" in it -> it.replace("Oct", "10")
            "Nov" in it -> it.replace("Nov", "11")
            "Dec" in it -> it.replace("Dec", "12")
            else -> it
        }
    }.substringBeforeLast(" ")

    FORMATTER.parseOrNull(text2)?.let { return it }
    return null // not supported
}