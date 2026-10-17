/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.models.fold
import me.him188.ani.app.data.models.runApiRequest
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.mediasource.MediaListFilter
import me.him188.ani.app.domain.mediasource.MediaListFilterContext
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers
import me.him188.ani.app.domain.mediasource.asCandidate
import me.him188.ani.app.domain.mediasource.codec.DefaultMediaSourceCodec
import me.him188.ani.app.domain.mediasource.codec.DontForgetToRegisterCodec
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.app.domain.mediasource.keywordSearchChain
import me.him188.ani.app.domain.mediasource.latestAiredEpisode
import me.him188.ani.app.domain.mediasource.rememberedKeywords
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.app.domain.mediasource.web.findMatchingEpisodeOrNull
import me.him188.ani.app.domain.mediasource.web.guessSubtitleLanguageIds
import me.him188.ani.app.domain.mediasource.web.matchingEpisodeSort
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.matcher.ApiVideoResolver
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.paging.emptySizedSource
import me.him188.ani.datasources.api.paging.map
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
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
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.titles.LabelFirstRawTitleParser
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [JsonApiMediaSource] 的用户侧配置.
 *
 * @see JsonApiMediaSourceCodec
 */
@OptIn(DontForgetToRegisterCodec::class)
@Serializable
data class JsonApiMediaSourceArguments(
    override val name: String,
    val description: String = "",
    val iconUrl: String = "",
    val searchConfig: JsonApiSearchConfig = JsonApiSearchConfig.Default,
    override val tier: MediaSourceTier = MediaSourceTier.Fallback,
    /**
     * 线路级阶级, 键为线路名称 ([JsonApiDeclaredChannel.name] 或分组线路的名称).
     */
    override val channelTiers: Map<String, MediaSourceTier> = emptyMap(),
) : MediaSourceArguments {
    companion object {
        val Default = JsonApiMediaSourceArguments(
            name = "JSON API",
            description = "",
            iconUrl = "",
        )
    }
}

object JsonApiMediaSourceCodec : DefaultMediaSourceCodec<JsonApiMediaSourceArguments>(
    JsonApiMediaSource.FactoryId,
    JsonApiMediaSourceArguments::class,
    currentVersion = 1,
    JsonApiMediaSourceArguments.serializer(),
)

/**
 * 通用 JSON API 数据源: 站点通过 HTTP 接口提供搜索、剧集与播放地址, 配置见 [JsonApiSearchConfig].
 *
 * 浏览与自动匹配的结构与 Selector 数据源相同 (关键词链、搜索缓存、按集筛选), 区别在于页面是 JSON 而不是 HTML,
 * 以及视频地址在播放前由 [resolveApiVideo] 向站点重新获取 (签发请求, 或重取剧集列表), 而不是用 WebView 加载页面.
 */
class JsonApiMediaSource(
    override val mediaSourceId: String,
    config: MediaSourceConfig,
    private val repository: SelectorMediaSourceEpisodeCacheRepository,
    private val client: ScopedHttpClient,
    private val engine: JsonApiMediaSourceEngine = JsonApiMediaSourceEngine(client),
    private val getTimeMillis: () -> Long = { currentTimeMillis() },
) : HttpMediaSource(), ApiVideoResolver {
    companion object {
        val FactoryId = FactoryId("json-api")

        /**
         * 播放时发出的请求 (签发请求或剧集列表请求) 的响应在这段时间内复用:
         * 签发响应里已经含所有线路的候选, 剧集列表含条目的全部剧集, 换线路、换集不必再请求, 也不会因为连续请求被限流.
         */
        val PLAY_RESPONSE_TTL: Duration = 60.seconds

        private const val NO_REQUEST_YET = Long.MIN_VALUE

        private const val PLAY_RESPONSE_CACHE_SIZE = 8
    }

    class Factory(
        private val repository: SelectorMediaSourceEpisodeCacheRepository,
    ) : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId

        override val info: MediaSourceInfo = MediaSourceInfo(
            displayName = "JSON API",
            description = "通用 JSON 接口数据源",
            iconUrl = "",
        )

        override val allowMultipleInstances: Boolean get() = true

        override fun create(
            mediaSourceId: String,
            config: MediaSourceConfig,
            client: ScopedHttpClient,
        ): MediaSource = JsonApiMediaSource(mediaSourceId, config, repository, client)
    }

    private val arguments = config.deserializeArgumentsOrNull(JsonApiMediaSourceArguments.serializer())
        ?: JsonApiMediaSourceArguments.Default
    val searchConfig: JsonApiSearchConfig get() = arguments.searchConfig

    override val kind: MediaSourceKind get() = MediaSourceKind.WEB
    override val location: MediaSourceLocation get() = MediaSourceLocation.Online

    override val info: MediaSourceInfo = MediaSourceInfo(
        displayName = arguments.name,
        description = arguments.description,
        websiteUrl = searchConfig.websiteUrl,
        iconUrl = arguments.iconUrl,
        tier = arguments.tier,
    )

    override suspend fun checkConnection(): ConnectionStatus {
        return kotlin.runCatching {
            runApiRequest {
                client.use {
                    get(searchConfig.baseUrl) // 提交一个请求, 只要它不是因为网络错误就行
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

    // region 请求

    /** 上一个请求发出的时刻; [NO_REQUEST_YET] 表示还没发过. */
    @OptIn(ExperimentalAtomicApi::class)
    private val lastRequestTime = AtomicLong(NO_REQUEST_YET)

    /**
     * 任意两个请求之间至少间隔 [JsonApiSearchConfig.requestInterval]. 搜索、剧集、播放各阶段共用.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun delayUntilNextAllowedRequest() {
        val interval = searchConfig.requestInterval.inWholeMilliseconds
        while (true) {
            val now = getTimeMillis()
            val last = lastRequestTime.load()
            val wait = if (last == NO_REQUEST_YET) 0 else (last + interval) - now
            if (wait > 0) {
                delay(wait)
                continue
            }
            if (lastRequestTime.compareAndSet(last, now)) return
        }
    }

    /**
     * 代入变量并发送一个阶段的请求. 限流时等一个间隔后重试一次.
     *
     * @return 配置无效或站点返回 404 时为 `null`.
     */
    private suspend fun request(
        stage: String,
        request: JsonApiRequestConfig,
        variables: Map<String, String>,
    ): JsonApiResponse? {
        val prepared = engine.prepareRequest(searchConfig, request, variables) ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': invalid $stage request config, url=${request.url}" }
            return null
        }
        return execute(stage, prepared)
    }

    /**
     * 发送准备好的请求, 遵守请求间隔. 限流时等一个间隔后重试一次.
     */
    private suspend fun execute(stage: String, prepared: JsonApiPreparedRequest): JsonApiResponse? {
        delayUntilNextAllowedRequest()
        return try {
            engine.execute(prepared)
        } catch (e: RepositoryRateLimitedException) {
            logger.info { "JsonApiMediaSource '$mediaSourceId': rate limited on $stage request, retrying once" }
            delay(searchConfig.requestInterval)
            delayUntilNextAllowedRequest()
            engine.execute(prepared)
        }
    }

    private suspend fun searchSubjectsOnline(keyword: String): List<JsonApiSubjectInfo> {
        val stage = searchConfig.search
        val response = request(
            "search", stage.request,
            JsonApiVariables.search(keyword, stage),
        ) ?: return emptyList()
        val json = response.json ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': search response is not JSON: $response" }
            return emptyList()
        }
        return engine.parseSubjects(json, stage) ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': invalid search stage config" }
            emptyList()
        }
    }

    private suspend fun loadChannels(subject: JsonApiSubjectInfo): List<JsonApiChannelInfo> {
        val stage = searchConfig.subject
        val response = request(
            "subject", stage.request,
            JsonApiVariables.subject(subject),
        ) ?: return emptyList()
        val json = response.json ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': subject response is not JSON: $response" }
            return emptyList()
        }
        return engine.parseChannels(json, stage, subject) ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': invalid subject stage config" }
            emptyList()
        }
    }

    // endregion

    // region 浏览

    override val supportsBrowsing: Boolean get() = true

    override suspend fun searchSubjects(keyword: String): List<BrowseSubject> =
        searchSubjectsOnline(keyword).map { BrowseSubject(name = it.name, url = it.url) }

    override suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> {
        val info = subjectInfoFromUrl(subject) ?: run {
            logger.warn {
                "JsonApiMediaSource '$mediaSourceId': subject url '${subject.url}' does not match template " +
                        "'${searchConfig.search.subjectUrlTemplate}', cannot browse"
            }
            return emptyList()
        }
        return loadChannels(info)
            .filter { it.episodes.isNotEmpty() }
            .map { channel ->
                BrowseChannel(
                    name = channel.name,
                    label = channel.name,
                    episodes = channel.episodes.map { BrowseEpisode(name = it.name, url = it.url, episodeSort = it.episodeSortOrEp) },
                )
            }
    }

    override fun createMedia(
        subject: BrowseSubject,
        channelName: String?,
        episode: BrowseEpisode,
        episodeSort: EpisodeSort?,
    ): Media {
        val subjectInfo = subjectInfoFromUrl(subject)
            ?: throw IllegalArgumentException(
                "Subject url '${subject.url}' does not match template '${searchConfig.search.subjectUrlTemplate}'",
            )
        val episodeId = JsonApiTemplate.extract(searchConfig.subject.episodes.episodeUrlTemplate, episode.url)?.get("id")
            ?: throw IllegalArgumentException(
                "Episode url '${episode.url}' does not match template '${searchConfig.subject.episodes.episodeUrlTemplate}'",
            )
        return createMedia(
            subjectInfo,
            JsonApiEpisodeInfo(
                channel = channelName,
                channelMatch = channelName?.let { channelMatchFor(it) },
                name = episode.name,
                episodeSortOrEp = episode.episodeSort,
                id = episodeId,
                url = episode.url,
            ),
            episodeSort,
        )
    }

    /**
     * 从条目地址反解出站点 id. 地址与当前模板对不上 (例如模板改过) 时为 `null`.
     */
    private fun subjectInfoFromUrl(subject: BrowseSubject): JsonApiSubjectInfo? {
        val id = JsonApiTemplate.extract(searchConfig.search.subjectUrlTemplate, subject.url)?.get("id") ?: return null
        return JsonApiSubjectInfo(id = id, name = subject.name, url = subject.url)
    }

    /**
     * 线路名称对应的播放匹配值: 声明的线路用声明的值, 其余 (分组线路) 名称就是匹配值.
     */
    private fun channelMatchFor(channelName: String): String {
        val stage = searchConfig.subject
        if (stage.channelFormatId == JsonApiChannelFormatDeclared.id) {
            stage.channelFormatDeclared.channels.firstOrNull { it.name == channelName }?.let { return it.match }
        }
        return channelName
    }

    // endregion

    // region 资源

    /**
     * 把站点上的一集转换为 [DefaultMedia]. 自动匹配与浏览手动选集共用, 保证同一集两种途径得到相同的 [Media.mediaId].
     *
     * 资源 id 由站点 id 组成 (`源.条目id[-线路]-剧集id`), 不含名称, 显示名称模板改动不影响它. 有线路时线路总是参与:
     * 声明线路的站点里同一集的各线路只差线路这一个维度, 合并它们会让自动选择与失败换线路无从区分.
     *
     * @param episodeSort 该资源对应条目服务的哪一集. `null` 表示未知, 此时 [Media.episodeRange] 为 `null`.
     */
    internal fun createMedia(
        subject: JsonApiSubjectInfo,
        episode: JsonApiEpisodeInfo,
        episodeSort: EpisodeSort?,
        parser: LabelFirstRawTitleParser = LabelFirstRawTitleParser(),
    ): DefaultMedia {
        val apiParameters = JsonApiVariables.play(subject, episode)
        return DefaultMedia(
            mediaId = buildString {
                append(mediaSourceId).append('.').append(subject.id)
                episode.channel?.let { append('-').append(it) }
                append('-').append(episode.id)
            },
            mediaSourceId = mediaSourceId,
            originalUrl = episode.url,
            download = ResourceLocation.WebVideo(episode.url, apiParameters = apiParameters),
            originalTitle = "${subject.name} ${episode.name}",
            publishedTime = 0L,
            properties = MediaProperties(
                subjectName = subject.name,
                episodeName = episode.name,
                subtitleLanguageIds = guessSubtitleLanguageIds(episode.channel, episode.name, parser)
                    ?: listOf(searchConfig.defaultSubtitleLanguage.id),
                resolution = searchConfig.defaultResolution.id,
                alliance = episode.channel ?: "",
                size = FileSize.Unspecified,
                subtitleKind = SubtitleKind.EMBEDDED,
            ),
            episodeRange = episodeSort?.let { EpisodeRange.single(it) },
            location = MediaSourceLocation.Online,
            kind = MediaSourceKind.WEB,
        )
    }

    // endregion

    // region 自动匹配

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        val autoMatch = searchConfig.autoMatch
        if (!autoMatch.enabled) return emptySizedSource()
        if (!MediaSourceEngineHelpers.isSupportedByPlatformPlayer(searchConfig.onlySupportsPlayers, info.displayName, logger)) {
            return emptySizedSource()
        }

        val subjectId = query.subjectId.toIntOrNull()
        val filterContext = MediaListFilterContext(
            subjectNames = query.subjectNames.toSet(),
            episodeSort = query.episodeSort,
            episodeEp = query.episodeEp,
            episodeName = query.episodeName,
        )
        val freshnessProbe = query.latestAiredEpisode()?.let { Probe(it.sort, it.ep, it.name) }
            ?: Probe(query.episodeSort, query.episodeEp, query.episodeName)

        val primary = query.subjectNames.take(autoMatch.searchUseSubjectNamesCount.coerceAtLeast(1))
        val fallback = distinctFallbackKeywords(primary, query.fallbackSearchKeywords)
        val remembered = repository.rememberedKeywords(subjectId, mediaSourceId, primary + fallback, logger)
        return keywordSearchChain(
            primary, fallback, remembered,
            requestInterval = searchConfig.requestInterval,
            matches = { media -> media.any { it.subjectNameMatches(filterContext) } },
        ) { name ->
            searchFromCacheOrNull(subjectId, name, freshnessProbe, filterContext)
                ?: run {
                    beforeOnlineSearch()
                    searchOnline(subjectId, name, filterContext)
                }
        }.map { MediaMatch(it, MatchKind.FUZZY) }
    }

    private class Probe(val episodeSort: EpisodeSort, val episodeEp: EpisodeSort?, val episodeName: String?)

    /**
     * 搜索一个关键词, 取所有名字能对上请求条目的条目的全部剧集. 名字对不上的条目选择器也会排除, 不值得为它们请求剧集.
     * 结果写入搜索缓存.
     */
    private suspend fun searchOnline(
        subjectId: Int?,
        subjectName: String,
        filterContext: MediaListFilterContext,
    ): List<DefaultMedia> {
        val autoMatch = searchConfig.autoMatch
        val keyword = MediaSourceEngineHelpers.getSearchKeyword(
            subjectName, autoMatch.searchRemoveSpecial, autoMatch.searchUseOnlyFirstWord,
        )
        val subjects = searchSubjectsOnline(keyword)
            .let { if (autoMatch.preferShorterName) it.sortedBy { s -> s.name.length } else it }
            .mapNotNull { subject -> subject.withMatchingName(filterContext) }
        return buildList {
            for (subject in subjects) {
                val channels = try {
                    loadChannels(subject)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RepositoryException) {
                    // 单个条目的网络错误不终止整个搜索
                    logger.warn(e) { "JsonApiMediaSource '$mediaSourceId': failed to load subject ${subject.url}" }
                    continue
                }
                val episodes = channels.flatMap { it.episodes }
                if (episodes.isEmpty()) continue
                repository.addCache(
                    subjectId, mediaSourceId, subjectName,
                    subject.toWebSearchSubjectInfo(),
                    episodes.map { it.toWebSearchEpisodeInfo() },
                    sourceCacheTtl = searchConfig.searchCacheTtl,
                )
                addAll(selectMedia(subject, episodes, filterContext))
            }
        }
    }

    /**
     * 从搜索缓存构建结果. 仅当缓存的剧集列表里能找到 [probe] (已上映的最新一集) 时才命中; 否则 `null`, 走完整搜索.
     * 缓存行的剧集地址与当前模板对不上 (模板改过) 的页面当作没有缓存.
     */
    private suspend fun searchFromCacheOrNull(
        subjectId: Int?,
        subjectName: String,
        probe: Probe,
        filterContext: MediaListFilterContext,
    ): List<DefaultMedia>? {
        val caches = try {
            repository.getCache(subjectId, mediaSourceId, subjectName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "JsonApiMediaSource '$mediaSourceId': failed to read search cache, falling back to search" }
            return null
        }
        val episodeUrlTemplate = searchConfig.subject.episodes.episodeUrlTemplate
        return buildList {
            for (cache in caches) {
                val rows = cache.webEpisodeInfos
                if (rows.findMatchingEpisodeOrNull(probe.episodeSort, probe.episodeEp, probe.episodeName) == null) continue
                val subject = JsonApiSubjectInfo(
                    id = cache.webSubjectInfo.internalId,
                    name = cache.webSubjectInfo.name,
                    url = cache.webSubjectInfo.fullUrl,
                )
                val episodes = rows.map { row ->
                    val id = JsonApiTemplate.extract(episodeUrlTemplate, row.playUrl)?.get("id") ?: run {
                        // 缓存行是旧模板写入的, 整个关键词按没有缓存处理, 重新搜索
                        logger.info { "JsonApiMediaSource '$mediaSourceId': cached play url '${row.playUrl}' does not match template '$episodeUrlTemplate', ignoring cache" }
                        return null
                    }
                    JsonApiEpisodeInfo(
                        channel = row.channel,
                        channelMatch = row.channel?.let { channelMatchFor(it) },
                        name = row.name,
                        episodeSortOrEp = row.episodeSortOrEp,
                        id = id,
                        url = row.playUrl,
                    )
                }
                addAll(selectMedia(subject, episodes, filterContext))
            }
        }.takeIf { it.isNotEmpty() }
    }

    /**
     * 把条目的剧集转换为资源, 并按当前剧集筛选 ([me.him188.ani.app.domain.mediasource.web.SelectorAutoMatchConfig.filterByEpisodeSort]).
     */
    private fun selectMedia(
        subject: JsonApiSubjectInfo,
        episodes: List<JsonApiEpisodeInfo>,
        filterContext: MediaListFilterContext,
    ): List<DefaultMedia> {
        val parser = LabelFirstRawTitleParser()
        val list = episodes.mapNotNull { episode ->
            val sort = episode.toWebSearchEpisodeInfo().matchingEpisodeSort(filterContext.episodeSort, filterContext.episodeEp)
                ?: return@mapNotNull null
            createMedia(subject, episode, sort, parser)
        }
        if (!searchConfig.autoMatch.filterByEpisodeSort) return list
        return with(filterContext) {
            list.filter { MediaListFilters.ContainsAnyEpisodeInfo.applyOn(it.asCandidate()) }
        }
    }

    /**
     * 条目名或任一别名对上请求的条目名时, 返回以对上的名字为 [JsonApiSubjectInfo.name] 的条目; 都对不上为 `null`.
     *
     * 资源与搜索缓存都用对上的名字: 选择器下游用同一判据按资源的条目名过滤, 保留站点标题会让只有别名对上的条目在那里被排除.
     */
    private fun JsonApiSubjectInfo.withMatchingName(context: MediaListFilterContext): JsonApiSubjectInfo? {
        val matched = (listOf(name) + aliases).firstOrNull { candidate ->
            with(context) { MediaListFilters.ContainsSubjectName.applyOn(subjectNameCandidate(candidate)) }
        } ?: return null
        return if (matched == name) this else copy(name = matched)
    }

    private fun DefaultMedia.subjectNameMatches(context: MediaListFilterContext): Boolean = with(context) {
        val media = this@subjectNameMatches
        MediaListFilters.ContainsSubjectName.applyOn(
            object : MediaListFilter.Candidate by media.asCandidate() {
                override val subjectName: String get() = media.properties.subjectName ?: media.originalTitle
            },
        )
    }

    /**
     * 回退关键词里去掉归一化后与主关键词或彼此相同的, 保持原顺序.
     */
    private fun distinctFallbackKeywords(primary: List<String>, fallback: List<String>): List<String> {
        val autoMatch = searchConfig.autoMatch
        fun normalize(name: String) = MediaSourceEngineHelpers.getSearchKeyword(
            name, autoMatch.searchRemoveSpecial, autoMatch.searchUseOnlyFirstWord,
        ).trim().lowercase()

        val seen = primary.mapTo(HashSet()) { normalize(it) }
        return fallback.filter { name ->
            val keyword = normalize(name)
            keyword.isNotEmpty() && seen.add(keyword)
        }
    }

    // endregion

    // region 播放

    private class PlayResponseEntry(val response: JsonApiResponse?, val fetchedAtMillis: Long)

    private val playResponseLock = Mutex()

    /**
     * 播放时发出的请求按准备好的请求记忆响应: 签发请求模板只用到 `{id}` 时同一集的各线路共用一个响应,
     * 用到线路变量时各线路分别请求; 重取的剧集列表按条目共用.
     */
    private val playResponses = LinkedHashMap<JsonApiPreparedRequest, PlayResponseEntry>()

    /**
     * 向站点取当前有效的视频地址: 剧集列表自带地址的站点重取列表读这一集的地址, 否则发签发请求.
     * 同一个请求在 [PLAY_RESPONSE_TTL] 内复用上次的响应, 见 [playResponses].
     */
    override suspend fun resolveApiVideo(media: Media): WebVideo? {
        val parameters = (media.download as? ResourceLocation.WebVideo)?.apiParameters ?: return null
        if (searchConfig.episodeListHasVideoUrl) {
            resolveVideoUrlFromEpisodeList(parameters)?.let { return WebVideo(it, videoHeaders(parameters)) }
            if (!searchConfig.play.isValid()) {
                logger.info { "JsonApiMediaSource '$mediaSourceId': episode list has no video url for ${media.mediaId} and no play request is configured" }
                return null
            }
        }
        val prepared = engine.prepareRequest(searchConfig, searchConfig.play.request, parameters) ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': invalid play request config, url=${searchConfig.play.request.url}" }
            return null
        }
        val response = executeMemoized("play", prepared) ?: return null
        val json = response.json ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': play response is not JSON: $response" }
            return null
        }
        val channelMatch = parameters["channelMatch"] ?: parameters["channel"]
        val result = engine.parseVideo(json, searchConfig.play, channelMatch) ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': invalid play stage config" }
            return null
        }
        val selected = result.selected ?: run {
            logger.info {
                "JsonApiMediaSource '$mediaSourceId': no candidate for channel '$channelMatch' among " +
                        result.candidates.map { it.channelMatch }
            }
            return null
        }
        if (result.isFallback) {
            logger.info { "JsonApiMediaSource '$mediaSourceId': channel '$channelMatch' not found, falling back to ${selected.channelMatch}" }
        }
        return WebVideo(selected.url, videoHeaders(parameters))
    }

    /**
     * 重新取资源所属条目的剧集列表, 读出这一集 (同线路、同 id) 当前的视频地址. 列表里没有这一集或这一集没有地址时为 `null`.
     */
    private suspend fun resolveVideoUrlFromEpisodeList(parameters: Map<String, String>): String? {
        val episodeId = parameters["id"] ?: return null
        val subject = JsonApiSubjectInfo(
            id = parameters["subjectId"] ?: return null,
            name = parameters["subjectName"].orEmpty(),
            url = parameters["subjectUrl"].orEmpty(),
        )
        val stage = searchConfig.subject
        val prepared = engine.prepareRequest(searchConfig, stage.request, JsonApiVariables.subject(subject)) ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': invalid subject request config, url=${stage.request.url}" }
            return null
        }
        val response = executeMemoized("subject", prepared) ?: return null
        val json = response.json ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': subject response is not JSON: $response" }
            return null
        }
        val channels = engine.parseChannels(json, stage, subject) ?: run {
            logger.warn { "JsonApiMediaSource '$mediaSourceId': invalid subject stage config" }
            return null
        }
        val channelName = parameters["channel"]
        val episode = channels.asSequence()
            .filter { it.name == channelName }
            .flatMap { it.episodes }
            .firstOrNull { it.id == episodeId }
        if (episode == null) {
            logger.info { "JsonApiMediaSource '$mediaSourceId': episode $episodeId of channel '$channelName' is no longer in the list of subject ${subject.id}" }
            return null
        }
        if (episode.videoUrl == null) {
            logger.info { "JsonApiMediaSource '$mediaSourceId': episode $episodeId of subject ${subject.id} has no video url in the list" }
        }
        return episode.videoUrl
    }

    /**
     * 发送播放时的请求, 在 [PLAY_RESPONSE_TTL] 内复用同一请求上次的响应.
     */
    private suspend fun executeMemoized(stage: String, prepared: JsonApiPreparedRequest): JsonApiResponse? =
        playResponseLock.withLock {
            val now = getTimeMillis()
            playResponses[prepared]?.takeIf { now - it.fetchedAtMillis < PLAY_RESPONSE_TTL.inWholeMilliseconds }
                ?.let { return@withLock it.response }
            val fetched = execute(stage, prepared)
            playResponses[prepared] = PlayResponseEntry(fetched, now)
            while (playResponses.size > PLAY_RESPONSE_CACHE_SIZE) {
                playResponses.remove(playResponses.keys.first())
            }
            fetched
        }

    /**
     * 播放器请求视频时的请求头: User-Agent、[JsonApiPlayStage.referer] 与代入 [variables] 后的 [JsonApiPlayStage.videoHeaders],
     * 同名以后者为准. 接口的请求头不在其中. 代入后含控制字符的值 (变量来自站点返回的名称) 丢弃, 以免注入请求头.
     */
    @Suppress("DEPRECATION")
    private fun videoHeaders(variables: Map<String, String>): Map<String, String> {
        val merged = LinkedHashMap<String, Pair<String, String>>()
        fun put(name: String, value: String) {
            merged[name.lowercase()] = name to value
        }
        put("User-Agent", searchConfig.userAgentOrNull ?: SelectorSearchConfig.VideoHeaders().userAgent)
        searchConfig.play.referer.trim().takeIf { it.isNotEmpty() }?.let { put("Referer", it) }
        for ((name, template) in JsonApiSearchConfig.parseHeaderLines(searchConfig.play.videoHeaders)) {
            val value = JsonApiTemplate.substitute(template, variables, JsonApiTemplate.Escaping.NONE)
            if (value.any { it < ' ' || it == '\u007f' }) {
                logger.warn { "JsonApiMediaSource '$mediaSourceId': video header '$name' contains control characters, dropped" }
                continue
            }
            put(name, value)
        }
        return merged.values.associate { it }
    }

    // endregion
}

/**
 * 把一个名字当作资源参与条目名过滤 ([MediaListFilters.ContainsSubjectName]).
 */
internal fun subjectNameCandidate(name: String): MediaListFilter.Candidate = object : MediaListFilter.Candidate {
    override val originalTitle: String get() = name
    override val episodeRange: EpisodeRange? get() = null
}

/**
 * 条目写入搜索缓存时的形态: `internalId` 与 `partialUrl` 都是站点 id, `fullUrl` 是条目地址.
 */
internal fun JsonApiSubjectInfo.toWebSearchSubjectInfo(): WebSearchSubjectInfo = WebSearchSubjectInfo(
    internalId = id,
    name = name,
    fullUrl = url,
    partialUrl = id,
    origin = null,
)

/**
 * 剧集写入搜索缓存与参与集号匹配时的形态. 站点 id 不单独存, 从 `playUrl` 按剧集地址模板反解.
 */
internal fun JsonApiEpisodeInfo.toWebSearchEpisodeInfo(): WebSearchEpisodeInfo = WebSearchEpisodeInfo(
    channel = channel,
    name = name,
    episodeSortOrEp = episodeSortOrEp,
    playUrl = url,
)
