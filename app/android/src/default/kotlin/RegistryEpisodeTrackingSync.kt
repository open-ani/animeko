package me.him188.ani.android

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.episode.EpisodeTrackingSync
import me.him188.ani.app.domain.tracking.TrackingEpisodeSynchronizer

internal class RegistryEpisodeTrackingSync(
    private val synchronizer: TrackingEpisodeSynchronizer,
    private val scope: CoroutineScope,
) : EpisodeTrackingSync {
    override fun onEpisodeWatched(subjectId: Int, episodeId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                synchronizer.episodeWatched(subjectId, episodeId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("TrackingSync", "Progress sync failed: ${failure.javaClass.simpleName}")
            }
        }
    }
}
