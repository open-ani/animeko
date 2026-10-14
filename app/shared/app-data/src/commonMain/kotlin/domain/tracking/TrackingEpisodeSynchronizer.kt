package me.him188.ani.app.domain.tracking

import kotlinx.coroutines.CancellationException
import me.him188.ani.tracking.api.TrackingRegistry

/** Dispatches watched episodes to every registered tracking source. */
class TrackingEpisodeSynchronizer(private val registry: TrackingRegistry) {
    suspend fun episodeWatched(subjectId: Int, episodeId: Int) {
        var failure: Exception? = null
        registry.sources.forEach { source ->
            try {
                source.episodeWatched(subjectId, episodeId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}
