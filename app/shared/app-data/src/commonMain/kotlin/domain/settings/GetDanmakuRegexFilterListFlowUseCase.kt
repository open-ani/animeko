/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Obtains the regex filters to be applied to danmaku collection, according to the user's settings.
 */
fun interface GetDanmakuRegexFilterListFlowUseCase : UseCase {
    operator fun invoke(): Flow<List<String>>
}

class GetDanmakuRegexFilterListFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : GetDanmakuRegexFilterListFlowUseCase, KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val danmakuRegexFilterRepository: DanmakuRegexFilterRepository by inject()

    override fun invoke(): Flow<List<String>> {
        return combine(settingsRepository.danmakuFilterConfig.flow, danmakuRegexFilterRepository.flow) { config, list ->
            if (!config.enableRegexFilter) emptyList()
            else list.filter { it.enabled }.map { it.regex }
        }.flowOn(flowContext)
            .catch { e ->
                if (e !is CancellationException) {
                    logger.error(e) { "Failed to get danmaku regex filter list" }
                }
                emit(listOf())
                throw e
            }
    }


    private val logger = logger<GetDanmakuRegexFilterListFlowUseCaseImpl>()
}
