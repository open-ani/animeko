/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MediaCacheMetadataSerializationTest {
    @Test
    fun `legacy records retain episode identity without implying a completed file`() {
        val legacy = """{
            "subjectId":"1", "episodeId":"2", "subjectNames":[],
            "episodeSort":{"type":"me.him188.ani.datasources.api.EpisodeSort.Normal","number":11.5},
            "episodeName":"SP", "creationTime":123
        }"""
        val record = Json.decodeFromString(MediaCacheMetadata.serializer(), legacy)
        assertEquals("2", record.episodeId)
        assertEquals(EpisodeSort("11.5"), record.episodeSort)
        assertEquals(123L, record.creationTime)
        assertNull(record.pathInTorrent)
        assertFalse(record.completed)
    }
}
