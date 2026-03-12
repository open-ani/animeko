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
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.SelectorSearchQuery
import me.him188.ani.app.domain.mediasource.web.WebCaptchaCoordinator
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.WebCaptchaRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaSearchProbe
import me.him188.ani.app.domain.mediasource.web.WebCaptchaSolveResult
import me.him188.ani.app.domain.mediasource.web.WebPageCaptchaException
import me.him188.ani.app.domain.mediasource.web.isSearchCooldownPage
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
 * 交互式的 [SelectorMediaSourceEngine]. 用于 UI 的 "测试数据源" 功能.
 */
class SelectorMediaSourceTester(
    private val engine: SelectorMediaSourceEngine,
    private val webCaptchaCoordinator: WebCaptchaCoordinator = WebCaptchaCoordinatorHolder.noop,
    val mediaSourceId: String = "selector-test",
    flowContext: CoroutineContext = Dispatchers.Default,
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

    private val scope = CoroutineScope(flowContext) // No ExceptionHandler! You must catch all exceptions in shareIn!

    val subjectSearchLifecycle = FlowRestarter()
    val subjectSearchRunning = FlowRunning()
    val episodeSearchLifecycle = FlowRestarter()
    val episodeSearchRunning = FlowRunning()
    private val hasCaptchaSessionFlow = MutableStateFlow(false)

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
     * 用于传递给 [engine], 以便筛选条目.
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
                    subjectDetailsPageUrl to searchEpisodes(subjectDetailsPageUrl)
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
    ) { episodeListDocumentResult, query, searchConfig, episodeQuery ->
        when {
            query == null || searchConfig == null || episodeQuery == null -> {
                SelectorTestEpisodeListResult.InvalidConfig
            }

            episodeListDocumentResult == null -> {
                SelectorTestEpisodeListResult.Success(null, emptyList())
            }

            else -> {
                val (subjectUrl, documentResult) = episodeListDocumentResult
                convertEpisodeResult(
                    documentResult, searchConfig,
                    createSelectorSearchQuery(query, episodeQuery),
                    subjectUrl,
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

    suspend fun solveCaptchaInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult {
        val result = webCaptchaCoordinator.solveInteractively(request)
        logger.info(
            "SelectorMediaSourceTester[$mediaSourceId] solveCaptchaInteractively ${request.pageUrl} -> ${result::class.simpleName}"
        )
        if (result is WebCaptchaSolveResult.Solved) {
            hasCaptchaSessionFlow.value = true
        }
        return result
    }

    fun resetCaptchaSession() {
        webCaptchaCoordinator.resetSolvedSession(mediaSourceId)
        hasCaptchaSessionFlow.value = false
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

    private fun createSearchCaptchaRequest(
        pageUrl: String,
        kind: WebCaptchaKind,
        searchConfig: SelectorSearchConfig,
    ): WebCaptchaRequest {
        return WebCaptchaRequest(
            mediaSourceId = mediaSourceId,
            pageUrl = pageUrl,
            kind = kind,
            searchProbe = WebCaptchaSearchProbe(searchConfig),
        )
    }

    private suspend fun searchEpisodes(subjectDetailsPageUrl: String): Result<Document?> {
        return try {
            val resolved = webCaptchaCoordinator
                .loadPageInSolvedSession(mediaSourceId, subjectDetailsPageUrl)
                ?.let { engine.parseDocument(it.finalUrl, it.html) }
                ?: try {
                    engine.searchEpisodes(subjectDetailsPageUrl)
                } catch (e: Throwable) {
                    val captchaRequest = findCaptchaRequest(e, subjectDetailsPageUrl)
                    if (captchaRequest != null) {
                        when (webCaptchaCoordinator.tryAutoSolve(captchaRequest)) {
                            is WebCaptchaSolveResult.Solved -> {
                                hasCaptchaSessionFlow.value = true
                                webCaptchaCoordinator
                                    .loadPageInSolvedSession(mediaSourceId, subjectDetailsPageUrl)
                                    ?.let { engine.parseDocument(it.finalUrl, it.html) }
                                    ?: throw e
                            }

                            else -> throw e
                        }
                    }
                    throw e
                }
            Result.success(resolved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun searchSubject(
        url: String?,
        searchKeyword: String,
        useOnlyFirstWord: Boolean?,
        removeSpecial: Boolean?,
    ): Result<SelectorMediaSourceEngine.SearchSubjectResult>? {
        if (url.isNullOrBlank() || searchKeyword.isBlank() || useOnlyFirstWord == null || removeSpecial == null) {
            return null
        }

        val searchUrl = createSearchUrl(url, searchKeyword, useOnlyFirstWord, removeSpecial)
        return try {
            var blockedRequest: WebCaptchaRequest? = null
            val searchConfig = selectorSearchConfigFlow.value
            suspend fun loadSearchResult(): SelectorMediaSourceEngine.SearchSubjectResult {
                return webCaptchaCoordinator
                    .loadPageInSolvedSession(mediaSourceId, searchUrl)
                    ?.let { page -> engine.parseSearchResult(Url(page.finalUrl), page.html) }
                    ?: engine.searchSubjects(
                        searchUrl = url,
                        searchKeyword,
                        useOnlyFirstWord = useOnlyFirstWord,
                        removeSpecial = removeSpecial,
                    )
            }

            suspend fun loadSearchResultWithCooldownRetry(): SelectorMediaSourceEngine.SearchSubjectResult {
                var result = loadSearchResult()
                if (result.document?.isSearchCooldownPage() == true) {
                    delay((searchConfig ?: SelectorSearchConfig.Empty).requestInterval)
                    result = loadSearchResult()
                }
                return result
            }

            val initial = loadSearchResultWithCooldownRetry()
            val res = if (initial.captchaKind != null) {
                val request = createSearchCaptchaRequest(
                    initial.url.toString(),
                    initial.captchaKind,
                    searchConfig ?: SelectorSearchConfig.Empty,
                )
                blockedRequest = request
                when (
                    val autoSolveResult = webCaptchaCoordinator.tryAutoSolve(request)
                ) {
                    is WebCaptchaSolveResult.Solved -> {
                        hasCaptchaSessionFlow.value = true
                        delay((searchConfig ?: SelectorSearchConfig.Empty).requestInterval)
                        val solvedResult = loadSearchResultWithCooldownRetry()
                        if (solvedResult.captchaKind != null) initial else solvedResult
                    }

                    else -> initial
                }
            } else {
                initial
            }
            val normalized = markBlockedEmptySearchResultAsCaptcha(res, blockedRequest)
            val selectedCount = normalized.document?.let { document ->
                searchConfig?.let { engine.selectSubjects(document, it) }?.size
            }
            logger.info(
                "SelectorMediaSourceTester[$mediaSourceId] searchSubject " +
                    "url=${normalized.url} captcha=${normalized.captchaKind} " +
                    "document=${normalized.document != null} subjects=$selectedCount"
            )
            if (normalized.document != null && selectedCount == 0) {
                val strictNames = normalized.document.select("body > .box-width .search-box .thumb-content > .thumb-txt")
                val strictLinks = normalized.document.select("body > .box-width .search-box .thumb-menu > a")
                val looseNames = normalized.document.select(".search-box .thumb-content .thumb-txt")
                val looseLinks = normalized.document.select(".search-box .thumb-menu a")
                val title = normalized.document.select("title").firstOrNull()?.text()
                logger.info(
                    "SelectorMediaSourceTester[$mediaSourceId] searchSubject selectorDebug " +
                        "title=$title captcha=${normalized.captchaKind} " +
                        "strictNames=${strictNames.size} strictLinks=${strictLinks.size} " +
                        "looseNames=${looseNames.size} looseLinks=${looseLinks.size} " +
                        "firstLooseName=${looseNames.firstOrNull()?.text()} " +
                        "firstLooseLink=${looseLinks.firstOrNull()?.attr("href")}"
                )
                val html = buildString {
                    normalized.document.html(this)
                }.replace('\n', ' ')
                val htmlChunks = html.chunked(4000).take(3)
                htmlChunks.forEachIndexed { index, chunk ->
                    logger.info(
                        "SelectorMediaSourceTester[$mediaSourceId] searchSubject htmlSnippet[$index]=$chunk"
                    )
                }
            }
            Result.success(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            findSubjectCaptchaResult(e, searchUrl)?.let {
                logger.info(
                    "SelectorMediaSourceTester[$mediaSourceId] searchSubject captcha exception " +
                        "url=${it.url} kind=${it.captchaKind}"
                )
                return Result.success(it)
            }
            logger.info(
                "SelectorMediaSourceTester[$mediaSourceId] searchSubject failure " +
                    "type=${e::class.qualifiedName} message=${e.message} cause=${e.cause?.let { it::class.qualifiedName }}"
            )
            Result.failure(e)
        }
    }

    private fun markBlockedEmptySearchResultAsCaptcha(
        result: SelectorMediaSourceEngine.SearchSubjectResult,
        blockedRequest: WebCaptchaRequest?,
    ): SelectorMediaSourceEngine.SearchSubjectResult {
        val request = blockedRequest ?: return result
        if (result.captchaKind != null) {
            return result
        }
        val document = result.document ?: return result.copy(captchaKind = request.kind)
        val searchConfig = selectorSearchConfigFlow.value ?: return result
        val subjects = engine.selectSubjects(document, searchConfig) ?: return result
        if (subjects.isNotEmpty()) {
            return result
        }
        return result.copy(captchaKind = request.kind)
    }


    private fun convertEpisodeResult(
        res: Result<Document?>,
        config: SelectorSearchConfig,
        query: SelectorSearchQuery,
        subjectUrl: String,
    ): SelectorTestEpisodeListResult {
        return res.fold(
            onSuccess = { document ->
                try {
                    document ?: return SelectorTestEpisodeListResult.Success(null, emptyList())
                    val episodeList = engine.selectEpisodes(document, subjectUrl, config)
                        ?: return SelectorTestEpisodeListResult.InvalidConfig
                    SelectorTestEpisodeListResult.Success(
                        episodeList.channels,
                        episodeList.episodes
                            .fastDistinctBy { it.playUrl }
                            .map {
                                SelectorTestEpisodePresentation.compute(it, query, document, config)
                            },
                    )
                } catch (e: Throwable) {
                    findCaptchaRequest(e, subjectUrl)?.let {
                        return SelectorTestEpisodeListResult.CaptchaRequired(it)
                    }
                    SelectorTestEpisodeListResult.UnknownError(e)
                }
            },
            onFailure = { reason ->
                findCaptchaRequest(reason, subjectUrl)?.let {
                    return@fold SelectorTestEpisodeListResult.CaptchaRequired(it)
                }
                if (reason is RepositoryException) {
                    SelectorTestEpisodeListResult.ApiError(reason)
                } else {
                    SelectorTestEpisodeListResult.UnknownError(reason)
                }
            },
        )
    }

    private fun selectSubjectResult(
        res: Result<SelectorMediaSourceEngine.SearchSubjectResult>,
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
    ): SelectorTestSearchSubjectResult {
        return res.fold(
            onSuccess = { data ->
                data.captchaKind?.let {
                    return SelectorTestSearchSubjectResult.CaptchaRequired(
                        createSearchCaptchaRequest(data.url.toString(), it, searchConfig),
                    )
                }
                val document = data.document

                val originalList = if (document == null) {
                    emptyList()
                } else {
                    engine.selectSubjects(document, searchConfig).let { list ->
                        if (list == null) {
                            return SelectorTestSearchSubjectResult.InvalidConfig
                        }
                        list
                    }
                }

                SelectorTestSearchSubjectResult.Success(
                    data.url.toString(),
                    originalList.map {
                        SelectorTestSubjectPresentation.compute(
                            it,
                            query,
                            document,
                            searchConfig.filterBySubjectName,
                        )
                    },
                )
            },
            onFailure = { reason ->
                val captchaRequest = findSearchCaptchaRequest(reason, searchConfig, query)
                    ?: when (reason) {
                        is RepositoryAuthorizationException -> createSearchCaptchaRequest(
                            createSearchUrl(
                                searchConfig.searchUrl,
                                query.subjectName,
                                searchConfig.searchUseOnlyFirstWord,
                                searchConfig.searchRemoveSpecial,
                            ),
                            WebCaptchaKind.Unknown,
                            searchConfig,
                        )

                        is RepositoryRateLimitedException -> createSearchCaptchaRequest(
                            createSearchUrl(
                                searchConfig.searchUrl,
                                query.subjectName,
                                searchConfig.searchUseOnlyFirstWord,
                                searchConfig.searchRemoveSpecial,
                            ),
                            WebCaptchaKind.Unknown,
                            searchConfig,
                        )

                        else -> null
                    }
                if (captchaRequest != null) {
                    logger.info(
                        "SelectorMediaSourceTester[$mediaSourceId] selectSubjectResult captcha " +
                            "type=${reason::class.qualifiedName} request=${captchaRequest.pageUrl} kind=${captchaRequest.kind}"
                    )
                } else {
                    logger.info(
                        "SelectorMediaSourceTester[$mediaSourceId] selectSubjectResult apiError " +
                            "type=${reason::class.qualifiedName} message=${reason.message} cause=${reason.cause?.let { it::class.qualifiedName }}"
                    )
                }
                captchaRequest?.let {
                    return@fold SelectorTestSearchSubjectResult.CaptchaRequired(it)
                }
                if (reason is RepositoryException) {
                    SelectorTestSearchSubjectResult.ApiError(reason)
                } else {
                    SelectorTestSearchSubjectResult.UnknownError(reason)
                }
            },
        )
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

    private fun findCaptchaRequest(
        throwable: Throwable,
        pageUrl: String,
    ): WebCaptchaRequest? {
        val captcha = findCaptchaThrowable(throwable)
            ?: return null
        return WebCaptchaRequest(
            mediaSourceId = mediaSourceId,
            pageUrl = captcha.url.ifBlank { pageUrl },
            kind = captcha.kind,
        )
    }

    private fun findSubjectCaptchaResult(
        throwable: Throwable,
        pageUrl: String,
    ): SelectorMediaSourceEngine.SearchSubjectResult? {
        val captcha = findCaptchaThrowable(throwable) ?: return null
        return SelectorMediaSourceEngine.SearchSubjectResult(
            url = Url(captcha.url.ifBlank { pageUrl }),
            document = null,
            captchaKind = captcha.kind,
        )
    }

    private fun findSearchCaptchaRequest(
        throwable: Throwable,
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
    ): WebCaptchaRequest? {
        val pageUrl = runCatching {
            createSearchUrl(
                searchConfig.searchUrl,
                query.subjectName,
                searchConfig.searchUseOnlyFirstWord,
                searchConfig.searchRemoveSpecial,
            )
        }.getOrDefault(searchConfig.searchUrl)
        return findCaptchaRequest(throwable, pageUrl)
            ?.copy(searchProbe = WebCaptchaSearchProbe(searchConfig))
    }

    private fun findCaptchaThrowable(
        throwable: Throwable,
    ): CaptchaThrowable? {
        return generateSequence(throwable) { it.cause }
            .mapNotNull { cause ->
                when (cause) {
                    is WebPageCaptchaException -> CaptchaThrowable(
                        url = cause.url,
                        kind = cause.kind,
                    )

                    is RepositoryAuthorizationException -> CaptchaThrowable(
                        url = "",
                        kind = WebCaptchaKind.Unknown,
                    )

                    is RepositoryRateLimitedException -> CaptchaThrowable(
                        url = "",
                        kind = WebCaptchaKind.Unknown,
                    )

                    is ClientRequestException -> when (cause.response.status) {
                        HttpStatusCode.Forbidden,
                        HttpStatusCode.TooManyRequests,
                        HttpStatusCode(468, "Captcha Required"),
                        -> CaptchaThrowable(
                            url = "",
                            kind = WebCaptchaKind.Unknown,
                        )

                        else -> null
                    }

                    else -> null
                }
            }
            .firstOrNull()
    }

    private data class CaptchaThrowable(
        val url: String,
        val kind: WebCaptchaKind,
    )

    private object WebCaptchaCoordinatorHolder {
        val noop = object : WebCaptchaCoordinator {
            override suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult {
                return WebCaptchaSolveResult.Unsupported
            }

            override suspend fun solveInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult {
                return WebCaptchaSolveResult.Unsupported
            }
        }
    }

    private companion object {
        private val logger = logger<SelectorMediaSourceTester>()
    }
}
