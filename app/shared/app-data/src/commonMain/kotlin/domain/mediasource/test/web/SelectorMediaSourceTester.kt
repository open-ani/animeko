/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test.web

import androidx.compose.ui.util.fastDistinctBy
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers
import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.PageVerdict
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.SelectorSearchQuery
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.normalizedSessionHost
import me.him188.ani.app.domain.mediasource.web.selectEpisodesImpl
import me.him188.ani.app.domain.mediasource.web.selectSubjectsForCaptchaProbe
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.FlowRunning
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.xml.Document
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * 交互式的数据源测试器. 用于 UI 的 "测试数据源" 功能.
 *
 * 页面获取走 [WebSessionManager.fetchPage] (与正式搜索同一条链路, 同一个 `PageEvaluator` 判决);
 * 获取到的原始页面缓存在 flow 中, 修改 selector 配置只会重新解析, 不会重新发请求.
 */
class SelectorMediaSourceTester(
    private val sessionManager: WebSessionManager,
    val mediaSourceId: String = "selector-test",
    flowContext: CoroutineContext = kotlinx.coroutines.Dispatchers.Default,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
) {
    // must be data class
    data class SubjectQuery(
        val searchKeyword: String,
        val searchUrl: String?,
        val searchUseOnlyFirstWord: Boolean?,
        val searchRemoveSpecial: Boolean?,
    )

    data class EpisodeQuery(
        val sort: EpisodeSort,
    )

    /**
     * 一次页面获取的原始结果: 请求的 URL + 判决 (Ok/EmptyContent 携带 document, 供改配置后重新解析).
     */
    class FetchedPage(
        val url: String,
        val verdict: PageVerdict<*>,
    )

    private val scope = CoroutineScope(flowContext) // No ExceptionHandler! You must catch all exceptions in shareIn!

    val subjectSearchLifecycle = FlowRestarter()
    val subjectSearchRunning = FlowRunning()
    val episodeSearchLifecycle = FlowRestarter()
    val episodeSearchRunning = FlowRunning()
    private val hasCaptchaSessionFlow = MutableStateFlow(false)

    private val fetchedHostsLock = SynchronizedObject()
    private val fetchedUrls = mutableSetOf<String>()

    /**
     * 将会影响两个筛选. 不会直接触发搜索. 如果变更导致 subject 的搜索结果变化, 可能会触发 episode list 搜索.
     */
    private val selectorSearchConfigFlow = MutableStateFlow<SelectorSearchConfig?>(null)
    private val subjectQueryFlow = MutableStateFlow<SubjectQuery?>(null)
    private val episodeQueryFlow = MutableStateFlow<EpisodeQuery?>(null)
    private val selectedSubjectIndexFlow = MutableStateFlow(0)

    /**
     * 用于查询条目列表, 每当编辑请求和 `searchUrl`, 会重新搜索, 但不会筛选.
     * 筛选在 [subjectSelectionResultFlow].
     */
    private val subjectSearchResultFlow = subjectQueryFlow
        .mapLatest { query ->
            if (query == null) {
                return@mapLatest null
            }

            subjectSearchRunning.withRunning {
                searchSubject(
                    query.searchUrl,
                    query.searchKeyword,
                    query.searchUseOnlyFirstWord,
                    query.searchRemoveSpecial,
                )
            }
        }
        .restartable(subjectSearchLifecycle)
        .shareIn(scope, sharingStarted, replay = 1)
        .distinctUntilChanged()

    /**
     * 用于筛选条目.
     * @see subjectSelectionResultFlow
     */
    private val selectorSearchQueryFlow =
        combine(subjectQueryFlow, episodeQueryFlow) { query, episodeQuery ->
            if (query == null || episodeQuery == null) return@combine null
            createSelectorSearchQuery(query, episodeQuery)
        }.distinctUntilChanged() // required, 否则在修改无关配置时也会触发重新搜索

    /**
     * 解析好的搜索结果.
     */
    val subjectSelectionResultFlow = combine(
        subjectSearchResultFlow,
        selectorSearchConfigFlow,
        selectorSearchQueryFlow,
    ) { apiResponse, searchConfig, query ->
        if (apiResponse == null) return@combine null
        if (searchConfig == null || query == null) return@combine SelectorTestSearchSubjectResult.InvalidConfig

        selectSubjectResult(apiResponse, searchConfig, query)
    }
        .shareIn(scope, sharingStarted, replay = 1)
        .distinctUntilChanged()

    /**
     * 用户选择的条目.
     */
    private val selectedSubjectFlow = subjectSelectionResultFlow
        .combine(selectedSubjectIndexFlow) { result, index ->
            if (result == null) return@combine null

            (result as? SelectorTestSearchSubjectResult.Success)?.subjects?.getOrNull(index)
        } // not shared
        .distinctUntilChanged() // required, 否则在修改无关配置时也会触发重新搜索

    /**
     * 用于查询条目的剧集列表, 每当选择新的条目时, 会重新搜索. 但不会筛选. 筛选在 [episodeListSelectionResultFlow].
     */
    private val episodeListSearchResultFlow = selectedSubjectFlow
        .mapLatest {
            it?.subjectDetailsPageUrl
        }
        .distinctUntilChanged()
        .mapLatest { subjectDetailsPageUrl ->
            if (subjectDetailsPageUrl == null) {
                null
            } else {
                episodeSearchRunning.withRunning {
                    searchEpisodes(subjectDetailsPageUrl)
                }
            }
        }.restartable(episodeSearchLifecycle)
        .shareIn(scope, sharingStarted, replay = 1)
        .distinctUntilChanged()

    /**
     * 解析好的剧集列表.
     */
    val episodeListSelectionResultFlow = combine(
        episodeListSearchResultFlow, subjectQueryFlow, selectorSearchConfigFlow, episodeQueryFlow,
    ) { episodeListResult, query, searchConfig, episodeQuery ->
        when {
            query == null || searchConfig == null || episodeQuery == null -> {
                SelectorTestEpisodeListResult.InvalidConfig
            }

            episodeListResult == null -> {
                SelectorTestEpisodeListResult.Success(null, emptyList())
            }

            else -> {
                convertEpisodeResult(
                    episodeListResult, searchConfig,
                    createSelectorSearchQuery(query, episodeQuery),
                )
            }
        }
    }
        .shareIn(scope, sharingStarted, replay = 1)
        .distinctUntilChanged()

    // region setters

    fun setSelectorSearchConfig(config: SelectorSearchConfig?) {
        selectorSearchConfigFlow.value = config
    }

    fun setSubjectQuery(query: SubjectQuery) {
        subjectQueryFlow.value = query
    }

    fun setEpisodeQuery(query: EpisodeQuery) {
        episodeQueryFlow.value = query
    }

    fun clearSubjectQuery() {
        subjectQueryFlow.value = null
    }

    fun setSubjectIndex(index: Int) {
        selectedSubjectIndexFlow.value = index
    }

    suspend fun solveCaptchaInteractively(request: SolveRequest): SolveOutcome {
        val result = sessionManager.solve(request, interactive = true)
        logger.info {
            "SelectorMediaSourceTester[$mediaSourceId] solveCaptchaInteractively ${request.pageUrl} -> $result"
        }
        if (result == SolveOutcome.Solved) {
            hasCaptchaSessionFlow.value = true
        }
        return result
    }

    /**
     * 丢弃本次测试涉及的所有 host 的暖会话与 cookie.
     */
    fun resetCaptchaSession() {
        val urls = synchronized(fetchedHostsLock) { fetchedUrls.toList().also { fetchedUrls.clear() } }
        hasCaptchaSessionFlow.value = false
        scope.launch {
            for (url in urls) {
                normalizedSessionHost(url)?.let { sessionManager.invalidate(it) }
            }
        }
    }

    val hasCaptchaSession = hasCaptchaSessionFlow
        .shareIn(scope, sharingStarted, replay = 1)

    // endregion

    private fun createSelectorSearchQuery(
        query: SubjectQuery,
        episodeQuery: EpisodeQuery,
    ) = SelectorSearchQuery(
        subjectName = query.searchKeyword,
        episodeSort = episodeQuery.sort,
        allSubjectNames = setOf(query.searchKeyword),
        episodeName = null,
        episodeEp = null,
    )

    /**
     * 与正式搜索一致的获取逻辑: 限流时按 `requestInterval` 重试一次; 验证码先尝试自动解决 (v1 恒失败).
     */
    private suspend fun <T> fetchWithRetries(
        url: String,
        expectation: PageExpectation<T>,
    ): PageVerdict<T> {
        synchronized(fetchedHostsLock) { fetchedUrls.add(url) }
        var verdict = sessionManager.fetchPage(url, expectation)
        ((verdict as? PageVerdict.Blocked)?.reason as? BlockReason.RateLimited)?.let { rateLimited ->
            delay(rateLimited.retryAfter ?: (selectorSearchConfigFlow.value ?: SelectorSearchConfig.Empty).requestInterval)
            verdict = sessionManager.fetchPage(url, expectation)
        }
        ((verdict as? PageVerdict.Blocked)?.reason as? BlockReason.Captcha)?.let { captcha ->
            val request = SolveRequest(
                mediaSourceId = mediaSourceId,
                pageUrl = url,
                kind = captcha.kind,
                expectation = expectation,
            )
            if (sessionManager.solve(request, interactive = false) == SolveOutcome.Solved) {
                hasCaptchaSessionFlow.value = true
                verdict = sessionManager.fetchPage(url, expectation)
            }
        }
        return verdict
    }

    private suspend fun searchSubject(
        url: String?,
        searchKeyword: String,
        useOnlyFirstWord: Boolean?,
        removeSpecial: Boolean?,
    ): Result<FetchedPage>? {
        if (url.isNullOrBlank() || searchKeyword.isBlank() || useOnlyFirstWord == null || removeSpecial == null) {
            return null
        }

        val searchUrl = createSearchUrl(url, searchKeyword, useOnlyFirstWord, removeSpecial)
        val searchConfig = selectorSearchConfigFlow.value ?: SelectorSearchConfig.Empty
        return try {
            val verdict = fetchWithRetries(searchUrl, PageExpectation.SearchResults(searchConfig))
            logger.info {
                "SelectorMediaSourceTester[$mediaSourceId] searchSubject url=$searchUrl verdict=${verdict::class.simpleName}"
            }
            Result.success(FetchedPage(searchUrl, verdict))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.info {
                "SelectorMediaSourceTester[$mediaSourceId] searchSubject failure " +
                        "type=${e::class.qualifiedName} message=${e.message} cause=${e.cause?.let { it::class.qualifiedName }}"
            }
            Result.failure(e)
        }
    }

    private suspend fun searchEpisodes(subjectDetailsPageUrl: String): Result<FetchedPage> {
        val searchConfig = selectorSearchConfigFlow.value ?: SelectorSearchConfig.Empty
        return try {
            val verdict = fetchWithRetries(
                subjectDetailsPageUrl,
                PageExpectation.SubjectDetails(searchConfig, subjectDetailsPageUrl),
            )
            Result.success(FetchedPage(subjectDetailsPageUrl, verdict))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun selectSubjectResult(
        res: Result<FetchedPage>,
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
    ): SelectorTestSearchSubjectResult {
        return res.fold(
            onSuccess = { fetched ->
                val document = fetched.verdict.documentOrNull()
                when (val verdict = fetched.verdict) {
                    is PageVerdict.Ok<*>, is PageVerdict.EmptyContent -> {
                        // 用当前配置重新解析缓存的 document, 编辑 selector 时无需重新抓取
                        val subjects = document?.let { selectSubjectsForCaptchaProbe(it, searchConfig) }
                        if (document != null && subjects == null) {
                            return SelectorTestSearchSubjectResult.InvalidConfig
                        }
                        SelectorTestSearchSubjectResult.Success(
                            fetched.url,
                            subjects.orEmpty().map {
                                SelectorTestSubjectPresentation.compute(
                                    it,
                                    query,
                                    document,
                                    searchConfig.filterBySubjectName,
                                )
                            },
                        )
                    }

                    is PageVerdict.Blocked -> verdict.reason.toSubjectResult(fetched.url, searchConfig)
                }
            },
            onFailure = { reason ->
                if (reason is RepositoryException) {
                    SelectorTestSearchSubjectResult.ApiError(reason)
                } else {
                    SelectorTestSearchSubjectResult.UnknownError(reason)
                }
            },
        )
    }

    private fun BlockReason.toSubjectResult(
        pageUrl: String,
        searchConfig: SelectorSearchConfig,
    ): SelectorTestSearchSubjectResult = when (this) {
        is BlockReason.Captcha -> SelectorTestSearchSubjectResult.CaptchaRequired(
            SolveRequest(
                mediaSourceId = mediaSourceId,
                pageUrl = pageUrl,
                kind = kind,
                expectation = PageExpectation.SearchResults(searchConfig),
            ),
        )

        // 限流不是验证码: 如实显示为 API 错误, 不误导用户去解验证码
        is BlockReason.RateLimited -> SelectorTestSearchSubjectResult.ApiError(RepositoryRateLimitedException())
        BlockReason.NotFound -> SelectorTestSearchSubjectResult.Success(pageUrl, emptyList())
        is BlockReason.Forbidden -> SelectorTestSearchSubjectResult.ApiError(RepositoryAuthorizationException())
    }

    private fun convertEpisodeResult(
        res: Result<FetchedPage>,
        config: SelectorSearchConfig,
        query: SelectorSearchQuery,
    ): SelectorTestEpisodeListResult {
        return res.fold(
            onSuccess = { fetched ->
                val document = fetched.verdict.documentOrNull()
                when (val verdict = fetched.verdict) {
                    is PageVerdict.Ok<*>, is PageVerdict.EmptyContent -> {
                        document ?: return SelectorTestEpisodeListResult.Success(null, emptyList())
                        val episodeList = runCatching {
                            selectEpisodesImpl(document, fetched.url, config)
                        }.getOrNull() ?: return SelectorTestEpisodeListResult.InvalidConfig
                        SelectorTestEpisodeListResult.Success(
                            episodeList.channels,
                            episodeList.episodes
                                .fastDistinctBy { it.playUrl }
                                .map {
                                    SelectorTestEpisodePresentation.compute(it, query, document, config)
                                },
                        )
                    }

                    is PageVerdict.Blocked -> when (val reason = verdict.reason) {
                        is BlockReason.Captcha -> SelectorTestEpisodeListResult.CaptchaRequired(
                            SolveRequest(
                                mediaSourceId = mediaSourceId,
                                pageUrl = fetched.url,
                                kind = reason.kind,
                                expectation = PageExpectation.SubjectDetails(config, fetched.url),
                            ),
                        )

                        is BlockReason.RateLimited ->
                            SelectorTestEpisodeListResult.ApiError(RepositoryRateLimitedException())

                        BlockReason.NotFound -> SelectorTestEpisodeListResult.Success(null, emptyList())
                        is BlockReason.Forbidden ->
                            SelectorTestEpisodeListResult.ApiError(RepositoryAuthorizationException())
                    }
                }
            },
            onFailure = { reason ->
                if (reason is RepositoryException) {
                    SelectorTestEpisodeListResult.ApiError(reason)
                } else {
                    SelectorTestEpisodeListResult.UnknownError(reason)
                }
            },
        )
    }

    private fun PageVerdict<*>.documentOrNull(): Document? = when (this) {
        is PageVerdict.Ok<*> -> document
        is PageVerdict.EmptyContent -> document
        is PageVerdict.Blocked -> null
    }

    private fun createSearchUrl(
        searchUrl: String,
        subjectName: String,
        useOnlyFirstWord: Boolean,
        removeSpecial: Boolean,
    ): String {
        val encodedUrl = MediaSourceEngineHelpers.encodeUrlSegment(
            MediaSourceEngineHelpers.getSearchKeyword(
                subjectName,
                removeSpecial,
                useOnlyFirstWord,
            ),
        )
        return searchUrl.replace("{keyword}", encodedUrl)
    }

    private companion object {
        private val logger = logger<SelectorMediaSourceTester>()
    }
}
