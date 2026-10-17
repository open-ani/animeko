/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.source.MediaSourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaSourceLocationTest {
    @Test
    fun `locations initialized before deserialization retain their wire names`() {
        val locations = listOf(MediaSourceLocation.Online, MediaSourceLocation.Lan, MediaSourceLocation.Local)
        assertEquals(locations, MediaSourceLocation.entries)
        for ((location, name) in locations.zip(listOf("ONLINE", "LAN", "LOCAL"))) {
            val encoded = "\"$name\""
            assertEquals(encoded, Json.encodeToString<MediaSourceLocation>(location))
            assertEquals(location, Json.decodeFromString<MediaSourceLocation>(encoded))
        }
    }

    @Test
    fun `unknown location is rejected`() {
        assertFailsWith<SerializationException> { Json.decodeFromString<MediaSourceLocation>("\"UNKNOWN\"") }
    }
}
