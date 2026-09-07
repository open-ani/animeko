/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.trace

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.MediaSelectorSubtitlePreferences
import me.him188.ani.app.domain.media.selector.SubtitleKindPreference
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceTier
import me.him188.ani.utils.platform.collections.ImmutableEnumMap

/** Recorded playback observations, not a hand-authored schedule. URLs are removed before serialization. */
@Serializable
class MediaSelectionTrace(
    val formatVersion: Int = 1,
    val capturedAt: String,
    val applicationVersion: String,
    val acquisition: String = "desktop-playback",
    val durationMillis: Long,
    val searchCacheCleared: Boolean = false,
    val request: MediaFetchRequest,
    val preferredSourceId: String?,
    val sources: List<Source>,
    val frames: List<Frame>,
) {
    @Serializable
    data class Source(val id: String, val name: String, val kind: MediaSourceKind)

    /** Time is measured from attaching to the playback bundle, using a monotonic clock. */
    @Serializable
    data class Frame(
        val elapsedMillis: Long,
        /** Same order as the session's source list; distinct instances can share a mediaSourceId. */
        val sources: List<SourceUpdate>,
        val context: Context,
        val settings: MediaSelectorSettings,
        val preference: MediaPreference,
        val defaultPreference: MediaPreference,
        val selectedMediaId: String?,
    )

    @Serializable
    data class SourceUpdate(
        val id: String,
        val state: String,
        val generation: Int?,
        val results: List<DefaultMedia>,
    )

    /** Only subject fields read by filtering/selection are retained; artwork and user account data are omitted. */
    @Serializable
    data class Subject(val id: Int, val name: String, val nameCn: String, val aliases: List<String>) {
        fun restore(): SubjectInfo = SubjectInfo.Empty.copy(subjectId = id, name = name, nameCn = nameCn, aliases = aliases)
    }

    @Serializable
    data class Series(val seasonSort: Int, val sequelNames: Set<String>, val otherNames: Set<String>) {
        fun restore(): SubjectSeriesInfo = SubjectSeriesInfo(seasonSort, sequelNames, otherNames)
    }

    @Serializable
    data class Context(
        val subjectFinished: Boolean?,
        val sourcePrecedence: List<String>?,
        val subtitlePreferences: Map<SubtitleKind, SubtitleKindPreference>?,
        val series: Series?,
        val subject: Subject?,
        val episode: EpisodeInfo?,
        val sourceTiers: Map<String, UInt>?,
        val channelTiers: Map<String, Map<String, UInt>>?,
    ) {
        fun restore(): MediaSelectorContext = MediaSelectorContext(
            subjectFinished, sourcePrecedence,
            subtitlePreferences?.let { preferences ->
                MediaSelectorSubtitlePreferences(ImmutableEnumMap { preferences.getValue(it) })
            },
            series?.restore(), subject?.restore(), episode,
            sourceTiers?.let { tiers ->
                MediaSelectorSourceTiers(
                    tiers.mapValues { MediaSourceTier(it.value) },
                    channelTiers.orEmpty().mapValues { (_, channels) -> channels.mapValues { MediaSourceTier(it.value) } },
                )
            },
        )

        companion object {
            fun capture(context: MediaSelectorContext, sourceIds: List<String>): Context = Context(
                context.subjectFinished, context.mediaSourcePrecedence,
                context.subtitlePreferences?.let { preferences -> SubtitleKind.entries.associateWith { preferences[it] } },
                context.subjectSeriesInfo?.let { Series(it.seasonSort, it.sequelSubjectNames, it.seriesSubjectNamesWithoutSelf) },
                context.subjectInfo?.let { Subject(it.subjectId, it.name, it.nameCn, it.aliases) },
                context.episodeInfo?.copy(desc = ""),
                context.mediaSourceTiers?.let { tiers -> sourceIds.associateWith { tiers[it].value } },
                context.mediaSourceTiers?.channelTiers?.mapValues { (_, channels) -> channels.mapValues { it.value.value } },
            )
        }
    }

    companion object {
        val json = Json { prettyPrint = true; encodeDefaults = true }
    }
}
