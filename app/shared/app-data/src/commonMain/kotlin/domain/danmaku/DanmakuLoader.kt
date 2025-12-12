/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.danmaku.api.DanmakuCollection
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import me.him188.ani.utils.platform.collections.tupleOf
import kotlin.time.Duration.Companion.seconds

/**
 * A general danmaku loader, that fetches danmaku from the network and cache and provides a [Flow] of [DanmakuCollection]
 */
sealed interface DanmakuLoader {
    val danmakuLoadingStateFlow: StateFlow<DanmakuLoadingState>
    val fetchResultFlow: Flow<List<DanmakuFetchResult>?>
}

class DanmakuLoaderImpl(
    requestFlow: Flow<SearchDanmakuRequest?>,
    flowScope: CoroutineScope,
    danmakuRepository: DanmakuRepository,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed()
) : DanmakuLoader {

    private val saveDanmakuTasker = SingleTaskExecutor(flowScope.coroutineContext)

    override val danmakuLoadingStateFlow: MutableStateFlow<DanmakuLoadingState> =
        MutableStateFlow(DanmakuLoadingState.Idle)

    private val overrideResultsFlow = MutableStateFlow<Map<DanmakuProviderId, List<DanmakuFetchResult>>>(emptyMap())

    private val originalFetchResultFlow = requestFlow.distinctUntilChanged().transformLatest { request ->
        emit(null) // 每次更换 mediaFetchSession 时 (ep 变更), 首先清空历史弹幕

        if (request == null) {
            danmakuLoadingStateFlow.value = DanmakuLoadingState.Idle
            return@transformLatest
        }
        danmakuLoadingStateFlow.value = DanmakuLoadingState.Loading
        try {
            coroutineScope {
                var remoteFetched = false
                launch(start = CoroutineStart.ATOMIC) {
                    val result = danmakuRepository.fetchFromLocal(request.toFetchRequest()).first()
                    if (!remoteFetched && result.isNotEmpty()) {
                        emit(result)
                        danmakuLoadingStateFlow.value = DanmakuLoadingState.Success
                    }
                }
                launch {
                    val result = danmakuRepository.fetchFromAllRemotes(request.toFetchRequest()).first()
                    danmakuLoadingStateFlow.value = DanmakuLoadingState.Success
                    remoteFetched = true
                    emit(result)
                }
            }
        } catch (e: CancellationException) {
            danmakuLoadingStateFlow.value = DanmakuLoadingState.Idle
            throw e
        } catch (e: Throwable) {
            danmakuLoadingStateFlow.value = DanmakuLoadingState.Failed(e)
            throw e
        }
    }.shareIn(flowScope, started = sharingStarted, replay = 1)

    override val fetchResultFlow: Flow<List<DanmakuFetchResult>?> = requestFlow
        .distinctUntilChangedBy {
            tupleOf(it?.subjectInfo?.subjectId, it?.episodeInfo?.episodeId)
        }.flatMapLatest { req ->
            // 每次切换剧集时, 这里会重新执行.
            overrideResultsFlow.value = emptyMap() // 清空覆盖

            combine(originalFetchResultFlow, overrideResultsFlow) { original, overrideResults ->
                val accumulatedResults = computeFinalDanmakuResult(original, overrideResults)
                if (accumulatedResults.isNotEmpty()) {
                    val subjectId = req?.subjectInfo?.subjectId
                    val episodeId = req?.episodeInfo?.episodeId
                    if (subjectId != null && episodeId != null) {
                        flowScope.launch {
                            saveDanmakuTasker.invoke {
                                delay(3.seconds)
                                danmakuRepository.cacheDanmakuIfNeeded(subjectId, episodeId, accumulatedResults)
                            }
                        }
                    }
                }
                accumulatedResults
            }
        }

    private fun computeFinalDanmakuResult(
        original: List<DanmakuFetchResult>?,
        override: Map<DanmakuProviderId, List<DanmakuFetchResult>>,
    ): List<DanmakuFetchResult> {
        return if (original == null) {
            override.values.flatten()
        } else {
            // Combine and replace
            LinkedHashMap<DanmakuProviderId, List<DanmakuFetchResult>>().apply {
                original.groupBy { it.providerId }.forEach { (providerId, result) ->
                    put(providerId, result)
                }
                for ((providerId, result) in override) {
                    put(providerId, result)
                }
            }.values.flatten()
        }
    }

    fun overrideResults(provider: DanmakuProviderId, result: List<DanmakuFetchResult>) {
        overrideResultsFlow.update {
            it + (provider to result)
        }
    }

    private fun SearchDanmakuRequest.toFetchRequest(): DanmakuFetchRequest {
        return DanmakuFetchRequest(
            subjectId = subjectInfo.subjectId,
            subjectPrimaryName = subjectInfo.displayName,
            subjectNames = subjectInfo.allNames,
            subjectPublishDate = subjectInfo.airDate,
            episodeId = episodeInfo.episodeId,
            episodeSort = episodeInfo.sort,
            episodeEp = episodeInfo.ep,
            episodeName = episodeInfo.displayName,
            filename = filename,
            fileHash = fileHash,
            fileSize = fileLength,
            videoDuration = videoDuration,
        )
    }
}

