/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFinal
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind

/**
 * 某一时刻一个数据源的查询状态与它已经返回的全部结果.
 *
 * 这是自动选择决策核的输入之一. 属于实现细节, 仅因需要出现在 [me.him188.ani.app.domain.media.selector.MediaSelector] 接口签名上而公开.
 */
data class SourceSnapshot(
    val mediaSourceId: String,
    val kind: MediaSourceKind,
    val state: MediaSourceFetchState,
    val results: List<Media>,
) {
    val isFinal: Boolean get() = state.isFinal
    val isSucceed: Boolean get() = state is MediaSourceFetchState.Succeed
}

/**
 * 自动选择决策核的完整输入: 一次内部一致的世界视图.
 *
 * [candidates] 与 [preferred] 都是在同一次 emission 内由 [sources] 同步算出的, 不存在 "源已完成但候选还没吸收结果",
 * 或 "过滤列表已更新但偏好列表还没更新" 的中间态. 属于实现细节, 仅因需要出现在接口签名上而公开.
 *
 * @property candidates `filterMediaList` + `sortMediaList` 的完整结果, 含被排除的项, 顺序即排序结果.
 * @property preferred [candidates] 经用户偏好过滤后剩下的、未被排除的项.
 * @property mergedPreference 会话内生效的合并偏好.
 */
data class AutoSelectSnapshot(
    val sources: List<SourceSnapshot>,
    val candidates: List<MaybeExcludedMedia>,
    val preferred: List<MaybeExcludedMedia.Included>,
    val mergedPreference: MediaPreference,
    val settings: MediaSelectorSettings,
    val context: MediaSelectorContext,
) {
    /** [candidates] 中未被排除的项. */
    val included: List<MaybeExcludedMedia.Included> = candidates.filterIsInstance<MaybeExcludedMedia.Included>()

    /** 候选中出现过的全部字幕组, 与 `MediaSelector.alliance.available` 的口径一致. */
    val availableAlliances: List<String> = included.mapTo(HashSet()) { it.result.properties.alliance }.sortedBy { it }

    val webSources: List<SourceSnapshot> = sources.filter { it.kind == MediaSourceKind.WEB }

    /** 所有 WEB 源都已结束 (完成或禁用). 没有 WEB 源时为 `true`. */
    val allWebSourcesFinal: Boolean = webSources.all { it.isFinal }

    /** 会话中是否存在未被禁用的 WEB 源. */
    val hasEnabledWebSource: Boolean = webSources.any { it.state !is MediaSourceFetchState.Disabled }

    /**
     * "偏好 WEB 的查询已经结束" 的完成条件, 与旧 `awaitCompletedAndSelectDefault(waitForKind = WEB)` 一致:
     * 有启用的 WEB 源时看 WEB 源是否全部结束; 一个 WEB 源都没启用 (或根本没有) 时退化为等所有源结束,
     * 这样只用 BT / 缓存的用户仍然能在查询结束后得到默认选择.
     */
    val allSourcesFinalForPreferredWeb: Boolean =
        if (hasEnabledWebSource) allWebSourcesFinal else sources.all { it.isFinal }

    val succeededWebSourceIds: Set<String> = webSources.filter { it.isSucceed }.mapTo(HashSet()) { it.mediaSourceId }
}

/**
 * 观察会话中每个数据源的 (状态, 结果) 快照. collect 此 flow 即 collect 每个源的 `results`, 因此会驱动惰性查询开始.
 */
fun MediaFetchSession.sourceSnapshots(): Flow<List<SourceSnapshot>> {
    if (mediaSourceResults.isEmpty()) return flowOf(emptyList())
    return combine(mediaSourceResults.map { it.snapshotFlow() }) { it.toList() }
}

private fun MediaSourceFetchResult.snapshotFlow(): Flow<SourceSnapshot> {
    return combine(state, results) { state, results ->
        // combine 可能先收到 Succeed、后收到对应的最后一批结果. MediaFetcher 保证 Succeed 发布时 results 的 replayCache
        // 已经是完整列表 (其他终态没有这个保证, 但决策也只使用 Succeed 源的结果), 所以 Succeed 下直接读 replayCache,
        // 绝不把 Succeed 和旧列表配对.
        val consistentResults = if (state is MediaSourceFetchState.Succeed) this.results.first() else results
        SourceSnapshot(mediaSourceId, kind, state, consistentResults)
    }
}
