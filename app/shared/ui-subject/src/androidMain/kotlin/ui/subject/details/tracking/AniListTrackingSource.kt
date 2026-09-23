/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ui.subject.details.tracking

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.tracking.api.TrackingAccount
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingDateField
import me.him188.ani.tracking.api.TrackingEdit
import me.him188.ani.tracking.api.TrackingListEntry
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingProvider
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.tracking.api.TrackingSnapshot
import me.him188.ani.tracking.api.TrackingSource
import me.him188.ani.tracking.api.TrackingSourceCapabilities
import me.him188.ani.tracking.api.TrackingStatus

/** AniList's account-scoped remote list, with local title matches kept in the existing preferences. */
class AniListTrackingSource(
    context: Context,
    private val provider: TrackingProvider,
    private val episodes: Lazy<EpisodeCollectionRepository>,
) : TrackingSource {
    private val appContext = context.applicationContext
    private val bindings = appContext.getSharedPreferences(BINDINGS_PREFERENCES, Context.MODE_PRIVATE)
    private val snapshots = ConcurrentHashMap<Int, MutableStateFlow<TrackingSnapshot?>>()
    private val episodeSyncMutex = Mutex()

    override val info = provider.info
    override val connection: Flow<TrackingAccountState> = provider.accountState
    override val capabilities = TrackingSourceCapabilities(
        needsMatchSearch = true,
        canEditDates = provider.capabilities.supportsStartAndCompletionDates,
        canEditPrivacy = provider.capabilities.supportsPrivateEntries,
        canDeleteRemoteEntry = true,
    )
    override val statusOptions get() = provider.statusOptions
    override val scoreOptions get() = provider.scoreOptions

    override fun observe(subjectId: Int): Flow<TrackingSnapshot?> = flow {
        val current = snapshotState(subjectId)
        coroutineScope {
            launch {
                provider.accountState.collectLatest { accountState ->
                    when (accountState) {
                        is TrackingAccountState.LoggedIn -> {
                            current.value = loadSnapshot(subjectId, accountState.account)
                        }
                        is TrackingAccountState.Refreshing -> Unit
                        TrackingAccountState.LoggedOut -> current.value = null
                    }
                }
            }
            launch {
                try {
                    provider.refreshAccount()
                } catch (_: TrackingProviderException.Unauthorized) {
                    current.value = null
                }
            }
            current.collect { emit(it) }
        }
    }

    override suspend fun search(query: String) = provider.search(query)

    override suspend fun bind(subjectId: Int, mediaId: TrackingMediaId): TrackingSnapshot {
        val account = currentAccount()
        val candidate = provider.prepareBinding(mediaId)
            ?: throw TrackingProviderException.Remote("AniList title is unavailable")
        val entry = candidate.listEntry ?: provider.bind(
            TrackingListEntry(candidate.media.id, TrackingStatus.PLANNING, progress = 0),
        )
        bindings.edit().putString(bindingKey(account.remoteId, subjectId), candidate.media.id.value).apply()
        return TrackingSnapshot(candidate.media, entry).also { snapshotState(subjectId).value = it }
    }

    override suspend fun edit(subjectId: Int, edit: TrackingEdit): TrackingSnapshot {
        val account = currentAccount()
        val current = loadSnapshot(subjectId, account)
            ?: throw TrackingProviderException.Remote("AniList title is not linked")
        val mediaId = current.media.id
        val existing = current.entry

        if (edit == TrackingEdit.DeleteRemoteEntry) {
            provider.delete(mediaId)
            val refreshed = provider.refresh(mediaId)
                ?: throw TrackingProviderException.Remote("AniList title is unavailable")
            return TrackingSnapshot(refreshed.media, refreshed.listEntry)
                .also { snapshotState(subjectId).value = it }
        }

        if (edit is TrackingEdit.Episode || edit == TrackingEdit.MarkAllEpisodesWatched) {
            throw UnsupportedOperationException("AniList stores cumulative progress; use a progress edit")
        }

        val base = existing ?: TrackingListEntry(mediaId, TrackingStatus.PLANNING, progress = 0)
        val updated = when (edit) {
            is TrackingEdit.Status -> base.copy(status = edit.value)
            is TrackingEdit.Progress -> base.copy(progress = edit.value)
            is TrackingEdit.Score -> base.copy(score = edit.value)
            is TrackingEdit.Date -> base.copy(
                startedAt = if (edit.field == TrackingDateField.STARTED) edit.value else base.startedAt,
                completedAt = if (edit.field == TrackingDateField.COMPLETED) edit.value else base.completedAt,
            )
            is TrackingEdit.Privacy -> base.copy(isPrivate = edit.isPrivate)
            is TrackingEdit.Episode, TrackingEdit.MarkAllEpisodesWatched, TrackingEdit.DeleteRemoteEntry -> error("Handled above")
        }

        val saved = if (existing == null) provider.bind(updated) else when (edit) {
            is TrackingEdit.Date -> provider.updateDate(existing, edit.field, edit.value)
            is TrackingEdit.Privacy -> provider.updateVisibility(existing, edit.isPrivate)
            else -> provider.update(updated)
        }
        return TrackingSnapshot(current.media, saved).also { snapshotState(subjectId).value = it }
    }

    override suspend fun unlink(subjectId: Int) {
        val account = (provider.accountState.value as? TrackingAccountState.LoggedIn)?.account
            ?: throw TrackingProviderException.Unauthorized()
        bindings.edit().remove(bindingKey(account.remoteId, subjectId)).apply()
        snapshotState(subjectId).value = null
    }

    override suspend fun episodeWatched(subjectId: Int, episodeId: Int) {
        if (bindings.all.keys.none { it.endsWith(":$subjectId") }) return
        episodeSyncMutex.withLock {
            val account = try {
                provider.refreshAccount()
            } catch (_: TrackingProviderException.Unauthorized) {
                return
            }
            val mediaId = bindings.getString(bindingKey(account.remoteId, subjectId), null)
                ?.let(::TrackingMediaId) ?: return
            val episode = episodes.value.episodeCollectionInfoFlow(subjectId, episodeId).first().episodeInfo
            if (episode.type != null && episode.type != EpisodeType.MainStory) return
            val number = episode.ep?.number ?: return
            val progress = number.toInt()
            if (progress <= 0 || progress.toFloat() != number) return

            val current = provider.refresh(mediaId) ?: return
            val entry = current.listEntry ?: return
            if (progress > entry.progress) {
                val saved = provider.update(entry.copy(progress = progress), didWatchEpisode = true)
                snapshotState(subjectId).value = TrackingSnapshot(current.media, saved)
            }
        }
    }

    private suspend fun currentAccount(): TrackingAccount =
        (provider.accountState.value as? TrackingAccountState.LoggedIn)?.account
            ?: provider.refreshAccount()

    private suspend fun loadSnapshot(subjectId: Int, account: TrackingAccount): TrackingSnapshot? {
        val mediaId = bindings.getString(bindingKey(account.remoteId, subjectId), null)
            ?.let(::TrackingMediaId) ?: return null
        val current = provider.refresh(mediaId)
            ?: throw TrackingProviderException.Remote("AniList title is unavailable")
        return TrackingSnapshot(current.media, current.listEntry)
    }

    private fun snapshotState(subjectId: Int) = snapshots.computeIfAbsent(subjectId) {
        MutableStateFlow(null)
    }

    private fun bindingKey(accountId: String, subjectId: Int) = "$accountId:$subjectId"

    private companion object {
        const val BINDINGS_PREFERENCES = "anilist-bindings"
    }
}
