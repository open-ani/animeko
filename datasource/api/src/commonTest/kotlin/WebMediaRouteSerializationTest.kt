/*
 * Copyright (C) 2026 OpenAni and contributors.
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
import kotlin.test.assertNull

class WebMediaRouteSerializationTest {
    @Test
    fun `legacy properties decode without route metadata and new metadata round trips`() {
        val properties = Json.decodeFromString(
            MediaProperties.serializer(),
            """{"subtitleLanguageIds":["CHS"],"resolution":"1080P","alliance":"line-1"}""",
        )
        assertNull(properties.webRoute)
        val withRoute = properties.copy(webRoute = WebMediaRoute("https://site/subject/1", "line-1", "revision"))
        assertEquals(
            withRoute,
            Json.decodeFromString(MediaProperties.serializer(), Json.encodeToString(MediaProperties.serializer(), withRoute)),
        )
    }
}
