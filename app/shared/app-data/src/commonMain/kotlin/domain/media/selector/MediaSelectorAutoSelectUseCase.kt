/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.source.MediaSourceKind
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
    private val getMediaSelectorSettingsFlowUseCase: GetMediaSelectorSettingsFlowUseCase by inject()
    private val getMediaSelectorSourceTiersUseCase: GetMediaSelectorSourceTiersUseCase by inject()
    private val logger = logger<MediaSelectorAutoSelectUseCase>()

    override suspend fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector) {
        coroutineScope {
            val mediaSelectorSettingsFlow = getMediaSelectorSettingsFlowUseCase()
            val preferKindFlow = mediaSelectorSettingsFlow.map { it.preferKind }

            val autoSelector = mediaSelector.autoSelect

            val fastSelectJob = launch {
                // 快速自动选择数据源: 当按规则快速选择相应 Tier 的数据源. 仅在偏好 Web 时并且启用了快速选择时才执行.
                val selectorSettings = mediaSelectorSettingsFlow.first()
                if (!selectorSettings.fastSelectWebKind || selectorSettings.preferKind != MediaSourceKind.WEB) {
                    return@launch
                }

                autoSelector.fastSelectWebSources(
                    session,
                    getMediaSelectorSourceTiersUseCase().first(),
                    overrideUserSelection = false,
                    blacklistMediaIds = emptySet(),
                    selectorSettings.fastSelectWebLowTierToleranceDuration,
                ).also {
                    logger.info { "[MediaSelectorAutoSelect] fastSelectWebSources result: $it" }
                }
            }
            launch {
                fastSelectJob.join() // 等待 fast select (tier-based) 结束, 再进行 fallback 选择.
                autoSelector.awaitCompletedAndSelectDefault(
                    // 这个不会考虑 tier
                    session,
                    preferKindFlow,
                ).also {
                    logger.info { "[MediaSelectorAutoSelect] awaitCompletedAndSelectDefault result: $it" }
                }
            }
            launch {
                autoSelector.selectCached(session).also {
                    logger.info { "[MediaSelectorAutoSelect] selectCached result: $it" }
                }
            }

            launch {
                if (getMediaSelectorSettingsFlowUseCase().first().autoEnableLastSelected) {
                    autoSelector.autoEnableLastSelected(session).also {
                        logger.info { "[MediaSelectorAutoSelect] autoEnableLastSelected result: $it" }
                    }
                }
            }
        }
    }

    override fun getKoin(): Koin = koin
}

