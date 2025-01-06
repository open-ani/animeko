/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.danmaku.api.DanmakuCollection
import me.him188.ani.danmaku.api.TimeBasedDanmakuSession
import me.him188.ani.danmaku.api.emptyDanmakuCollection
import org.koin.core.Koin

/**
 * A general danmaku loader, that fetches danmaku from the network and cache and provides a [Flow] of [DanmakuCollection]
 */
sealed interface DanmakuLoader {
    val state: StateFlow<DanmakuLoadingState>
    val collectionFlow: Flow<DanmakuCollection>
}

class DanmakuLoaderImpl(
    requestFlow: Flow<SearchDanmakuRequest?>,
    flowScope: CoroutineScope,
    koin: Koin,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed()
) : DanmakuLoader {
    private val searchDanmakuUseCase: SearchDanmakuUseCase by koin.inject()
    
    override val state: MutableStateFlow<DanmakuLoadingState> = MutableStateFlow(DanmakuLoadingState.Idle)

    override val collectionFlow: Flow<DanmakuCollection> =
        requestFlow.distinctUntilChanged().transformLatest { request ->
            emit(emptyDanmakuCollection()) // 每次更换 mediaFetchSession 时 (ep 变更), 首先清空历史弹幕

            if (request == null) {
                state.value = DanmakuLoadingState.Idle
                return@transformLatest
            }
            state.value = DanmakuLoadingState.Loading
            try {
                val result = searchDanmakuUseCase(request)
                state.value = DanmakuLoadingState.Success(result.matchInfos)
                emit(TimeBasedDanmakuSession.create(result.list))
            } catch (e: CancellationException) {
                state.value = DanmakuLoadingState.Idle
                throw e
            } catch (e: Throwable) {
                state.value = DanmakuLoadingState.Failed(e)
                throw e
            }
        }.shareIn(flowScope, started = sharingStarted, replay = 1)
}

