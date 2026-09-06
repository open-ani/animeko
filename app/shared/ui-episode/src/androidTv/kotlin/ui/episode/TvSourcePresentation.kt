/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.datasources.api.source.MediaSourceKind

@OptIn(UnsafeOriginalMediaAccess::class)
internal fun tvSourceGroups(session: MediaFetchSession, selector: MediaSelector): Flow<List<TvSourceGroup>> {
    val sources = session.mediaSourceResults.filter { it.kind == MediaSourceKind.WEB }
    if (sources.isEmpty()) return flowOf(emptyList())
    return combine(
        sources.map { source ->
            combine(source.state, selector.filteredCandidates) { state, candidates ->
                val items = candidates.filter { it.original.mediaSourceId == source.mediaSourceId }
                    .map { TvSourceItem(it.original, it.exclusionReason?.tvDescription()) }
                TvSourceGroup(
                    instanceId = source.instanceId,
                    sourceId = source.mediaSourceId,
                    name = source.sourceInfo.displayName,
                    iconUrl = source.sourceInfo.iconUrl,
                    status = when (state) {
                        MediaSourceFetchState.Idle, MediaSourceFetchState.Working -> "正在查询…"
                        MediaSourceFetchState.Disabled -> "未启用"
                        is MediaSourceFetchState.CaptchaRequired -> "需要验证"
                        is MediaSourceFetchState.RateLimited -> "请求过于频繁，请稍后重试"
                        is MediaSourceFetchState.Failed, is MediaSourceFetchState.Abandoned -> "查询失败"
                        is MediaSourceFetchState.Succeed -> if (items.isEmpty()) "没有找到资源" else "${items.size} 个结果"
                    },
                    loading = state == MediaSourceFetchState.Idle || state == MediaSourceFetchState.Working,
                    items = items,
                    failed = state is MediaSourceFetchState.Failed || state is MediaSourceFetchState.Abandoned,
                )
            }
        },
    ) { it.toList() }
}

private fun MediaExclusionReason.tvDescription(): String = when (this) {
    is MediaExclusionReason.SingleEpisodeForCompleteSubject -> "已完结条目的单集资源"
    MediaExclusionReason.MediaWithoutSubtitle -> "没有字幕"
    MediaExclusionReason.UnsupportedByPlatformPlayer -> "播放器可能不支持"
    MediaExclusionReason.FromSequelSeason -> "可能属于续作"
    MediaExclusionReason.FromSeriesSeason -> "可能属于其他季度"
    MediaExclusionReason.SubjectNameMismatch -> "条目名称不匹配"
}
