package me.him188.ani.app.ui.subject.details.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.tracking.api.DefaultTrackingRegistry
import me.him188.ani.tracking.api.TrackingAccount
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingBindingRecord
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
    fun registryBacksUpBindingsAcrossSources() = runTest {
        val first = FakeSource("first")
        val second = FakeSource("second")
        val registry = DefaultTrackingRegistry(listOf(first, second))
        val records = listOf(
            TrackingBindingRecord("first", "account-a", 42, "100"),
            TrackingBindingRecord("second", "account-b", 42, "200"),
        )

        registry.restoreBindings(records)
        assertEquals(records, registry.exportBindings())
        assertFailsWith<IllegalArgumentException> {
            registry.restoreBindings(listOf(TrackingBindingRecord("unknown", "account", 42, "300")))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.restoreBindings(listOf(
                TrackingBindingRecord("first", "account-a", 43, "101"),
                TrackingBindingRecord("second", "account-b", -1, "201"),
            ))
        }
        assertEquals(records, registry.exportBindings())
    }

    @Test
    fun registryRestoresBindingsWithoutConnectedAccount() = runTest {
        val source = FakeSource("offline", connected = false)
        val registry = DefaultTrackingRegistry(listOf(source))
        val record = TrackingBindingRecord("offline", "account-a", 42, "100")

        registry.restoreBindings(listOf(record))

        assertEquals(listOf(record), registry.exportBindings())
    }

    private class FakeSource(
        id: String,
        override val capabilities: TrackingSourceCapabilities = TrackingSourceCapabilities(),
        connected: Boolean = true,
    ) : TrackingSource {
        override val info = TrackingProviderInfo(TrackingProviderId(id), id, "https://example.org")
        override val connection: Flow<TrackingAccountState> = flowOf(
            if (connected) TrackingAccountState.LoggedIn(TrackingAccount("1", "test")) else TrackingAccountState.LoggedOut,
        )
        override val statusOptions = listOf(TrackingStatusOption(TrackingStatus.CURRENT, "Watching"))
        override val scoreOptions = emptyList<TrackingScoreOption>()
        var lastEdit: TrackingEdit? = null
        private var bindings = emptyList<TrackingBindingRecord>()
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
        override fun exportBindings(): List<TrackingBindingRecord> = bindings
        override suspend fun validateBindings(records: List<TrackingBindingRecord>) {
            require(records.all { it.providerId == info.id.value && it.subjectId > 0 })
        }
        override fun applyValidatedBindings(records: List<TrackingBindingRecord>) { bindings = records }
    }
}
