/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import io.ktor.client.plugins.ServerResponseException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.nameCnOrName
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.toStringMultiline
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.collections.EnumMap
import me.him188.ani.utils.platform.collections.ImmutableEnumMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * [MediaFetcher], 为支持从多个 [MediaSource] 并行获取 [Media] 的综合查询工具.
 *
 * 封装了获取查询进度, 重试失败的查询, 以及合并结果等功能.
 *
 * @see MediaSourceMediaFetcher
 */
interface MediaFetcher {
    /**
     * 创建一个惰性查询会话, 从多个 [MediaSource] 并行获取 [Media].
     *
     * 查询仅在 [MediaFetchSession.cumulativeResults] 被 collect 时开始.
     *
     * @param flowContext 传递给 [MediaFetchSession] 的 flow 的 context. (用于 [Flow.flowOn].) 将会与 [MediaSourceMediaFetcher.flowContext] 叠加.
     * @see MediaFetchRequest.Companion.create
     */
    fun newSession(
        request: MediaFetchRequest,
        flowContext: CoroutineContext = EmptyCoroutineContext
    ): MediaFetchSession {
        return newSession(flowOf(request), flowContext)
    }

    /**
     * 创建一个惰性查询会话, 从多个 [MediaSource] 并行获取 [Media].
     *
     * 查询仅在 [MediaFetchSession.cumulativeResults] 被 collect 时开始.
     *
     * @param requestLazy 用于动态请求的 [MediaFetchRequest] 流. 只有第一个元素会被使用.
     *
     * @param flowContext 传递给 [MediaFetchSession] 的 flow 的 context. (用于 [Flow.flowOn].) 将会与 [MediaSourceMediaFetcher.flowContext] 叠加.
     *
     * @see MediaFetchRequest.Companion.create
     */
    fun newSession(
        requestLazy: Flow<MediaFetchRequest>,
        flowContext: CoroutineContext = EmptyCoroutineContext,
    ): MediaFetchSession
}

/**
 * 根据 [SubjectInfo] 和 [EpisodeInfo] 创建一个 [MediaFetchRequest].
 * @see createFlow
 */
fun MediaFetchRequest.Companion.create(
    subject: SubjectInfo,
    episode: EpisodeInfo,
): MediaFetchRequest {
    return MediaFetchRequest(
        subjectId = subject.subjectId.toString(),
        episodeId = episode.episodeId.toString(),
        subjectNameCN = subject.nameCnOrName,
        subjectNames = subject.allNames.toSet(),
        episodeSort = episode.sort,
        episodeName = episode.displayName,
        episodeEp = episode.ep,
    )
}

/**
 * 根据 [SubjectInfo] 和 [EpisodeInfo] 创建一个 [MediaFetchRequest].
 * @see create
 */
fun MediaFetchRequest.Companion.createFlow(
    info: Flow<Pair<SubjectInfo, EpisodeInfo>>,
): Flow<MediaFetchRequest> = info.map { (subject, episode) ->
    MediaFetchRequest.create(subject = subject, episode = episode)
}

class MediaFetcherConfig { // 战未来
    companion object {
        val Default = MediaFetcherConfig()
    }
}

/**
 * 一个 [MediaFetcher] 的实现, 从多个 [MediaSource] 并行[查询][MediaSource.fetch].
 *
 * @param configProvider 配置每一个 [MediaFetchSession]. 会在创建 [MediaFetchSession] 时调用.
 * @param flowContext 传递给 [MediaFetchSession] 的 flow 的 context. (用于 [Flow.flowOn].)
 */
class MediaSourceMediaFetcher(
    private val configProvider: () -> MediaFetcherConfig,
    private val mediaSources: List<MediaSourceInstance>,
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : MediaFetcher {
    private inner class MediaSourceResultImpl(
        override val mediaSourceId: String,
        override val sourceInfo: MediaSourceInfo,
        override val kind: MediaSourceKind,
        private val config: MediaFetcherConfig,
        val disabled: Boolean,
        pagedSources: Flow<SizedSource<MediaMatch>>,
        flowContext: CoroutineContext,
    ) : MediaSourceFetchResult, SynchronizedObject() {
        /**
         * 为了确保线程安全, 对 [state] 的写入必须谨慎.
         *
         * [state] 只能在 [results] 的 flow 里, 或者在 [restart] 中修改.
         */
        override val state: MutableStateFlow<MediaSourceFetchState> =
            MutableStateFlow(if (disabled) MediaSourceFetchState.Disabled else MediaSourceFetchState.Idle)
        private val restartCount = MutableStateFlow(0) // 只能在 [restart] 内修改

        override val results by lazy {
            restartCount.flatMapLatest { restartCount ->

                state.value.let { currentState ->
                    // 注意, 读 state 可能是线程不安全的, 因此检查一次 coroutine cancellation.
                    // 因为 [results] flow 被 share, 只有可能有一个协程在执行本代码, 相当于拥有 mutual exclusion.
                    // [state] 还能在 [restart] 中修改, 但 [restart] 在修改 [state] 之后一定会操作 [restartCount], 
                    // 导致本 flow 被 cancel ([flatMapLatest]).
                    currentCoroutineContext().ensureActive()

                    // 此时的 currentState 是可信的

                    if (restartCount == 0 && currentState is MediaSourceFetchState.Disabled)
                        return@flatMapLatest flowOf(emptyList()) // 禁用的数据源, 第一次查询给空列表, 必须要 restart 才能发起查询

                    val lastRestartCount = when (currentState) {
                        is MediaSourceFetchState.PendingSuccess -> currentState.id
                        is MediaSourceFetchState.Completed -> currentState.id
                        else -> -1
                    }
                    if (lastRestartCount == restartCount) {
                        // 这个 [restartCount] 已经跑过一次了, 不要重复跑
                        return@flatMapLatest emptyFlow() // 返回空 flow, 复用 replayCache
                    }
                }

                pagedSources
                    .onStart {
                        state.value = MediaSourceFetchState.Working
                    }
                    .flatMapMerge { sources ->
                        sources.results.map { it.media }
                    }
                    .catch { exception ->
                        state.value = MediaSourceFetchState.Failed(exception, restartCount)
                        when (exception) {
                            is ServerResponseException -> {
                                logger.error { "Failed to fetch media from $mediaSourceId due to ${exception.response.status}" }
                            }

                            is IOException -> {
                                logger.warn { "Failed to fetch media from $mediaSourceId due to network error" }
                            }

                            is CancellationException -> {
                                logger.error { "Failed to fetch media from $mediaSourceId due to CancellationException" }
                            }

                            else -> {
                                logger.error(exception) { "Failed to fetch media from $mediaSourceId due to upstream error" }
                            }
                        }
                    }
                    .onCompletion { exception ->
                        if (exception == null) {
                            // catch might have already updated the state
                            if (state.value !is MediaSourceFetchState.Completed) {
                                state.value = MediaSourceFetchState.PendingSuccess(restartCount)
                                // 不能直接诶设置为 Succeed, 必须等待 `shareIn` 完成缓存 (replayCache)
                            }
                        } else {
                            val currentState = state.value
                            if (currentState !is MediaSourceFetchState.Failed) {
                                // downstream (collector) failure
                                state.value = MediaSourceFetchState.Abandoned(exception, restartCount)
                                if (exception !is CancellationException) {
                                    logger.error(exception) { "Failed to fetch media from $mediaSourceId due to downstream error" }
                                }
                            }
                            // upstream failure re-caught here
                        }
                    }
                    .runningFold(emptyList<Media>()) { acc, list ->
                        acc + list
                    }
                    .map { list ->
                        list.distinctBy { it.mediaId }
                    }
            }.shareIn(
                CoroutineScope(flowContext), replay = 1, started = SharingStarted.WhileSubscribed(),
            ).transform {
                // 必须在 shareIn 更新好 replay 之后再标记为 success, 否则 awaitCompletion 不工作
                // (因为 WhileSubscribed 的 stopTimeoutMillis 为 0)
                finishPending()
                emit(it)
            }.onCompletion {
                if (it == null)
                    logger.error { "results is completed normally, however it shouldn't" }
            }
        }

        private fun finishPending() {
            val currentState = state.value
            if (currentState is MediaSourceFetchState.PendingSuccess) {
                state.compareAndSet(currentState, MediaSourceFetchState.Succeed(currentState.id))
                // Ok if we lost race - someone else must set state to Idle from [restart]
            }
        }

        override fun restart() {
            // 不允许同时调用 restart
            synchronized(this) {
                when (val value = state.value) {
                    is MediaSourceFetchState.Completed,
                    MediaSourceFetchState.Disabled -> {
                        restartCount.value += 1
                        // 必须使用 CAS
                        // 如果 [results] 现在有人 collect, [state] 可能会被变更. 
                        // 我们只需要在它没有被其他人修改的时候, 把它修改为 [Idle].
                        state.compareAndSet(value, MediaSourceFetchState.Idle)
                        // Ok if we lost race - the [results] must be running
                    }

                    MediaSourceFetchState.Idle -> {}
                    MediaSourceFetchState.Working -> {}
                }
            }
        }

        override fun enable() {
            if (state.value == MediaSourceFetchState.Disabled) {
                if (restartCount.compareAndSet(0, 1)) { // 非 0 表示有人已经 [restart] 过了
                    state.compareAndSet(state.value, MediaSourceFetchState.Idle)
                }
            }
        }
    }

    private inner class MediaFetchSessionImpl(
        request: Flow<MediaFetchRequest>,
        private val config: MediaFetcherConfig,
        private val flowContext: CoroutineContext,
    ) : MediaFetchSession {
        override val request: Flow<MediaFetchRequest> =
            request.take(1) // 只采用第一个, as per described in [fetch]
                .shareIn(CoroutineScope(flowContext), started = SharingStarted.Lazily, replay = 1) // only 
                .take(1) // 只采用 replayCache, 让后面 flow 能完结

        override val mediaSourceResults: List<MediaSourceFetchResult> = mediaSources.map { instance ->
            MediaSourceResultImpl(
                mediaSourceId = instance.source.mediaSourceId,
                sourceInfo = instance.source.info,
                kind = instance.source.kind,
                config = config,
                disabled = !instance.isEnabled,
                pagedSources = this.request
                    .onEach {
                        logger.info { "MediaFetchSessionImpl pagedSources creating, request: \n${it.toStringMultiline()}" }
                    }
                    .map {
                        instance.source.fetch(it)
                    },
                flowContext = flowContext,
            )
        }

        override val cumulativeResults: Flow<List<Media>> = kotlin.run {
            if (mediaSourceResults.isEmpty()) {
                return@run flowOfEmptyList()
            }
            combine(mediaSourceResults.map { it.results }) { lists ->
                lists.asSequence().flatten().toList()
            }.map { list ->
                list.distinctBy { it.mediaId } // distinct globally by id, just to be safe
            }.flowOn(flowContext)
                .run {
                    if (currentAniBuildConfig.isDebug && ENABLE_WATCHDOG) {
                        flow {
                            cancellableCoroutineScope {
                                val watchdog = launch {
                                    while (true) {
                                        delay(2000)
                                        logger.info {
                                            val states = mediaSourceResults.map { it.state.value }
                                            "cumulativeResults is still being collected, states=$states"
                                        }
                                    }
                                }
                                collect { emit(it) }
                                watchdog.cancel()
                            }
                        }
                    } else {
                        this
                    }
                }.onCompletion {
                    if (it == null)
                        logger.error { "cumulativeResults is completed normally, however it shouldn't" }
                }
        }

        override val hasCompleted = if (mediaSourceResults.isEmpty()) {
            flowOf(CompletedConditions.AllCompleted)
        } else {
            combine(mediaSourceResults.map { it.state }) {
                val pairs = mediaSourceResults.groupBy { it.kind }.mapValues { results ->
                    val states = results.value.map { it.state }
                    when {
                        // 该类型数据源全部禁用时返回 null，如果返回 false 会导致 awaitCompletion 无法结束
                        states.all { it.value is MediaSourceFetchState.Disabled } -> null
                        states.all { it.value is MediaSourceFetchState.Completed || it.value is MediaSourceFetchState.Disabled } -> true
                        else -> false
                    }
                }
                CompletedConditions(
                    ImmutableEnumMap<MediaSourceKind, _> { kind ->
                        pairs[kind]
                    },
                )
            }.flowOn(flowContext)
        }
    }

    override fun newSession(
        requestLazy: Flow<MediaFetchRequest>,
        flowContext: CoroutineContext
    ): MediaFetchSession {
        return MediaFetchSessionImpl(requestLazy, configProvider(), this.flowContext + flowContext)
    }

    private companion object {
        private val logger = logger<MediaSourceMediaFetcher>()
        private const val ENABLE_WATCHDOG = false
    }
}

class CompletedConditions(
    private val values: EnumMap<MediaSourceKind, Boolean?>
) {
    fun allCompleted() = values.values.all { it ?: true }

    operator fun get(kind: MediaSourceKind): Boolean? = try {
        values[kind]
    } catch (e: NoSuchElementException) {
        null
    }

    companion object {
        val AllCompleted = CompletedConditions(
            ImmutableEnumMap { true },
        )
    }
}
