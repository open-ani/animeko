/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test.jsonapi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.MediaListFilterContext
import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiEpisodeInfo
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceEngine
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiPreparedRequest
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiResponse
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiSearchConfig
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiSubjectInfo
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiVariables
import me.him188.ani.app.domain.mediasource.test.TestEpisodeListResult
import me.him188.ani.app.domain.mediasource.test.TestEpisodePresentation
import me.him188.ani.app.domain.mediasource.test.TestSubjectPresentation
import me.him188.ani.app.domain.mediasource.test.TestSubjectSearchResult
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.FlowRunning
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.coroutines.runCatchingCancellable
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

/**
 * JSON API 数据源的交互式测试器, 用于编辑器的 "测试数据源".
 *
 * 三个阶段 (搜索、剧集列表、播放) 各自只在准备好的请求变化时重新发送; 只改解析相关的配置时用缓存的响应重新解析.
 * 请求通过 [JsonApiMediaSourceEngine] 发送, 与正式搜索同一条链路, 但不做节流.
 */
class JsonApiMediaSourceTester(
    private val engine: JsonApiMediaSourceEngine,
    flowContext: CoroutineContext = Dispatchers.Default,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
) {
    data class SubjectQuery(
        val searchKeyword: String,
        val searchUseOnlyFirstWord: Boolean?,
        val searchRemoveSpecial: Boolean?,
    )

    data class EpisodeQuery(
        val sort: EpisodeSort,
    )

    /**
     * 一次请求与它的原始响应. 404 时 [response] 为 `null`.
     */
    class Exchange(
        val request: JsonApiPreparedRequest,
        val response: JsonApiResponse?,
    )

    /**
     * 一个阶段准备好的请求. [C] 是构造请求所用的输入 (如选中的条目), 随响应一起保留, 解析时用它而不用当时的最新输入,
     * 这样输入变化、新请求还在进行时, 不会把旧响应解析成新输入的结果.
     */
    private sealed class Prepared<out C> {
        data object Invalid : Prepared<Nothing>()

        /**
         * 本阶段不发请求: 视频地址来自剧集列表.
         */
        data object NotNeeded : Prepared<Nothing>()
        class Ready<C>(val request: JsonApiPreparedRequest, val context: C) : Prepared<C>()
    }

    private sealed class Fetched<out C> {
        data object InvalidRequest : Fetched<Nothing>()
        data object NotNeeded : Fetched<Nothing>()
        class Done<C>(val request: JsonApiPreparedRequest, val context: C, val result: Result<Exchange>) : Fetched<C>()
    }

    /**
     * 对应当前准备的请求的响应; 还没有或还是上一个请求的时为 `null`.
     */
    private fun <C> Fetched<C>?.matching(prepared: Prepared.Ready<C>): Fetched.Done<C>? {
        val done = this as? Fetched.Done<C> ?: return null
        return done.takeIf { it.request == prepared.request && it.context == prepared.context }
    }

    private val scope = CoroutineScope(flowContext) // No ExceptionHandler! You must catch all exceptions in shareIn!

    val subjectSearchLifecycle = FlowRestarter()
    val subjectSearchRunning = FlowRunning()
    val episodeSearchLifecycle = FlowRestarter()
    val episodeSearchRunning = FlowRunning()
    val playLifecycle = FlowRestarter()
    val playRunning = FlowRunning()

    private val configFlow = MutableStateFlow<JsonApiSearchConfig?>(null)
    private val subjectQueryFlow = MutableStateFlow<SubjectQuery?>(null)
    private val episodeQueryFlow = MutableStateFlow<EpisodeQuery?>(null)
    private val selectedSubjectIndexFlow = MutableStateFlow(0)
    private val playEpisodeFlow = MutableStateFlow<JsonApiTestEpisode?>(null)

    private val filterContextFlow = combine(subjectQueryFlow, episodeQueryFlow) { query, episodeQuery ->
        if (query == null || episodeQuery == null) return@combine null
        MediaListFilterContext(
            subjectNames = setOf(query.searchKeyword),
            episodeSort = episodeQuery.sort,
            episodeEp = null,
            episodeName = null,
        )
    }

    // region 搜索

    private val searchRequestFlow = combine(configFlow, subjectQueryFlow) { config, query ->
        if (config == null || query == null || query.searchKeyword.isBlank() ||
            query.searchUseOnlyFirstWord == null || query.searchRemoveSpecial == null
        ) return@combine null
        val keyword = MediaSourceEngineHelpers.getSearchKeyword(
            query.searchKeyword, query.searchRemoveSpecial, query.searchUseOnlyFirstWord,
        )
        engine.prepareRequest(config, config.search.request, JsonApiVariables.search(keyword, config.search))
            ?.let { Prepared.Ready(it, Unit) } ?: Prepared.Invalid
    }.distinctUntilChanged { old, new -> sameRequest(old, new) }

    private val subjectFetchFlow = searchRequestFlow
        .mapLatest { prepared -> fetch("search", prepared, subjectSearchRunning) }
        .restartable(subjectSearchLifecycle)
        .shareIn(scope, sharingStarted, replay = 1)

    private class SubjectStage(
        val result: TestSubjectSearchResult,
        val subjects: List<JsonApiSubjectInfo>,
    )

    private val subjectStageFlow = combine(
        subjectFetchFlow, searchRequestFlow, configFlow, filterContextFlow,
    ) { fetched, prepared, config, filter ->
        if (prepared == null) return@combine null
        if (prepared is Prepared.Invalid || config == null || filter == null) {
            return@combine SubjectStage(TestSubjectSearchResult.InvalidConfig, emptyList())
        }
        val done = fetched.matching(prepared as Prepared.Ready) ?: return@combine null
        done.result.fold(
            onSuccess = { exchange ->
                val response = exchange.response
                    ?: return@fold SubjectStage(TestSubjectSearchResult.Success(exchange.request.url, emptyList()), emptyList())
                val json = response.json
                    ?: return@fold SubjectStage(TestSubjectSearchResult.UnknownError(notJson(response)), emptyList())
                val subjects = engine.parseSubjects(json, config.search)
                    ?: return@fold SubjectStage(TestSubjectSearchResult.InvalidConfig, emptyList())
                SubjectStage(
                    TestSubjectSearchResult.Success(
                        exchange.request.url,
                        subjects.map { TestSubjectPresentation.compute(it, filter) },
                    ),
                    subjects,
                )
            },
            onFailure = { SubjectStage(it.toSubjectResult(), emptyList()) },
        )
    }.shareIn(scope, sharingStarted, replay = 1)

    /**
     * 解析好的搜索结果. `null` 表示还没有查询.
     */
    val subjectSelectionResultFlow: Flow<TestSubjectSearchResult?> = subjectStageFlow
        .map { it?.result }
        .distinctUntilChanged()

    private val selectedSubjectFlow: Flow<JsonApiSubjectInfo?> = combine(subjectStageFlow, selectedSubjectIndexFlow) { stage, index ->
        stage?.subjects?.getOrNull(index)
    }.distinctUntilChanged()

    // endregion

    // region 剧集列表

    private val subjectRequestFlow = combine(configFlow, selectedSubjectFlow) { config, subject ->
        if (config == null || subject == null) return@combine null
        engine.prepareRequest(config, config.subject.request, JsonApiVariables.subject(subject))
            ?.let { Prepared.Ready(it, subject) } ?: Prepared.Invalid
    }.distinctUntilChanged { old, new -> sameRequest(old, new) }

    private val episodeFetchFlow = subjectRequestFlow
        .mapLatest { prepared -> fetch("subject", prepared, episodeSearchRunning) }
        .restartable(episodeSearchLifecycle)
        .shareIn(scope, sharingStarted, replay = 1)

    private class EpisodeStage(
        val result: TestEpisodeListResult,
        val episodes: List<JsonApiTestEpisode>,
    )

    private val episodeStageFlow = combine(
        episodeFetchFlow, subjectRequestFlow, configFlow, filterContextFlow,
    ) { fetched, prepared, config, filter ->
        if (prepared == null) return@combine null
        if (prepared is Prepared.Invalid || config == null || filter == null) {
            return@combine EpisodeStage(TestEpisodeListResult.InvalidConfig, emptyList())
        }
        val done = fetched.matching(prepared as Prepared.Ready) ?: return@combine null
        val subject = done.context
        done.result.fold(
            onSuccess = { exchange ->
                val response = exchange.response
                    ?: return@fold EpisodeStage(TestEpisodeListResult.Success(null, emptyList()), emptyList())
                val json = response.json
                    ?: return@fold EpisodeStage(TestEpisodeListResult.UnknownError(notJson(response)), emptyList())
                val channels = engine.parseChannels(json, config.subject, subject)
                    ?: return@fold EpisodeStage(TestEpisodeListResult.InvalidConfig, emptyList())
                val episodes = channels.flatMap { it.episodes }.map { info ->
                    JsonApiTestEpisode(TestEpisodePresentation.compute(info, filter, config), subject, info)
                }
                EpisodeStage(
                    TestEpisodeListResult.Success(
                        channels = channels.mapNotNull { it.name }.takeIf { it.isNotEmpty() },
                        episodes = episodes.map { it.presentation },
                    ),
                    episodes,
                )
            },
            onFailure = { EpisodeStage(it.toEpisodeResult(), emptyList()) },
        )
    }.shareIn(scope, sharingStarted, replay = 1)

    /**
     * 选中条目的剧集列表. `null` 表示没有选中条目或还没有结果.
     */
    val episodeListSelectionResultFlow: Flow<TestEpisodeListResult?> = episodeStageFlow
        .map { it?.result }
        .distinctUntilChanged()

    /**
     * 与 [episodeListSelectionResultFlow] 成功结果一一对应的剧集, 附带发播放请求所需的信息.
     */
    val testEpisodesFlow: Flow<List<JsonApiTestEpisode>> = episodeStageFlow
        .map { it?.episodes.orEmpty() }

    // endregion

    // region 播放

    private val playRequestFlow = combine(configFlow, playEpisodeFlow) { config, episode ->
        if (config == null || episode == null) return@combine null
        // 与正式播放一致: 列表里有这一集的地址就不发播放请求; 没有地址也没有播放请求时无请求可发
        if (config.episodeListHasVideoUrl && (episode.episode.videoUrl != null || !config.play.isValid())) {
            return@combine Prepared.NotNeeded
        }
        engine.prepareRequest(config, config.play.request, episode.playVariables)
            ?.let { Prepared.Ready(it, episode) } ?: Prepared.Invalid
    }.distinctUntilChanged { old, new -> sameRequest(old, new) }

    private val playFetchFlow = playRequestFlow
        .mapLatest { prepared -> fetch("play", prepared, playRunning) }
        .restartable(playLifecycle)
        .shareIn(scope, sharingStarted, replay = 1)

    /**
     * 当前查看的剧集的播放测试结果. `null` 表示没有在查看剧集或还没有结果.
     */
    val playResultFlow: Flow<JsonApiPlayTestResult?> = combine(
        playFetchFlow, playRequestFlow, configFlow, playEpisodeFlow,
    ) { fetched, prepared, config, episode ->
        if (prepared == null) return@combine null
        if (prepared is Prepared.NotNeeded) return@combine JsonApiPlayTestResult.EpisodeListVideo(episode?.episode?.videoUrl)
        if (prepared is Prepared.Invalid || config == null) return@combine JsonApiPlayTestResult.InvalidConfig
        val done = fetched.matching(prepared as Prepared.Ready) ?: return@combine null
        done.result.fold(
            onSuccess = { exchange ->
                val video = exchange.response?.json?.let { engine.parseVideo(it, config.play, done.context.channelMatch) }
                JsonApiPlayTestResult.Success(exchange.request, exchange.response, video)
            },
            onFailure = { e ->
                if (e is RepositoryException) JsonApiPlayTestResult.ApiError(e) else JsonApiPlayTestResult.UnknownError(e)
            },
        )
    }.shareIn(scope, sharingStarted, replay = 1)
        .distinctUntilChanged()

    // endregion

    // region setters

    fun setSearchConfig(config: JsonApiSearchConfig?) {
        configFlow.value = config
    }

    fun setSubjectQuery(query: SubjectQuery) {
        subjectQueryFlow.value = query
    }

    fun clearSubjectQuery() {
        subjectQueryFlow.value = null
    }

    fun setEpisodeQuery(query: EpisodeQuery) {
        episodeQueryFlow.value = query
    }

    fun setSubjectIndex(index: Int) {
        selectedSubjectIndexFlow.value = index
    }

    /**
     * 查看一集的播放结果. `null` 停止播放阶段的测试.
     */
    fun setPlayEpisode(episode: JsonApiTestEpisode?) {
        playEpisodeFlow.value = episode
    }

    // endregion

    private suspend fun <C> fetch(stage: String, prepared: Prepared<C>?, running: FlowRunning): Fetched<C>? = when (prepared) {
        null -> null
        Prepared.Invalid -> Fetched.InvalidRequest
        Prepared.NotNeeded -> Fetched.NotNeeded
        is Prepared.Ready -> running.withRunning {
            Fetched.Done(
                prepared.request,
                prepared.context,
                runCatchingCancellable {
                    val response = engine.execute(prepared.request)
                    logger.info { "JsonApiMediaSourceTester $stage ${prepared.request.method} ${prepared.request.url} -> ${response?.status}" }
                    Exchange(prepared.request, response)
                }.onFailure { e ->
                    logger.info { "JsonApiMediaSourceTester $stage ${prepared.request.url} failed: ${e::class.simpleName}: ${e.message}" }
                },
            )
        }
    }

    private fun notJson(response: JsonApiResponse) =
        IllegalStateException("Response is not JSON (status ${response.status}): ${response.body.take(200)}")

    private fun Throwable.toSubjectResult(): TestSubjectSearchResult =
        if (this is RepositoryException) TestSubjectSearchResult.ApiError(this) else TestSubjectSearchResult.UnknownError(this)

    private fun Throwable.toEpisodeResult(): TestEpisodeListResult =
        if (this is RepositoryException) TestEpisodeListResult.ApiError(this) else TestEpisodeListResult.UnknownError(this)

    private companion object {
        private val logger = logger<JsonApiMediaSourceTester>()

        /**
         * 两次准备的请求是否相同. [Prepared.Ready] 按请求内容与输入比较, 这样只改解析配置不会重新发请求.
         */
        fun sameRequest(old: Prepared<*>?, new: Prepared<*>?): Boolean = when {
            old is Prepared.Ready && new is Prepared.Ready -> old.request == new.request && old.context == new.context
            else -> old == new
        }
    }
}
