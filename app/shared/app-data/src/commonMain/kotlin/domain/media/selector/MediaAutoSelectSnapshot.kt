/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind

/** Source state and the results belonging to it, before selector filtering. */
data class MediaSourceSelectionSnapshot(
    val mediaSourceId: String,
    val kind: MediaSourceKind,
    val state: MediaSourceFetchState,
    val results: List<Media>,
)

/** All decision inputs are derived together, without reading the UI's replayed candidate flows. */
data class MediaAutoSelectSnapshot(
    val sources: List<MediaSourceSelectionSnapshot>,
    val candidates: List<MaybeExcludedMedia.Included>,
    val preferred: List<MaybeExcludedMedia.Included>,
    val preference: MediaPreference,
    val settings: MediaSelectorSettings,
    val context: MediaSelectorContext,
) {
    internal val availableAlliances get() = candidates.map { it.result.properties.alliance }.distinct().sortedBy { it }
}
