/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.domain.episode.EpisodeTrackingSync
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.tracking.anilist.createAniListHttpClient
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.tracking.api.AndroidTrackingCredentialStore
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.utils.ktor.getPlatformKtorEngine

internal class AniListEpisodeTrackingSync(
    context: Context,
    private val episodes: Lazy<EpisodeCollectionRepository>,
    private val scope: CoroutineScope,
) : EpisodeTrackingSync {
    private val appContext = context.applicationContext
    private val bindings = appContext.getSharedPreferences("anilist-bindings", 0)
    private val credentials = AndroidTrackingCredentialStore(appContext, TrackingProviderId("anilist"))
    private val mutex = Mutex()

    override fun onEpisodeWatched(subjectId: Int, episodeId: Int) {
        if (bindings.all.keys.none { it.endsWith(":$subjectId") }) return
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    sync(subjectId, episodeId)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    Log.w("AniListTracking", "Progress sync failed: ${failure.javaClass.simpleName}")
                }
            }
        }
    }

    private suspend fun sync(subjectId: Int, episodeId: Int) {
        if (credentials.load() == null) return
        val episode = episodes.value.episodeCollectionInfoFlow(subjectId, episodeId).first().episodeInfo
        if (episode.type != null && episode.type != EpisodeType.MainStory) return
        val number = episode.ep?.number ?: return
        val progress = number.toInt()
        if (progress <= 0 || progress.toFloat() != number) return

        val client = createAniListHttpClient(getPlatformKtorEngine())
        try {
            val provider = AniListTrackingProvider(client, credentials)
            val accountId = provider.refreshAccount().remoteId
            val mediaId = bindings.getString("$accountId:$subjectId", null) ?: return
            val current = provider.refresh(TrackingMediaId(mediaId)) ?: return
            val entry = current.listEntry ?: return
            if (progress > entry.progress) {
                provider.update(entry.copy(progress = progress), didWatchEpisode = true)
            }
        } finally {
            client.close()
        }
    }
}
