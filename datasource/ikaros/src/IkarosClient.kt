package me.him188.ani.datasources.ikaros

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
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
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import me.him188.ani.datasources.ikaros.models.IkarosEpisodeGroup
import me.him188.ani.datasources.ikaros.models.IkarosEpisodeRecord
import me.him188.ani.datasources.ikaros.models.IkarosSubjectSync
import me.him188.ani.datasources.ikaros.models.IkarosVideoSubtitle
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import models.IkarosAttachment
import java.util.Collections

class IkarosClient(
    private val baseUrl: String,
    private val client: HttpClient,
) {
    companion object {
        private val logger = logger<IkarosClient>()
    }

    suspend fun checkConnection(): HttpStatusCode {
        return try {
            client.get(baseUrl).run {
                check(status.isSuccess()) { "Request failed: $this" }
            }
            HttpStatusCode.OK
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to $baseUrl" }
            HttpStatusCode.ServiceUnavailable
        }
    }


    suspend fun getSubjectSyncsWithBgmTvSubjectId(bgmTvSubjectId: String): List<IkarosSubjectSync> {
        if (bgmTvSubjectId.isBlank() || bgmTvSubjectId.toInt() <= 0) {
            return Collections.emptyList()
        }
        val url = "$baseUrl/api/v1alpha1/subject/syncs/platform?platform=BGM_TV&platformId=$bgmTvSubjectId"
        val responseText = client.get(url).bodyAsText();
        return Json { ignoreUnknownKeys = true }.decodeFromString(responseText)
    }

    suspend fun episodeRecords2SizeSource(
        subjectId: String,
        episodeRecords: List<IkarosEpisodeRecord>,
        episodeSort: EpisodeSort
    ): SizedSource<MediaMatch> {
        val mediaMatchs = mutableListOf<MediaMatch>()
        val epSortNumber = if (episodeSort.number == null) 1.0 else episodeSort.number!!.toDouble()
        val ikarosEpisodeGroup = if (episodeSort is EpisodeSort.Special) {
            when (episodeSort.type) {
                EpisodeType.SP -> IkarosEpisodeGroup.SPECIAL_PROMOTION
                EpisodeType.OP -> IkarosEpisodeGroup.OPENING_SONG
                EpisodeType.ED -> IkarosEpisodeGroup.ENDING_SONG
                EpisodeType.PV -> IkarosEpisodeGroup.PROMOTION_VIDEO
                EpisodeType.MAD -> IkarosEpisodeGroup.SMALL_THEATER
                EpisodeType.OVA -> IkarosEpisodeGroup.ORIGINAL_VIDEO_ANIMATION
                EpisodeType.OAD -> IkarosEpisodeGroup.ORIGINAL_ANIMATION_DISC
                else -> IkarosEpisodeGroup.MAIN
            }
        } else {
            IkarosEpisodeGroup.MAIN
        }
        val episode = episodeRecords.find { epRecord ->
            epRecord.episode.sequence == epSortNumber && ikarosEpisodeGroup.name == epRecord.episode.group.name
        }
        if (episode?.resources != null && episode.resources.isNotEmpty()) {
            for (epRes in episode.resources) {
                val media = epRes.let {
                    val attachment: IkarosAttachment? = getAttachmentById(epRes.attachmentId);
                    val parseResult = RawTitleParser.getDefault().parse(epRes.name);
                    DefaultMedia(
                        mediaId = epRes.attachmentId.toString(),
                        mediaSourceId = IkarosMediaSource.ID,
                        originalUrl = baseUrl.plus("/console/#/subjects/subject/details/").plus(subjectId),
                        download = ResourceLocation.HttpStreamingFile(
                            uri = getResUrl(epRes.url),
                        ),
                        originalTitle = epRes.name,
                        publishedTime = DateFormater.default.utcDateStr2timeStamp(attachment?.updateTime ?: ""),
                        properties = MediaProperties(
                            subtitleLanguageIds = parseResult.subtitleLanguages.map { it.id },
                            resolution = parseResult.resolution?.displayName ?: "480P",
                            alliance = IkarosMediaSource.ID,
                            size = (attachment?.size ?: 0).bytes,
                            subtitleKind = SubtitleKind.EXTERNAL_PROVIDED,
                        ),
                        episodeRange = parseResult.episodeRange,
                        location = MediaSourceLocation.Online,
                        kind = MediaSourceKind.WEB,
                        extraFiles = fetchVideoAttSubtitles2ExtraFiles(epRes.attachmentId),
                    )
                }
                val mediaMatch = media.let { MediaMatch(it, MatchKind.FUZZY) }
                mediaMatchs.add(mediaMatch)
            }
        }

        val sizedSource = IkarosSizeSource(
            totalSize = flowOf(mediaMatchs.size), finished = flowOf(true), results = mediaMatchs.asFlow(),
        )
        return sizedSource;
    }

    suspend fun getEpisodeRecordsWithId(subjectId: String): List<IkarosEpisodeRecord> {
        if (subjectId.isBlank() || subjectId.toInt() <= 0) {
            return Collections.emptyList();
        }
        val url = "$baseUrl/api/v1alpha1/episode/records/subjectId/$subjectId"
        val responseText = client.get(url).bodyAsText();
        return Json { ignoreUnknownKeys = true }.decodeFromString(responseText)
    }

    suspend fun getAttachmentById(attId: Long): IkarosAttachment? {
        if (attId <= 0) return null;
        val url = baseUrl.plus("/api/v1alpha1/attachment/").plus(attId)
        return client.get(url).body<IkarosAttachment>();
    }

    suspend fun getAttachmentVideoSubtitlesById(attId: Long): List<IkarosVideoSubtitle>? {
        if (attId <= 0) return null;
        val url = baseUrl.plus("/api/v1alpha1/attachment/relation/videoSubtitle/subtitles/").plus(attId);
        val responseText = client.get(url).bodyAsText();
        return Json { ignoreUnknownKeys = true }.decodeFromString(responseText)
    }

    fun getResUrl(url: String): String {
        if (url.isEmpty()) {
            return ""
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        return baseUrl + url
    }

    private suspend fun fetchVideoAttSubtitles2ExtraFiles(attachmentId: Long): MediaExtraFiles {
        if (attachmentId <= 0) return MediaExtraFiles()
        val attVideoSubtitleList = getAttachmentVideoSubtitlesById(attachmentId)
        val subtitles: MutableList<Subtitle> = mutableListOf();
        if (!attVideoSubtitleList.isNullOrEmpty()) {
            for (ikVideoSubtitle in attVideoSubtitleList) {
                // convert ikarosVideoSubtitle to ani subtitle
                subtitles.add(
                    Subtitle(
                        uri = getResUrl(ikVideoSubtitle.url),
                        language = AssNameParser.default.parseAssName2Language(ikVideoSubtitle.name),
                        mimeType = AssNameParser.httpMineType,
                    ),
                )
            }
        }
        return MediaExtraFiles(subtitles);
    }
}

class IkarosSizeSource(
    override val results: Flow<MediaMatch>, override val finished: Flow<Boolean>, override val totalSize: Flow<Int?>
) : SizedSource<MediaMatch>

