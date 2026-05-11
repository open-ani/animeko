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
            val subjectId = session.request.first().subjectId.toIntOrNull()
            val preferredWebMediaSourceId = if (mediaSelectorSettingsFlow.first().preferLastSelectedWebSource) {
                subjectId?.let { getPreferredWebMediaSource(it).first() }
            } else {
                null
            }

            // #355 播放时自动启用上次临时启用选择的数据源
            launch {
                if (getMediaSelectorSettingsFlow().first().autoEnableLastSelected) {
                    autoSelector.autoEnableLastSelected(session)
                }
            }

            /**
             * 严格优先播放用户上次手动选择的 Web 数据源。
             *
             * 如果上次选了数据源 A，就强制优先播放 A，而不是让 fast select、缓存或默认兜底先抢到结果。
             * 这里必须等待 A 查询完成，失败也算完成。A 查询成功时，如果有上次选择的线路 A1，就播放 A1；
             * 如果没有 A1，就播放同一个数据源 A 的其他可选线路，例如 A2。
             * 只有 A 查询完成后仍然不能选择 A，才继续走剩余算法选择别的源。
             */
            preferredWebMediaSourceId?.let {
                val result = autoSelector.selectPreferredWebSource(session, preferredWebMediaSourceId)
                logger.info { "selectPreferredWebSource result: $result" }
                if (result != null) return@coroutineScope
            }

            cancellableCoroutineScope {
                fun <T> SelectBuilder<T>.resulting(block: suspend CoroutineScope.() -> T) {
                    this@cancellableCoroutineScope.async { block() }.onAwait { it }
                }

                select {
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
                        val result = if (preferredWebMediaSourceId == null) {
                            autoSelector.awaitCompletedAndSelectDefault(session, preferKindFlow)
                        } else {
                            autoSelector.awaitCompletedAndSelectAnySource(session, preferKindFlow)
                        }
                        logger.info { "awaitCompletedAndSelectFallback result: $result" }
                        result
                    }
                }

                cancelScope()
            }
        }
    }

    override fun getKoin(): Koin = koin
}
