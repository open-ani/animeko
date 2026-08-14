/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import org.koin.core.Koin

/**
 * 自动选择数据源
 *
 * @see MediaSelector
 */
class AutoSelectExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("AutoSelect") {
    private val mediaSelectorAutoSelectUseCase: MediaSelectorAutoSelectUseCase by koin.inject()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("AutoSelect") {
            context.sessionFlow.collectLatest { session ->
                session.fetchSelectFlow.collectLatest { fetchSelect ->
                    if (fetchSelect == null) return@collectLatest
                    // 等待 web 切集快速路径的决策: 命中时已完成选择, 不启动自动选择
                    // (自动选择的部分策略会 collect 查询结果, 从而触发数据源搜索).
                    // 快速路径失效时决策会翻转为 false, 此时重新启动自动选择.
                    session.webFastPathHit.filterNotNull().collectLatest fastPath@{ hit ->
                        if (hit) return@fastPath
                        mediaSelectorAutoSelectUseCase(fetchSelect.mediaFetchSession, fetchSelect.mediaSelector)
                    }
                }
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<AutoSelectExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): AutoSelectExtension {
            return AutoSelectExtension(context, koin)
        }
    }
}
