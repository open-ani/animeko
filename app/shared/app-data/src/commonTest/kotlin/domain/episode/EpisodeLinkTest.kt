/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import me.him188.ani.datasources.api.EpisodeSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeLinkTest {
    @Test
    fun `parse supported links`() {
        assertEquals(EpisodeLink.Subject(12), EpisodeLink.parse("ani://subjects/12"))
        assertEquals(EpisodeLink.Subject(12, EpisodeSort(1)), EpisodeLink.parse("ani://subjects/12?ep=1"))
        assertEquals(EpisodeLink.Subject(12, EpisodeSort(1)), EpisodeLink.parse("ani://subjects/12/?ep=01.0"))
        assertEquals(EpisodeLink.Subject(12, EpisodeSort("1.5")), EpisodeLink.parse("ani://subjects/12?ep=1.5"))
        assertEquals(EpisodeLink.Episode(34), EpisodeLink.parse("ani://episodes/34"))
        assertEquals(EpisodeLink.Episode(34), EpisodeLink.parse("ani://episodes/34?ep=2"))
    }

    @Test
    fun `invalid ep falls back to subject`() {
        for (ep in listOf("", "0", "-1", "NaN", "Infinity", "1e2", "SP1", "abc", "1.2", "1e100")) {
            assertEquals(EpisodeLink.Subject(12), EpisodeLink.parse("ani://subjects/12?ep=$ep"), ep)
        }
    }

    @Test
    fun `ignore invalid ids and unsupported routes`() {
        for (url in listOf(
            "https://subjects/12", "ani://qr-login?requestId=x", "ani://subjects", "ani://episodes/",
            "ani://subjects/0", "ani://episodes/-1", "ani://subjects/nope", "ani://episodes/2147483648",
            "ani://subjects/12/34", "ani://episodes/1.5", "not a url",
        )) {
            assertNull(EpisodeLink.parse(url), url)
        }
    }
}
