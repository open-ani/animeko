/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.client.models.AniDELETE
import me.him188.ani.client.models.AniSyncRequest
import me.him188.ani.client.models.AniUPSERT
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackHistorySyncerTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `api request serializes playback history ops with op type discriminator`() {
        val encoded = json.encodeToString(
            AniSyncRequest(
                ops = listOf(
                    AniUPSERT(
                        episodeId = 1,
                        subjectId = 10,
                        positionMillis = 20_000,
                        durationMillis = 100_000,
                        updatedAt = "1970-01-01T00:00:00.100Z",
                    ),
                    AniDELETE(
                        episodeId = 2,
                        deletedAt = "1970-01-01T00:00:00.200Z",
                    ),
                ),
                lastSyncAt = "1970-01-01T00:00:00Z",
            ),
        )

        val ops = json.parseToJsonElement(encoded).jsonObject.getValue("ops").jsonArray
        assertEquals("UPSERT", ops[0].jsonObject.getValue("opType").jsonPrimitive.content)
        assertEquals("DELETE", ops[1].jsonObject.getValue("opType").jsonPrimitive.content)
    }
}
