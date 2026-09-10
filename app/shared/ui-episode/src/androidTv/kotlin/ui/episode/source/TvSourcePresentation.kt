/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.datasources.api.source.MediaSourceKind

@OptIn(UnsafeOriginalMediaAccess::class)
internal fun tvSourceGroups(
    session: MediaFetchSession,
    selector: MediaSelector,
    captchaSupported: Boolean,
): Flow<List<TvSourceGroup>> {
    val sources = session.mediaSourceResults.filter { it.kind == MediaSourceKind.WEB }
    if (sources.isEmpty()) return flowOf(emptyList())
    return combine(
        sources.map { source ->
            combine(source.state, selector.filteredCandidates) { state, candidates ->
                val items = candidates.filter { it.original.mediaSourceId == source.mediaSourceId }
                    .map { TvSourceItem(it.original, it.exclusionReason) }
                TvSourceGroup(
                    instanceId = source.instanceId,
                    sourceId = source.mediaSourceId,
                    name = source.sourceInfo.displayName,
                    iconUrl = source.sourceInfo.iconUrl,
                    state = state,
                    items = items,
                    isCaptchaSupported = captchaSupported,
                )
            }
        },
    ) { it.toList() }
}
