/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetWebMediaSourceInstanceFlowUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.collections.tupleOf
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
    private val getMediaSelectorSourceTiersUseCase: GetMediaSelectorSourceTiersUseCase by inject()
    private val logger = logger<MediaSelectorAutoSelectUseCase>()

    override suspend fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector) {
        coroutineScope {
            val mediaSelectorSettingsFlow = getMediaSelectorSettingsFlowUseCase()
            val preferKindFlow = mediaSelectorSettingsFlow.map { it.preferKind }

            mediaSelector.autoSelect.run {
                val fastSelectJob = launch {
                    // 快速自动选择数据源. 当按数据源顺序排序, 当最高排序的数据源查询完成后立即自动选择. #1322
                    val mediaSelectorSettings = mediaSelectorSettingsFlow.first()
                    if (!mediaSelectorSettings.fastSelectWebKind) {
                        return@launch
                    }

                    suspend fun doSelect(allowNonPreferred: Flow<Boolean>): Media? {
                        // no need to subscribe to changes
                        val (fastMediaSourceIdOrder, sourceTiers) = combine(
                            getWebMediaSourceInstanceFlowUseCase(),
                            getMediaSelectorSourceTiersUseCase(), // load data in parallel
                        ) { a, b -> tupleOf(a, b) }.first()

                        return fastSelectSources(
                            session,
                            fastMediaSourceIdOrder,
                            preferKind = preferKindFlow,
                            overrideUserSelection = false,
                            blacklistMediaIds = emptySet(),
                            allowNonPreferredFlow = allowNonPreferred,
                            sourceTiers = sourceTiers,
                        )
                    }

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
                    logger.info { "[MediaSelectorAutoSelect] fastSelectSources result: $result" }
                }
                launch {
                    fastSelectJob.join() // 等待 fast select (tier-based) 结束, 再进行 fallback 选择.
                    awaitCompletedAndSelectDefault(
                        // 这个不会考虑 tier
                        session,
                        preferKindFlow,
                    ).also {
                        logger.info { "[MediaSelectorAutoSelect] awaitCompletedAndSelectDefault result: $it" }
                    }
                }
                launch {
                    selectCached(session).also {
                        logger.info { "[MediaSelectorAutoSelect] selectCached result: $it" }
                    }
                }

                launch {
                    if (getMediaSelectorSettingsFlowUseCase().first().autoEnableLastSelected) {
                        autoEnableLastSelected(session).also {
                            logger.info { "[MediaSelectorAutoSelect] autoEnableLastSelected result: $it" }
                        }
                    }
                }
            }
        }
    }

    override fun getKoin(): Koin = koin
}

