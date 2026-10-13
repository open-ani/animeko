/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.editor

import androidx.compose.runtime.Stable
import me.him188.ani.app.domain.mediasource.web.SelectorAutoMatchConfig
import me.him188.ani.app.ui.settings.mediasource.rss.ConfigSection
import me.him188.ani.datasources.api.source.MediaSourceTier
import kotlin.properties.ReadWriteProperty
import kotlin.time.Duration

/**
 * 自动匹配层的编辑状态: [SelectorAutoMatchConfig], 数据源阶级与搜索缓存有效期.
 *
 * 阶级与缓存有效期不在 [SelectorAutoMatchConfig] 里, 由各数据源的参数以属性委托的形式提供.
 */
@Stable
class AutoMatchConfigState(
    config: ConfigSection<SelectorAutoMatchConfig>,
    tier: ReadWriteProperty<Any?, MediaSourceTier>,
    searchCacheTtl: ReadWriteProperty<Any?, Duration>,
) {
    var enabled: Boolean by config.prop({ it.enabled }, { copy(enabled = it) })
    var tier: MediaSourceTier by tier
    var searchUseOnlyFirstWord: Boolean by config.prop({ it.searchUseOnlyFirstWord }, { copy(searchUseOnlyFirstWord = it) })
    var searchRemoveSpecial: Boolean by config.prop({ it.searchRemoveSpecial }, { copy(searchRemoveSpecial = it) })
    var searchUseSubjectNamesCount: Int by config.prop(
        { it.searchUseSubjectNamesCount },
        { copy(searchUseSubjectNamesCount = it) },
    )
    var preferShorterName: Boolean by config.prop({ it.preferShorterName }, { copy(preferShorterName = it) })
    var filterByEpisodeSort: Boolean by config.prop({ it.filterByEpisodeSort }, { copy(filterByEpisodeSort = it) })

    /**
     * 播放 session 内复用搜索结果的时长. 浏览不读写缓存, 因此属于自动匹配层.
     */
    var searchCacheTtl: Duration by searchCacheTtl
}
