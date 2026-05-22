/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.dandanplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuEpisode
import me.him188.ani.danmaku.api.provider.DanmakuEpisodeWithSubject
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuMatcher
import me.him188.ani.danmaku.api.provider.DanmakuMatchInfo
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.api.provider.DanmakuMatchers
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.api.provider.DanmakuSubject
import me.him188.ani.danmaku.api.provider.MatchingDanmakuProvider
import me.him188.ani.danmaku.dandanplay.data.SearchEpisodesAnime
import me.him188.ani.danmaku.dandanplay.data.toDanmakuOrNull
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.seasonMonth
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.thisLogger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class DandanplayDanmakuProvider(
    dandanplayAppId: String,
    dandanplayAppSecret: String,
    client: ScopedHttpClient,
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : MatchingDanmakuProvider {
    private val logger = thisLogger()
    override val providerId: DanmakuProviderId get() = DanmakuProviderId.Dandanplay
    override val mainServiceId: DanmakuServiceId get() = DanmakuServiceId.Dandanplay

    private val dandanplayClient = DandanplayClient(client, dandanplayAppId, dandanplayAppSecret)

    private enum class DanmakuOrigin(val serviceId: DanmakuServiceId) {
        BiliBili(DanmakuServiceId.Bilibili),
        AcFun(DanmakuServiceId.AcFun),
        Tucao(DanmakuServiceId.Tucao),
        Baha(DanmakuServiceId.Baha),
        DanDanPlay(DanmakuServiceId.Dandanplay),
    }

    override suspend fun fetchAutomatic(
        request: DanmakuFetchRequest,
    ): List<DanmakuFetchResult> = withContext(defaultDispatcher) {
        val raw = fetchImpl(request)
        categorizeByService(raw)
    }

    private fun categorizeByService(
        raw: DanmakuFetchResult
    ): List<DanmakuFetchResult> {
        val rawList = raw.list.toList()
        return rawList.groupBy { it.origin }.map { (origin, list) ->
            DanmakuFetchResult(
                providerId = providerId,
                matchInfo = DanmakuMatchInfo(
                    serviceId = origin.serviceId,
                    count = list.size,
                    method = raw.matchInfo.method,
                ),
                list = list,
            )
        }.let { results ->
            if (results.none { it.matchInfo.serviceId == DanmakuServiceId.Dandanplay }) {
                // 保证至少返回 DanDanPlay
                results + DanmakuFetchResult(
                    providerId = providerId,
                    matchInfo = raw.matchInfo,
                    list = rawList.filter { it.origin == DanmakuOrigin.DanDanPlay },
                )
            } else {
                results
            }
        }
    }

    private val DanmakuInfo.origin
        get() = when {
            senderId.startsWith("[BiliBili]") -> DanmakuOrigin.BiliBili // 这个是确定的, 其他的不确定
            senderId.startsWith("[Acfun]") -> DanmakuOrigin.AcFun
            senderId.startsWith("[Tucao]") -> DanmakuOrigin.Tucao

            senderId.startsWith("[Gamer]", ignoreCase = true)
                    || senderId.startsWith("[Gamers]", ignoreCase = true)
                    || senderId.startsWith("[Baha]", ignoreCase = true) -> DanmakuOrigin.Baha

            else -> DanmakuOrigin.DanDanPlay
        }

    private suspend fun fetchImpl(request: DanmakuFetchRequest): DanmakuFetchResult {
        // 获取剧集流程:
        //
        // 1. 用 Bangumi subject id 请求弹弹 Play 的 Bangumi.tv 映射接口
        // 2. 若失败, 获取该番剧所属季度的所有番的名字, 匹配 bangumi 条目所有别名
        // 3. 若失败, 用番剧名字搜索, 匹配 bangumi 条目所有别名
        // 4. 如果按别名精准匹配到了, 那就获取该番的所有剧集
        // 5. 如果没有, 那就提交条目名字给弹弹直接让弹弹获取相关剧集 (很不准)
        //
        // 匹配剧集流程:
        // 1. 用剧集在系列中的序号 (sort) 匹配
        // 2. 用剧集在当前季度中的序号 (ep) 匹配
        // 3. 用剧集名字模糊匹配

        val prefixedExpectedEpisodeName =
            "第${(request.episodeEp ?: request.episodeSort).toString().removePrefix("0")}话 " + request.episodeName
        val matcher = DanmakuMatchers.mostRelevant(
            request.subjectPrimaryName,
            prefixedExpectedEpisodeName,
        )

        val bgmtvEpisodes = runCatching { getEpisodesByBgmtvSubjectId(request) }
            .onFailure {
                if (it is CancellationException) throw it
                logger.warn(it) { "Failed to fetch episodes by Bangumi subject id: ${request.subjectId}" }
            }.getOrNull()
        tryMatchEpisodes(request, bgmtvEpisodes, prefixedExpectedEpisodeName, matcher)?.let { return it }

        val episodes: List<DanmakuEpisodeWithSubject>? =
            runCatching { getEpisodesByExactSubjectMatch(request) }
                .onFailure {
                    if (it is CancellationException) throw it
                    logger.error(it) { "Failed to fetch episodes by exact match" }
                }.getOrNull()
                ?: runCatching {
                    getEpisodesByFuzzyEpisodeSearch(request)
                }.onFailure {
                    if (it is CancellationException) throw it
                    logger.error(it) { "Failed to fetch episodes by fuzzy search" }
                }.getOrNull()
        tryMatchEpisodes(request, episodes, prefixedExpectedEpisodeName, matcher)?.let { return it }

        // 都不行, 那就用最不准的方法

        request.filename?.let { filename ->
            val resp = dandanplayClient.matchVideo(
                filename = filename,
                fileHash = request.fileHash,
                fileSize = request.fileSize,
                videoDuration = request.videoDuration,
            )
            val match = if (resp.isMatched) {
                resp.matches.firstOrNull() ?: return DanmakuFetchResult.noMatch(providerId, DanmakuServiceId.Dandanplay)
            } else {
                matcher.match(
                    resp.matches.map {
                        DanmakuEpisodeWithSubject(
                            it.episodeId.toString(),
                            it.animeTitle,
                            it.episodeTitle,
                            null,
                        )
                    },
                )?.let { match ->
                    resp.matches.first { it.episodeId.toString() == match.id }
                } ?: return DanmakuFetchResult.noMatch(providerId, DanmakuServiceId.Dandanplay)
            }
            logger.info { "Best match by file match: ${match.animeTitle} - ${match.episodeTitle}" }
            val episodeId = match.episodeId
            return createResult(
                episodeId,
                DanmakuMatchMethod.Fuzzy(match.animeTitle, match.episodeTitle),
            )
        }
        return DanmakuFetchResult.noMatch(providerId, DanmakuServiceId.Dandanplay)
    }

    private suspend fun tryMatchEpisodes(
        request: DanmakuFetchRequest,
        episodes: List<DanmakuEpisodeWithSubject>?,
        prefixedExpectedEpisodeName: String,
        matcher: DanmakuMatcher,
    ): DanmakuFetchResult? {
        if (episodes == null) return null

        // 用剧集编号匹配. 先用系列的, 因为系列的更大.
        episodes.firstOrNull { it.epOrSort != null && it.epOrSort == request.episodeSort }?.let {
            logger.info { "Matched episode by exact episodeSort: ${it.subjectName} - ${it.episodeName}" }
            return createResult(it.id.toLong(), DanmakuMatchMethod.Exact(it.subjectName, it.episodeName))
        }
        episodes.firstOrNull { it.epOrSort != null && it.epOrSort == request.episodeEp }?.let {
            logger.info { "Matched episode by exact episodeEp: ${it.subjectName} - ${it.episodeName}" }
            return createResult(it.id.toLong(), DanmakuMatchMethod.Exact(it.subjectName, it.episodeName))
        }

        // 用名称精确匹配, 标记为 Exact.
        if (request.episodeName.isNotBlank()) {
            val match =
                episodes.firstOrNull { it.episodeName == request.episodeName }
                    ?: episodes.firstOrNull { it.episodeName == prefixedExpectedEpisodeName }
            match?.let { episode ->
                logger.info { "Matched episode by exact episodeName: ${episode.subjectName} - ${episode.episodeName}" }
                return createResult(
                    episode.id.toLong(),
                    DanmakuMatchMethod.Exact(episode.subjectName, episode.episodeName),
                )
            }
        }

        // 用名字不精确匹配.
        if (episodes.isNotEmpty()) {
            matcher.match(episodes)?.let {
                logger.info { "Matched episode by ep search: ${it.subjectName} - ${it.episodeName}" }
                return createResult(
                    it.id.toLong(),
                    DanmakuMatchMethod.ExactSubjectFuzzyEpisode(it.subjectName, it.episodeName),
                )
            }
        }

        return null
    }

    private suspend fun getEpisodesByBgmtvSubjectId(
        request: DanmakuFetchRequest
    ): List<DanmakuEpisodeWithSubject>? {
        if (request.subjectId <= 0) return null

        val response = dandanplayClient.getBangumiEpisodesByBgmtvSubjectId(request.subjectId)
        val bangumi = response.bangumi
        if (!response.success || bangumi == null) {
            logger.info {
                "No Dandanplay Bangumi mapping found for Bangumi subject id: ${request.subjectId}, " +
                        "errorCode=${response.errorCode}, errorMessage=${response.errorMessage}"
            }
            return null
        }

        val episodes = bangumi.episodes.orEmpty()
        if (episodes.isEmpty()) return null

        logger.info {
            "Matched Dandanplay Bangumi by Bangumi subject id: ${request.subjectId} ${bangumi.animeTitle}"
        }
        return episodes.map { episode ->
            DanmakuEpisodeWithSubject(
                id = episode.episodeId.toString(),
                subjectName = bangumi.animeTitle ?: request.subjectPrimaryName,
                episodeName = episode.episodeTitle ?: "",
                epOrSort = episode.episodeNumber?.let { EpisodeSort(it) },
            )
        }
    }

    private suspend fun DandanplayDanmakuProvider.getEpisodesByExactSubjectMatch(
        request: DanmakuFetchRequest
    ): List<DanmakuEpisodeWithSubject>? {
        if (!request.subjectPublishDate.isValid) return null

        // 将筛选范围缩小到季度
        val anime = getDandanplayAnimeIdOrNull(request) ?: return null
        return dandanplayClient.getBangumiEpisodes(anime.bangumiId ?: anime.animeId)
            .bangumi?.episodes?.map { episode ->
                DanmakuEpisodeWithSubject(
                    id = episode.episodeId.toString(),
                    subjectName = request.subjectPrimaryName,
                    episodeName = episode.episodeTitle ?: "",
                    epOrSort = episode.episodeNumber?.let { EpisodeSort(it) },
                )
            }
    }

    private suspend fun getEpisodesByFuzzyEpisodeSearch(request: DanmakuFetchRequest): List<DanmakuEpisodeWithSubject> {
        val searchEpisodeResponse = request.subjectNames.flatMap { name ->
            kotlin.runCatching {
                dandanplayClient.searchEpisode(
                    subjectName = name.trim().substringBeforeLast(" "),
                    episodeName = null, // 用我们的匹配算法
                    //            episodeName = "第${(request.episodeEp ?: request.episodeSort).toString().removePrefix("0")}话",
                    // 弹弹的是 EP 顺序
                    // 弹弹数据库有时候会只有 "第x话" 没有具体标题, 所以不带标题搜索就够了
                ).animes
            }.onFailure {
                if (it is CancellationException) throw it
                logger.error(it) { "Failed to getEpisodesByFuzzyEpisodeSearch with '$name'" }
            }.getOrNull() ?: emptySet()
        }
        logger.info { "Ep search result: ${searchEpisodeResponse}}" }
        return searchEpisodeResponse.flatMap { anime ->
            anime.episodes.map { ep ->
                DanmakuEpisodeWithSubject(
                    id = ep.episodeId.toString(),
                    subjectName = anime.animeTitle ?: "",
                    episodeName = ep.episodeTitle ?: "",
                    epOrSort = ep.episodeNumber?.let { EpisodeSort(it) },
                )
            }
        }
    }

    private suspend fun getDandanplayAnimeIdOrNull(
        request: DanmakuFetchRequest
    ): SearchEpisodesAnime? = withContext(defaultDispatcher) {
        val date = request.subjectPublishDate
        val mo = date.seasonMonth
        if (mo == 0) return@withContext null

        val expectedNames = request.subjectNames.toSet()

        kotlin.runCatching {
            // 搜索这个季度的所有的番, 然后用名字匹配
            // 不建议用名字去请求弹弹 play 搜索, 它的搜索很不准
            dandanplayClient.getSeasonAnimeList(date.year, date.seasonMonth)
        }.onFailure {
            if (it is CancellationException) throw it
            logger.warn(it) { "Failed to fetch season anime list" }
        }.getOrNull()
            ?.bangumiList
            ?.firstOrNull { it.animeTitle in expectedNames }
            ?.let {
                logger.info { "Matched Dandanplay Anime in season using name: ${it.animeId} ${it.animeTitle}" }
                return@withContext it
            }


        request.subjectNames.flatMap { name ->
            kotlin.runCatching {
                dandanplayClient.searchSubject(name).animes
            }.onFailure {
                if (it is CancellationException) throw it
                logger.warn(it) { "Failed to fetch anime list by name" }
            }.getOrNull() ?: emptySet()
        }
            .firstOrNull { it.animeTitle in expectedNames }
            ?.let {
                logger.info { "Matched Dandanplay Anime by search using name: ${it.animeId} ${it.animeTitle}" }
                return@withContext it
            }

        null
    }

    private suspend fun createResult(
        episodeId: Long,
        matchMethod: DanmakuMatchMethod,
    ): DanmakuFetchResult {
        val list = dandanplayClient.getDanmakuList(episodeId = episodeId)
        logger.info { "${DanmakuServiceId.Dandanplay} Fetched danmaku list: ${list.size}" }
        return DanmakuFetchResult(
            providerId = providerId,
            matchInfo = DanmakuMatchInfo(DanmakuServiceId.Dandanplay, list.size, matchMethod),
            list = list.mapNotNull { it.toDanmakuOrNull() },
        )
    }

    override suspend fun fetchSubjectList(name: String): List<DanmakuSubject> = withContext(defaultDispatcher) {
        dandanplayClient.searchSubject(subjectName = name).animes.map {
            DanmakuSubject(
                it.animeId.toString(),
                it.animeTitle ?: it.animeId.toString(),
            )
        }
    }

    override suspend fun fetchEpisodeList(subject: DanmakuSubject): List<DanmakuEpisode> =
        withContext(defaultDispatcher) {
            val animeId = subject.id.toIntOrNull() ?: throw IllegalArgumentException("Invalid anime id: ${subject.id}")
            val bangumi = dandanplayClient.getBangumiEpisodes(animeId)

            bangumi.bangumi?.episodes?.map {
                DanmakuEpisode(
                    id = it.episodeId.toString(),
                    name = it.episodeTitle ?: "",
                )
            } ?: emptyList()
        }

    override suspend fun fetchDanmakuList(
        subject: DanmakuSubject,
        episode: DanmakuEpisode
    ): List<DanmakuFetchResult> = withContext(defaultDispatcher) {
        val episodeId =
            episode.id.toLongOrNull() ?: throw IllegalArgumentException("Invalid episode id: ${episode.id}")
        val list = dandanplayClient.getDanmakuList(episodeId = episodeId)

        list.mapNotNull { it.toDanmakuOrNull() }.let { list ->
            categorizeByService(
                DanmakuFetchResult(
                    providerId = providerId,
                    matchInfo = DanmakuMatchInfo(
                        DanmakuServiceId.Dandanplay, list.size,
                        DanmakuMatchMethod.Exact(
                            subject.name,
                            episode.name,
                        ),
                    ),
                    list = list,
                ),
            )
        }
    }
}
