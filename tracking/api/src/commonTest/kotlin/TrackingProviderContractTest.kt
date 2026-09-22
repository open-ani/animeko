/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tracking.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TrackingProviderContractTest {
    @Test
    fun `adapter supports the complete list entry lifecycle through the contract`() = runCommonTest {
        val provider: TrackingProvider = InMemoryTrackingProvider()
        val media = provider.searchAnime("Frieren").single()

        assertEquals("Test Tracker", provider.info.displayName)
        assertEquals("https://tracker.example", provider.info.websiteUrl)
        assertEquals("https://tracker.example/anime/1", media.siteUrl)
        assertNull(provider.getAnime(media.id)?.listEntry)

        val saved = provider.saveListEntry(
            TrackingListEntry(
                mediaId = media.id,
                status = TrackingStatus.CURRENT,
                progress = 8,
                score = TrackingScore(90),
            ),
        )
        assertEquals(saved, provider.getAnime(media.id)?.listEntry)

        provider.deleteListEntry(media.id)
        assertNull(provider.getAnime(media.id)?.listEntry)
    }

    @Test
    fun `contract rejects invalid identity progress and score values`() {
        assertFailsWith<IllegalArgumentException> { TrackingProviderId(" ") }
        assertFailsWith<IllegalArgumentException> { TrackingMediaId("") }
        assertFailsWith<IllegalArgumentException> {
            TrackingListEntry(
                mediaId = TrackingMediaId("1"),
                status = TrackingStatus.CURRENT,
                progress = -1,
            )
        }
        assertFailsWith<IllegalArgumentException> { TrackingScore(101) }
    }
}

private class InMemoryTrackingProvider : TrackingProvider {
    override val info = TrackingProviderInfo(
        id = TrackingProviderId("test"),
        displayName = "Test Tracker",
        websiteUrl = "https://tracker.example",
    )

    private val media = TrackingMedia(
        id = TrackingMediaId("1"),
        title = "Frieren: Beyond Journey's End",
        siteUrl = "https://tracker.example/anime/1",
        coverImageUrl = null,
        totalEpisodes = 28,
    )
    private var entry: TrackingListEntry? = null

    override suspend fun searchAnime(query: String): List<TrackingMedia> = listOf(media)

    override suspend fun getAnime(mediaId: TrackingMediaId): TrackingMediaWithEntry? {
        return if (mediaId == media.id) TrackingMediaWithEntry(media, entry) else null
    }

    override suspend fun saveListEntry(entry: TrackingListEntry): TrackingListEntry {
        this.entry = entry
        return entry
    }

    override suspend fun deleteListEntry(mediaId: TrackingMediaId) {
        if (mediaId == media.id) entry = null
    }
}

private fun runCommonTest(block: suspend () -> Unit) {
    kotlinx.coroutines.test.runTest { block() }
}
