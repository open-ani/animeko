/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.data.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCase
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.tracking.api.TrackingAccount
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingEdit
import me.him188.ani.tracking.api.TrackingEpisode
import me.him188.ani.tracking.api.TrackingListEntry
import me.him188.ani.tracking.api.TrackingMedia
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.tracking.api.TrackingProviderInfo
import me.him188.ani.tracking.api.TrackingScore
import me.him188.ani.tracking.api.TrackingScoreOption
import me.him188.ani.tracking.api.TrackingSnapshot
import me.him188.ani.tracking.api.TrackingSource
import me.him188.ani.tracking.api.TrackingSourceCapabilities
import me.him188.ani.tracking.api.TrackingSourcePresentation
import me.him188.ani.tracking.api.TrackingStatus
import me.him188.ani.tracking.api.TrackingStatusOption

class BangumiTrackingSource(
    private val sessionStateProvider: SessionStateProvider,
    private val userRepository: UserRepository,
    private val subjectCollectionRepository: Lazy<SubjectCollectionRepository>,
    private val setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase,
    private val episodeCollectionRepository: Lazy<EpisodeCollectionRepository>,
) : TrackingSource {
    override val info = INFO

    override val connection: Flow<TrackingAccountState> = combine(
        sessionStateProvider.stateFlow,
        userRepository.selfInfoFlow,
    ) { session, selfInfo ->
        if (session is SessionState.Valid) {
            TrackingAccountState.LoggedIn(selfInfo.toTrackingAccount())
        } else {
            TrackingAccountState.LoggedOut
        }
    }.distinctUntilChanged()

    override val presentation: Flow<TrackingSourcePresentation> = sessionStateProvider.stateFlow.map { state ->
        if (state is SessionState.Valid && !state.bangumiConnected) {
            TrackingSourcePresentation("Animeko", "animeko")
        } else TrackingSourcePresentation("Bangumi", "bangumi")
    }.distinctUntilChanged()

    override val capabilities = TrackingSourceCapabilities(
        needsMatchSearch = false,
        canEditStatus = true,
        canEditProgress = true,
        canEditScore = true,
        canDeleteRemoteEntry = true,
    )

    override val statusOptions = listOf(
        TrackingStatusOption(TrackingStatus.PLANNING, "Plan to watch"),
        TrackingStatusOption(TrackingStatus.CURRENT, "Watching"),
        TrackingStatusOption(TrackingStatus.COMPLETED, "Completed"),
        TrackingStatusOption(TrackingStatus.PAUSED, "On hold"),
        TrackingStatusOption(TrackingStatus.DROPPED, "Dropped"),
    )

    override val scoreOptions = (0..10).map { score ->
        TrackingScoreOption(TrackingScore(score * 10), if (score == 0) "Unrated" else score.toString())
    }

    override fun observe(subjectId: Int): Flow<TrackingSnapshot?> = sessionStateProvider.stateFlow.flatMapLatest { state ->
        if (state !is SessionState.Valid) {
            flowOf(null)
        } else {
            subjectCollectionRepository.value.subjectCollectionFlow(subjectId)
                .map { it.toTrackingSnapshot(state.bangumiConnected) }
                .distinctUntilChanged()
        }
    }

    override suspend fun bind(subjectId: Int, mediaId: TrackingMediaId): TrackingSnapshot {
        requireDirectId(subjectId, mediaId)
        requireConnected()
        val current = readSnapshot(subjectId)
        if (current.entry == null) {
            subjectCollectionRepository.value.setSubjectCollectionTypeOrDelete(subjectId, UnifiedCollectionType.WISH)
        }
        return readSnapshot(subjectId)
    }

    override suspend fun edit(subjectId: Int, edit: TrackingEdit): TrackingSnapshot {
        requireConnected()
        val currentInfo = readCollectionInfo(subjectId)
        val current = currentInfo.toTrackingSnapshot(isBangumiConnected())
        when (edit) {
            is TrackingEdit.Status -> {
                subjectCollectionRepository.value.setSubjectCollectionTypeOrDelete(
                    subjectId,
                    edit.value.toBangumiCollectionType(),
                )
            }

            is TrackingEdit.Episode -> {
                check(current.entry != null) { "Add this title to Bangumi before editing episode progress" }
                require(current.episodes.orEmpty().any { it.id == edit.episodeId }) {
                    "Episode ${edit.episodeId} is not a main-story episode in this subject"
                }
                setEpisodeCollectionType(
                    subjectId = subjectId,
                    episodeId = edit.episodeId,
                    collectionType = if (edit.watched) UnifiedCollectionType.DONE else UnifiedCollectionType.NOT_COLLECTED,
                )
            }

            TrackingEdit.MarkAllEpisodesWatched -> {
                check(current.entry != null) { "Add this title to Bangumi before editing episode progress" }
                episodeCollectionRepository.value.setAllEpisodesWatched(subjectId)
            }

            is TrackingEdit.Score -> {
                check(current.entry != null) { "Add this title to Bangumi before editing its score" }
                require(edit.value.value % 10 == 0) { "Bangumi scores must be whole numbers from 0 to 10" }
                subjectCollectionRepository.value.updateRating(
                    subjectId = subjectId,
                    score = edit.value.value / 10,
                    comment = currentInfo.selfRatingInfo.comment,
                    tags = currentInfo.selfRatingInfo.tags,
                    isPrivate = currentInfo.selfRatingInfo.isPrivate,
                )
            }

            TrackingEdit.DeleteRemoteEntry -> {
                subjectCollectionRepository.value.setSubjectCollectionTypeOrDelete(subjectId, UnifiedCollectionType.NOT_COLLECTED)
            }

            is TrackingEdit.Progress -> throw UnsupportedOperationException(
                "Bangumi progress is edited one episode at a time",
            )

            is TrackingEdit.Date, is TrackingEdit.Privacy -> throw UnsupportedOperationException(
                "Bangumi does not support tracking dates or private entries",
            )
        }
        return readSnapshot(subjectId)
    }

    override suspend fun unlink(subjectId: Int) {
        // Bangumi media IDs are the subject IDs, so there is no local match to remove.
    }

    private suspend fun requireConnected() {
        if (sessionStateProvider.stateFlow.first() !is SessionState.Valid) {
            throw TrackingProviderException.Unauthorized()
        }
    }

    private suspend fun readSnapshot(subjectId: Int): TrackingSnapshot {
        return readCollectionInfo(subjectId).toTrackingSnapshot(isBangumiConnected())
    }

    private suspend fun isBangumiConnected(): Boolean =
        (sessionStateProvider.stateFlow.first() as? SessionState.Valid)?.bangumiConnected == true

    private suspend fun readCollectionInfo(subjectId: Int): SubjectCollectionInfo {
        requireConnected()
        return subjectCollectionRepository.value.subjectCollectionFlow(subjectId).first()
    }

    private fun requireDirectId(subjectId: Int, mediaId: TrackingMediaId) {
        require(mediaId.value == subjectId.toString()) {
            "Bangumi media ID ${mediaId.value} does not match Animeko subject ID $subjectId"
        }
    }

    private fun SubjectCollectionInfo.toTrackingSnapshot(bangumiConnected: Boolean): TrackingSnapshot {
        // This repository flow honors the user's episode-type filter; progress therefore reflects
        // the main-story episodes exposed by the current collection flow.
        val mainEpisodes = episodes.filter { it.episodeInfo.type == null || it.episodeInfo.type == EpisodeType.MainStory }
        val media = TrackingMedia(
            id = TrackingMediaId(subjectId.toString()),
            title = subjectInfo.displayName,
            siteUrl = if (bangumiConnected) "https://bgm.tv/subject/$subjectId" else "",
            coverImageUrl = subjectInfo.imageLarge.takeIf { it.isNotBlank() },
            totalEpisodes = mainEpisodes.size,
        )
        val entry = collectionType.toTrackingStatus()?.let { status ->
            TrackingListEntry(
                mediaId = media.id,
                status = status,
                progress = mainEpisodes.count { it.collectionType == UnifiedCollectionType.DONE },
                score = TrackingScore(selfRatingInfo.score.toTrackingScore()),
            )
        }
        return TrackingSnapshot(
            media = media,
            entry = entry,
            episodes = mainEpisodes.map { it.toTrackingEpisode() },
        )
    }

    private fun EpisodeCollectionInfo.toTrackingEpisode() = TrackingEpisode(
        id = episodeId,
        number = episodeInfo.ep?.toString() ?: episodeInfo.sort.toString(),
        watched = collectionType == UnifiedCollectionType.DONE,
    )

    private fun UnifiedCollectionType.toTrackingStatus(): TrackingStatus? = when (this) {
        UnifiedCollectionType.WISH -> TrackingStatus.PLANNING
        UnifiedCollectionType.DOING -> TrackingStatus.CURRENT
        UnifiedCollectionType.DONE -> TrackingStatus.COMPLETED
        UnifiedCollectionType.ON_HOLD -> TrackingStatus.PAUSED
        UnifiedCollectionType.DROPPED -> TrackingStatus.DROPPED
        UnifiedCollectionType.NOT_COLLECTED -> null
    }

    private fun TrackingStatus.toBangumiCollectionType(): UnifiedCollectionType = when (this) {
        TrackingStatus.PLANNING -> UnifiedCollectionType.WISH
        TrackingStatus.CURRENT -> UnifiedCollectionType.DOING
        TrackingStatus.COMPLETED -> UnifiedCollectionType.DONE
        TrackingStatus.PAUSED -> UnifiedCollectionType.ON_HOLD
        TrackingStatus.DROPPED -> UnifiedCollectionType.DROPPED
        TrackingStatus.REPEATING -> throw UnsupportedOperationException("Bangumi has no repeating status")
    }

    private fun Int.toTrackingScore(): Int {
        require(this in 0..10) { "Bangumi score must be between 0 and 10" }
        return this * 10
    }

    private fun SelfInfo?.toTrackingAccount(): TrackingAccount {
        val username = this?.bangumiUsername?.takeIf { it.isNotBlank() }
        return TrackingAccount(
            remoteId = username ?: this?.id?.toString() ?: "current",
            displayName = username ?: "Bangumi",
        )
    }

    companion object {
        val INFO = TrackingProviderInfo(
            id = TrackingProviderId("bangumi"),
            displayName = "Bangumi",
            websiteUrl = "https://bgm.tv",
        )
    }
}
