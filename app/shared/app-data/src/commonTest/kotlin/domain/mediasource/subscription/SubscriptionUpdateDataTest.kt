/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.subscription

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceData
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.createTestMediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.serializeToString
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceArguments
import me.him188.ani.datasources.api.source.FactoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SubscriptionUpdateDataTest {
    private val codecManager = createTestMediaSourceCodecManager()

    private val entry = """{"factoryId":"rss","version":1,"arguments":{"name":"Mikan","description":"","iconUrl":""}}"""

    @Test
    fun `reads legacy subscription`() {
        val data = codecManager.decodeSubscriptionFromString(
            """{"exportedMediaSourceDataList":{"mediaSources":[$entry]}}""",
        )
        assertEquals(FactoryId("rss"), data.mediaSources.single().factoryId)
        assertEquals(SubscriptionMetadata(), data.metadata)
    }

    @Test
    fun `reads legacy exported media sources`() {
        val data = codecManager.decodeSubscriptionFromString("""{"mediaSources":[$entry]}""")
        assertEquals(FactoryId("rss"), data.mediaSources.single().factoryId)
    }

    @Test
    fun `reads current format`() {
        val data = codecManager.decodeSubscriptionFromString(
            """
            {
              "schemaVersion": 2,
              "name": "在线源合集",
              "description": "常用在线视频网站合集",
              "websiteUrl": "https://example.com",
              "iconUrl": "https://example.com/icon.png",
              "mediaSources": [{"id":"mikan","factoryId":"rss","version":1,"arguments":{"name":"Mikan","description":"","iconUrl":""}}]
            }
            """.trimIndent(),
        )
        assertEquals(
            SubscriptionMetadata("在线源合集", "常用在线视频网站合集", "https://example.com", "https://example.com/icon.png"),
            data.metadata,
        )
        assertEquals("mikan", data.mediaSources.single().id)
    }

    @Test
    fun `ignores unknown fields`() {
        val data = codecManager.decodeSubscriptionFromString(
            """{"schemaVersion":2,"future":{"a":1},"mediaSources":[{"future":true,"factoryId":"rss","version":1,"arguments":{}}]}""",
        )
        assertNull(data.mediaSources.single().id)
    }

    @Test
    fun `rejects newer schema version`() {
        val e = assertFailsWith<UnsupportedSubscriptionSchemaException> {
            codecManager.decodeSubscriptionFromString("""{"schemaVersion":3,"mediaSources":[]}""")
        }
        assertEquals(3, e.schemaVersion)
    }

    @Test
    fun `rejects content without media sources`() {
        assertFailsWith<SerializationException> {
            codecManager.decodeSubscriptionFromString("""{"name":"在线源合集"}""")
        }
    }

    @Test
    fun `exports current format without absent fields`() {
        val obj = Json.parseToJsonElement(
            codecManager.serializeToString(listOf(RssMediaSourceArguments.Default)),
        ).jsonObject

        assertEquals(setOf("schemaVersion", "mediaSources"), obj.keys)
        assertEquals(SubscriptionUpdateData.CURRENT_SCHEMA_VERSION, obj.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(
            setOf("factoryId", "version", "arguments"),
            obj.getValue("mediaSources").jsonArray.single().jsonObject.keys,
        )
    }

    @Test
    fun `exports null values inside arguments`() {
        val arguments = buildJsonObject {
            put("name", "Mikan")
            put("extra", JsonNull)
        }
        val string = codecManager.serializeSubscriptionToString(
            SubscriptionUpdateData(mediaSources = listOf(ExportedMediaSourceData(FactoryId("rss"), 1, arguments))),
        )
        assertEquals(arguments, codecManager.decodeSubscriptionFromString(string).mediaSources.single().arguments)
    }

    @Test
    fun `exported media sources can be imported by legacy clients`() {
        val string = codecManager.serializeToString(listOf(RssMediaSourceArguments.Default))
        // 旧客户端导入时使用的结构
        val legacy = MediaSourceCodecManager.json.decodeFromString(LegacyExportedMediaSourceDataList.serializer(), string)
        assertEquals(FactoryId("rss"), legacy.mediaSources.single().factoryId)
    }

    @Serializable
    private class LegacyExportedMediaSourceDataList(
        val mediaSources: List<ExportedMediaSourceData>,
    )
}
