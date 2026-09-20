/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.ikaros

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.Subtitle
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import me.him188.ani.datasources.ikaros.models.IkarosEpisodeGroup
import me.him188.ani.datasources.ikaros.models.IkarosEpisodeMeta
import me.him188.ani.datasources.ikaros.models.IkarosEpisodeRecord
import me.him188.ani.datasources.ikaros.models.IkarosEpisodeResource
import me.him188.ani.datasources.ikaros.models.IkarosSubjectSync
import me.him188.ani.datasources.ikaros.models.IkarosVideoSubtitle
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import models.IkarosAttachment

class IkarosClient(
    private val baseUrl: String,
    private val client: ScopedHttpClient,
    private val addAuthorizationHeaders: io.ktor.http.HttpMessageBuilder.() -> Unit,
) {
    companion object {
        private val logger = logger<IkarosClient>()
        private val json = Json { ignoreUnknownKeys = true }
        private const val API_VERSION = "v1alpha1"

        /**
         * 同时转换的资源数. 每个资源要 3 次请求, 数量随集数线性增长.
         */
        private const val RESOURCE_CONCURRENCY = 4
    }

    suspend fun checkConnection(): HttpStatusCode {
        return try {
            client.use {
                get(baseUrl) {
                    addAuthorizationHeaders()
                }.run {
                    check(status.isSuccess()) { "Request failed: $this" }
                }
            }
            HttpStatusCode.OK
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to $baseUrl" }
            HttpStatusCode.ServiceUnavailable
        }
    }


    suspend fun getSubjectSyncsWithBgmTvSubjectId(bgmTvSubjectId: String): List<IkarosSubjectSync> {
        if (bgmTvSubjectId.isBlank() || bgmTvSubjectId.toInt() <= 0) {
            return emptyList()
        }
        val url = "$baseUrl/api/$API_VERSION/subject/syncs/platform?platform=BGM_TV&platformId=$bgmTvSubjectId"
        val responseText = client.use {
            get(url) {
                addAuthorizationHeaders()
            }.bodyAsText()
        }
        return json.decodeFromString(responseText)
    }

    /**
     * 把条目的全部剧集记录转换为资源. 每个资源需要多次请求 (附件信息, 播放地址, 字幕), 并发数受 [RESOURCE_CONCURRENCY] 限制.
     *
     * 资源的剧集范围来自记录的分组与序号 ([episodeSortOf]); 无法对应到剧集类型的分组 (如 OST, LIVE) 跳过.
     */
    fun episodeRecords2SizeSource(
        subjectId: String,
        episodeRecords: List<IkarosEpisodeRecord>,
    ): SizedSource<MediaMatch> {
        val entries = episodeRecords.flatMap { record ->
            val sort = episodeSortOf(record.episode) ?: return@flatMap emptyList()
            record.resources.orEmpty().map { resource -> sort to resource }
        }
        val results = channelFlow {
            val semaphore = Semaphore(RESOURCE_CONCURRENCY)
            for ((sort, resource) in entries) {
                launch {
                    semaphore.withPermit {
                        send(MediaMatch(resourceToMedia(subjectId, sort, resource), MatchKind.EXACT))
                    }
                }
            }
        }
        return IkarosSizeSource(
            totalSize = flowOf(entries.size), finished = flowOf(true), results = results,
        )
    }

    private suspend fun resourceToMedia(
        subjectId: String,
        sort: EpisodeSort,
        epRes: IkarosEpisodeResource,
    ): DefaultMedia {
        val attachment: IkarosAttachment? = getAttachmentById(epRes.attachmentId)
        val parseResult = RawTitleParser.getDefault().parse(epRes.name)
        return DefaultMedia(
            mediaId = epRes.attachmentId.toString(),
            mediaSourceId = IkarosMediaSource.ID,
            originalUrl = baseUrl.plus("/console/#/subjects/subject/details/").plus(subjectId),
            download = ResourceLocation.HttpStreamingFile(
                uri = getAttReadUrl(epRes.attachmentId),
            ),
            originalTitle = epRes.name,
            publishedTime = kotlin.runCatching {
                DateFormater.utcDateStr2timeStamp(attachment?.updateTime ?: "")
            }.getOrElse { 0 },
            properties = MediaProperties(
                subjectName = null, // Ikaros is exact match and hence does not need these properties.
                episodeName = null,
                subtitleLanguageIds = parseResult.subtitleLanguages.map { it.id },
                resolution = parseResult.resolution?.displayName ?: "480P",
                alliance = IkarosMediaSource.ID,
                size = (attachment?.size ?: 0).bytes,
                subtitleKind = SubtitleKind.EXTERNAL_PROVIDED,
            ),
            episodeRange = EpisodeRange.single(sort),
            location = MediaSourceLocation.Online,
            kind = MediaSourceKind.WEB,
            extraFiles = fetchVideoAttSubtitles2ExtraFiles(epRes.attachmentId),
        )
    }

    /**
     * 记录对应的集数: 正片为序号, 其他分组为对应类型的特殊剧集. 没有对应剧集类型的分组返回 `null`.
     */
    private fun episodeSortOf(episode: IkarosEpisodeMeta): EpisodeSort? {
        val number = episode.sequence.let { seq ->
            if (seq == seq.toLong().toDouble()) seq.toLong().toString() else seq.toString()
        }
        val type = when (episode.group) {
            IkarosEpisodeGroup.MAIN -> return EpisodeSort(number)
            IkarosEpisodeGroup.SPECIAL_PROMOTION -> EpisodeType.SP
            IkarosEpisodeGroup.OPENING_SONG -> EpisodeType.OP
            IkarosEpisodeGroup.ENDING_SONG -> EpisodeType.ED
            IkarosEpisodeGroup.PROMOTION_VIDEO -> EpisodeType.PV
            IkarosEpisodeGroup.SMALL_THEATER -> EpisodeType.MAD
            IkarosEpisodeGroup.ORIGINAL_VIDEO_ANIMATION -> EpisodeType.OVA
            IkarosEpisodeGroup.ORIGINAL_ANIMATION_DISC -> EpisodeType.OAD
            else -> return null
        }
        return EpisodeSort(type.value + number)
    }

    suspend fun getAttReadUrl(attachmentId: Long): String {
        val url = "$baseUrl/api/$API_VERSION/attachment/url/read/id/$attachmentId"
        val responseText = client.use {
            get(url) {
                addAuthorizationHeaders()
            }.bodyAsText()
        }
        return getResUrl(responseText)
    }

    suspend fun getEpisodeRecordsWithId(subjectId: String): List<IkarosEpisodeRecord> {
        if (subjectId.isBlank() || subjectId.toInt() <= 0) {
            return emptyList()
        }
        val url = "$baseUrl/api/$API_VERSION/episode/records/subjectId/$subjectId"
        val responseText = client.use {
            get(url) {
                addAuthorizationHeaders()
            }.bodyAsText()
        }
        return json.decodeFromString(responseText)
    }

    private suspend fun getAttachmentById(attId: Long): IkarosAttachment? {
        if (attId <= 0) return null
        val url = baseUrl.plus("/api/$API_VERSION/attachment/").plus(attId)
        return client.use {
            get(url) {
                addAuthorizationHeaders()
            }.body<IkarosAttachment>()
        }
    }

    private suspend fun getAttachmentVideoSubtitlesById(attId: Long): List<IkarosVideoSubtitle>? {
        if (attId <= 0) return null
        val url = baseUrl.plus("/api/$API_VERSION/attachment/relation/videoSubtitle/subtitles/").plus(attId)
        val responseText = client.use {
            get(url) {
                addAuthorizationHeaders()
            }.bodyAsText()
        }
        return json.decodeFromString(responseText)
    }

    private fun getResUrl(url: String): String {
        if (url.isEmpty()) {
            return ""
        }
        @Suppress("HttpUrlsUsage")
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        return baseUrl + url
    }

    private suspend fun fetchVideoAttSubtitles2ExtraFiles(attachmentId: Long): MediaExtraFiles {
        if (attachmentId <= 0) return MediaExtraFiles()
        val attVideoSubtitleList = getAttachmentVideoSubtitlesById(attachmentId)
        val subtitles: MutableList<Subtitle> = mutableListOf()
        if (!attVideoSubtitleList.isNullOrEmpty()) {
            for (ikVideoSubtitle in attVideoSubtitleList) {
                // convert ikarosVideoSubtitle to ani subtitle
                subtitles.add(
                    Subtitle(
                        uri = getAttReadUrl(ikVideoSubtitle.attachmentId),
                        language = AssNameParser.default.parseAssName2Language(ikVideoSubtitle.name),
                        mimeType = AssNameParser.httpMineType,
                    ),
                )
            }
        }
        return MediaExtraFiles(subtitles)
    }
}

class IkarosSizeSource(
    override val results: Flow<MediaMatch>, override val finished: Flow<Boolean>, override val totalSize: Flow<Int?>
) : SizedSource<MediaMatch>

