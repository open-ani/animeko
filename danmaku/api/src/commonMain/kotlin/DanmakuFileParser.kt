/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 用户可以导入的本地弹幕文件格式.
 */
enum class DanmakuFileFormat {
    /**
     * Bilibili 的 XML 弹幕文件. 根元素为 `<i>`, 每条弹幕是
     * `<d p="time,mode,fontsize,color,timestamp,pool,uidhash,rowid">text</d>`.
     */
    BilibiliXml,

    /**
     * dandanplay 的 JSON 弹幕文件. 形如
     * `{"count": n, "comments": [{"cid": 1, "p": "time,mode,color,uid", "m": "text"}]}`.
     */
    DandanplayJson,
}

/**
 * 解析结果.
 *
 * @param skippedCount 被跳过的条目数 (格式错误, 或是不支持的高级/代码弹幕).
 */
class ParsedDanmakuFile(
    val format: DanmakuFileFormat,
    val list: List<DanmakuInfo>,
    val skippedCount: Int,
)

/**
 * 本地弹幕文件解析失败.
 *
 * [reason] 是给 UI 用来出本地化提示的; [message] 只用于日志.
 */
class DanmakuFileParseException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Reason {
        /**
         * 完全不认识这个文件, 既不是 XML 也不是 JSON.
         */
        UnsupportedFormat,

        /**
         * 格式认出来了, 但是内容坏了.
         */
        Malformed,

        /**
         * 文件是好的, 但是里面一条能用的弹幕都没有.
         */
        NoDanmaku,
    }
}

/**
 * 解析用户导入的本地弹幕文件.
 *
 * 本对象不依赖任何 IO, 输入是已经读进内存的字符串/字节, 因此可以直接单元测试.
 * 读文件由 app 层负责.
 *
 * 解析是"尽力而为"的: 单条弹幕格式错误只会导致这一条被跳过 (计入
 * [ParsedDanmakuFile.skippedCount]), 不会让整个文件失败. 只有在完全无法识别文件格式,
 * 或是识别出来了但一条弹幕都没有的时候, 才会抛 [DanmakuFileParseException].
 */
object DanmakuFileParser {
    /**
     * 单个文件最多解析多少条弹幕, 防止用户导入一个巨大的文件导致 OOM.
     */
    const val MAX_DANMAKU_COUNT = 500_000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 根据内容 (而不是扩展名) 判断文件格式. 无法识别时返回 `null`.
     */
    fun detectFormat(content: String): DanmakuFileFormat? {
        val firstChar = content.firstOrNull { !it.isWhitespace() && it != '﻿' } ?: return null
        return when (firstChar) {
            '{', '[' -> DanmakuFileFormat.DandanplayJson
            '<' -> DanmakuFileFormat.BilibiliXml
            else -> null
        }
    }

    /**
     * 解析文件内容. 字节会按 UTF-8 解码 (并去掉可能存在的 BOM).
     *
     * @throws DanmakuFileParseException
     */
    fun parse(
        bytes: ByteArray,
        serviceId: DanmakuServiceId = DanmakuServiceId.LocalFile,
    ): ParsedDanmakuFile = parse(bytes.decodeToString(), serviceId)

    /**
     * @throws DanmakuFileParseException
     */
    fun parse(
        content: String,
        serviceId: DanmakuServiceId = DanmakuServiceId.LocalFile,
    ): ParsedDanmakuFile {
        val text = content.removePrefix("﻿")
        val format = detectFormat(text)
            ?: throw DanmakuFileParseException(
                DanmakuFileParseException.Reason.UnsupportedFormat,
                "Unrecognized danmaku file format, expected Bilibili XML or dandanplay JSON",
            )

        val result = when (format) {
            DanmakuFileFormat.BilibiliXml -> parseBilibiliXml(text, serviceId)
            DanmakuFileFormat.DandanplayJson -> parseDandanplayJson(text, serviceId)
        }
        if (result.list.isEmpty()) {
            throw DanmakuFileParseException(
                DanmakuFileParseException.Reason.NoDanmaku,
                "No usable danmaku in file",
            )
        }
        return result
    }

    ///////////////////////////////////////////////////////////////////////////
    // Bilibili XML
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 手写扫描器, 而不是引入一个 XML 库: 弹幕 XML 的结构非常固定, 而且我们只关心 `<d>` 元素.
     */
    private fun parseBilibiliXml(content: String, serviceId: DanmakuServiceId): ParsedDanmakuFile {
        val list = ArrayList<DanmakuInfo>()
        var skipped = 0
        var index = 0
        var searchFrom = 0

        while (list.size < MAX_DANMAKU_COUNT) {
            val tagStart = content.indexOf("<d", searchFrom)
            if (tagStart == -1) break
            // 排除 <div>, <data> 之类. `<d` 后面必须是空白或者 `>`.
            val afterName = content.getOrNull(tagStart + 2)
            if (afterName != null && afterName != '>' && !afterName.isWhitespace()) {
                searchFrom = tagStart + 2
                continue
            }
            val tagEnd = content.indexOf('>', tagStart)
            if (tagEnd == -1) break
            searchFrom = tagEnd + 1

            if (content.getOrNull(tagEnd - 1) == '/') continue // 自闭合, 没有文本

            val textEnd = content.indexOf("</d>", tagEnd)
            if (textEnd == -1) break
            searchFrom = textEnd + 4

            val attributes = content.substring(tagStart + 2, tagEnd)
            val p = extractAttribute(attributes, "p")
            if (p == null) {
                skipped++
                continue
            }
            val rawText = content.substring(tagEnd + 1, textEnd)
            val pFields = p.split(',')
            val danmaku = buildDanmaku(
                p = pFields,
                rawText = rawText,
                serviceId = serviceId,
                index = index++,
                colorFieldIndex = 3,
                idField = pFields.getOrNull(7)?.trim()?.takeIf { it.isNotEmpty() },
                senderId = pFields.getOrNull(6)?.trim().orEmpty(),
                decodeText = { unescapeXml(it) },
            )
            if (danmaku == null) skipped++ else list.add(danmaku)
        }

        if (list.isEmpty() && skipped == 0 && !content.contains("<d")) {
            throw DanmakuFileParseException(
                DanmakuFileParseException.Reason.NoDanmaku,
                "No <d> element found in XML file",
            )
        }
        return ParsedDanmakuFile(DanmakuFileFormat.BilibiliXml, list, skipped)
    }

    /**
     * 从 `p="..." user="..."` 这样的属性串里取出某个属性的值. 只支持双引号和单引号.
     */
    private fun extractAttribute(attributes: String, name: String): String? {
        var i = 0
        while (i < attributes.length) {
            val nameStart = attributes.indexOf(name, i)
            if (nameStart == -1) return null
            i = nameStart + name.length
            // 属性名前必须是空白 (或者是串首)
            if (nameStart > 0 && !attributes[nameStart - 1].isWhitespace()) continue
            var j = i
            while (j < attributes.length && attributes[j].isWhitespace()) j++
            if (attributes.getOrNull(j) != '=') continue
            j++
            while (j < attributes.length && attributes[j].isWhitespace()) j++
            val quote = attributes.getOrNull(j) ?: return null
            if (quote != '"' && quote != '\'') continue
            val valueEnd = attributes.indexOf(quote, j + 1)
            if (valueEnd == -1) return null
            return attributes.substring(j + 1, valueEnd)
        }
        return null
    }

    private fun unescapeXml(text: String): String {
        if (text.indexOf('&') == -1) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '&') {
                sb.append(c)
                i++
                continue
            }
            val semicolon = text.indexOf(';', i + 1)
            if (semicolon == -1 || semicolon - i > 12) {
                sb.append(c)
                i++
                continue
            }
            when (val entity = text.substring(i + 1, semicolon)) {
                "amp" -> sb.append('&')
                "lt" -> sb.append('<')
                "gt" -> sb.append('>')
                "quot" -> sb.append('"')
                "apos" -> sb.append('\'')
                else -> {
                    val code = when {
                        entity.startsWith("#x") || entity.startsWith("#X") ->
                            entity.substring(2).toIntOrNull(16)

                        entity.startsWith("#") -> entity.substring(1).toIntOrNull()
                        else -> null
                    }
                    if (code == null || code !in 1..0x10FFFF) {
                        sb.append(text, i, semicolon + 1) // 不认识, 原样保留
                    } else {
                        appendCodePoint(sb, code)
                    }
                }
            }
            i = semicolon + 1
        }
        return sb.toString()
    }

    private fun appendCodePoint(sb: StringBuilder, code: Int) {
        if (code <= 0xFFFF) {
            sb.append(code.toChar())
        } else {
            val v = code - 0x10000
            sb.append((0xD800 + (v shr 10)).toChar())
            sb.append((0xDC00 + (v and 0x3FF)).toChar())
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // dandanplay JSON
    ///////////////////////////////////////////////////////////////////////////

    private fun parseDandanplayJson(content: String, serviceId: DanmakuServiceId): ParsedDanmakuFile {
        val root = try {
            json.parseToJsonElement(content)
        } catch (e: Exception) {
            throw DanmakuFileParseException(
                DanmakuFileParseException.Reason.Malformed,
                "Failed to parse JSON danmaku file: ${e.message}",
                e,
            )
        }

        val comments = try {
            if (root is JsonArray) {
                root
            } else {
                root.jsonObject["comments"]?.jsonArray
                    ?: throw DanmakuFileParseException(
                        DanmakuFileParseException.Reason.Malformed,
                        "JSON danmaku file has no `comments` field",
                    )
            }
        } catch (e: DanmakuFileParseException) {
            throw e
        } catch (e: Exception) {
            throw DanmakuFileParseException(
                DanmakuFileParseException.Reason.Malformed,
                "Malformed JSON danmaku file: ${e.message}",
                e,
            )
        }

        val list = ArrayList<DanmakuInfo>(comments.size.coerceAtMost(4096))
        var skipped = 0
        var index = 0
        for (element in comments) {
            if (list.size >= MAX_DANMAKU_COUNT) break
            val obj = (element as? JsonObject)
            if (obj == null) {
                skipped++
                continue
            }
            val p = obj["p"]?.jsonPrimitiveContentOrNull()
            val m = obj["m"]?.jsonPrimitiveContentOrNull()
            if (p == null || m == null) {
                skipped++
                continue
            }
            val pFields = p.split(',')
            val danmaku = buildDanmaku(
                p = pFields,
                rawText = m,
                serviceId = serviceId,
                index = index++,
                colorFieldIndex = 2,
                idField = obj["cid"]?.jsonPrimitiveContentOrNull(),
                senderId = pFields.getOrNull(3)?.trim().orEmpty(),
                decodeText = { it },
            )
            if (danmaku == null) skipped++ else list.add(danmaku)
        }
        return ParsedDanmakuFile(DanmakuFileFormat.DandanplayJson, list, skipped)
    }

    private fun JsonElement.jsonPrimitiveContentOrNull(): String? =
        try {
            jsonPrimitive.content
        } catch (e: IllegalArgumentException) {
            null
        }

    ///////////////////////////////////////////////////////////////////////////
    // 公共部分
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 两种格式的 `p` 属性都是逗号分隔, 前两个字段都是 `time,mode`, 只有颜色的下标不同:
     * Bilibili 是 `time,mode,fontsize,color,...`, dandanplay 是 `time,mode,color,uid`.
     *
     * @return `null` 表示这条弹幕应当被跳过.
     */
    private inline fun buildDanmaku(
        p: List<String>,
        rawText: String,
        serviceId: DanmakuServiceId,
        index: Int,
        colorFieldIndex: Int,
        idField: String?,
        senderId: String,
        decodeText: (String) -> String,
    ): DanmakuInfo? {
        val fields = p
        if (fields.size <= colorFieldIndex) return null

        val seconds = fields[0].trim().toDoubleOrNull() ?: return null
        if (seconds.isNaN() || seconds.isInfinite() || seconds < 0.0) return null
        val playTimeMillis = (seconds * 1000).toLong()

        val location = parseLocation(fields[1].trim().toIntOrNull() ?: return null) ?: return null

        // 颜色可能超过 Int.MAX_VALUE (虽然不合法), 用 Long 解析后再截断
        val color = (fields[colorFieldIndex].trim().toLongOrNull() ?: return null)
            .toInt() and 0xFFFFFF

        val text = decodeText(rawText).trim { it.isWhitespace() || it.isISOControl() }
        if (text.isEmpty()) return null

        return DanmakuInfo(
            // 加前缀避免和其他弹幕源的 id 撞车
            id = "local-file-" + (idField?.takeIf { it.isNotEmpty() } ?: index.toString()),
            serviceId = serviceId,
            senderId = senderId,
            content = DanmakuContent(
                playTimeMillis = playTimeMillis,
                color = color,
                text = text,
                location = location,
            ),
        )
    }

    /**
     * `null` 表示这个模式不支持 (7 = 高级弹幕, 8 = 代码弹幕, 9 = BAS 弹幕), 应当跳过.
     */
    private fun parseLocation(mode: Int): DanmakuLocation? = when (mode) {
        1, 2, 3 -> DanmakuLocation.NORMAL
        4 -> DanmakuLocation.BOTTOM
        5 -> DanmakuLocation.TOP
        6 -> DanmakuLocation.NORMAL // 逆向滚动, 渲染器不支持, 当作普通滚动
        else -> null
    }
}
