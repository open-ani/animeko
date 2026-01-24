/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.select
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface MediaSelectorAutoSelectUseCase : UseCase {
    suspend operator fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector)
}

class MediaSelectorAutoSelectUseCaseImpl(
    private val koin: Koin = GlobalKoin,
) : MediaSelectorAutoSelectUseCase, KoinComponent {
    private val getMediaSelectorSettingsFlow: GetMediaSelectorSettingsFlowUseCase by inject()
    private val getMediaSelectorSourceTiers: GetMediaSelectorSourceTiersUseCase by inject()
    private val getPreferredWebMediaSource: GetPreferredWebMediaSourceUseCase by inject()

    private val logger = logger<MediaSelectorAutoSelectUseCase>()

    override suspend fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector) {
        coroutineScope {
            val mediaSelectorSettingsFlow = getMediaSelectorSettingsFlow()
            val preferKindFlow = mediaSelectorSettingsFlow.map { it.preferKind }

            val autoSelector = mediaSelector.autoSelect

            // #355 播放时自动启用上次临时启用选择的数据源
            launch {
                if (getMediaSelectorSettingsFlow().first().autoEnableLastSelected) {
                    autoSelector.autoEnableLastSelected(session)
                }
            }

            /**
             * 为什么可以让 fast select 和 preferred select 一起跑?
             * 
             * 如果是热门资源, 通常不需要用户自己设置 web source preference, fast select 很快立刻就可以选好.
             * 对于冷门资源, fast select 很大概率在 tolerance 时间内也选不到合适的, 但这时 preferred select 可能在这之内选好.
             * 
             * 所以一起跑, 不是 fast select 先选好, 就是 preferred 先选好, 总之能尽快选出一个合适的.
             * 无论是 preferred select 或者 fast select 的结果, 对于用户来说都是比较优质的.
             * 
             * 如果 preferred 刚好也是 fast select 的一个结果也无所谓, 哪个 clause 快跑那个, 结果都是一样的.
             */
            cancellableCoroutineScope {
                fun <T> SelectBuilder<T>.resulting(block: suspend CoroutineScope.() -> T) {
                    this@cancellableCoroutineScope.async { block() }.onAwait { it }
                }

                select {
                    // 选择用户偏好的源
                    resulting {
                        // subjectId 无效就等别的 clause.
                        val subjectId = session.request.first().subjectId.toIntOrNull() ?: awaitCancellation()
                        val result = autoSelector.selectPreferredWebSource(
                            session, getPreferredWebMediaSource(subjectId).first(),
                        )

                        logger.info { "selectPreferredWebSource result: $result" }
                        result ?: awaitCancellation()
                    }

                    // 快速自动选择数据源: 当按规则快速选择相应 Tier 的数据源. 仅在偏好 Web 时并且启用了快速选择时才执行.
                    resulting {
                        val selectorSettings = mediaSelectorSettingsFlow.first()
                        if (!selectorSettings.fastSelectWebKind || selectorSettings.preferKind != MediaSourceKind.WEB) {
                            // 没开启 fast select 就等别的 clause.
                            awaitCancellation()
                        }

                        val result = autoSelector.fastSelectWebSources(
                            session,
                            getMediaSelectorSourceTiers().first(),
                            overrideUserSelection = false,
                            blacklistMediaIds = emptySet(),
                            selectorSettings.fastSelectWebLowTierToleranceDuration,
                        )

                        logger.info { "fastSelectWebSources result: $result" }
                        result ?: awaitCancellation()
                    }

                    // 选缓存, 如果有缓存通常非常快
                    resulting {
                        val result = autoSelector.selectCached(session)
                        logger.info { "selectCached result: $result" }
                        result ?: awaitCancellation()
                    }

                    // 兜底策略: 等所有数据源都准备好后, 选择一个.
                    resulting {
                        val result = autoSelector.awaitCompletedAndSelectDefault(session, preferKindFlow)
                        logger.info { "awaitCompletedAndSelectDefault result: $result" }
                        result
                    }
                }

                cancelScope()
            }
        }
    }

    override fun getKoin(): Koin = koin
}

