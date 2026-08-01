/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.danmaku.api.DanmakuPreprocessConfig
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * 根据用户设置, 得到加载弹幕时的预处理配置 (合并重复弹幕等).
 */
fun interface GetDanmakuPreprocessConfigFlowUseCase : UseCase {
    operator fun invoke(): Flow<DanmakuPreprocessConfig>
}

class GetDanmakuPreprocessConfigFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : GetDanmakuPreprocessConfigFlowUseCase, KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    override fun invoke(): Flow<DanmakuPreprocessConfig> {
        return settingsRepository.danmakuFilterConfig.flow
            .map { config ->
                DanmakuPreprocessConfig(
                    enableMerge = config.enableMerge,
                    zhConversion = config.zhConversion,
                )
            }
            .distinctUntilChanged()
            .flowOn(flowContext)
            .catch { e ->
                if (e !is CancellationException) {
                    logger.error(e) { "Failed to get danmaku preprocess config" }
                }
                emit(DanmakuPreprocessConfig.Default)
                throw e
            }
    }

    private val logger = logger<GetDanmakuPreprocessConfigFlowUseCaseImpl>()
}
