/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.selector.trace.MediaSelectionTrace
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.source.MediaFetchRequest

/** Test-only, cleaned recording: each distinct media value occurs once; events contain source deltas. */
@Serializable
internal class MediaSelectionFixture(
    val formatVersion: Int,
    val provenance: Provenance,
    val request: MediaFetchRequest,
    val preferredSourceId: String?,
    val durationMillis: Long,
    val sources: List<MediaSelectionTrace.Source>,
    val initial: Inputs,
    val media: List<DefaultMedia>,
    val events: List<Event>,
) {
    @Serializable
    data class Provenance(
        val capturedAt: String,
        val applicationVersion: String,
        val acquisition: String,
        val searchCacheCleared: Boolean,
        val rawSha256: String,
        val rawBytes: Int,
        val rawFrameCount: Int,
    )

    @Serializable
    data class Inputs(
        val context: MediaSelectionTrace.Context,
        val settings: MediaSelectorSettings,
        val preference: MediaPreference,
        val defaultPreference: MediaPreference,
    )

    @Serializable
    data class SourceChange(val source: Int, val state: String, val generation: Int?, val media: List<Int>)

    @Serializable
    data class Event(
        val elapsedMillis: Long,
        val sources: List<SourceChange>,
        val selectedMediaId: String?,
        val context: MediaSelectionTrace.Context? = null,
        val settings: MediaSelectorSettings? = null,
        val preference: MediaPreference? = null,
        val defaultPreference: MediaPreference? = null,
    )

    fun restore(): MediaSelectionTrace {
        require(formatVersion == 1)
        require(events.isNotEmpty() && events.size <= provenance.rawFrameCount)
        require(events.zipWithNext().all { (a, b) -> a.elapsedMillis <= b.elapsedMillis })
        require(events.last().elapsedMillis <= durationMillis)
        val states = sources.map { MediaSelectionTrace.SourceUpdate(it.id, "Idle", null, emptyList()) }.toMutableList()
        var inputs = initial
        val frames = events.map { event ->
            inputs = Inputs(
                event.context ?: inputs.context, event.settings ?: inputs.settings,
                event.preference ?: inputs.preference, event.defaultPreference ?: inputs.defaultPreference,
            )
            event.sources.forEach { change ->
                states[change.source] = MediaSelectionTrace.SourceUpdate(
                    sources[change.source].id, change.state, change.generation, change.media.map { media[it] },
                )
            }
            MediaSelectionTrace.Frame(
                event.elapsedMillis, states.toList(), inputs.context, inputs.settings,
                inputs.preference, inputs.defaultPreference, event.selectedMediaId,
            )
        }
        return MediaSelectionTrace(
            capturedAt = provenance.capturedAt,
            applicationVersion = provenance.applicationVersion,
            acquisition = provenance.acquisition,
            durationMillis = durationMillis,
            searchCacheCleared = provenance.searchCacheCleared,
            request = request,
            preferredSourceId = preferredSourceId,
            sources = sources,
            frames = frames,
        )
    }
}
