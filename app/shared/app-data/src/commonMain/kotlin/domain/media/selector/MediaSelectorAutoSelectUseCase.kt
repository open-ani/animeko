/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.mediasource.GetWebMediaSourceInstanceFlowUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration

interface MediaSelectorAutoSelectUseCase : UseCase {
    suspend operator fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector)
}

class MediaSelectorAutoSelectUseCaseImpl(
    private val koin: Koin = GlobalKoin,
) : MediaSelectorAutoSelectUseCase, KoinComponent {
    private val getMediaSelectorSettingsFlowUseCase: GetMediaSelectorSettingsFlowUseCase by inject()
    private val getWebMediaSourceInstanceFlowUseCase: GetWebMediaSourceInstanceFlowUseCase by inject()
    private val logger = logger<MediaSelectorAutoSelectUseCase>()

    override suspend fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector) {
        coroutineScope {
            val mediaSelectorSettingsFlow = getMediaSelectorSettingsFlowUseCase()
            val preferKindFlow = mediaSelectorSettingsFlow.map { it.preferKind }

            mediaSelector.autoSelect.run {
                launch {
                    awaitCompletedAndSelectDefault(
                        session,
                        preferKindFlow,
                    )
                }
                launch {
                    // 快速自动选择数据源. 当按数据源顺序排序, 当最高排序的数据源查询完成后立即自动选择. #1322
                    val mediaSelectorSettings = mediaSelectorSettingsFlow.first()
                    if (!mediaSelectorSettings.fastSelectWebKind) {
                        return@launch
                    }

                    suspend fun doSelect(allowNonPreferred: Flow<Boolean>) = fastSelectSources(
                        session,
                        getWebMediaSourceInstanceFlowUseCase().first()
                            .map { it.mediaSourceId }, // no need to subscribe to changes
                        preferKind = preferKindFlow,
                        overrideUserSelection = false,
                        blacklistMediaIds = emptySet(),
                        allowNonPreferredFlow = allowNonPreferred,
                    )

                    var result = doSelect(
                        allowNonPreferred = flow {
                            when (val delay = mediaSelectorSettings.fastSelectWebKindAllowNonPreferredDelay) {
                                Duration.ZERO -> {
                                    emit(true)
                                }

                                Duration.INFINITE -> {
                                    emit(false)
                                }

                                else -> {
                                    emit(false)
                                    delay(delay)
                                    emit(true)
                                }
                            }
                        },
                    )
                    if (result == null && mediaSelectorSettings.fastSelectWebKindAllowNonPreferredDelay != Duration.INFINITE) {
                        // 所有数据源查询完成, 仍然没有选择到, 有可能是 `allowNonPreferred` 一直为 `false`. 所以我们要最后再尝试 select 一次
                        result = doSelect(
                            allowNonPreferred = flowOf(true),
                        )
                    }
                    logger.info { "fastSelectSources result: $result" }
                }
                launch {
                    selectCached(session)
                }

                launch {
                    if (getMediaSelectorSettingsFlowUseCase().first().autoEnableLastSelected) {
                        autoEnableLastSelected(session)
                    }
                }
            }
        }
    }

    override fun getKoin(): Koin = koin
}

