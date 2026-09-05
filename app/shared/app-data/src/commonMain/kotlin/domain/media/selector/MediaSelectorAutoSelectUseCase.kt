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
            val autoSelector = mediaSelector.autoSelect

            // #355 播放时自动启用上次临时启用选择的数据源. 独立于选择流程, 不随选择结束而取消.
            launch {
                if (mediaSelectorSettingsFlow.first().autoEnableLastSelected) {
                    autoSelector.autoEnableLastSelected(session)
                }
            }

            val settings = mediaSelectorSettingsFlow.first()
            val subjectId = session.request.first().subjectId.toIntOrNull()
            val preferredWebSourceId = subjectId?.let { getPreferredWebMediaSource(it).first() }

            if (settings.preferKind == MediaSourceKind.WEB) {
                // 偏好 WEB: 记忆源 → 分阶段快速选择 → 全部结束后默认选择, 缓存随时可胜出. 一个纯策略, 一个执行循环.
                val result = autoSelector.autoSelectWeb(
                    session,
                    sourceTiers = getMediaSelectorSourceTiers().first(),
                    preferredWebMediaSourceId = preferredWebSourceId,
                    fastSelect = settings.fastSelectWebKind,
                    lowTierToleranceDuration = settings.fastSelectWebLowTierToleranceDuration,
                )
                logger.info { "autoSelectWeb result: $result" }
                return@coroutineScope
            }

            // 其他偏好 (BT / 无偏好): 沿用原有编排. 三条路径竞速, 任一路径返回即结束; awaitCancellation() 表示放弃竞争但不结束编排.
            cancellableCoroutineScope {
                fun <T> SelectBuilder<T>.resulting(block: suspend CoroutineScope.() -> T) {
                    this@cancellableCoroutineScope.async { block() }.onAwait { it }
                }

                select {
                    // 记忆的 web 源
                    resulting {
                        val result = autoSelector.trySelectPreferredWebSource(session, preferredWebSourceId)
                        logger.info { "selectPreferredWebSource result: $result" }
                        result ?: awaitCancellation()
                    }

                    // 选缓存, 如果有缓存通常非常快
                    resulting {
                        val result = autoSelector.selectCached(session)
                        logger.info { "selectCached result: $result" }
                        result ?: awaitCancellation()
                    }

                    // 兜底策略: 等偏好类型的数据源都准备好后, 选择一个.
                    resulting {
                        val result = autoSelector.awaitCompletedAndSelectDefault(
                            session,
                            mediaSelectorSettingsFlow.map { it.preferKind },
                        )
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

