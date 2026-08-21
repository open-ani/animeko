/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */


package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.request.get
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.models.fold
import me.him188.ani.app.data.models.runApiRequest
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers
import me.him188.ani.app.domain.mediasource.codec.DefaultMediaSourceCodec
import me.him188.ani.app.domain.mediasource.codec.DontForgetToRegisterCodec
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherContext
import me.him188.ani.datasources.api.matcher.WebVideoMatcherProvider
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.paging.map
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Suppress("unused") // bug
private typealias ArgumentType = SelectorMediaSourceArguments
private typealias EngineType = DefaultSelectorMediaSourceEngine

/**
 * [SelectorMediaSource] 的用户侧配置, 用于创建 [SelectorMediaSource] 实例.
 *
 * @since 3.10
 * @see SelectorMediaSourceCodec
 */
@OptIn(DontForgetToRegisterCodec::class)
@Serializable
data class SelectorMediaSourceArguments(
    override val name: String,
    val description: String,
    val iconUrl: String,
    val searchConfig: SelectorSearchConfig = SelectorSearchConfig.Empty,
    override val tier: MediaSourceTier = MediaSourceTier.Fallback,
    /**
     * Channel 级别的 tier 覆盖, key 为 channel 名称 (与页面解析出的 channel 名一致).
     * @since 4.9
     */
    override val channelTiers: Map<String, MediaSourceTier> = emptyMap(),
) : MediaSourceArguments {
    companion object {
        val Default = SelectorMediaSourceArguments(
            name = "Selector",
            description = "",
            iconUrl = "",
            searchConfig = SelectorSearchConfig.Empty,
        )
    }
}

object SelectorMediaSourceCodec : DefaultMediaSourceCodec<SelectorMediaSourceArguments>(
    SelectorMediaSource.FactoryId,
    SelectorMediaSourceArguments::class,
    currentVersion = 2,
    SelectorMediaSourceArguments.serializer(),
)

/**
 * @since 3.10
 */
class SelectorMediaSource(
    override val mediaSourceId: String,
    config: MediaSourceConfig,
    val repository: SelectorMediaSourceEpisodeCacheRepository,
    override val kind: MediaSourceKind = MediaSourceKind.WEB,
    private val client: ScopedHttpClient,
    private val sessionManager: WebSessionManager,
    coroutineScope: CoroutineScope,
) : HttpMediaSource(), WebVideoMatcherProvider {
    companion object {
        val FactoryId = FactoryId("web-selector")

        private val REGEX_OVA_TAILING = Regex(".+OVA\\s*\\d*$", RegexOption.IGNORE_CASE)

        /**
         * 按 cookie 名称合并多组 cookies: 后面列表中的同名 cookie 覆盖前面的, 顺序为名称首次出现的顺序.
         *
         * 这是 [matcher] 向 WebView 注入 cookies 时的合并语义.
         * 公开给外部工具 (如 `tools/datasource-test-mcp`) 复用, 以保证与 App 行为一致.
         */
        fun mergeCookies(vararg cookieLists: List<String>): List<String> {
            val merged = linkedMapOf<String, String>()
            for (cookie in cookieLists.asSequence().flatten()) {
                val trimmed = cookie.trim()
                if (trimmed.isBlank()) continue
                val name = trimmed.substringBefore("=").trim()
                if (name.isBlank()) continue
                merged[name] = trimmed
            }
            return merged.values.toList()
        }
    }

    /**
     * 真实搜索任务运行的作用域. 生命周期为整个 media source 实例, 而不是单次 fetch:
     * 后台刷新缓存的搜索不随播放页退出而取消, 结果会写入缓存供下次使用.
     * 实例被替换 (配置变更) 或关闭时随 [close] 取消.
     *
     * 使用 SupervisorJob (见 [childScope]), 单个搜索失败不会影响其他搜索.
     */
    private val backgroundSearchScope = coroutineScope.childScope()

    /**
     * 进行中的真实搜索任务, 按查询参数去重: 相同查询的并发调用 (前台缓存未命中与后台刷新) 共享同一个任务.
     *
     * 搜索结果与 [SelectorSearchQuery] 相关 (查询名决定搜索 URL, 剧集信息决定过滤结果),
     * 因此不能只按 subjectId 去重, 否则一个查询会错误地拿到另一个查询的结果.
     */
    private val searchJobsLock = ReentrantLock()
    private val searchJobs = mutableMapOf<SearchJobKey, Deferred<List<DefaultMedia>>>()

    private data class SearchJobKey(val subjectId: Int?, val query: SelectorSearchQuery)

    init {
        addCloseable(AutoCloseable { backgroundSearchScope.cancel() })
    }

    private val arguments =
        config.deserializeArgumentsOrNull(ArgumentType.serializer())
            ?: SelectorMediaSourceArguments.Default
    private val searchConfig = arguments.searchConfig

    private val engine by lazy { EngineType(client) }

    override val location: MediaSourceLocation get() = MediaSourceLocation.Online

    class Factory(
        val repository: SelectorMediaSourceEpisodeCacheRepository,
        val sessionManager: WebSessionManager,
        private val coroutineScope: CoroutineScope,
    ) : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId

        override val info: MediaSourceInfo = MediaSourceInfo(
            displayName = "Selector",
            description = "通用 CSS Selector 数据源",
            iconUrl = "",
        )

        override val allowMultipleInstances: Boolean get() = true
        override fun create(
            mediaSourceId: String,
            config: MediaSourceConfig,
            client: ScopedHttpClient
        ): MediaSource =
            SelectorMediaSource(
                mediaSourceId,
                config,
                repository,
                client = client,
                sessionManager = sessionManager,
                coroutineScope = coroutineScope,
            )
    }

    override suspend fun checkConnection(): ConnectionStatus {
        return kotlin.runCatching {
            runApiRequest {
                client.use {
                    get(searchConfig.searchUrl) // 提交一个请求, 只要它不是因为网络错误就行
                }
            }.fold(
                onSuccess = { ConnectionStatus.SUCCESS },
                onKnownFailure = {
                    when (it) {
                        ApiFailure.NetworkError -> ConnectionStatus.FAILED
                        ApiFailure.ServiceUnavailable -> ConnectionStatus.FAILED
                        ApiFailure.Unauthorized -> ConnectionStatus.SUCCESS
                    }
                },
            )
        }.recover {
            // 只要不是网络错误就行
            ConnectionStatus.SUCCESS
        }.getOrThrow()
    }

    override val info: MediaSourceInfo = MediaSourceInfo(
        displayName = arguments.name,
        description = arguments.description,
        websiteUrl = searchConfig.searchUrl,
        iconUrl = arguments.iconUrl,
        tier = arguments.tier,
    )

    @OptIn(ExperimentalAtomicApi::class)
    private val lastSearchTime = AtomicLong(0L)

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun delayUntilNextAllowedSearch() {
        val interval = searchConfig.requestInterval.inWholeMilliseconds
        while (true) {
            val now = currentTimeMillis()
            val last = lastSearchTime.load()
            val wait = (last + interval) - now
            if (wait > 0) {
                delay(wait)
                continue
            }
            // try to claim the slot
            if (lastSearchTime.compareAndSet(last, now)) return
            // someone else just took it – retry
        }
    }

    /**
     * 获取页面并把被挡状态转换为异常:
     *
     * - 解析成功 → 返回值;
     * - 合法无结果 / 404 → `null`;
     * - [BlockReason.RateLimited] → 延迟后重试一次, 仍失败则抛 [BlockedException];
     * - [BlockReason.Captcha] → 尝试自动解决 (v1: solver 列表为空, 立即失败) → 抛 [BlockedException];
     * - 其他被挡 → 抛 [BlockedException].
     */
    private suspend fun <T> fetchPageOrThrow(
        url: String,
        expectation: PageExpectation<T>,
    ): T? {
        suspend fun once(): PageVerdict<T> = sessionManager.fetchPage(url, expectation)

        var verdict = once()
        ((verdict as? PageVerdict.Blocked)?.reason as? BlockReason.RateLimited)?.let { rateLimited ->
            // 限流不是验证码: 不弹浏览器, 延迟后重试一次
            delay(rateLimited.retryAfter ?: searchConfig.requestInterval)
            delayUntilNextAllowedSearch()
            verdict = once()
        }

        when (val v = verdict) {
            is PageVerdict.Ok -> return v.value
            is PageVerdict.EmptyContent -> return null
            is PageVerdict.Blocked -> {
                val reason = v.reason
                val request = SolveRequest(
                    mediaSourceId = mediaSourceId,
                    pageUrl = url,
                    kind = (reason as? BlockReason.Captcha)?.kind ?: WebCaptchaKind.Unknown,
                    expectation = expectation,
                )
                when (reason) {
                    BlockReason.NotFound -> return null

                    is BlockReason.Captcha -> {
                        if (sessionManager.solve(request, interactive = false) == SolveOutcome.Solved) {
                            delayUntilNextAllowedSearch()
                            when (val retried = once()) {
                                is PageVerdict.Ok -> return retried.value
                                is PageVerdict.EmptyContent -> return null
                                is PageVerdict.Blocked -> throw BlockedException(retried.reason, request)
                            }
                        }
                        throw BlockedException(reason, request)
                    }

                    else -> throw BlockedException(reason, request)
                }
            }
        }
    }

    /**
     * 尝试从 [repository] 缓存中构建搜索结果.
     *
     * 仅当缓存的条目页面剧集列表中能找到 [query] 请求的剧集时才命中, 否则返回 `null` 走完整搜索
     * (页面可能已更新, 例如刚开播的新集在缓存里还没有).
     */
    private suspend fun EngineType.searchFromCacheOrNull(
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
        mediaSourceId: String,
        subjectId: Int?,
    ): SearchCacheResult? {
        val caches = try {
            repository.getCache(subjectId, mediaSourceId, query.subjectName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 读缓存失败不影响功能, 退化为真实搜索. 但要记录, 否则缓存彻底失效时没有任何线索.
            logger.warn(e) { "SelectorMediaSource '$mediaSourceId': failed to read search cache, falling back to search" }
            return null
        }

        var minDurationToExpired = Long.MAX_VALUE
        val resultList = buildList {
            for (cache in caches) {
                val episodes = cache.webEpisodeInfos
                if (episodes.findMatchingEpisodeOrNull(query.episodeSort, query.episodeEp, query.episodeName) == null) {
                    continue
                }
                addAll(
                    selectMedia(
                        episodes.asSequence(),
                        searchConfig,
                        query,
                        mediaSourceId,
                        subjectName = cache.webSubjectInfo.name,
                    ).filteredList,
                )
                minDurationToExpired = min(minDurationToExpired, cache.minDurationMillisToExpired)
            }
        }.takeIf(List<DefaultMedia>::isNotEmpty) ?: return null

        return SearchCacheResult(resultList, minDurationToExpired.milliseconds)
    }

    /**
     * 启动 (或加入进行中的) 真实搜索任务.
     *
     * 任务在 [backgroundSearchScope] 中运行, 因此调用方被取消 (例如离开播放页) 不会中断搜索,
     * 结果仍会写入缓存. 任务失败的异常只会在 [Deferred.await] 时抛出.
     */
    private fun EngineType.startSearchFromSources(
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
        mediaSourceId: String,
        subjectId: Int?,
    ): Deferred<List<DefaultMedia>> {
        val key = SearchJobKey(subjectId, query)
        return searchJobsLock.withLock {
            searchJobs[key]?.takeIf { it.isActive }
                ?: backgroundSearchScope.async {
                    delayUntilNextAllowedSearch()
                    searchFromSources(searchConfig, query, mediaSourceId, subjectId)
                }.also { job ->
                    // 先放进 map 再注册回调: 回调可能在注册时同步触发 (任务已完成),
                    // ReentrantLock 保证此时在锁内重入删除也是安全的.
                    searchJobs[key] = job
                    job.invokeOnCompletion {
                        searchJobsLock.withLock {
                            if (searchJobs[key] === job) searchJobs.remove(key)
                        }
                    }
                }
        }
    }

    private suspend fun EngineType.searchFromSources(
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
        mediaSourceId: String,
        subjectId: Int?,
    ): List<DefaultMedia> {
        val searchUrl = searchConfig.searchUrl.replace(
            "{keyword}",
            MediaSourceEngineHelpers.encodeUrlSegment(
                MediaSourceEngineHelpers.getSearchKeyword(
                    query.subjectName,
                    searchConfig.searchRemoveSpecial,
                    searchConfig.searchUseOnlyFirstWord,
                ),
            ),
        )

        val originalSubjects = fetchPageOrThrow(searchUrl, PageExpectation.SearchResults(searchConfig))
            ?: return emptyList()

        val subjects = originalSubjects.let { originalList ->
            val filters = searchConfig.createFiltersForSubject()
            with(query.toFilterContext()) {
                originalList.filter {
                    filters.applyOn(it.asCandidate())
                }
            }
        }

        return buildList {
            for (subjectInfo in subjects) {
                val episodes = try {
                    fetchPageOrThrow(
                        subjectInfo.fullUrl,
                        PageExpectation.SubjectDetails(searchConfig, subjectInfo.fullUrl),
                    )?.episodes
                } catch (e: BlockedException) {
                    throw e
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 单个条目页的网络错误不终止整个搜索
                    logger.warn(e) { "SelectorMediaSource '$mediaSourceId': failed to load subject page ${subjectInfo.fullUrl}" }
                    null
                } ?: continue
                repository.addCache(
                    subjectId, mediaSourceId, query.subjectName, subjectInfo, episodes,
                    sourceCacheTtl = searchConfig.searchCacheTtl,
                )
                addAll(
                    selectMedia(
                        episodes.asSequence(),
                        searchConfig,
                        query,
                        mediaSourceId,
                        subjectName = subjectInfo.name,
                    ).filteredList,
                )
            }
        }
    }

    // all-in-one search
    private suspend fun EngineType.search(
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
        mediaSourceId: String,
        subjectId: Int?,
    ): List<DefaultMedia> = withContext(Dispatchers.Default) {
        val currentPlayerNames = when (currentPlatform()) {
            // 桌面端已迁移至 mpv, 但许多现有订阅仍声明 "vlc", 暂时保持兼容
            is Platform.Desktop -> listOf("mpv", "vlc")
            is Platform.Android -> listOf("exoplayer")
            Platform.Ios -> listOf("avkit")
        }
        if (
            searchConfig.onlySupportsPlayers.isNotEmpty()
            && currentPlayerNames.none { it in searchConfig.onlySupportsPlayers }
        ) {
            logger.warn {
                val supports =
                    searchConfig.onlySupportsPlayers.joinToString(prefix = "[", postfix = "]")

                "SelectorMediaSource '${info.displayName}' is not supported by the platform player. " +
                        "Declared supported players: $supports, " +
                        "current players: $currentPlayerNames"
            }
            return@withContext emptyList()
        }

        // 搜索缓存: 上一次真实搜索已把条目页面的全部剧集写入缓存 (addCache).
        // 若缓存的剧集列表包含当前请求的剧集 (典型场景: 切集), 直接从缓存构建结果, 不发起任何网络请求.
        // 缓存按 TTL 过期, 也会在该条目手动重新查询时被清除.
        val caches = searchFromCacheOrNull(searchConfig, query, mediaSourceId, subjectId)

        if (caches != null) {
            if (repository.shouldRefreshCache(caches.durationToExpired, searchConfig.searchCacheTtl)) {
                // 缓存即将过期: 本次仍然返回缓存结果, 同时在后台重新搜索以刷新缓存,
                // 使连续观看时的每次切集都能命中缓存. 不 await, 失败只记录日志,
                // 下次命中缓存时会再次尝试; 真正过期后由前台完整搜索兜底.
                startSearchFromSources(searchConfig, query, mediaSourceId, subjectId)
                    .invokeOnCompletion { e ->
                        if (e != null && e !is CancellationException) {
                            logger.warn(e) {
                                "SelectorMediaSource '$mediaSourceId': background cache refresh failed for '${query.subjectName}'"
                            }
                        }
                    }
            }
            caches.mediaList
        } else {
            startSearchFromSources(searchConfig, query, mediaSourceId, subjectId).await()
        }
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        val allSubjectNames = query.subjectNames.toSet()

        return query.subjectNames
            .take(searchConfig.searchUseSubjectNamesCount.coerceAtLeast(1))
            .map { name ->
                SinglePagePagedSource {
                    engine.search(
                        searchConfig,
                        SelectorSearchQuery(
                            subjectName = name,
                            // Web 源的 OVA 通常和正篇在一个页面, 修改请求 epSort 为 OVA 可以搜高 OVA 条目.
                            episodeSort = if (name.matches(REGEX_OVA_TAILING)) EpisodeSort("OVA") else
                                query.episodeSort,
                            allSubjectNames = allSubjectNames,
                            episodeEp = query.episodeEp,
                            episodeName = query.episodeName,
                        ),
                        mediaSourceId,
                        query.subjectId.toIntOrNull(),
                    ).asFlow()
                }.map {
                    MediaMatch(it, MatchKind.FUZZY)
                }
            }.flattenConcat(searchConfig.requestInterval)
    }

    override val matcher: WebVideoMatcher by lazy {
        object : WebVideoMatcher {
            override fun match(
                url: String,
                context: WebVideoMatcherContext
            ): WebVideoMatcher.MatchResult = engine.matchWebVideo(url, arguments.searchConfig.matchVideo)

            override fun patchConfig(config: WebViewConfig): WebViewConfig {
                val configuredCookies = arguments.searchConfig.matchVideo.cookies
                    .lines()
                    .filter { it.isNotBlank() }
                // HTTP、播放器、两个平台共用同一份 cookie 真相
                val captchaCookies = sessionManager.cookieJar.getCookieHeaderValues(searchConfig.searchUrl)
                return config.copy(
                    cookies = mergeCookies(
                        config.cookies,
                        configuredCookies,
                        captchaCookies,
                    ),
                )
            }
        }
    }

    private class SearchCacheResult(
        val mediaList: List<DefaultMedia>,
        val durationToExpired: Duration,
    )
}

/**
 * Concat multiple [SizedSource]s into one.
 *
 * [Results][SizedSource.results] are be concated in the [Flow.flattenConcat] flavor.
 */
private fun <T> Iterable<SizedSource<T>>.flattenConcat(delayInBetween: Duration): SizedSource<T> {
    return object : SizedSource<T> {
        override val results: Flow<T> = flow {
            val flows = this@flattenConcat.map { it.results }
            flows.forEachIndexed { index, flow ->
                emitAll(flow)
                if (index != flows.lastIndex) {
                    delay(delayInBetween)
                }
            }
        }
        override val finished: Flow<Boolean> = combine(this@flattenConcat.map { it.finished }) { values ->
            values.all { it }
        }

        override val totalSize: Flow<Int?> = combine(this@flattenConcat.map { it.totalSize }) { values ->
            if (values.any { it == null }) {
                return@combine null
            }
            @Suppress("UNCHECKED_CAST")
            (values as Array<Int>).sum()
        }
    }
}
