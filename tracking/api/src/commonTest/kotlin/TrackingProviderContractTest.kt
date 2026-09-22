/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.tracking.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackingProviderContractTest {
    @Test
    fun `provider exposes Mihon-style account capabilities and remote lifecycle`() = runTest {
        val provider = InMemoryTrackingProvider()

        assertEquals("Test Tracker", provider.info.displayName)
        assertFalse(provider.capabilities.supportsPrivateEntries)
        assertEquals(TrackingAccountState.LoggedOut, provider.accountState.value)

        val account = provider.login(TrackingLoginCredentials(secret = "token"))
        assertEquals("haru", account.displayName)
        assertTrue(provider.accountState.value is TrackingAccountState.LoggedIn)

        val media = provider.search("Frieren").single()
        assertEquals("https://tracker.example/anime/1", media.siteUrl)
        assertNull(provider.prepareBinding(media.id)?.listEntry)

        val bound = provider.bind(
            TrackingListEntry(media.id, TrackingStatus.CURRENT, progress = 8, score = TrackingScore(80)),
        )
        assertEquals(bound, provider.refresh(media.id)?.listEntry)

        val updated = provider.update(bound.copy(progress = 9), didWatchEpisode = true)
        assertEquals(9, updated.progress)

        provider.delete(media.id)
        assertNull(provider.refresh(media.id)?.listEntry)

        provider.logout()
        assertEquals(TrackingAccountState.LoggedOut, provider.accountState.value)
    }

    @Test
    fun `normalized values reject invalid state`() {
        assertTrue(runCatching { TrackingProviderId("") }.isFailure)
        assertTrue(runCatching { TrackingMediaId(" ") }.isFailure)
        assertTrue(runCatching { TrackingLoginCredentials(secret = "") }.isFailure)
        assertTrue(
            runCatching {
                TrackingListEntry(TrackingMediaId("1"), TrackingStatus.CURRENT, progress = -1)
            }.isFailure,
        )
        assertTrue(runCatching { TrackingScore(101) }.isFailure)
    }
}

private class InMemoryTrackingProvider : TrackingProvider {
    override val info = TrackingProviderInfo(
        id = TrackingProviderId("test"),
        displayName = "Test Tracker",
        websiteUrl = "https://tracker.example",
    )
    override val capabilities = TrackingCapabilities()
    override val accountState = MutableStateFlow<TrackingAccountState>(TrackingAccountState.LoggedOut)
    override val statusOptions = TrackingStatus.entries.map { TrackingStatusOption(it, it.name) }
    override val scoreOptions = (0..10).map { TrackingScoreOption(TrackingScore(it * 10), it.toString()) }

    private val media = TrackingMedia(
        id = TrackingMediaId("1"),
        title = "Frieren: Beyond Journey's End",
        siteUrl = "https://tracker.example/anime/1",
        coverImageUrl = null,
        totalEpisodes = 28,
    )
    private var entry: TrackingListEntry? = null

    override suspend fun login(credentials: TrackingLoginCredentials): TrackingAccount {
        return TrackingAccount("1", "haru").also { accountState.value = TrackingAccountState.LoggedIn(it) }
    }

    override suspend fun refreshAccount(): TrackingAccount =
        (accountState.value as TrackingAccountState.LoggedIn).account

    override suspend fun logout() {
        accountState.value = TrackingAccountState.LoggedOut
    }

    override suspend fun search(query: String): List<TrackingMedia> =
        if (media.title.contains(query, ignoreCase = true)) listOf(media) else emptyList()

    override suspend fun prepareBinding(mediaId: TrackingMediaId) = refresh(mediaId)

    override suspend fun bind(entry: TrackingListEntry): TrackingListEntry = entry.also { this.entry = it }

    override suspend fun update(entry: TrackingListEntry, didWatchEpisode: Boolean): TrackingListEntry =
        entry.also { this.entry = it }

    override suspend fun refresh(mediaId: TrackingMediaId): TrackingMediaWithEntry? =
        media.takeIf { it.id == mediaId }?.let { TrackingMediaWithEntry(it, entry) }

    override suspend fun delete(mediaId: TrackingMediaId) {
        if (media.id == mediaId) entry = null
    }
}
