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
import kotlinx.coroutines.Dispatchers
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
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

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

    private val arguments =
        config.deserializeArgumentsOrNull(ArgumentType.serializer())
            ?: SelectorMediaSourceArguments.Default
    private val searchConfig = arguments.searchConfig

    private val engine by lazy { EngineType(client) }

    override val location: MediaSourceLocation get() = MediaSourceLocation.Online

    class Factory(
        val repository: SelectorMediaSourceEpisodeCacheRepository,
        val sessionManager: WebSessionManager,
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
            SelectorMediaSource(mediaSourceId, config, repository, client = client, sessionManager = sessionManager)
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
    ): List<DefaultMedia>? {
        val caches = try {
            repository.getCache(mediaSourceId, query.subjectName)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }

        return buildList {
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
            }
        }.takeIf(List<DefaultMedia>::isNotEmpty)
    }

    // all-in-one search
    private suspend fun EngineType.search(
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
        mediaSourceId: String,
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

        // 播放 session 内缓存: 上一次真实搜索已把条目页面的全部剧集写入缓存 (addCache).
        // 若缓存的剧集列表包含当前请求的剧集 (典型场景: 切集), 直接从缓存构建结果, 不发起任何网络请求.
        // 缓存的有效期由播放页控制: 进入/退出播放页与手动重新查询时清空全部缓存.
        searchFromCacheOrNull(searchConfig, query, mediaSourceId)?.let { return@withContext it }

        delayUntilNextAllowedSearch()

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
            ?: return@withContext emptyList()

        val subjects = originalSubjects.let { originalList ->
            val filters = searchConfig.createFiltersForSubject()
            with(query.toFilterContext()) {
                originalList.filter {
                    filters.applyOn(it.asCandidate())
                }
            }
        }

        buildList {
            for (subjectInfo in subjects) {
                val episodes = try {
                    fetchPageOrThrow(
                        subjectInfo.fullUrl,
                        PageExpectation.SubjectDetails(searchConfig, subjectInfo.fullUrl),
                    )?.episodes
                } catch (e: BlockedException) {
                    throw e
                } catch (e: Exception) {
                    // 单个条目页的网络错误不终止整个搜索
                    logger.warn(e) { "SelectorMediaSource '$mediaSourceId': failed to load subject page ${subjectInfo.fullUrl}" }
                    null
                } ?: continue
                repository.addCache(
                    mediaSourceId, query.subjectName, subjectInfo, episodes,
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
