/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.usecase.UseCase
import kotlin.coroutines.CoroutineContext

fun interface GetMediaSelectorSourceTiersUseCase : UseCase {
    operator fun invoke(): Flow<MediaSelectorSourceTiers>
}

class GetMediaSelectorSourceTiersUseCaseImpl(
    private val mediaSourceManager: MediaSourceManager,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : GetMediaSelectorSourceTiersUseCase {
    override fun invoke(): Flow<MediaSelectorSourceTiers> = mediaSourceManager.mediaSourceTiersFlow().flowOn(dispatcher)
}
