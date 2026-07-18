/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.selector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectorConfigSupportTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun validate(config: String): ValidateSelectorConfigResult {
        return SelectorConfigSupport.validate(
            ValidateSelectorConfigInput(config = json.parseToJsonElement(config)),
            json,
        )
    }

    @Test
    fun `valid bare searchConfig`() {
        val result = validate(
            """{"searchUrl": "https://example.com/search?q={keyword}"}""",
        )
        assertTrue(result.ok, result.summary)
        assertEquals(SelectorConfigSupport.FORMAT_SEARCH_CONFIG, result.detectedFormat)
        assertTrue(result.issues.none { it.severity == "error" })
    }

    @Test
    fun `valid arguments form`() {
        val result = validate(
            """
            {
              "name": "Test Source",
              "description": "",
              "iconUrl": "",
              "searchConfig": {"searchUrl": "https://example.com/search?q={keyword}"}
            }
            """.trimIndent(),
        )
        assertTrue(result.ok, result.summary)
        assertEquals(SelectorConfigSupport.FORMAT_ARGUMENTS, result.detectedFormat)
        assertEquals("Test Source", result.sourceName)
    }

    @Test
    fun `valid exported form`() {
        val result = validate(
            """
            {
              "factoryId": "web-selector",
              "version": 2,
              "arguments": {
                "name": "Exported",
                "description": "",
                "iconUrl": "",
                "searchConfig": {"searchUrl": "https://example.com/s/{keyword}"}
              }
            }
            """.trimIndent(),
        )
        assertTrue(result.ok, result.summary)
        assertEquals(SelectorConfigSupport.FORMAT_EXPORTED, result.detectedFormat)
        assertEquals("Exported", result.sourceName)
    }

    @Test
    fun `exported list picks web-selector entries`() {
        val result = validate(
            """
            {
              "mediaSources": [
                {"factoryId": "rss", "version": 1, "arguments": {}},
                {
                  "factoryId": "web-selector",
                  "version": 2,
                  "arguments": {
                    "name": "In List",
                    "description": "",
                    "iconUrl": "",
                    "searchConfig": {"searchUrl": "https://example.com/s/{keyword}"}
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        assertTrue(result.ok, result.summary)
        assertEquals(SelectorConfigSupport.FORMAT_EXPORTED_LIST, result.detectedFormat)
        assertEquals("In List", result.sourceName)
    }

    @Test
    fun `blank searchUrl is an error`() {
        val result = validate("""{"searchUrl": ""}""")
        assertFalse(result.ok)
        assertTrue(result.issues.any { it.severity == "error" && it.path.endsWith("searchUrl") })
    }

    @Test
    fun `missing keyword placeholder is a warning`() {
        val result = validate("""{"searchUrl": "https://example.com/search"}""")
        assertTrue(result.ok)
        assertTrue(result.issues.any { it.severity == "warning" && it.path.endsWith("searchUrl") })
    }

    @Test
    fun `invalid css selector is an error`() {
        val result = validate(
            """
            {
              "searchUrl": "https://example.com/s/{keyword}",
              "subjectFormatId": "a",
              "selectorSubjectFormatA": {"selectLists": "div[["}
            }
            """.trimIndent(),
        )
        assertFalse(result.ok)
        assertTrue(result.issues.any { it.severity == "error" && it.path.endsWith("selectLists") })
    }

    @Test
    fun `invalid video regex is an error`() {
        val result = validate(
            """
            {
              "searchUrl": "https://example.com/s/{keyword}",
              "matchVideo": {"matchVideoUrl": "([unclosed"}
            }
            """.trimIndent(),
        )
        assertFalse(result.ok)
        assertTrue(result.issues.any { it.severity == "error" && it.path.endsWith("matchVideoUrl") })
    }

    @Test
    fun `episode sort regex without ep group is a warning`() {
        val result = validate(
            """
            {
              "searchUrl": "https://example.com/s/{keyword}",
              "channelFormatId": "no-channel",
              "selectorChannelFormatNoChannel": {
                "selectEpisodes": "a.ep",
                "matchEpisodeSortFromName": "EP(\\d+)"
              }
            }
            """.trimIndent(),
        )
        assertTrue(result.ok, result.summary)
        assertTrue(
            result.issues.any { it.severity == "warning" && it.path.endsWith("matchEpisodeSortFromName") },
        )
    }

    @Test
    fun `unknown format id is an error`() {
        val result = validate(
            """
            {
              "searchUrl": "https://example.com/s/{keyword}",
              "subjectFormatId": "does-not-exist"
            }
            """.trimIndent(),
        )
        assertFalse(result.ok)
        assertTrue(result.issues.any { it.severity == "error" && it.path.endsWith("subjectFormatId") })
    }

    @Test
    fun `unrecognized shape reports readable error`() {
        val result = validate("""{"foo": 1}""")
        assertFalse(result.ok)
        assertTrue(result.summary.contains("无法识别"), result.summary)
    }

    @Test
    fun `config as json string is unwrapped`() {
        val result = SelectorConfigSupport.validate(
            ValidateSelectorConfigInput(
                config = JsonPrimitive("""{"searchUrl": "https://example.com/s/{keyword}"}"""),
            ),
            json,
        )
        assertTrue(result.ok, result.summary)
    }

    @Test
    fun `parseSelectorArguments rejects wrong factory`() {
        assertFailsWith<IllegalArgumentException> {
            SelectorConfigSupport.parseSelectorArguments(
                json.parseToJsonElement("""{"factoryId": "rss", "version": 1, "arguments": {}}"""),
                json,
            )
        }
    }

    @Test
    fun `parseSelectorArguments accepts bare searchConfig`() {
        val parsed = SelectorConfigSupport.parseSelectorArguments(
            json.parseToJsonElement("""{"searchUrl": "https://example.com/s/{keyword}"}"""),
            json,
        )
        assertEquals(SelectorConfigSupport.FORMAT_SEARCH_CONFIG, parsed.detectedFormat)
        assertEquals("https://example.com/s/{keyword}", parsed.arguments.searchConfig.searchUrl)
    }

    /**
     * selector-engine-docs.md 中的「最小可用示例」必须始终是有效配置 (文档由 get_selector_engine_docs 提供给 agent).
     */
    @Test
    fun `docs example config is valid`() {
        val docs = checkNotNull(javaClass.getResourceAsStream("/selector-engine-docs.md")) {
            "selector-engine-docs.md not found in resources"
        }.bufferedReader().use { it.readText() }
        val example = docs.substringAfter("```json", "").substringBefore("```").trim()
        assertTrue(example.isNotEmpty(), "docs should contain a ```json example block")

        val result = validate(example)
        assertTrue(result.ok, result.summary + ": " + result.issues)
        assertEquals(SelectorConfigSupport.FORMAT_EXPORTED, result.detectedFormat)

        val parsed = SelectorConfigSupport.parseSelectorArguments(json.parseToJsonElement(example), json)
        assertEquals("稀饭动漫", parsed.arguments.name)
        assertEquals("indexed", parsed.arguments.searchConfig.subjectFormatId.value)
        assertEquals("index-grouped", parsed.arguments.searchConfig.channelFormatId.value)
    }
}
