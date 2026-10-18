/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.danmaku.DanmakuTextConversionSettings
import me.him188.ani.app.domain.episode.DanmakuFetchResultWithConfig
import me.him188.ani.app.ui.episode.danmaku.DanmakuSourceItem
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import me.him188.ani.utils.platform.Uuid

/**
 * 弹幕列表项数据类，用于在UI中显示单条弹幕信息。
 */
data class DanmakuListItem(
    val id: String,
    val randomId: Uuid,
    val content: String,
    val timeMillis: Long,
    val serviceId: DanmakuServiceId,
    val isSelf: Boolean,
)

/**
 * 弹幕列表状态数据类
 */
data class DanmakuListState(
    val danmakuItems: List<DanmakuListItem>,
    val sourceItems: List<DanmakuSourceItem>,
    val isLoading: Boolean,
    val isEmpty: Boolean,
    /**
     * 全局弹幕文字转换设置, 用于展示各来源的跟随值.
     */
    val globalTextConversion: DanmakuTextConversion = DanmakuTextConversion.ORIGINAL,
    /**
     * 按来源的弹幕文字转换覆盖, 用于打开统一设置对话框.
     */
    val textConversionOverrides: Map<DanmakuServiceId, DanmakuTextConversion> = emptyMap(),
) {
    companion object {
        val Loading = DanmakuListState(
            danmakuItems = emptyList(),
            sourceItems = emptyList(),
            isLoading = true,
            isEmpty = false,
        )
    }
}

/**
 * 弹幕列表状态生产者，负责处理弹幕数据的计算和转换逻辑
 */
class DanmakuListStateProducer(
    danmakuFlow: Flow<List<DanmakuPresentation>>,
    fetchResultsFlow: Flow<List<DanmakuFetchResultWithConfig>>,
    textConversion: Flow<DanmakuTextConversionSettings> = flowOf(DanmakuTextConversionSettings.Default),
) {
    val stateFlow: Flow<DanmakuListState> = combine(
        danmakuFlow,
        fetchResultsFlow,
        textConversion,
    ) { danmakuList, fetchResults, textConversion ->
        val sourceItems = fetchResults.map { result ->
            DanmakuSourceItem(
                serviceId = result.serviceId,
                enabled = result.config.enabled,
                matchMethod = result.matchInfo.method,
                shiftMillis = result.config.shiftMillis,
                count = danmakuList.count { presentation -> presentation.danmaku.serviceId == result.serviceId },
                textConversionOverride = textConversion.overrides[result.serviceId],
            )
        }

        val danmakuItems = danmakuList.map { presentation ->
            DanmakuListItem(
                id = presentation.danmaku.id,
                randomId = Uuid.random(),
                content = presentation.danmaku.text,
                timeMillis = presentation.danmaku.playTimeMillis,
                serviceId = presentation.danmaku.serviceId,
                isSelf = presentation.isSelf,
            )
        }.sortedBy { it.timeMillis }

        DanmakuListState(
            danmakuItems = danmakuItems,
            sourceItems = sourceItems,
            isLoading = fetchResults.isEmpty(),
            isEmpty = danmakuList.isEmpty() && fetchResults.isNotEmpty(),
            globalTextConversion = textConversion.global,
            textConversionOverrides = textConversion.overrides,
        )
    }.distinctUntilChanged()

}
