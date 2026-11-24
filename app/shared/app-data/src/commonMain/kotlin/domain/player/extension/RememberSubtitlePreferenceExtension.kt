/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.domain.episode.EpisodeSession
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.metadata.TrackGroup
import org.openani.mediamp.source.MediaData

/**
 * 记住并恢复当前番剧条目的字幕选择偏好 (无历史记录时优先简体中文).
 */
class RememberSubtitlePreferenceExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
    private val subtitleTracksProvider: (MediampPlayer) -> TrackGroup<SubtitleTrack>? = { it.subtitleTracks },
) : PlayerExtension("RememberSubtitlePreference") {
    private val episodePreferencesRepository: EpisodePreferencesRepository by koin.inject()

    private class RestoreSessionState(val data: MediaData) {
        var exactRestored: Boolean = false
        var fallbackApplied: Boolean = false
        var userOverrode: Boolean = false
        var sawInitialTrackSelection: Boolean = false
        var checkedInitialCandidates: Boolean = false
    }

    private sealed interface PendingSelection {
        data object Disable : PendingSelection
        data class Track(val id: String) : PendingSelection
    }

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope,
    ) {
        val subtitleTracks = subtitleTracksProvider(context.player) ?: return
        val stateMutex = Mutex()
        var session: RestoreSessionState? = null
        var pendingSelection: PendingSelection? = null

        fun ensureSessionLocked(data: MediaData): RestoreSessionState {
            val current = session
            if (current != null && current.data === data) {
                return current
            }
            pendingSelection = null
            return RestoreSessionState(data).also { session = it }
        }

        backgroundTaskScope.launch("RememberSubtitlePreference-Restore") {
            combine(
                context.player.mediaData,
                context.player.state.map { it.mediaStatus }.distinctUntilChanged(),
                subtitleTracks.candidates,
            ) { data, status, candidates ->
                Triple(data, status, candidates)
            }.collect { (data, status, candidates) ->
                if (data == null) return@collect

                val shouldSkip = stateMutex.withLock {
                    val current = ensureSessionLocked(data)
                    if (status == MediaStatus.Ready && candidates.isNotEmpty()) {
                        current.checkedInitialCandidates = true
                    }
                    status != MediaStatus.Ready || candidates.isEmpty() || current.userOverrode
                }
                if (shouldSkip) return@collect

                val pref = episodePreferencesRepository.mediaPreferenceFlow(context.subjectId).first()

                val action: PendingSelection? = stateMutex.withLock {
                    val current = session
                    if (current == null || current.data !== data || current.userOverrode) {
                        return@withLock null
                    }
                    when {
                        pref.subtitleDisabled -> {
                            current.exactRestored = true
                            if (subtitleTracks.selected.value != null) {
                                pendingSelection = PendingSelection.Disable
                                PendingSelection.Disable
                            } else {
                                null
                            }
                        }

                        hasSavedSubtitle(pref) -> {
                            if (current.exactRestored) {
                                null
                            } else {
                                val target = findSavedTrack(candidates, pref)
                                if (target != null) {
                                    current.exactRestored = true
                                    if (subtitleTracks.selected.value?.id != target.id) {
                                        PendingSelection.Track(target.id).also { pendingSelection = it }
                                    } else {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }
                        }

                        else -> {
                            if (current.fallbackApplied) {
                                null
                            } else {
                                val target = candidates.firstOrNull { it.isSimplifiedChinese() }
                                if (target != null) {
                                    current.fallbackApplied = true
                                    if (subtitleTracks.selected.value?.id != target.id) {
                                        PendingSelection.Track(target.id).also { pendingSelection = it }
                                    } else {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }
                        }
                    }
                }

                when (action) {
                    PendingSelection.Disable -> {
                        withContext(context.player.mainDispatcher) {
                            subtitleTracks.select(null)
                        }
                    }

                    is PendingSelection.Track -> {
                        val target = candidates.firstOrNull { it.id == action.id } ?: return@collect
                        withContext(context.player.mainDispatcher) {
                            subtitleTracks.select(target)
                        }
                    }

                    null -> Unit
                }
            }
        }

        backgroundTaskScope.launch("RememberSubtitlePreference-Save") {
            subtitleTracks.selected
                .drop(1)
                .distinctUntilChanged()
                .collect { selected ->
                    if (context.player.state.value.mediaStatus != MediaStatus.Ready) {
                        return@collect
                    }

                    val currentPref = episodePreferencesRepository.mediaPreferenceFlow(context.subjectId).first()
                    val shouldIgnore = stateMutex.withLock {
                        val pending = pendingSelection
                        val matchedPending = when (pending) {
                            PendingSelection.Disable -> selected == null
                            is PendingSelection.Track -> selected?.id == pending.id
                            null -> false
                        }
                        if (matchedPending) {
                            pendingSelection = null
                            return@withLock true
                        }

                        val current = context.player.mediaData.value?.let { ensureSessionLocked(it) } ?: session
                        if (current != null && !current.checkedInitialCandidates && selected == null) {
                            return@withLock true
                        }
                        // 忽略新媒体首次就绪时播放器自动选中的默认首条字幕，避免覆盖已有偏好
                        if (current != null && !current.userOverrode && !current.sawInitialTrackSelection && selected != null) {
                            current.sawInitialTrackSelection = true
                            val waitingSavedRestore = currentPref.subtitleDisabled ||
                                (hasSavedSubtitle(currentPref) && !current.exactRestored && !matchesSavedTrack(selected, currentPref))
                            val isImplicitDefault = !hasSavedSubtitle(currentPref) && !currentPref.subtitleDisabled &&
                                !current.fallbackApplied
                            if (waitingSavedRestore || isImplicitDefault) {
                                return@withLock true
                            }
                        }

                        current?.userOverrode = true
                        false
                    }
                    if (shouldIgnore) return@collect

                    val updated = if (selected == null) {
                        currentPref.copy(subtitleDisabled = true)
                    } else {
                        currentPref.copy(
                            preferredSubtitleLabel = selected.labels.firstOrNull()?.value,
                            preferredSubtitleLanguage = selected.language,
                            subtitleDisabled = false,
                        )
                    }
                    if (updated != currentPref) {
                        episodePreferencesRepository.setMediaPreference(context.subjectId, updated)
                    }
                }
        }
    }

    private fun hasSavedSubtitle(pref: MediaPreference): Boolean {
        return !pref.preferredSubtitleLabel.isNullOrBlank() || !pref.preferredSubtitleLanguage.isNullOrBlank()
    }

    private fun matchesSavedTrack(track: SubtitleTrack, pref: MediaPreference): Boolean {
        val savedLabel = pref.preferredSubtitleLabel?.takeIf { it.isNotBlank() }
        if (savedLabel != null && track.labels.any { it.value.equals(savedLabel, ignoreCase = true) }) {
            return true
        }
        val savedLanguage = pref.preferredSubtitleLanguage?.takeIf { it.isNotBlank() }
        return savedLanguage != null && track.language.equals(savedLanguage, ignoreCase = true)
    }

    private fun findSavedTrack(candidates: List<SubtitleTrack>, pref: MediaPreference): SubtitleTrack? {
        val savedLabel = pref.preferredSubtitleLabel?.takeIf { it.isNotBlank() }
        if (savedLabel != null) {
            candidates.firstOrNull { track ->
                track.labels.any { it.value.equals(savedLabel, ignoreCase = true) }
            }?.let { return it }
        }
        val savedLanguage = pref.preferredSubtitleLanguage?.takeIf { it.isNotBlank() }
        if (savedLanguage != null) {
            candidates.firstOrNull { track ->
                track.language.equals(savedLanguage, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    private fun SubtitleTrack.isSimplifiedChinese(): Boolean {
        val labelMatch = labels.any { label ->
            val v = label.value.lowercase()
            "简" in v || "chs" in v || "simplified" in v || "zh-cn" in v || "zh-hans" in v ||
                v == "sc" || v.startsWith("sc ") || v.endsWith(" sc")
        }
        if (labelMatch) return true
        val lang = language?.lowercase() ?: return false
        return lang in setOf("chs", "sc", "zh-cn", "zh-hans", "chi", "zho", "zh")
    }

    companion object : EpisodePlayerExtensionFactory<RememberSubtitlePreferenceExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): RememberSubtitlePreferenceExtension {
            return RememberSubtitlePreferenceExtension(context, koin)
        }
    }
}
