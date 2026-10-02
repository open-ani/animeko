/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SelectorSearchConfig] 的 JSON 兼容性: 6.2 以前自动匹配字段平铺在顶层, 现在收进 `autoMatch`.
 * 新旧两种 JSON 都要能读; 写出时两处都写, 让旧客户端继续读到平铺字段.
 */
class SelectorSearchConfigSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun decode(text: String) = json.decodeFromString(SelectorSearchConfig.serializer(), text)
    private fun encode(config: SelectorSearchConfig): JsonObject =
        json.encodeToJsonElement(SelectorSearchConfig.serializer(), config).jsonObject

    @Test
    fun `legacy flat fields populate autoMatch when autoMatch is absent`() {
        val config = decode(
            """
            {
              "searchUrl": "https://example.com/search?wd={keyword}",
              "searchUseOnlyFirstWord": false,
              "searchRemoveSpecial": false,
              "searchUseSubjectNamesCount": 3,
              "filterByEpisodeSort": false,
              "filterBySubjectName": false
            }
            """.trimIndent(),
        )
        assertEquals("https://example.com/search?wd={keyword}", config.searchUrl)
        assertEquals(
            SelectorAutoMatchConfig(
                enabled = true,
                searchUseOnlyFirstWord = false,
                searchRemoveSpecial = false,
                searchUseSubjectNamesCount = 3,
                filterBySubjectName = false,
                filterByEpisodeSort = false,
            ),
            config.autoMatch,
        )
    }

    @Test
    fun `legacy preferShorterName is read from the active subject format`() {
        val config = decode(
            """
            {
              "subjectFormatId": "indexed",
              "selectorSubjectFormatA": { "preferShorterName": true },
              "selectorSubjectFormatIndexed": { "preferShorterName": false }
            }
            """.trimIndent(),
        )
        assertFalse(config.autoMatch.preferShorterName)
    }

    @Test
    fun `preferShorterName is mirrored into every subject format on write`() {
        val obj = encode(SelectorSearchConfig(autoMatch = SelectorAutoMatchConfig(preferShorterName = false)))
        for (key in listOf("selectorSubjectFormatA", "selectorSubjectFormatIndexed", "selectorSubjectFormatJsonPathIndexed")) {
            assertFalse(obj.getValue(key).jsonObject.getValue("preferShorterName").jsonPrimitive.boolean, key)
        }
        assertFalse(obj.getValue("autoMatch").jsonObject.getValue("preferShorterName").jsonPrimitive.boolean)
    }

    @Test
    fun `absent fields fall back to defaults`() {
        val config = decode("""{"searchUrl": "https://example.com/{keyword}"}""")
        assertEquals(SelectorAutoMatchConfig.Default, config.autoMatch)
        assertTrue(config.autoMatch.enabled)
    }

    @Test
    fun `autoMatch wins over legacy flat fields when both present`() {
        val config = decode(
            """
            {
              "searchUseOnlyFirstWord": false,
              "searchUseSubjectNamesCount": 3,
              "autoMatch": { "enabled": false, "searchUseOnlyFirstWord": true, "searchUseSubjectNamesCount": 2 }
            }
            """.trimIndent(),
        )
        assertFalse(config.autoMatch.enabled)
        assertTrue(config.autoMatch.searchUseOnlyFirstWord)
        assertEquals(2, config.autoMatch.searchUseSubjectNamesCount)
    }

    @Test
    fun `serialization writes autoMatch and mirrors legacy flat fields`() {
        val config = SelectorSearchConfig(
            searchUrl = "https://example.com/{keyword}",
            autoMatch = SelectorAutoMatchConfig(
                enabled = false,
                searchUseOnlyFirstWord = false,
                searchRemoveSpecial = false,
                searchUseSubjectNamesCount = 4,
                filterBySubjectName = false,
                filterByEpisodeSort = false,
            ),
        )
        val obj = encode(config)

        val auto = obj.getValue("autoMatch").jsonObject
        assertFalse(auto.getValue("enabled").jsonPrimitive.boolean)
        assertEquals(4, auto.getValue("searchUseSubjectNamesCount").jsonPrimitive.int)

        // 旧客户端只认平铺字段
        assertFalse(obj.getValue("searchUseOnlyFirstWord").jsonPrimitive.boolean)
        assertFalse(obj.getValue("searchRemoveSpecial").jsonPrimitive.boolean)
        assertEquals(4, obj.getValue("searchUseSubjectNamesCount").jsonPrimitive.int)
        assertFalse(obj.getValue("filterBySubjectName").jsonPrimitive.boolean)
        assertFalse(obj.getValue("filterByEpisodeSort").jsonPrimitive.boolean)
    }

    @Test
    fun `round trip preserves config`() {
        val config = SelectorSearchConfig(
            searchUrl = "https://example.com/{keyword}",
            rawBaseUrl = "https://example.com",
            onlySupportsPlayers = listOf("mpv"),
            autoMatch = SelectorAutoMatchConfig(searchUseSubjectNamesCount = 2, filterByEpisodeSort = false),
        )
        assertEquals(config, decode(json.encodeToString(SelectorSearchConfig.serializer(), config)))
    }

    @Test
    fun `arguments round trip through the codec keeps autoMatch`() {
        val arguments = SelectorMediaSourceArguments.Default.copy(
            searchConfig = SelectorSearchConfig(autoMatch = SelectorAutoMatchConfig(enabled = false)),
        )
        val text = json.encodeToString(SelectorMediaSourceArguments.serializer(), arguments)
        val decoded = json.decodeFromString(SelectorMediaSourceArguments.serializer(), text)
        assertEquals(arguments, decoded)
        assertFalse(decoded.searchConfig.autoMatch.enabled)
    }
}
