/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.domain.episode.EpisodeSession
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.metadata.SubtitleTrack

/**
 * 记住并恢复当前番剧条目的字幕选择偏好 (无历史记录时优先简体中文).
 */
class RememberSubtitlePreferenceExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
) : PlayerExtension("RememberSubtitlePreference") {
    private val episodePreferencesRepository: EpisodePreferencesRepository by koin.inject()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope,
    ) {
        val subtitleTracks = context.player.subtitleTracks ?: return
        backgroundTaskScope.launch("RememberSubtitlePreference") {
            var applied = false
            var lastRestoredTrackId: String? = null
            var restoredDisabled = false

            combine(
                context.player.state.map { it.mediaStatus }.distinctUntilChanged(),
                subtitleTracks.candidates,
            ) { status, candidates ->
                status to candidates
            }.collectLatest { (status, candidates) ->
                if (status != MediaStatus.Ready || candidates.isEmpty()) return@collectLatest
                if (!applied) {
                    val pref = episodePreferencesRepository.mediaPreferenceFlow(context.subjectId).first()
                    val target = selectPreferredTrack(candidates, pref)
                    applied = true
                    if (pref.subtitleDisabled) {
                        restoredDisabled = true
                        lastRestoredTrackId = null
                        withContext(context.player.mainDispatcher) {
                            subtitleTracks.select(null)
                        }
                    } else if (target != null && subtitleTracks.selected.value?.id != target.id) {
                        restoredDisabled = false
                        lastRestoredTrackId = target.id
                        withContext(context.player.mainDispatcher) {
                            subtitleTracks.select(target)
                        }
                    } else {
                        restoredDisabled = false
                        lastRestoredTrackId = subtitleTracks.selected.value?.id
                    }

                    subtitleTracks.selected
                        .drop(1)
                        .distinctUntilChanged()
                        .collect { selected ->
                            if (selected == null && restoredDisabled) {
                                restoredDisabled = false
                                return@collect
                            }
                            if (selected != null && selected.id == lastRestoredTrackId) {
                                lastRestoredTrackId = null
                                return@collect
                            }
                            lastRestoredTrackId = null
                            restoredDisabled = false
                            val current = episodePreferencesRepository.mediaPreferenceFlow(context.subjectId).first()
                            val updated = if (selected == null) {
                                current.copy(subtitleDisabled = true)
                            } else {
                                current.copy(
                                    preferredSubtitleLabel = selected.labels.firstOrNull()?.value,
                                    preferredSubtitleLanguage = selected.language,
                                    subtitleDisabled = false,
                                )
                            }
                            if (updated != current) {
                                episodePreferencesRepository.setMediaPreference(context.subjectId, updated)
                            }
                        }
                }
            }
        }
    }

    private fun selectPreferredTrack(
        candidates: List<SubtitleTrack>,
        pref: MediaPreference,
    ): SubtitleTrack? {
        if (pref.subtitleDisabled) return null

        val savedLabel = pref.preferredSubtitleLabel?.takeIf { it.isNotBlank() }
        val savedLanguage = pref.preferredSubtitleLanguage?.takeIf { it.isNotBlank() }
        if (savedLabel != null || savedLanguage != null) {
            if (savedLabel != null) {
                candidates.firstOrNull { track ->
                    track.labels.any { it.value.equals(savedLabel, ignoreCase = true) }
                }?.let { return it }
            }
            if (savedLanguage != null) {
                candidates.firstOrNull { track ->
                    track.language.equals(savedLanguage, ignoreCase = true)
                }?.let { return it }
            }
        }

        // 无保存偏好时优先简体中文
        return candidates.firstOrNull { it.isSimplifiedChinese() }
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
