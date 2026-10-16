/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceData
import me.him188.ani.app.domain.mediasource.codec.createTestMediaSourceCodecManager
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(TestOnly::class)
class JsonApiSearchConfigSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `an empty object decodes to the defaults`() {
        assertEquals(JsonApiSearchConfig.Default, json.decodeFromString(JsonApiSearchConfig.serializer(), "{}"))
    }

    @Test
    fun `durations are written as milliseconds like the selector config`() {
        val config = JsonApiSearchConfig.Default.copy(requestInterval = 3.seconds, searchCacheTtl = 30.minutes)
        val encoded = json.encodeToJsonElement(JsonApiSearchConfig.serializer(), config).jsonObject
        assertEquals("3000", encoded["requestInterval"]!!.jsonPrimitive.content)
        assertEquals("1800000", encoded["searchCacheTtl"]!!.jsonPrimitive.content)
        assertEquals(config, json.decodeFromString(JsonApiSearchConfig.serializer(), encoded.toString()))
    }

    @Test
    fun `stages formats and enums are written with stable names`() {
        val encoded = json.encodeToJsonElement(JsonApiSearchConfig.serializer(), SampleSite.config).jsonObject
        assertEquals("POST", encoded["search"]!!.jsonObject["request"]!!.jsonObject["method"]!!.jsonPrimitive.content)
        assertEquals("declared", encoded["subject"]!!.jsonObject["channelFormatId"]!!.jsonPrimitive.content)
        val channels = encoded["subject"]!!.jsonObject["channelFormatDeclared"]!!.jsonObject["channels"]!!.jsonArray
        assertEquals("A", channels[0].jsonObject["match"]!!.jsonPrimitive.content)
    }

    @Test
    fun `arguments round trip through the codec manager`() {
        val manager = createTestMediaSourceCodecManager()
        val arguments = JsonApiMediaSourceArguments(
            name = "Sample Site",
            description = "test",
            iconUrl = "https://example.com/icon.png",
            searchConfig = SampleSite.config.copy(requestInterval = 2.seconds),
        )

        val exported = manager.encode(arguments)
        assertEquals(FactoryId("json-api"), exported.factoryId)
        assertEquals(1, exported.version)

        val text = manager.serializeSingleToString(arguments)
        val decoded = manager.decode(json.decodeFromString(ExportedMediaSourceData.serializer(), text))
        assertEquals(arguments, decoded)
    }
}
