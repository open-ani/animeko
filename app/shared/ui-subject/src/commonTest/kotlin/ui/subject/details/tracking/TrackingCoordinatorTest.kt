package me.him188.ani.app.ui.subject.details.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.tracking.api.DefaultTrackingRegistry
import me.him188.ani.tracking.api.TrackingAccount
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingEdit
import me.him188.ani.tracking.api.TrackingListEntry
import me.him188.ani.tracking.api.TrackingMedia
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.tracking.api.TrackingProviderInfo
import me.him188.ani.tracking.api.TrackingScoreOption
import me.him188.ani.tracking.api.TrackingSnapshot
import me.him188.ani.tracking.api.TrackingSource
import me.him188.ani.tracking.api.TrackingSourceCapabilities
import me.him188.ani.tracking.api.TrackingStatus
import me.him188.ani.tracking.api.TrackingStatusOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class TrackingCoordinatorTest {
    @Test
    fun registeredThirdSourceUsesTheSameCardAndEditPath() = runTest {
        val sources = listOf(FakeSource("bangumi"), FakeSource("anilist"),
            FakeSource("third", TrackingSourceCapabilities(canEditScore = false)))
        val coordinator = TrackingCoordinator(DefaultTrackingRegistry(sources))

        val cards = coordinator.observe(42).first { cards -> cards.size == 3 && cards.all { it.load is TrackingLoad.Ready } }
        assertEquals(listOf("bangumi", "anilist", "third"), cards.map { it.providerId.value })
        assertFalse(cards.last().capabilities.canEditScore)

        coordinator.edit(42, TrackingProviderId("third"), TrackingEdit.Status(TrackingStatus.COMPLETED))
        assertEquals(TrackingEdit.Status(TrackingStatus.COMPLETED), sources.last().lastEdit)
    }

    @Test
    fun watchedEventContinuesAfterOneSourceFails() = runTest {
        val first = FakeSource("first").apply { failWatched = true }
        val second = FakeSource("second")
        val coordinator = TrackingCoordinator(DefaultTrackingRegistry(listOf(first, second)))

        assertFailsWith<IllegalStateException> { coordinator.episodeWatched(42, 7) }
        assertEquals(1, second.watchedCalls)
    }

    private class FakeSource(id: String, override val capabilities: TrackingSourceCapabilities = TrackingSourceCapabilities()) : TrackingSource {
        override val info = TrackingProviderInfo(TrackingProviderId(id), id, "https://example.org")
        override val connection: Flow<TrackingAccountState> = flowOf(TrackingAccountState.LoggedIn(TrackingAccount("1", "test")))
        override val statusOptions = listOf(TrackingStatusOption(TrackingStatus.CURRENT, "Watching"))
        override val scoreOptions = emptyList<TrackingScoreOption>()
        var lastEdit: TrackingEdit? = null
        var failWatched = false
        var watchedCalls = 0
        private val snapshot = TrackingSnapshot(
            TrackingMedia(TrackingMediaId("42"), "Test", "https://example.org/42", null, 12),
            TrackingListEntry(TrackingMediaId("42"), TrackingStatus.CURRENT, 1),
        )

        override fun observe(subjectId: Int): Flow<TrackingSnapshot?> = flowOf(snapshot)
        override suspend fun bind(subjectId: Int, mediaId: TrackingMediaId): TrackingSnapshot = snapshot
        override suspend fun edit(subjectId: Int, edit: TrackingEdit): TrackingSnapshot {
            lastEdit = edit
            return snapshot
        }
        override suspend fun unlink(subjectId: Int) {}
        override suspend fun episodeWatched(subjectId: Int, episodeId: Int) {
            watchedCalls++
            if (failWatched) error("source unavailable")
        }
    }
}
