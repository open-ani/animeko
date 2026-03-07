/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoResolverSettingsTest {
    @Test
    fun `default timeout is 8 seconds`() {
        assertEquals(8, VideoResolverSettings.Default.effectiveResourceExtractionTimeoutSeconds)
        assertEquals(8_000L, VideoResolverSettings.Default.effectiveResourceExtractionTimeoutMillis)
    }

    @Test
    fun `invalid timeout falls back to default`() {
        val settings = VideoResolverSettings(resourceExtractionTimeoutSeconds = 9)

        assertEquals(8, settings.effectiveResourceExtractionTimeoutSeconds)
        assertEquals(8_000L, settings.effectiveResourceExtractionTimeoutMillis)
    }
}
