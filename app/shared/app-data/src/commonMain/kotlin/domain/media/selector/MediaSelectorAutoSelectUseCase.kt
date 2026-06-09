/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CompletableDeferred
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

                select {
                    val selectPreferredFailed = CompletableDeferred<Unit>()

                    // 这个 clause 和下面 选缓存 与 兜底 一起竞争
                    // 快速选择不和这些竞争, 快速选择在这个 clause 完成之后再启动
                    resulting {
                        // subjectId 无效就等别的 clause.
                        val subjectId = session.request.first().subjectId.toIntOrNull() ?: awaitCancellation()
                        val result = autoSelector.trySelectPreferredWebSource(
                            session, getPreferredWebMediaSource(subjectId).first(),
                        )

                        logger.info { "selectPreferredWebSource result: $result" }

                        if (result == null) {
                            selectPreferredFailed.complete(Unit)
                            awaitCancellation()
                        }
                        result
                    }

                    // 快速自动选择数据源: 当按规则快速选择相应 Tier 的数据源. 仅在偏好 Web 时并且启用了快速选择时才执行.
                    resulting {
                        val selectorSettings = mediaSelectorSettingsFlow.first()
                        if (!selectorSettings.fastSelectWebKind || selectorSettings.preferKind != MediaSourceKind.WEB) {
                            // 没开启 fast select 就等别的 clause.
                            awaitCancellation()
                        }

                        // 上面选完并且没结果再开始这个 clause
                        selectPreferredFailed.await()

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

