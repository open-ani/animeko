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

            cancellableCoroutineScope {
                fun <T> SelectBuilder<T>.resulting(block: suspend CoroutineScope.() -> T) {
                    this@cancellableCoroutineScope.async { block() }.onAwait { it }
                }

                // 四条路径竞速, 任一路径返回即结束编排. 返回 null 也算结束 (目前只有兜底会这样).
                // 路径内部通过 awaitCancellation() 表示 "放弃竞争但不结束编排".
                select {
                    // 偏好 web 源, 然后是快速选择. 二者顺序执行, 不竞速: 只有偏好源落空后才开始快速选择.
                    resulting {
                        val subjectId = session.request.first().subjectId.toIntOrNull()
                        if (subjectId != null) {
                            val preferred = autoSelector.trySelectPreferredWebSource(
                                session, getPreferredWebMediaSource(subjectId).first(),
                            )
                            logger.info { "selectPreferredWebSource result: $preferred" }
                            if (preferred != null) return@resulting preferred
                        }

                        // 快速选择仅在偏好 Web 且启用了快速选择时才执行.
                        val selectorSettings = mediaSelectorSettingsFlow.first()
                        if (!selectorSettings.fastSelectWebKind || selectorSettings.preferKind != MediaSourceKind.WEB) {
                            awaitCancellation()
                        }

                        val lowTierTolerance = selectorSettings.fastSelectWebLowTierToleranceDuration
                        val result = autoSelector.fastSelectWebSources(
                            session,
                            getMediaSelectorSourceTiers().first(),
                            overrideUserSelection = false,
                            blacklistMediaIds = emptySet(),
                            lowTierToleranceDuration = lowTierTolerance,
                            fuzzyFallbackDuration = maxOf(MediaSelectorAutoSelect.DefaultFuzzyFallbackDuration, lowTierTolerance),
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

