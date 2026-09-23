package me.him188.ani.tracking.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** One account-scoped tracking implementation. The UI only sees [TrackingSnapshot]. */
interface TrackingSource {
    val info: TrackingProviderInfo
    val connection: Flow<TrackingAccountState>
    val presentation: Flow<TrackingSourcePresentation>
        get() = flowOf(TrackingSourcePresentation(info.displayName, info.id.value))
    val capabilities: TrackingSourceCapabilities
    val statusOptions: List<TrackingStatusOption>
    val scoreOptions: List<TrackingScoreOption>

    /** Emits local changes as well as the initial remote state. Null means no media match. */
    fun observe(subjectId: Int): Flow<TrackingSnapshot?>

    /** Empty for a source whose media ID is the Animeko subject ID. */
    suspend fun search(query: String): List<TrackingMedia> = emptyList()

    /** Keeps an existing remote entry intact when a match is first selected. */
    suspend fun bind(subjectId: Int, mediaId: TrackingMediaId): TrackingSnapshot

    suspend fun edit(subjectId: Int, edit: TrackingEdit): TrackingSnapshot

    /** Removes only the local match; remote deletion is a separate edit. */
    suspend fun unlink(subjectId: Int)

    /** Called after Animeko has recorded a watched episode. Local sources may ignore it. */
    suspend fun episodeWatched(subjectId: Int, episodeId: Int) {}
}

data class TrackingSourcePresentation(val name: String, val iconKey: String)

data class TrackingSourceCapabilities(
    val needsMatchSearch: Boolean = false,
    val canEditStatus: Boolean = true,
    val canEditProgress: Boolean = true,
    val canEditScore: Boolean = true,
    val canEditDates: Boolean = false,
    val canEditPrivacy: Boolean = false,
    val canDeleteRemoteEntry: Boolean = false,
)

data class TrackingEpisode(val id: Int, val number: String, val watched: Boolean)

data class TrackingSnapshot(
    val media: TrackingMedia,
    /** A match may remain after the remote list entry has been deleted. */
    val entry: TrackingListEntry?,
    /** Present when the source has per-episode state; null for cumulative-progress services. */
    val episodes: List<TrackingEpisode>? = null,
)

sealed interface TrackingEdit {
    data class Status(val value: TrackingStatus) : TrackingEdit
    data class Progress(val value: Int) : TrackingEdit
    data class Episode(val episodeId: Int, val watched: Boolean) : TrackingEdit
    data object MarkAllEpisodesWatched : TrackingEdit
    data class Score(val value: TrackingScore) : TrackingEdit
    data class Date(val field: TrackingDateField, val value: TrackingDate?) : TrackingEdit
    data class Privacy(val isPrivate: Boolean) : TrackingEdit
    data object DeleteRemoteEntry : TrackingEdit
}

interface TrackingRegistry {
    val sources: List<TrackingSource>
}

class DefaultTrackingRegistry(override val sources: List<TrackingSource>) : TrackingRegistry {
    init {
        require(sources.map { it.info.id }.distinct().size == sources.size) { "Duplicate tracking source ID" }
    }
}

/** Non-secret title matches that may be transferred to another device. */
data class TrackingBindingRecord(
    val providerId: String,
    val accountId: String,
    val subjectId: Int,
    val mediaId: String,
)

interface TrackingBindingBackup {
    fun exportBindings(): List<TrackingBindingRecord>
    fun restoreBindings(records: List<TrackingBindingRecord>)
}
