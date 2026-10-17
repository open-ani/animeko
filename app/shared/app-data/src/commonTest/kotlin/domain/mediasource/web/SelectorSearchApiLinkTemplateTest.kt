/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 回归 `linkTemplate`: 站点搜索 API 只返回裸 id 时(girigiri `ajax/suggest` 只有 `{id, name, en, pic}` 没有链接),
 * 用模板把 id 拼成详情页链接, 而不必写站点专属的 Kotlin 路由.
 */
class SelectorSearchApiLinkTemplateTest {
    private val suggestJson = """
        {"list":[
          {"id":"5395","name":"女友成堆","en":"Kanojo mo Kanojo","pic":"a.jpg"},
          {"id":"25582","name":"女友成堆 第二季","en":"KanoKano 2","pic":"b.jpg"}
        ]}
    """.trimIndent()

    private val site = MockEngine { _ ->
        respond(
            suggestJson,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    @Test
    fun `bare id becomes templated detail url`() = runTest {
        val config = SelectorSearchConfig(
            searchUrl = "https://ani.girigirilove.com/index.php/ajax/suggest?mid=1&wd={keyword}",
            subjectFormatId = SelectorSubjectFormatJsonPathIndexed.id,
            selectorSubjectFormatJsonPathIndexed = SelectorSubjectFormatJsonPathIndexed.Config(
                selectLinks = "$.list[*]['id']",
                selectNames = "$.list[*]['name']",
                linkTemplate = "/GV{value}/",
            ),
        )
        val source = createTestSelectorMediaSource(config, site)

        val subjects = source.searchSubjects("女友成堆")

        // {value} 代入 selectLinks 抽出的 id, 相对模板以 baseUrl(https://ani.girigirilove.com) 解析
        assertEquals(
            listOf(
                "https://ani.girigirilove.com/GV5395/",
                "https://ani.girigirilove.com/GV25582/",
            ),
            subjects.map { it.url },
        )
        assertEquals(listOf("女友成堆", "女友成堆 第二季"), subjects.map { it.name })
    }

    /**
     * 与 `girigiri愛動漫.json` 的 `searchConfig` 同形, 验证 `linkTemplate` 从规则 JSON 反序列化后同样生效
     * (规则作者写的是 JSON, 不是 Kotlin).
     */
    @Test
    fun `rule json with linkTemplate deserializes and templates urls`() = runTest {
        val ruleJson = """
            {
              "searchUrl": "https://ani.girigirilove.com/index.php/ajax/suggest?mid=1&wd={keyword}",
              "subjectFormatId": "json-path-indexed",
              "selectorSubjectFormatJsonPathIndexed": {
                "selectLinks": "$.list[*]['id']",
                "selectNames": "$.list[*]['name']",
                "linkTemplate": "/GV{value}/"
              }
            }
        """.trimIndent()
        val config = Json { ignoreUnknownKeys = true }
            .decodeFromString(SelectorSearchConfig.serializer(), ruleJson)
        val source = createTestSelectorMediaSource(config, site)

        val subjects = source.searchSubjects("女友成堆")

        assertEquals(
            listOf(
                "https://ani.girigirilove.com/GV5395/",
                "https://ani.girigirilove.com/GV25582/",
            ),
            subjects.map { it.url },
        )
    }
}
