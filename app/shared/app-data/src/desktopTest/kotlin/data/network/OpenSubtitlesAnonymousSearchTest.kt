/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.preference.OpenSubtitlesSettings
import me.him188.ani.utils.ktor.createDefaultHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

class OpenSubtitlesAnonymousSearchTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "ANI_TEST_OPENSUBTITLES_LIVE", matches = "1")
    fun `official anonymous search returns subtitle files without user credentials`() = runBlocking {
        val client = createDefaultHttpClient(bare = true)
        try {
            val page = OpenSubtitlesClient(client, OpenSubtitlesSettings(languages = "en"))
                .search("Naruto", episode = 1)
            assertTrue(page.files.isNotEmpty())
            assertTrue(page.files.all { it.id > 0 && it.name.isNotBlank() })
        } finally {
            client.close()
        }
    }
}
