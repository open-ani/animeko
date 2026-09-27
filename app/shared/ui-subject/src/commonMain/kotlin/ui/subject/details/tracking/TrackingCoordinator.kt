package me.him188.ani.app.ui.subject.details.tracking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingEdit
import me.him188.ani.tracking.api.TrackingMedia
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.tracking.api.TrackingRegistry
import me.him188.ani.tracking.api.TrackingSnapshot
import me.him188.ani.tracking.api.TrackingSource
import me.him188.ani.tracking.api.TrackingSourceCapabilities
import me.him188.ani.tracking.api.TrackingStatusOption
import me.him188.ani.tracking.api.TrackingScoreOption

sealed interface TrackingLoad {
    data object Loading : TrackingLoad
    data class Ready(val snapshot: TrackingSnapshot?) : TrackingLoad
    data class RateLimited(val retryAfterSeconds: Int?) : TrackingLoad
    data object Failed : TrackingLoad
}

data class TrackingCardModel(
    val providerId: TrackingProviderId,
    val providerName: String,
    val capabilities: TrackingSourceCapabilities,
    val statusOptions: List<TrackingStatusOption>,
    val scoreOptions: List<TrackingScoreOption>,
    val account: TrackingAccountState,
    val load: TrackingLoad,
) {
    val isTracked: Boolean get() = (load as? TrackingLoad.Ready)?.snapshot?.entry != null
    val isConnected: Boolean get() = account is TrackingAccountState.LoggedIn || account is TrackingAccountState.Refreshing
    val shouldShowInTrackingSheet: Boolean get() = isConnected || load is TrackingLoad.RateLimited
}

internal fun retryAfterSeconds(millis: Long): Int {
    val positiveMillis = millis.coerceAtLeast(0)
    val seconds = positiveMillis / 1_000 + if (positiveMillis % 1_000 == 0L) 0 else 1
    return seconds.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

class TrackingCoordinator(private val registry: TrackingRegistry) {
    private val refreshes = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(subjectId: Int): Flow<List<TrackingCardModel>> {
        val cards = registry.sources.map { source ->
            combine(
                source.connection,
                source.presentation,
                refreshes.flatMapLatest { generation ->
                    val snapshots = source.observe(subjectId)
                        .map<TrackingSnapshot?, TrackingLoad> { TrackingLoad.Ready(it) }
                        .catch { failure ->
                            if (failure is CancellationException) throw failure
                            if (failure is TrackingProviderException.RateLimited) {
                                emit(TrackingLoad.RateLimited(failure.retryAfterMillis?.let(::retryAfterSeconds)))
                            } else {
                                emit(TrackingLoad.Failed)
                            }
                        }
                    if (generation == 0) snapshots.onStart { emit(TrackingLoad.Loading) } else snapshots
                },
            ) { account, presentation, load -> TrackingCardModel(
                source.info.id, presentation.name, source.capabilities,
                source.statusOptions, source.scoreOptions, account, load,
            ) }
        }
        return if (cards.isEmpty()) flowOf(emptyList()) else combine(cards) { it.toList() }
    }

    suspend fun search(providerId: TrackingProviderId, query: String): List<TrackingMedia> =
        source(providerId).search(query)

    suspend fun bind(subjectId: Int, providerId: TrackingProviderId, mediaId: TrackingMediaId) {
        source(providerId).bind(subjectId, mediaId)
        refresh()
    }

    suspend fun edit(subjectId: Int, providerId: TrackingProviderId, edit: TrackingEdit) {
        source(providerId).edit(subjectId, edit)
        refresh()
    }

    suspend fun unlink(subjectId: Int, providerId: TrackingProviderId) {
        source(providerId).unlink(subjectId)
        refresh()
    }

    fun refresh() { refreshes.value += 1 }

    private fun source(id: TrackingProviderId): TrackingSource =
        registry.sources.first { it.info.id == id }
}
