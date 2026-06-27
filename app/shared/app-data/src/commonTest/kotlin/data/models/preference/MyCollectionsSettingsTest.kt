/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MyCollectionsSettingsTest {
    @Test
    fun `default sortByName is false preserves old behavior`() {
        assertFalse(MyCollectionsSettings.Default.sortByName)
    }

    @Test
    fun `old JSON without sortByName deserializes with default false`() {
        val oldJson = """{"enableListAnimation1":true}"""
        val parsed = Json { ignoreUnknownKeys = true }
            .decodeFromString(MyCollectionsSettings.serializer(), oldJson)
        assertEquals(true, parsed.enableListAnimation1)
        assertFalse(parsed.sortByName)
    }
}
