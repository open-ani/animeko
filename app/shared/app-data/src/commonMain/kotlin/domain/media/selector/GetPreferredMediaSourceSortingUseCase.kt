/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.usecase.UseCase
import kotlin.coroutines.CoroutineContext

fun interface GetPreferredMediaSourceSortingUseCase : UseCase {
    /**
     * @return [MediaSourceFetchResult.instanceId]
     */
    operator fun invoke(): Flow<List<String>>
}

class GetPreferredMediaSourceSortingUseCaseImpl(
    private val mediaSourceManager: MediaSourceManager,
    private val context: CoroutineContext = Dispatchers.Default,
) : GetPreferredMediaSourceSortingUseCase {
    override fun invoke(): Flow<List<String>> {
        return mediaSourceManager.allInstances.map { list ->
            list.map {
                it.instanceId
            }
        }.flowOn(context)
    }
}
