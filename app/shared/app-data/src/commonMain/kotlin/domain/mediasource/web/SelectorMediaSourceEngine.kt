/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.MediaListFilter
import me.him188.ani.app.domain.mediasource.MediaListFilterContext
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers
import me.him188.ani.app.domain.mediasource.asCandidate
import me.him188.ani.app.domain.mediasource.web.format.SelectedChannelEpisodes
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorFormatConfig
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormat
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.datasources.api.topic.titles.LabelFirstRawTitleParser
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.xml.Document
import me.him188.ani.utils.xml.Element
import me.him188.ani.utils.xml.Html
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * For [SelectorMediaSourceEngine.selectMedia]
 */
data class SelectorSearchQuery(
    val subjectName: String,
    val allSubjectNames: Set<String>,
    val episodeSort: EpisodeSort,
    val episodeEp: EpisodeSort?,
    val episodeName: String?,
)

fun SelectorSearchQuery.toFilterContext() = MediaListFilterContext(
    subjectNames = allSubjectNames,
    episodeSort = episodeSort,
    episodeEp = episodeEp,
    episodeName = episodeName,
)

/**
 * 基于 CSS Selector 解析页面的数据源引擎.
 *
 * 解析流程:
 *
 * 1. 搜索条目列表: [SelectorMediaSourceEngine.searchSubjects]
 * 2. 解析条目页面: [SelectorMediaSourceEngine.selectSubjects]
 * 3. 搜索一个条目的剧集列表 [SelectorMediaSourceEngine.searchEpisodes]
 * 4. 解析剧集列表页面 [SelectorMediaSourceEngine.selectEpisodes]
 * 5. 将剧集信息转换为 [Media]: [SelectorMediaSourceEngine.selectMedia]
 */
abstract class SelectorMediaSourceEngine {
    companion object {
        const val CURRENT_VERSION: UInt = 1u

        // single instance to save memory
        private val defaultSubtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id)
    }

    data class SearchSubjectResult(
        val url: Url,
        /**
         * `null` means 404
         */
        val document: Document?,
    ) {
        override fun toString(): String = "SearchSubjectResult(url=$url, document=${document.toString().length}...)"
    }

    /**
     * 根据给定信息搜索条目列表.
     */
    @Throws(RepositoryException::class, CancellationException::class)
    suspend fun searchSubjects(
        searchUrl: String,
        subjectName: String,
        useOnlyFirstWord: Boolean,
        removeSpecial: Boolean,
    ): SearchSubjectResult {
        val encodedUrl = MediaSourceEngineHelpers.encodeUrlSegment(
            MediaSourceEngineHelpers.getSearchKeyword(subjectName, removeSpecial, useOnlyFirstWord),
        )

        val finalUrl = Url(
            searchUrl.replace("{keyword}", encodedUrl),
        )

        return searchImpl(finalUrl)
    }

    @Throws(RepositoryException::class, CancellationException::class)
    protected abstract suspend fun searchImpl(
        finalUrl: Url,
    ): SearchSubjectResult

    /**
     * 解析条目搜索结果. 返回该页面的所有条目.
     *
     * @return `null` if config is invalid
     */
    open fun selectSubjects(
        document: Element,
        config: SelectorSearchConfig,
    ): List<WebSearchSubjectInfo>? {
        val subjectFormat = SelectorSubjectFormat.findById(config.subjectFormatId)
            ?: throw UnsupportedOperationException("Unsupported subject format: ${config.subjectFormatId}")

        @Suppress("UNCHECKED_CAST")
        subjectFormat as SelectorSubjectFormat<SelectorFormatConfig>

        val formatConfig = config.getFormatConfig(subjectFormat)
        if (!formatConfig.isValid()) {
            return null
        }
        val originalList = subjectFormat.select(document, config.finalBaseUrl, formatConfig)

        return originalList
    }

    suspend fun searchEpisodes(
        subjectDetailsPageUrl: String,
    ): Document? = try {
        doHttpGet(subjectDetailsPageUrl)
    } catch (e: ClientRequestException) {
        e.response.status.let {
            if (it == HttpStatusCode.NotFound) {
                return null
            }
            throw e
        }
    }

    /**
     * @param subjectUrl episode 所属 (来自) 的条目的完整 URL.
     * @return `null` if config is invalid
     */
    fun selectEpisodes(
        subjectDetailsPage: Element,
        subjectUrl: String,
        config: SelectorSearchConfig,
    ): SelectedChannelEpisodes? {
        val channelFormat = SelectorChannelFormat.findById(config.channelFormatId)
            ?: throw UnsupportedOperationException("Unsupported channel format: ${config.channelFormatId}")

        @Suppress("UNCHECKED_CAST")
        channelFormat as SelectorChannelFormat<SelectorFormatConfig>
        val formatConfig = config.getFormatConfig(channelFormat)
        if (!formatConfig.isValid()) {
            return null
        }

        val finalBaseUrl = kotlin.runCatching {
            URLBuilder(subjectUrl).apply {
                pathSegments = pathSegments.dropLast(1)
            }.buildString()
        }.getOrElse {
            return null
        }
        return channelFormat.select(
            subjectDetailsPage,
            finalBaseUrl,
            formatConfig,
        )
    }

    data class SelectMediaResult(
        val originalList: List<DefaultMedia>,
        val filteredList: List<DefaultMedia>,
    )

    fun selectMedia(
        episodes: Sequence<WebSearchEpisodeInfo>,
        config: SelectorSearchConfig,
        query: SelectorSearchQuery,
        mediaSourceId: String,
        subjectName: String,
    ): SelectMediaResult {
        val parser = LabelFirstRawTitleParser()
        val originalMediaList = episodes.mapNotNull { info ->
            val subtitleLanguages = guessSubtitleLanguages(info, parser)
            info.episodeSortOrEp ?: return@mapNotNull null
            DefaultMedia(
                mediaId = buildString {
                    append(mediaSourceId)
                    append(".")
                    if (config.selectMedia.distinguishSubjectName) {
                        append(subjectName)
                        append("-")
                    }
                    if (config.selectMedia.distinguishChannelName) {
                        append(info.channel)
                        append("-")
                    }
                    append(info.name)
                    append("-")
                    append(info.episodeSortOrEp)
                },
                mediaSourceId = mediaSourceId,
                originalUrl = info.playUrl,
                download = ResourceLocation.WebVideo(info.playUrl),
                originalTitle = buildString {
                    if (config.selectMedia.distinguishSubjectName) {
                        append(subjectName)
                        append(" ")
                    }
                    append(info.name)
                },
                publishedTime = 0L,
                properties = MediaProperties(
                    subjectName = subjectName,
                    episodeName = info.name,
                    subtitleLanguageIds = subtitleLanguages,
                    resolution = config.defaultResolution.id,
                    alliance = info.channel ?: mediaSourceId,
                    size = FileSize.Unspecified,
                    subtitleKind = SubtitleKind.EMBEDDED,
                ),
                episodeRange = EpisodeRange.single(info.episodeSortOrEp),
                location = MediaSourceLocation.Online,
                kind = MediaSourceKind.WEB,
            )
        }.toList()

        return with(query.toFilterContext()) {
            val filters = config.createFiltersForEpisode()
            val filteredList = originalMediaList.filter {
                filters.applyOn(it.asCandidate())
            }
            SelectMediaResult(originalMediaList, filteredList)
        }
    }

    /**
     * 有的 channel 会叫 "简中" 和 "繁中"
     */
    private fun guessSubtitleLanguages(
        info: WebSearchEpisodeInfo,
        parser: LabelFirstRawTitleParser
    ): List<String> {
        val languagesFromChannel = info.channel?.let { parser.parseSubtitleLanguages(it) } ?: emptyList()
        val languagesFromName = info.name.let { parser.parseSubtitleLanguages(it) }

        return when {
            languagesFromChannel.isEmpty() && languagesFromName.isEmpty() -> defaultSubtitleLanguages
            else -> languagesFromChannel.asSequence()
                .plus(languagesFromName)
                .map {
                    it.id
                }
                .toList()
                .ifEmpty {
                    defaultSubtitleLanguages
                }
        }
    }

    fun shouldLoadPage(url: String, config: SelectorSearchConfig.MatchVideoConfig): Boolean {
        if (config.enableNestedUrl) {
            config.matchNestedUrlRegex?.find(url)?.let {
                return true
            }
        }
        return false
    }

    fun matchWebVideo(url: String, searchConfig: SelectorSearchConfig.MatchVideoConfig): WebVideoMatcher.MatchResult {
        if (shouldLoadPage(url, searchConfig)) {
            return WebVideoMatcher.MatchResult.LoadPage
        }

        val result = searchConfig.matchVideoUrlRegex?.find(url) ?: return WebVideoMatcher.MatchResult.Continue
        val videoUrl = try {
            result.groups["v"]?.value ?: url
        } catch (_: IllegalArgumentException) { // no group
            url
        }

        return WebVideoMatcher.MatchResult.Matched(
            WebVideo(
                videoUrl,
                mapOf(
                    "User-Agent" to searchConfig.addHeadersToVideo.userAgent,
                    "Referer" to searchConfig.addHeadersToVideo.referer,
                    "Sec-Ch-Ua-Mobile" to "?0",
                    "Sec-Ch-Ua-Platform" to "macOS",
                    "Sec-Fetch-Dest" to "video",
                    "Sec-Fetch-Mode" to "no-cors",
                    "Sec-Fetch-Site" to "cross-site",
                ),
            ),
        )
    }

    @Throws(RepositoryException::class, CancellationException::class)
    protected abstract suspend fun doHttpGet(uri: String): Document
}

// TODO: require MediaListFilterContext when context parameters
fun WebSearchSubjectInfo.asCandidate(): MediaListFilter.Candidate {
    val info = this
    return object : MediaListFilter.Candidate {
        override val originalTitle: String get() = info.name
        override val episodeRange: EpisodeRange? get() = null
    }
}

/**
 * If you change, you also need to change
 */
internal fun SelectorSearchConfig.createFiltersForSubject() = buildList {
    if (filterBySubjectName) add(MediaListFilters.ContainsSubjectName)
}

internal fun SelectorSearchConfig.createFiltersForEpisode() = buildList {
    // 不使用 filterBySubjectName, 因为 web 的剧集名称通常为 "第x集", 不包含 subject
    if (filterByEpisodeSort) add(MediaListFilters.ContainsAnyEpisodeInfo)
}

class DefaultSelectorMediaSourceEngine(
    /**
     * Engine 自己不会 cache 实例, 每次都调用 `.first()`.
     */
    private val client: Flow<HttpClient>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : SelectorMediaSourceEngine() {
    override suspend fun searchImpl(
        finalUrl: Url,
    ): SearchSubjectResult = withContext(ioDispatcher) {
        try {
            val document = try {
                client.first().get(finalUrl) {
                    accept(ContentType.Text.Html)
                }.let { resp ->
                    parseResp(resp)
                }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) {
                    // 404 Not Found
                    return@withContext SearchSubjectResult(
                        finalUrl,
                        document = null,
                    )
                }
                throw e
            }

            SearchSubjectResult(
                finalUrl,
                document,
            )
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }


    @Throws(RepositoryException::class, CancellationException::class)
    public override suspend fun doHttpGet(uri: String): Document = withContext(ioDispatcher) {
        try {
            client.first().get(uri) {
                accept(ContentType.Text.Html)
            }.let { resp ->
                parseResp(resp)
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    private suspend fun parseResp(resp: HttpResponse): Document {
        var body = resp.bodyAsText()
        // 非常奇怪, 有时候会是一个字符串
        if (body.startsWith("\"")) {
            body = runCatching { Json.parseToJsonElement(body).jsonPrimitive.content }.getOrNull() ?: body
        }
        return Html.parse(body)
    }
}
