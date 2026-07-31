/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

/**
 * A general danmaku loader, that fetches danmaku from the network and cache and provides a [Flow] of [DanmakuCollection]
 */
sealed interface DanmakuLoader {
    val danmakuLoadingStateFlow: StateFlow<DanmakuLoadingState>
    val fetchResultFlow: Flow<List<DanmakuFetchResult>?>
}

class DanmakuLoaderImpl internal constructor(
    requestFlow: Flow<SearchDanmakuRequest?>,
    flowScope: CoroutineScope,
    private val fetchFromLocal: (DanmakuFetchRequest) -> Flow<List<DanmakuFetchResult>>,
    private val fetchFromAllRemotes: (DanmakuFetchRequest) -> Flow<List<DanmakuFetchResult>>,
    private val cacheDanmakuIfNeeded: (Int, Int, List<DanmakuFetchResult>) -> Unit,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed()
) : DanmakuLoader {
    constructor(
        requestFlow: Flow<SearchDanmakuRequest?>,
        flowScope: CoroutineScope,
        danmakuRepository: DanmakuRepository,
        sharingStarted: SharingStarted = SharingStarted.WhileSubscribed()
    ) : this(
        requestFlow,
        flowScope,
        danmakuRepository::fetchFromLocal,
        danmakuRepository::fetchFromAllRemotes,
        { subjectId, episodeId, results ->
            danmakuRepository.cacheDanmakuIfNeeded(subjectId, episodeId, results)
        },
        sharingStarted,
    )

    private val saveDanmakuTasker = SingleTaskExecutor(flowScope.coroutineContext)

    override val danmakuLoadingStateFlow: MutableStateFlow<DanmakuLoadingState> =
        MutableStateFlow(DanmakuLoadingState.Idle)

    private val overrideResultsFlow = MutableStateFlow(OverrideResults.Empty)

    /**
     * [fetchResultFlow] 正在加载的剧集, 用于标记 [overrideResults] 属于哪一集.
     */
    @Volatile
    private var currentRequestKey: DanmakuRequestKey? = null

    private val originalFetchResultFlow = requestFlow.distinctUntilChanged().transformLatest { request ->
        emit(null) // 每次更换 mediaFetchSession 时 (ep 变更), 首先清空历史弹幕

        if (request == null) {
            danmakuLoadingStateFlow.value = DanmakuLoadingState.Idle
            return@transformLatest
        }
        danmakuLoadingStateFlow.value = DanmakuLoadingState.Loading
        try {
            coroutineScope {
                val fetchRequest = request.toFetchRequest()
                var localResult: List<DanmakuFetchResult>? = null
                var remoteCanReplaceLocal = false
                launch(start = CoroutineStart.ATOMIC) {
                    val result = fetchFromLocal(fetchRequest).first()
                    localResult = result
                    if (!remoteCanReplaceLocal && result.hasDisplayableDanmakus()) {
                        emit(result)
                        danmakuLoadingStateFlow.value = DanmakuLoadingState.Success
                    }
                }
                launch {
                    val result = fetchFromAllRemotes(fetchRequest).first()
                    danmakuLoadingStateFlow.value = DanmakuLoadingState.Success
                    remoteCanReplaceLocal = result.canReplaceLocalCache()
                    if (remoteCanReplaceLocal || !localResult.hasDisplayableDanmakus()) {
                        emit(result)
                    }
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
        .distinctUntilChangedBy { DanmakuRequestKey(it) }
        .flatMapLatest { req ->
            val requestKey = DanmakuRequestKey(req)
            currentRequestKey = requestKey

            combine(originalFetchResultFlow, overrideResultsFlow) { original, override ->
                // 手动匹配的结果只对执行匹配的那一集生效, 换集后自动作废.
                //
                // 这里不能直接清空 [overrideResultsFlow]: 本 flow 不只在切换剧集时执行, 每次
                // 重新订阅都会重新执行 (切换全屏、进出内嵌详情页导致 UI 短暂停止收集, 或者调整
                // 弹幕源开关/时间轴导致 DanmakuSession 重建), 清空会让手动匹配的弹幕源退回自动
                // 匹配的结果. #2801
                val overrideResults = if (override.requestKey == requestKey) override.results else emptyMap()
                val accumulatedResults = computeFinalDanmakuResult(original, overrideResults)
                if (accumulatedResults.isNotEmpty()) {
                    val subjectId = req?.subjectInfo?.subjectId
                    val episodeId = req?.episodeInfo?.episodeId
                    if (subjectId != null && episodeId != null) {
                        flowScope.launch {
                            saveDanmakuTasker.invoke {
                                delay(3.seconds)
                                cacheDanmakuIfNeeded(subjectId, episodeId, accumulatedResults)
                            }
                        }
                    }
                }
                accumulatedResults
            }
        }.shareIn(flowScope, started = sharingStarted, replay = 1)

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
        val requestKey = currentRequestKey
        overrideResultsFlow.update {
            // 上一集的覆盖不再累加, 避免换集后残留
            val existing = if (it.requestKey == requestKey) it.results else emptyMap()
            OverrideResults(requestKey, existing + (provider to result))
        }
    }

    /**
     * 手动匹配得到的弹幕结果, 以及它属于哪一集.
     */
    private class OverrideResults(
        val requestKey: DanmakuRequestKey?,
        val results: Map<DanmakuProviderId, List<DanmakuFetchResult>>,
    ) {
        companion object {
            val Empty = OverrideResults(null, emptyMap())
        }
    }

    /**
     * 标识一次弹幕加载请求对应的剧集. 同一集内 [SearchDanmakuRequest] 还会因为视频文件名、时长等
     * 变化而变化, 这些不影响手动匹配结果的有效性.
     */
    private data class DanmakuRequestKey(
        val subjectId: Int?,
        val episodeId: Int?,
    ) {
        constructor(request: SearchDanmakuRequest?) : this(
            request?.subjectInfo?.subjectId,
            request?.episodeInfo?.episodeId,
        )
    }

    private fun List<DanmakuFetchResult>?.hasDisplayableDanmakus(): Boolean {
        return this?.any { it.list.isNotEmpty() } == true
    }

    private fun List<DanmakuFetchResult>.canReplaceLocalCache(): Boolean {
        return any { result ->
            result.list.isNotEmpty() || result.matchInfo.method !is DanmakuMatchMethod.NoMatch
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

