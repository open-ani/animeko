/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.source.MediaSourceKind
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

fun interface GetWebMediaSourceInstanceFlowUseCase : UseCase {
    suspend operator fun invoke(): Flow<List<String>>
}

class GetWebMediaSourceInstanceFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : GetWebMediaSourceInstanceFlowUseCase, KoinComponent {
    private val mediaSourceManager: MediaSourceManager by inject()

    override suspend fun invoke(): Flow<List<String>> {
        return mediaSourceManager.allInstances
            .map { list ->
                list.filter { it.source.kind == MediaSourceKind.WEB }
                    .map { it.mediaSourceId }
            }
            .flowOn(flowContext)
    }
}
