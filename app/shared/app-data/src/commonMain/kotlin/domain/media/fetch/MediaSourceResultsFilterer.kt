/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList

/**
 * 正在进行中的数据源查询的结果. 根据用户设置隐藏禁用的数据源
 */
class MediaSourceResultsFilterer(
    results: Flow<List<MediaSourceFetchResult>>,
    settings: Flow<MediaSelectorSettings>, // 不可以为 empty flow
    flowScope: CoroutineScope,
) {
    /**
     * 根据设置, 过滤掉禁用的数据源, 并按照查询到的数量降序排序.
     */
    val filteredSourceResults: Flow<List<MediaSourceFetchResult>> = results.flatMapLatest { results ->
        settings.flatMapLatest inner@{ settings ->
            // 过滤掉禁用的
            val candidates = results.filterTo(mutableListOf()) {
                if (!settings.showDisabled && it.state.value.isDisabled) return@filterTo false
                true
            }

            // 收集它们的 size
            if (candidates.isEmpty()) {
                return@inner flowOfEmptyList()
            }
            combine(candidates.map { sizes -> sizes.resultsIfEnabled.map { it.size } }) { sizes ->
                // 按照结果数量从大到小, 把禁用的放在最后.
                candidates.sortedWith(
                    compareByDescending<MediaSourceFetchResult> { result ->
                        if (result.state.value.isDisabled) {
                            Int.MIN_VALUE
                        } else {
                            sizes[candidates.indexOf(result)]
                        }
                    }.then(compareBy { it.mediaSourceId }), // 大小相同的按 ID 排序, 保证稳定
                )
            }
        }
    }.shareIn(flowScope, started = SharingStarted.WhileSubscribed(), replay = 1)
}
