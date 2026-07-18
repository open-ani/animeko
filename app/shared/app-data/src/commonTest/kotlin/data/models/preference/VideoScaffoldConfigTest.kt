/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import me.him188.ani.app.data.persistent.DataStoreJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class VideoScaffoldConfigTest {
    @Test
    fun `missing OP ED skip duration uses 85 seconds`() {
        val config = DataStoreJson.decodeFromString(VideoScaffoldConfig.serializer(), "{}")

        assertEquals(85.seconds, config.opEdSkipDuration)
    }

    @Test
    fun `OP ED skip duration survives serialization`() {
        for (duration in listOf(80.seconds, 85.seconds, 90.seconds)) {
            val encoded = DataStoreJson.encodeToString(
                VideoScaffoldConfig.serializer(),
                VideoScaffoldConfig.Default.copy(opEdSkipDuration = duration),
            )

            val decoded = DataStoreJson.decodeFromString(VideoScaffoldConfig.serializer(), encoded)

            assertEquals(duration, decoded.opEdSkipDuration)
        }
    }
}
