/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.datastore.core.DataStore
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * 从 TMDB 获取条目的横版背景图 (backdrop), 用于 TV 详情页 Hero 背景等.
 *
 * Bangumi 只有竖版封面; TMDB 的 backdrop 是"剧"级别的, 用日文原名搜索命中即可,
 * 搜不到时沿 Bangumi 关联条目回溯到根条目再搜 (见 [searchLayered]).
 * 不涉及季/集映射 (TMDB 与 Bangumi 的季划分对不齐的问题只影响以后的分集缩略图,
 * 届时匹配键须用分集播出日期而非集号, 见 fork 内验证: 無職転生 两 cour 合并为 TMDB S1,
 * 進撃の巨人 Final Season 的 Bangumi 60 话对应 TMDB S4E1).
 *
 * 结果按 subjectId 持久缓存 (含"确认无图"的负缓存, 存空串); 网络错误不缓存.
 * 未配置 `ani.tmdb.api.token` 时直接返回 null, 功能自动关闭.
 */
class TmdbImageService(
    httpClientProvider: HttpClientProvider,
    private val dataStore: DataStore<TmdbImageCache>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    private val client = httpClientProvider.get()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 短连接超时: api.themoviedb.org 解析出的部分 IP 直连不通, 全局默认 30s 连接超时
     * 会让单个请求挂满半分钟才轮到重试 (实测 30183ms, 表现为 backdrop 半分钟才出来).
     * 5s 连不上就报错, 交给全局 HttpRequestRetry 换连接重试.
     */
    private fun HttpRequestBuilder.shortConnectTimeout() {
        timeout {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 20_000
        }
    }

    /**
     * 代理设置页的连通性探测.
     *
     * 接口和图片本体是两个域名 (`api.themoviedb.org` / `image.tmdb.org`), 在墙内各自独立
     * 被墙 —— 只探一个会漏判 (常见情况是接口通、图片超时, 表现为详情页背景一直空着),
     * 所以两个都通才算通.
     *
     * 未配置 `ani.tmdb.api.token` 时直接算失败: 那种情况下整个功能本来就是关的
     * ([getBackdropUrl] 直接返回 null), 报"通"只会让人以为图马上就要出来了.
     */
    suspend fun testConnection(): Boolean = withContext(ioDispatcher) {
        val token = currentAniBuildConfig.tmdbApiToken
        if (token.isBlank()) return@withContext false
        try {
            // /configuration 是最轻的鉴权端点, 顺带验证 token 有效 (token 不对是 401)
            val apiOk = client.use {
                get("$API_BASE_URL/configuration") {
                    bearerAuth(token)
                    shortConnectTimeout()
                    expectSuccess = false
                }.status.isSuccess()
            }
            if (!apiOk) return@withContext false
            // 图片 CDN 只看能否拿到 HTTP 响应, 不看状态码: 裸目录本身就会返回 4xx,
            // 那不代表被墙; 被墙的表现是连不上或超时, 会抛到下面的 catch
            client.use {
                head(IMAGE_BASE_URL) {
                    shortConnectTimeout()
                    expectSuccess = false
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "TMDB connection test failed" }
            false
        }
    }

    /**
     * 本进程内已解析出结果的 backdrop (subjectId -> URL, `""` = 已确认无图).
     *
     * 存在的意义是**同步可读** (见 [peekBackdropUrl]): [getBackdropUrl] 即使全部命中持久缓存,
     * 也要走一次 `withContext(ioDispatcher)` + DataStore 读盘, 耗时随磁盘/GC 抖动 ——
     * 详情页首帧等不到它, 只能先按"加载中"渲染, 之后再把图淡进来.
     * 它同时是 [getBackdropUrl] 自己的第一道查表 (只短路正缓存, 原因见那里), 页面级薄映射
     * 被清空后重新问过来的条目不必再读一次盘.
     *
     * 写入是 copy-on-write 的整表替换 (读方永远看到一个完整的不可变 map); 并发写最坏是丢一条,
     * 表现为该条目这次没命中热缓存, 无副作用.
     */
    @Volatile
    private var resolvedBackdropUrls: Map<Int, String> = emptyMap()

    /**
     * 同步读取本进程**已经解析过**的 backdrop 结果, 不发请求也不读盘.
     *
     * 给"上一个页面早就查过同一条目"的场景做首帧初值用 (TV 探索/搜索/时间表页聚焦时会预取
     * 背景图, 点进详情页时结果就在这张表里). 首帧直接拿到 URL 意味着图还在 Coil 内存缓存里,
     * 详情页 Hero 一进场就是满的, 没有"先空着再淡入"那一下.
     *
     * @return URL; `""` = 已确认无图 (调用方应走无图回退); `null` = 本进程还没解析过, 按加载中处理.
     */
    fun peekBackdropUrl(subjectId: Int): String? = resolvedBackdropUrls[subjectId]

    private fun rememberResolvedBackdrop(subjectId: Int, url: String) {
        resolvedBackdropUrls = resolvedBackdropUrls + (subjectId to url)
    }

    /**
     * 获取条目横版背景图 URL (w1280). [originalName] 为日文原名 (SubjectInfo.name).
     * 找不到或未配置 token 时返回 null.
     *
     * @param activeAsOfDate 该条目最新已播集的日期 (`YYYY-MM-DD`), 拿不到分集时可传开播日期.
     *   决定负缓存的有效期 (见 [negativeCacheTtl]); 不传则负缓存永久有效 (旧行为).
     */
    suspend fun getBackdropUrl(
        subjectId: Int,
        originalName: String,
        activeAsOfDate: String? = null,
    ): String? {
        // 本进程已解析出 URL 的直接给结果, 连 withContext 与读盘都省掉 (正缓存永久有效, 读盘只会
        // 拿到同一个 URL). 页面级的薄映射 (如 TvHeroMediaCache.backdrops) 超量时是整表清空的,
        // 清空后成批条目会重新走到这里 —— 少了这道短路, 早就解析过的卡要等一次 DataStore 读盘
        // 才拿回图, hero 背景当场空一下再淡回来.
        //
        // 负缓存 ("") 故意不在这里短路: 它该不该重取取决于 activeAsOfDate 与重取闸门, 而这张表
        // 只记结果不记时间, 短路会把"传了更近播出日期本该重取一次"的条目钉死到进程结束.
        resolvedBackdropUrls[subjectId]?.takeIf { it.isNotEmpty() }?.let { return it }
        return resolveBackdropUrl(subjectId, originalName, activeAsOfDate)
    }

    /** 读盘 / 走网络的慢路径, 仅由 [getBackdropUrl] 在进程内热缓存未命中时调用. */
    private suspend fun resolveBackdropUrl(
        subjectId: Int,
        originalName: String,
        activeAsOfDate: String?,
    ): String? = withContext(ioDispatcher) {
        if (currentAniBuildConfig.tmdbApiToken.isBlank() || originalName.isBlank()) return@withContext null

        val cache = readCache()
        cache.backdropUrls[subjectId]?.let { cached ->
            if (cached.isNotEmpty()) {
                // 正缓存永久有效: URL 拿到就不会变
                rememberResolvedBackdrop(subjectId, cached)
                return@withContext cached
            }
            // 负缓存: 过期才重取, 且闸门保证进程内每条目只放行一次 ——
            // TMDB 侧确实没图时, 反复进出详情页不会反复空拉
            val stale = negativeCacheStale(cache.backdropMissAt[subjectId], activeAsOfDate)
            if (!backdropRefreshGate.shouldRefresh(subjectId) { stale }) {
                rememberResolvedBackdrop(subjectId, "")
                return@withContext null
            }
            logger.info { "Retrying TMDB backdrop for subject $subjectId (negative cache expired)" }
        }

        val path = try {
            searchLayered(originalName, { resolveLineageOrNull(subjectId, originalName)?.rootName }) { query ->
                searchBackdropPath(query)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to search TMDB backdrop for subject $subjectId, will retry next time" }
            return@withContext null // 网络错误不写缓存, 下次进页面重试
        }

        val url = path?.let { "$IMAGE_BASE_URL$it" }
        logger.info { "TMDB backdrop for subject $subjectId: ${url ?: "not found"}" }
        dataStore.updateData {
            it.copy(
                backdropUrls = it.backdropUrls + (subjectId to (url ?: "")),
                // 拿到图就清掉时间戳, 免得这个 map 随收藏量无限增长
                backdropMissAt = if (url != null) {
                    it.backdropMissAt - subjectId
                } else {
                    it.backdropMissAt + (subjectId to currentTimeMillis())
                },
            )
        }
        rememberResolvedBackdrop(subjectId, url ?: "")
        url
    }

    /**
     * 获取条目在 TMDB 上的全部横版剧照 (backdrop) URL (w1280), 用于 TV 屏保轮播.
     *
     * 条目匹配与 [getBackdropUrl] 同一套三层搜索; 命中后再拉 `/images` 一次取全量
     * (不带 language 参数, backdrop 基本都是无语言图, 过滤反而会漏).
     * 找不到条目或未配置 token 时返回空列表, 调用方跳过该动画.
     * 结果按 subjectId 持久缓存 (空列表 = 已确认无图的负缓存); 网络错误不缓存.
     */
    suspend fun getAllBackdropUrls(
        subjectId: Int,
        originalName: String,
        activeAsOfDate: String? = null,
    ): List<String> = withContext(ioDispatcher) {
        if (currentAniBuildConfig.tmdbApiToken.isBlank() || originalName.isBlank()) return@withContext emptyList()

        val cache = readCache()
        cache.allBackdrops[subjectId]?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached
            val stale = negativeCacheStale(cache.allBackdropsMissAt[subjectId], activeAsOfDate)
            if (!allBackdropsRefreshGate.shouldRefresh(subjectId) { stale }) return@withContext cached
            logger.info { "Retrying TMDB backdrops for subject $subjectId (negative cache expired)" }
        }

        val urls = try {
            searchLayered(originalName, { resolveLineageOrNull(subjectId, originalName)?.rootName }) { query ->
                searchAnimeRef(query)
            }?.let { fetchBackdropPaths(it) }
                .orEmpty()
                .take(MAX_BACKDROPS_PER_SUBJECT)
                .map { "$IMAGE_BASE_URL$it" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch TMDB backdrops for subject $subjectId, will retry next time" }
            return@withContext emptyList() // 网络错误不写缓存, 下次重试
        }

        logger.info { "TMDB backdrops for subject $subjectId: ${urls.size}" }
        dataStore.updateData {
            it.copy(
                allBackdrops = it.allBackdrops + (subjectId to urls),
                allBackdropsMissAt = if (urls.isNotEmpty()) {
                    it.allBackdropsMissAt - subjectId
                } else {
                    it.allBackdropsMissAt + (subjectId to currentTimeMillis())
                },
            )
        }
        urls
    }

    /** 跨类型取匹配条目的引用 (type + id), 档次顺序与 [searchBackdropPath] 一致. */
    private suspend fun searchAnimeRef(query: String): TmdbMediaRef? {
        val tv = searchAnime(query, "tv")
        tv.primary.firstNotNullOfOrNull { it.id }?.let { return TmdbMediaRef("tv", it) }
        val movie = searchAnime(query, "movie")
        return movie.primary.firstNotNullOfOrNull { it.id }?.let { TmdbMediaRef("movie", it) }
            ?: tv.fallback.firstNotNullOfOrNull { it.id }?.let { TmdbMediaRef("tv", it) }
            ?: movie.fallback.firstNotNullOfOrNull { it.id }?.let { TmdbMediaRef("movie", it) }
    }

    /** `/{type}/{id}/images` 的全部 backdrop 路径 (TMDB 已按投票排序). */
    private suspend fun fetchBackdropPaths(ref: TmdbMediaRef): List<String> = client.use {
        val body = get("$API_BASE_URL/${ref.type}/${ref.id}/images") {
            bearerAuth(currentAniBuildConfig.tmdbApiToken)
            shortConnectTimeout()
        }.bodyAsText()
        json.decodeFromString(TmdbImagesResponse.serializer(), body).backdrops.mapNotNull { it.filePath }
    }

    /**
     * 获取条目所有分集数据 (缩略图 / 时长 / [language] 语言的简介) 索引.
     *
     * 主键是播出日期而非集号: TMDB 与 Bangumi 的季/集划分对不齐
     * (分割放送合并为一季、Bangumi 跨季连续编号), 播出日期是唯一可靠的对应关系.
     * 仅当 TMDB 上该剧只有一季正片时才另存按集号的索引 (此时两边集号一一对应),
     * 供 Bangumi 无分集播出日期的老番兜底 (如 1997 剑风传奇, Bangumi 全部分集无日期).
     *
     * 元数据按季一次性拉取 (一季一个请求) 并按 subjectId 持久缓存; 缓存记录抓取语言,
     * 用户切换 APP 语言后按新语言重取 (简介是本地化字段).
     * 图片本体由 UI 层 (LazyRow + coil) 惰性加载, 此处只返回 URL.
     *
     * @param language TMDB 语言码 (如 `zh-CN`), 决定简介语言.
     * @param newestWantedAirDate 调用方希望缓存覆盖到的最新播出日期 (`YYYY-MM-DD`).
     *   缓存是按条目永久保存的, 连载番早先拉取的缓存不含之后新播的集; 传入此参数后,
     *   若缓存最新日期落后于它 (超出 ±1 天匹配容差), 经 [stillsRefreshGate] 放行
     *   (进程内每条目最多一次, 防 TMDB 自身滞后时反复空拉) 重取一次.
     */
    suspend fun getEpisodeStills(
        subjectId: Int,
        originalName: String,
        language: String,
        newestWantedAirDate: String? = null,
    ): TmdbEpisodeStills =
        withContext(ioDispatcher) {
            if (currentAniBuildConfig.tmdbApiToken.isBlank() || originalName.isBlank()) {
                return@withContext TmdbEpisodeStills()
            }

            val cached = readCache().episodeStills[subjectId]?.takeIf { it.language == language }
            if (cached != null) {
                val refresh = newestWantedAirDate != null &&
                    stillsRefreshGate.shouldRefresh(subjectId) { !cached.coversAirDate(newestWantedAirDate) }
                if (!refresh) return@withContext cached
            }

            val stills = try {
                fetchEpisodeStills(subjectId, originalName, language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to fetch TMDB episode stills for subject $subjectId, will retry next time" }
                // 网络错误不写缓存; 陈旧重取失败时继续用旧缓存, 首次拉取失败下次进页面重试
                return@withContext cached ?: TmdbEpisodeStills()
            }

            // 陈旧重取拿到空结果 (如 TMDB 瞬时搜索不中) 时保留旧缓存, 不用坏数据覆盖好数据
            if (cached != null && stills.isEmpty() && !cached.isEmpty()) {
                logger.info { "TMDB episode stills refresh for subject $subjectId returned empty, keeping cached" }
                return@withContext cached
            }

            logger.info {
                "TMDB episode stills for subject $subjectId (lang=$language): " +
                    "${stills.byAirDate.size} by air date, ${stills.byEpisodeNumber.size} by episode number"
            }
            dataStore.updateData {
                it.copy(episodeStills = it.episodeStills + (subjectId to stills))
            }
            stills
        }

    /** 分集缓存的陈旧重取闸门: 进程内每条目最多放行一次, 见 [getEpisodeStills]. */
    private val stillsRefreshGate = StaleRefreshGate<Int>()

    /** backdrop 负缓存的重取闸门 (与 [allBackdropsRefreshGate] 分开计次), 见 [negativeCacheStale]. */
    private val backdropRefreshGate = StaleRefreshGate<Int>()

    /** 全量剧照负缓存的重取闸门. */
    private val allBackdropsRefreshGate = StaleRefreshGate<Int>()

    /**
     * 负缓存 ("TMDB 上没有这张图") 还能不能相信.
     *
     * 图和标题都是 TMDB 社区在开播后陆续补的, 所以新番的"没有"往往只是"还没有" ——
     * 一次空结果被永久缓存的后果是: 之后 TMDB 补了图, 这个条目也永远不会再查一次
     * (表现为"别人有图我没有", 而代理测试里 TMDB 全绿, 因为压根没发请求).
     *
     * @param missAt 负缓存写入时刻; null = 旧缓存没记时间, 给一次重取机会
     *   (这样就不必像匹配算法变更那样 bump [TmdbImageCache.CURRENT_VERSION] 作废整个缓存,
     *   代价从"所有条目重新搜索"降到"只重取负缓存那几条")
     */
    private fun negativeCacheStale(missAt: Long?, activeAsOfDate: String?): Boolean {
        if (missAt == null) return true
        val ttl = negativeCacheTtl(activeAsOfDate) ?: return false
        // 相减而非比较绝对值: 时钟回拨得到负数, 自然判为未过期, 不会因为系统时间乱跳而反复重取
        return currentTimeMillis() - missAt >= ttl.inWholeMilliseconds
    }

    /**
     * 负缓存有效期; null = 永久.
     *
     * 判据是"这部番有多活"而非"开播多久": 两年前开播但仍在连载的长番, 按开播日期会被误判成
     * 老番而拿到永久负缓存. 因此 [activeAsOfDate] 取最新已播集的日期 (口径同
     * [getEpisodeStills] 的 `newestWantedAirDate`), 调用方拿不到分集时退化为开播日期.
     */
    private fun negativeCacheTtl(activeAsOfDate: String?): Duration? {
        val aired = activeAsOfDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return when (aired.daysUntil(today)) {
            // 还在播或刚完结: TMDB 正在陆续补图, 最坏等三天
            in Int.MIN_VALUE..NEGATIVE_CACHE_AIRING_DAYS -> NEGATIVE_CACHE_TTL_AIRING
            // 补图概率已低, 但不能说没有
            in (NEGATIVE_CACHE_AIRING_DAYS + 1)..NEGATIVE_CACHE_RECENT_DAYS -> NEGATIVE_CACHE_TTL_RECENT
            // 一年都没人补, 基本不会再有; 屏保轮播会扫全部收藏, 老番参与重试会明显放大请求
            else -> null
        }
    }

    private suspend fun fetchEpisodeStills(
        subjectId: Int,
        originalName: String,
        language: String,
    ): TmdbEpisodeStills = client.use {
        val token = currentAniBuildConfig.tmdbApiToken
        // 血统判定在搜索前主动做 (搜索层只在直搜落空时才需要根条目名):
        // 建索引时要用"是否衍生作"决定 season 0 的取舍, 见下方季循环.
        val lineage = resolveLineageOrNull(subjectId, originalName)

        // 逐层搜索 (层次同 searchLayered), 对确认正传的条目多一条规则: 首候选 (完整条目名)
        // 的非精确标题命中不可信 —— 超集标题常是拆分出的兄弟条目 (如 "進撃の巨人 The Final
        // Season" 命中单集条目 "…完結編(後編)"), 正传季应归属母条目; 暂存该命中, 根条目名
        // 与削字候选全落空时才回退采用. 衍生作不受影响 (正确条目常常正是超集标题, 如
        // デート・ア・バレット 前編, 必须直搜命中).
        val nameCandidates = searchQueryCandidates(originalName)
        val tried = mutableSetOf<String>()
        var acceptedId: Int? = null
        var matchedQuery: String? = null
        var tentativeId: Int? = null
        suspend fun trySearch(query: String): Boolean {
            if (!tried.add(query)) return false
            val result = searchAnime(query, "tv")
                .let { it.primary.firstOrNull() ?: it.fallback.firstOrNull() } ?: return false
            val id = result.id ?: return false
            if (lineage?.isDerivative == false && query == nameCandidates.firstOrNull() &&
                !result.hasExactTitle(normalizeForMatch(query))
            ) {
                tentativeId = id
                return false
            }
            acceptedId = id
            matchedQuery = query
            return true
        }
        run {
            nameCandidates.firstOrNull()?.let { if (trySearch(it)) return@run }
            lineage?.rootName?.let { root ->
                searchQueryCandidates(root).forEach { if (trySearch(it)) return@run }
            }
            nameCandidates.drop(1).forEach { if (trySearch(it)) return@run }
        }
        if (acceptedId == null && tentativeId != null) {
            acceptedId = tentativeId
            matchedQuery = nameCandidates.firstOrNull()
        }
        val tvId = acceptedId ?: return@use TmdbEpisodeStills()
        // 排查错配时可据此人工核对 tvId 指向的剧对不对
        logger.info { "TMDB tv match for subject $subjectId: https://www.themoviedb.org/tv/$tvId" }

        // language: 顺带取整部剧的本地化简介 (Bangumi 简介为日文原文时整段替换用);
        // TMDB 无该语言翻译时 overview 为空串, 存 null 由 Bangumi 简介兜底
        val detailBody = get("$API_BASE_URL/tv/$tvId") {
            parameter("language", language)
            bearerAuth(token)
            shortConnectTimeout()
        }.bodyAsText()
        val detail = json.decodeFromString(TmdbTvDetail.serializer(), detailBody)
        val seasons = detail.seasons
        val singleSeason = seasons.count { it.seasonNumber > 0 } == 1

        // 确认正传的条目把 season 0 (特别篇) 排在正片之后入索引: TMDB 常把同期放送的
        // 衍生短篇挂在正传条目的特别篇下, 且与正片同日播出 (如 Re:ゼロ休憩時間 4th 与
        // 正传 4th season 喪失編 逐集同日), S0 先入索引会让正传分集错拿短篇的数据 ——
        // 殿后使同日对位优先取正片; 不整个跳过是因为正传的第0话这类特别篇只存在于 S0
        // (如 無職転生Ⅱ 第0集), 日期只在 S0 出现时仍要能命中. 判定失败
        // (关系数据缺失/请求失败) 时维持原顺序.
        val specialsLast = lineage?.isDerivative == false
        // 反向: 衍生条目靠根条目名才归并到本篇的 (直搜自己的名字落空), 说明它没有独立
        // TMDB 条目, 分集必然在本篇的 S0 里 —— 只索引 S0. 否则正片与短篇播出日差 ±1 天时
        // (如 休憩時間 3rd 比正传晚一天), 精确日期会先命中正片, 衍生分集错拿正片数据.
        // 直搜命中自己条目的衍生作 (如有独立条目的外传) 走正常全量索引.
        val matched = matchedQuery
        val rootName = lineage?.rootName
        val specialsOnly = lineage?.isDerivative == true && matched != null &&
            rootName != null && matched in searchQueryCandidates(rootName)
        val indexedSeasons = when {
            specialsOnly -> seasons.filter { it.seasonNumber == 0 }
            specialsLast -> seasons.sortedBy { if (it.seasonNumber == 0) 1 else 0 }
            else -> seasons
        }
        val byAirDate = mutableMapOf<String, MutableList<TmdbEpisodeMedia>>()
        val byEpisodeNumber = mutableMapOf<Int, TmdbEpisodeMedia>()
        val specialsByNumber = mutableMapOf<Int, TmdbEpisodeMedia>()
        for (season in indexedSeasons) {
            // language: 分集简介取该语言的翻译 (无翻译时 overview 为空, 由 Bangumi 简介兜底);
            // still/时长/日期与语言无关.
            val seasonBody = get("$API_BASE_URL/tv/$tvId/season/${season.seasonNumber}") {
                parameter("language", language)
                bearerAuth(token)
                shortConnectTimeout()
            }.bodyAsText()
            for (ep in json.decodeFromString(TmdbSeasonDetail.serializer(), seasonBody).episodes) {
                val media = TmdbEpisodeMedia(
                    stillUrl = ep.stillPath?.let { "$STILL_IMAGE_BASE_URL$it" },
                    runtimeMinutes = ep.runtime?.takeIf { it > 0 },
                    overview = ep.overview?.trim()?.takeIf { it.isNotBlank() },
                )
                // 同一天可能有多集 (双集连播首播, 如 無職転生Ⅲ 第1+2话), 按集号顺序追加成列表,
                // 匹配侧按 Bangumi "当日第几集" 对位取用. 字段全空的集也要占位, 保持对位不错乱.
                ep.airDate?.let { byAirDate.getOrPut(it) { mutableListOf() }.add(media) }
                if (singleSeason && season.seasonNumber == 1) {
                    ep.episodeNumber?.let { byEpisodeNumber[it] = media }
                }
                if (season.seasonNumber == 0) {
                    ep.episodeNumber?.let { specialsByNumber[it] = media }
                }
            }
        }

        // S0 集名索引 (剧的原语言): 特别篇在 Bangumi 与 TMDB 的播出日期记录常有出入
        // (如 転スラ "救われるラミリス 後編" 两边差 8 天), 日期对不上时匹配侧用
        // "集名精确一致"兜底. 名字必须按原语言再取一次 S0 —— 上面按 APP 语言取的是
        // 译名, 与 Bangumi 中文名是不同来源的译文, 几乎必然对不上 (菈/拉之差);
        // Bangumi 的原名与 TMDB 原语言名才能逐字一致. 重名的集直接全部丢弃, 保精度.
        val byName = mutableMapOf<String, TmdbEpisodeMedia?>()
        if (specialsByNumber.isNotEmpty()) {
            val originalLanguage = detail.originalLanguage?.takeIf { it.isNotBlank() } ?: "ja"
            val s0Body = get("$API_BASE_URL/tv/$tvId/season/0") {
                parameter("language", originalLanguage)
                bearerAuth(token)
                shortConnectTimeout()
            }.bodyAsText()
            for (ep in json.decodeFromString(TmdbSeasonDetail.serializer(), s0Body).episodes) {
                val media = ep.episodeNumber?.let { specialsByNumber[it] } ?: continue
                val key = ep.name?.let { normalizeForMatch(it) }?.takeIf { it.isNotEmpty() } ?: continue
                byName[key] = if (key in byName) null else media
            }
        }

        TmdbEpisodeStills(
            byAirDate,
            byEpisodeNumber,
            language,
            showOverview = detail.overview?.trim()?.takeIf { it.isNotBlank() },
            specialsByName = byName.mapNotNull { (k, v) -> v?.let { k to it } }.toMap(),
        )
    }

    /**
     * 三层搜索, 层内层间都短路 (命中即停, 已试过的词不重试):
     *
     * 1. 原名直搜 — 有独立 TMDB 条目的剧场版/衍生作 (如 デート・ア・バレット) 必须先命中
     *    自己的条目, 回溯放前面会把它们错误归并到母番;
     * 2. Bangumi 关联条目回溯到根条目再搜 — 数据驱动, 覆盖 "Re:ゼロから始める休憩時間"
     *    这类换名短篇 (任何削字规则都不可解); 根条目名也过一遍削字候选;
     * 3. 削字规则兜底 — Bangumi 关系数据缺失的条目仍靠它.
     */
    private suspend fun <R : Any> searchLayered(
        originalName: String,
        resolveRootName: suspend () -> String?,
        search: suspend (query: String) -> R?,
    ): R? {
        val tried = mutableSetOf<String>()
        suspend fun trySearch(query: String): R? = if (tried.add(query)) search(query) else null

        val nameCandidates = searchQueryCandidates(originalName)
        nameCandidates.firstOrNull()?.let { trySearch(it) }?.let { return it }
        resolveRootName()?.let { rootName ->
            searchQueryCandidates(rootName).forEach { candidate ->
                trySearch(candidate)?.let { return it }
            }
        }
        nameCandidates.drop(1).forEach { candidate ->
            trySearch(candidate)?.let { return it }
        }
        return null
    }

    /**
     * 沿 Bangumi 关联条目回溯"血统": 每跳优先「主线故事」(从番外/短篇跳回本篇),
     * 其次「前传」(沿季链上溯), 走到没有出边为止 —— 通常是第一季, 名字最干净, 正对应
     * TMDB "一个剧条目含全部季"的组织方式. 带环路保护与跳数上限.
     *
     * 顺带判定正传/衍生: 链上任何一跳出现「主线故事」出边即为衍生 (衍生/番外条目才有
     * 这种指回本篇的边, 正传季只有前传/续集); 整链只走前传则确认正传. 时长比对方案
     * (正片 ~24min vs 短篇 ~2min) 曾作备选, 但未播出的集两边时长都缺, 关系判定不依赖
     * 播出数据, 更稳.
     *
     * 直接调 Bangumi v0 公开 API 而非 Ani API: 后者服务端会过滤掉「主线故事」关系
     * (实测 getRelatedSubjects 对 Re:ゼロ休憩時間只返回续集). 失败返回 null, 不影响兜底.
     */
    private suspend fun resolveLineageOrNull(subjectId: Int, originalName: String): BgmLineage? = try {
        var currentId = subjectId
        var rootName: String? = null
        var sawMainStoryEdge = false
        val seen = mutableSetOf(subjectId)
        var hops = 0
        while (hops < MAX_RELATION_HOPS) {
            val body = client.use {
                get("$BANGUMI_API_BASE_URL/v0/subjects/$currentId/subjects") {
                    shortConnectTimeout()
                }.bodyAsText()
            }
            val relations = json.decodeFromString(ListSerializer(BgmRelatedSubject.serializer()), body)
                .filter { it.type == BGM_SUBJECT_TYPE_ANIME }
            val mainStory = relations.firstOrNull { it.relation == "主线故事" }
            if (mainStory != null) sawMainStoryEdge = true
            val next = mainStory
                ?: relations.firstOrNull { it.relation == "前传" }
                ?: break
            if (!seen.add(next.id)) break
            currentId = next.id
            if (next.name.isNotBlank()) rootName = next.name
            hops++
        }
        BgmLineage(
            rootName = rootName?.takeIf { it != originalName },
            isDerivative = sawMainStoryEdge,
        ).also {
            logger.info {
                "Resolved lineage for $subjectId: root=${it.rootName ?: "(self)"}, " +
                    "derivative=${it.isDerivative} ($hops hops)"
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.warn(e) { "Failed to resolve lineage via Bangumi relations for subject $subjectId" }
        null
    }

    /**
     * 跨类型按信号强弱取 backdrop: tv 动画 → movie 动画 → tv 兜底 → movie 兜底.
     * 兜底档 (genre 缺失 + 日语原声) 必须排在两个类型的动画档之后 —— 否则舞台剧/纪录片
     * 这类无 genre 条目会抢在真正的动画前面 (实测 "千と千尋の神隠し" 的 tv 搜索首位是
     * 舞台剧纪录片, 无 genre、日语、标题含全部查询词, 正确的 movie 条目反而排在了后面).
     * tv 动画档命中时不发 movie 请求 (最常见情形保持单请求).
     */
    private suspend fun searchBackdropPath(query: String): String? {
        val tv = searchAnime(query, "tv")
        tv.primary.firstNotNullOfOrNull { it.backdropPath }?.let { return it }
        val movie = searchAnime(query, "movie")
        return movie.primary.firstNotNullOfOrNull { it.backdropPath }
            ?: tv.fallback.firstNotNullOfOrNull { it.backdropPath }
            ?: movie.fallback.firstNotNullOfOrNull { it.backdropPath }
    }

    /**
     * TMDB 搜索, 结果限定为动画且标题须与查询词逐词匹配.
     *
     * 动画过滤: TMDB 会把同名真人版排在动画前面 (如 ONE PIECE 首位是 Netflix 真人剧),
     * 必须按 genre 16 (Animation) 过滤; 个别条目缺失 genre 数据, 用日语原声兜底.
     * 全都不是动画时宁可不出图也不出真人版.
     *
     * 标题校验: TMDB 的模糊搜索对短查询词会返回貌似相关的错误条目 (实测 "うらおん!"
     * 返回 "うらみちお兄さん", "君の名は。" 的 tv 搜索返回 "君の魔名はリナ・ウィッチ..."),
     * 要求查询词的每个分词都作为子串出现在结果标题里 —— 标题多出词允许 (如
     * "デート・ア・バレット 前編 デッド・オア・バレット" 命中不带 "前編" 的查询词),
     * 插字/换字则拒绝. 校验失败宁可无结果, 交给下一层候选 (关联回溯/削字).
     */
    private suspend fun searchAnime(query: String, type: String): TmdbAnimeSearchResults = client.use {
        val body = get("$API_BASE_URL/search/$type") {
            parameter("query", query)
            parameter("include_adult", "true")
            bearerAuth(currentAniBuildConfig.tmdbApiToken)
            shortConnectTimeout()
        }.bodyAsText()
        val tokens = tokenizeForMatch(query)
        val queryNormalized = normalizeForMatch(query)
        val results = json.decodeFromString(TmdbSearchResponse.serializer(), body).results
        val anime = results.filter { GENRE_ANIMATION in it.genreIds }
        val matched = anime.filter { it.matchesTokens(tokens) }
            .ifEmpty {
                // 主标题没匹配上时查别名再校验一次: TMDB 模糊搜索能命中而主标题不含查询词,
                // 通常是别名在起作用 (如 JoJo 主条目别名含 "スティール・ボール・ラン ジョジョの奇妙な冒険").
                // 只查最靠前的 2 个结果, 且仅发生在失败路径, 结果又按条目持久缓存, 成本一次性.
                anime.take(2).filter { result ->
                    val id = result.id ?: return@filter false
                    val altTitles = runCatching { fetchAlternativeTitles(id, type) }.getOrElse { emptyList() }
                    altTitles.isNotEmpty() && result.matchesTokens(tokens, altTitles)
                }
            }
            // 标题与查询完全一致的排最前: "标题多出词允许"会让外传/衍生作也通过校验
            // (如搜 DanMachi 正传名, TMDB 把外传 "ソード・オラトリア ...だろうか外伝" 排在
            // 正传前面, 外传标题包含完整正传名), 完全一致的正传必须优先; 稳定排序,
            // 无完全一致时保持 TMDB 原序 (前編/後編这类只有超集标题的场景不受影响).
            .sortedByDescending { it.hasExactTitle(queryNormalized) }
        TmdbAnimeSearchResults(
            primary = matched,
            // 兜底档只做主标题校验, 不值得为弱信号再发别名请求
            fallback = results.filter { it.genreIds.isEmpty() && it.originalLanguage == "ja" }
                .filter { it.matchesTokens(tokens) },
        )
    }

    private suspend fun fetchAlternativeTitles(id: Int, type: String): List<String> = client.use {
        val body = get("$API_BASE_URL/$type/$id/alternative_titles") {
            bearerAuth(currentAniBuildConfig.tmdbApiToken)
            shortConnectTimeout()
        }.bodyAsText()
        val parsed = json.decodeFromString(TmdbAlternativeTitles.serializer(), body)
        (parsed.results + parsed.titles).mapNotNull { it.title }
    }

    /**
     * 生成搜索候选名, 依次尝试: 原名 → 去掉 OVA/OAD 类关键字 → 从季标记处截断 →
     * 去掉罗马数字季号 → 去掉尾部裸数字季号 → 末尾非文字字符逐个回退 →
     * (仅 OVA 条目) 逐词去尾回退到母番名.
     *
     * 候选是懒惰短路搜索的 (firstNotNullOfOrNull): 前面的候选命中后, 后面的不发请求;
     * 结果按条目持久缓存, 只有全部规则落空的条目才会把候选走到底, 多出的查询成本一次性.
     *
     * TMDB 把分割放送/续季并进同一个剧条目, 用 Bangumi 本季条目名常搜不到
     * (如 "無職転生 ～...～ 第2クール" 0 结果, 去后缀即命中); 季标记后面可能还跟着
     * 篇章名 (如 "Re:ゼロ... 4th season 喪失編"), 所以从标记处截断到串尾;
     * 序数词式 ("4th season") 与 "Season 4" 式都要认.
     *
     * OVA/OAD 在 TMDB 中是母番的特别篇 (season 0), 已被分集索引覆盖且按播出日期
     * (发售日) 可精确匹配 (实测 進撃の巨人 OAD、DanMachi 各季 OVA 均逐日对上),
     * 所以只需把条目名还原成母番名: 去掉关键字直接搜 (含副标题也常能命中, 如
     * "進撃の巨人 悔いなき選択"), 搜不到再逐词去掉尾部副标题.
     */
    private fun searchQueryCandidates(name: String): List<String> = buildList {
        fun addCandidate(candidate: String) {
            val trimmed = candidate.replace(Regex("""\s+"""), " ").trim()
            if (trimmed.isNotBlank() && trimmed !in this) add(trimmed)
        }
        addCandidate(name)

        val ovaMode = OVA_KEYWORD_REGEX.containsMatchIn(name)
        val base = if (ovaMode) name.replace(OVA_KEYWORD_REGEX, " ") else name
        addCandidate(base)

        val suffixStripped = base
            .replace(Regex("""第\s*\d+\s*(クール|期|部|シーズン|季).*$"""), "")
            .replace(
                Regex("""\s(?:(?:Part|Season|Cour)\s*\d+|\d+(?:st|nd|rd|th)\s+Season)\b.*$""", RegexOption.IGNORE_CASE),
                "",
            )
        addCandidate(suffixStripped)
        val romanStripped = suffixStripped.replace(Regex("""[ⅡⅢⅣⅤⅥⅦⅧⅨⅩ]"""), "")
        addCandidate(romanStripped)
        // 裸数字季号: 续季常直接在名字尾部跟数字 (如 "有頂天家族2" — TMDB 只有 "有頂天家族" 一个剧条目).
        // 只认 1-2 位, 3 位以上视为名字本体 (如 "モブサイコ100"); 且作为末位候选,
        // 仅在前面候选全部落空时才轮到, 名字本体恰好以数字结尾的条目会先被原名命中.
        // (下面的逐字符回退不适用纯拉丁名, 这条规则保留给它们, 如 "STEINS;GATE 0".)
        addCandidate(romanStripped.replace(Regex("""\s*[0-9０-９]{1,2}$"""), ""))

        // 末尾非文字字符逐个回退: 尾部季号/副标题形态繁多 (ASCII 罗马数字 "灼眼のシャナII"、
        // "R2"、"III -Final-" 等), 枚举不完; 从末尾逐字符去掉非日文/中文的字符, 每一步都
        // 作为候选 (先长后短, 更具体的先试). 要求剩余部分仍含日文/中文字符, 避免把
        // "BLEACH" 这类纯拉丁名逐字拆碎; 限最多回退 12 字符, 防病态长尾.
        var walked = romanStripped
        var steps = 0
        while (steps < 12) {
            val trimmed = walked.trimEnd()
            val last = trimmed.lastOrNull() ?: break
            if (last.isCjkOrKana()) break
            walked = trimmed.dropLast(1)
            steps++
            if (walked.none { it.isCjkOrKana() }) break
            addCandidate(walked)
        }

        if (ovaMode) {
            // OVA 副标题搜不到时逐词回退 (如 "進撃の巨人 悔いなき選択" → "進撃の巨人"), 最多 3 层
            var truncated = romanStripped.replace(Regex("""\s+"""), " ").trim()
            var depth = 0
            while (depth < 3 && truncated.contains(' ')) {
                truncated = truncated.substringBeforeLast(' ').trim()
                addCandidate(truncated)
                depth++
            }
        }
    }

    /**
     * 读缓存; 版本不符时整体作废重建 —— 匹配算法变更后旧结果可能是错的
     * (如动画过滤加入前 ONE PIECE 缓存了真人剧的 backdrop).
     */
    private suspend fun readCache(): TmdbImageCache {
        val cache = dataStore.data.first()
        if (cache.version == TmdbImageCache.CURRENT_VERSION) return cache
        return dataStore.updateData { TmdbImageCache(version = TmdbImageCache.CURRENT_VERSION) }
    }

    private companion object {
        private val logger = logger<TmdbImageService>()
        private const val API_BASE_URL = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w1280"
        private const val GENRE_ANIMATION = 16
        private const val BANGUMI_API_BASE_URL = "https://api.bgm.tv"
        private const val BGM_SUBJECT_TYPE_ANIME = 2

        /** 关联回溯跳数上限 (实测常见链 1-2 跳, 上限只是环路/脏数据保险). */
        private const val MAX_RELATION_HOPS = 8

        /** 最新已播集在此天数内 = 还在播或刚完结, 负缓存按 [NEGATIVE_CACHE_TTL_AIRING] 失效. */
        private const val NEGATIVE_CACHE_AIRING_DAYS = 60

        /** 最新已播集在此天数内 = 近作, 负缓存按 [NEGATIVE_CACHE_TTL_RECENT] 失效; 更早则永久. */
        private const val NEGATIVE_CACHE_RECENT_DAYS = 365

        private val NEGATIVE_CACHE_TTL_AIRING = 3.days
        private val NEGATIVE_CACHE_TTL_RECENT = 30.days

        /** 单条目剧照上限 (屏保轮播用不到更多, 控制缓存体积). */
        private const val MAX_BACKDROPS_PER_SUBJECT = 20

        /** OVA/OAD/特别篇类关键字: 触发母番名还原 (这些内容在 TMDB 里是母番的 season 0 特别篇). */
        private val OVA_KEYWORD_REGEX =
            Regex("""(?i)\b(?:OVA|OAD)S?\b|特別[編篇]|特别篇|スペシャル""")

        /**
         * 分集 still 官方档位只有 w92/w185/w300/original, w300 太糊, 存原图档 URL;
         * 消费端按用途降档: 选集卡片 [tmdbStillCardSizeUrl] (w780), 全屏 hero 背景
         * [tmdbStillHeroSizeUrl] (w1280) —— 都不直接解码原图.
         */
        private const val STILL_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original"
    }
}

/**
 * 把 original 档的 TMDB still URL 降到卡片档: 选集卡片目标尺寸远小于全屏, 原图档
 * (1920 级, ~180KB) 的下载/解码是纯浪费. w780 不在 still 官方档位表里, 但 CDN
 * 实测同样支持 (~40KB), 且远高于卡片所需分辨率. 非 TMDB original URL 原样返回.
 */
fun tmdbStillCardSizeUrl(url: String): String = url.replace("/t/p/original/", "/t/p/w780/")

/**
 * 把 original 档的 TMDB still URL 降到全屏 hero 背景档 (探索/追番页"下一集剧照"背景):
 * w1280 铺 4K backdrop 约 2 倍放大, 经渐隐/压暗后 10-foot 距离不可辨; 原图档偶有 4K 级
 * (解码位图 8-33MB), 低端盒子上每次聚焦换卡都是一记下载+解码重锤 (2026-07-31 性能整改).
 * 非 TMDB original URL 原样返回.
 *
 * [fullQuality] 为真时原样返回 (设置里开了完整视觉效果, 见
 * [ThemeSettings.tvFullVisualEffects][me.him188.ani.app.data.models.preference.ThemeSettings]).
 * 因此**存缓存时不要降档**, 存原图档 URL, 由显示端按当前设置现降 —— 否则改设置要清缓存才生效.
 */
fun tmdbStillHeroSizeUrl(url: String, fullQuality: Boolean = false): String =
    if (fullQuality) url else url.replace("/t/p/original/", "/t/p/w1280/")

/** 日文假名/汉字 (含中文): 候选名末尾回退时视为名字本体, 到此为止不再往前剥. */
private fun Char.isCjkOrKana(): Boolean =
    this in '぀'..'ヿ' || // 平假名 + 片假名 (含长音符 ー)
        this in '一'..'鿿' || // CJK 统一汉字
        this == '々' // 々 (叠字符)

@Serializable
data class TmdbImageCache(
    /** subjectId -> backdrop URL; 空串表示已确认 TMDB 无此条目图 (负缓存). */
    val backdropUrls: Map<Int, String> = emptyMap(),
    /** subjectId -> 分集缩略图 (按播出日期索引); 存在但为空 = 已确认无图 (负缓存). */
    val episodeStills: Map<Int, TmdbEpisodeStills> = emptyMap(),
    /** subjectId -> 全部横版剧照 URL (屏保轮播用); 空列表 = 已确认无图 (负缓存). 新字段有默认值, 不影响旧缓存. */
    val allBackdrops: Map<Int, List<String>> = emptyMap(),
    /**
     * subjectId -> [backdropUrls] 负缓存的写入时刻 (epoch millis), 决定它何时失效.
     * 新番的"没有 backdrop"通常只是"还没有" (TMDB 的图由社区在开播后陆续补), 见 [negativeCacheTtl].
     * 缺失 (旧缓存写下的负缓存) 视为已过期, 下次访问重取一次. 新字段有默认值, 不影响旧缓存.
     */
    val backdropMissAt: Map<Int, Long> = emptyMap(),
    /**
     * 同 [backdropMissAt], 对应 [allBackdrops].
     * 必须与前者分开存: 共用一份时间戳会让"单图重取成功后清除时间戳"把全量剧照的负缓存
     * 变成永久有效, 屏保轮播从此不再重试.
     */
    val allBackdropsMissAt: Map<Int, Long> = emptyMap(),
    /** 匹配算法版本, 与 [CURRENT_VERSION] 不符时整个缓存作废 (旧算法结果可能有误). */
    val version: Int = 0,
) {
    companion object {
        val Empty = TmdbImageCache()

        /**
         * v1: 搜索加入动画过滤 + 季后缀降级, 之前缓存的结果可能命中真人版, 作废.
         * v2: 分集缩略图增加单季剧的按集号索引, 旧缓存缺该字段, 作废重取.
         * v3: 季标记改为截断式且支持 "4th season" 序数词, 此前搜不到的条目留有负缓存, 作废.
         * v4: OVA/OAD 条目还原母番名搜索, 此前这类条目全是负缓存, 作废.
         * v5: 分集缩略图索引增加时长 (runtime) 字段, 旧缓存缺该数据, 作废重取.
         * v6: 支持尾部裸数字季号 (如 "有頂天家族2"), 此前这类条目全是负缓存, 作废.
         * v7: 末尾非文字字符逐个回退 (如 "灼眼のシャナII"), 同上作废负缓存.
         * v8: 新增 Bangumi 关联条目回溯层 (主线故事/前传归根), 同上作废负缓存.
         * v9: 搜索结果加标题逐词校验, 此前模糊搜索可能缓存了错误条目的图 (如
         *     "うらおん!" 命中 "うらみちお兄さん"), 作废.
         * v10: 标题校验放宽为跨标题并集 + 别名 (alternative_titles) 兜底, 混写名
         *      (BanG Dream! ゆめ∞みた) 与仅别名命中 (スティール・ボール・ラン) 的
         *      条目此前是负缓存, 作废.
         * v11: "genre 缺失 + 日语"兜底档降到所有类型的动画档之后, 此前可能缓存了
         *      舞台剧/纪录片的图 (如 千と千尋の神隠し 的舞台剧纪录片), 作废.
         * v12: 标题完全一致的结果优先于"标题多出词"的结果, 此前正传名可能命中标题
         *      包含正传全名的外传 (如 DanMachi 命中 ソード・オラトリア 外伝, 分集
         *      日期全对不上导致选集卡片无图), 作废.
         * v13: 播出日期索引改为"日期 -> 当日多集列表" (双集连播首播时后一集不再覆盖
         *      前一集, 如 無職転生Ⅲ 第1话曾显示第2话的图), 结构变更, 作废.
         * v14: 每集整合为单条目 (图 + 时长 + 新增本地化简介, 语言跟随 APP 设置), 结构变更, 作废.
         * v15: 新增整部剧的本地化简介 (showOverview, Bangumi 日文简介整段替换用), 旧缓存缺该字段, 作废重取.
         * v16: 按 Bangumi 关系链判定正传/衍生后取舍 season 0 —— 正传跳过 S0 (同期衍生短篇
         *      与正片同日播出时占据同一日期键且排在正片前面, 如 Re:ゼロ 4th season 喪失編
         *      逐集错拿休憩時間 4th 的数据); 归并到本篇的衍生条目只索引 S0 (播出日差 ±1 天时
         *      精确日期会先命中正片, 如 休憩時間 3rd 错拿正传的数据), 作废重取.
         * v17: 正传不再整个跳过 S0, 改为殿后入索引 (同日对位仍正片优先) —— 只存在于 S0 的
         *      第0话特别篇找回图 (如 無職転生Ⅱ 第0集); 正传首候选的非精确标题命中降级为
         *      暂存 (進撃の巨人 The Final Season 曾命中拆分的 完結編(後編) 单集条目);
         *      新增 S0 原语言集名索引, 日期对不上时按集名精确一致兜底 (転スラ
         *      救われるラミリス 後編 两边日期差 8 天). 结构变更, 作废重取.
         * v18: 标题校验加 Unicode 兼容折叠 (康熙部首/全角/罗马数字), 此前 TMDB 上用这类字符
         *      录入原名的条目 (如 乙女ゲー世界はモブに厳しい世界です 的 ⼄⼥) 搜索命中却被校验
         *      判为不匹配, 留下负缓存, 作废.
         */
        const val CURRENT_VERSION = 18
    }
}

/** TMDB 单个分集的展示数据: 缩略图 / 时长 / 简介, 均可缺失. */
@Serializable
data class TmdbEpisodeMedia(
    val stillUrl: String? = null,
    val runtimeMinutes: Int? = null,
    /** 分集简介 (按抓取语言本地化, 见 [TmdbEpisodeStills.language]; TMDB 无该语言翻译时为 null). */
    val overview: String? = null,
)

@Serializable
data class TmdbEpisodeStills(
    /**
     * 播出日期 `YYYY-MM-DD` -> 当日全部分集 (按集号升序).
     * 通常一天一集; 双集连播首播 (如 無職転生Ⅲ 第1+2话) 时一天多集,
     * 匹配侧按 Bangumi "当日第几集" 的序号对位.
     */
    val byAirDate: Map<String, List<TmdbEpisodeMedia>> = emptyMap(),
    /**
     * 集号 -> 分集数据; 仅当 TMDB 上该剧只有一季正片时非空
     * (多季时 Bangumi 连续编号与 TMDB 分季编号对不齐, 按集号匹配不可靠).
     * 供 Bangumi 分集无播出日期的老番兜底.
     */
    val byEpisodeNumber: Map<Int, TmdbEpisodeMedia> = emptyMap(),
    /** 抓取时用的 TMDB 语言码 (决定 overview 语言); 与当前 APP 语言不符时缓存不命中, 按新语言重取. */
    val language: String = "",
    /** 整部剧的本地化简介 ([language] 语言); TMDB 无该语言翻译或未匹配到剧时为 null. */
    val showOverview: String? = null,
    /**
     * season 0 特别篇的 "归一化原语言集名 -> 分集数据" 索引 (重名集已剔除).
     * 特别篇两边日期记录常有出入 (±1 天都够不着), 日期匹配落空时按集名精确一致兜底,
     * 用 [findSpecialByName] 查询.
     */
    val specialsByName: Map<String, TmdbEpisodeMedia> = emptyMap(),
) {
    /** 按集名 (原名/中文名等, 依次尝试) 精确匹配 season 0 特别篇; 名字归一化后比较. */
    fun findSpecialByName(vararg names: String?): TmdbEpisodeMedia? =
        names.firstNotNullOfOrNull { name ->
            name?.let { normalizeForMatch(it) }?.takeIf { it.isNotEmpty() }?.let { specialsByName[it] }
        }

    /**
     * 缓存是否已覆盖到播出日期 [date] (`YYYY-MM-DD`) 的分集.
     * 留 1 天余量 (两边日期常差一天, 匹配侧容差 ±1): 缓存最新日期 >= date-1 即视为覆盖.
     * 无任何按日期数据 (未匹配到剧/纯老番) 视为未覆盖, 由调用方的闸门限制重取频率.
     */
    fun coversAirDate(date: String): Boolean {
        val newestCached = byAirDate.keys.maxOrNull() ?: return false
        val wantedMinusSlack = runCatching {
            LocalDate.parse(date).minus(1, DateTimeUnit.DAY).toString()
        }.getOrElse { date }
        return newestCached >= wantedMinusSlack
    }

    /** 是否完全没有任何分集/剧集数据 (匹配失败或空剧). */
    fun isEmpty(): Boolean =
        byAirDate.isEmpty() && byEpisodeNumber.isEmpty() && specialsByName.isEmpty() && showOverview == null
}

/** TMDB 条目引用: 搜索命中的类型 (tv/movie) + id, 供 `/images` 等后续请求用. */
private class TmdbMediaRef(val type: String, val id: Int)

/** TMDB `/{type}/{id}/images` 响应 (只取 backdrops). */
@Serializable
private data class TmdbImagesResponse(
    val backdrops: List<TmdbImageFile> = emptyList(),
)

@Serializable
private data class TmdbImageFile(
    @SerialName("file_path") val filePath: String? = null,
)

/** Bangumi 关联条目回溯结果, 见 `resolveLineageOrNull`. */
private class BgmLineage(
    /** 根条目名 (通常是第一季); 与原名相同或没走到别的条目时为 null. */
    val rootName: String?,
    /**
     * 是否衍生/番外条目 (回溯链上出现过「主线故事」出边).
     * false = 确认正传 (整链只有前传边), 建分集索引时可放心跳过 TMDB season 0 特别篇.
     */
    val isDerivative: Boolean,
)

/** Bangumi v0 `/subjects/{id}/subjects` 关联条目; relation 是中文关系名 ("前传"/"主线故事"...). */
@Serializable
private data class BgmRelatedSubject(
    val id: Int = 0,
    val type: Int = 0,
    val name: String = "",
    val relation: String = "",
)

@Serializable
private data class TmdbSearchResponse(
    val results: List<TmdbSearchResult> = emptyList(),
)

@Serializable
private data class TmdbSearchResult(
    val id: Int? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("original_name") val originalName: String? = null, // tv
    @SerialName("original_title") val originalTitle: String? = null, // movie
    val name: String? = null, // tv 本地化标题
    val title: String? = null, // movie 本地化标题
)

/** 查询词分词: 兼容折叠后按非字母/数字切开, 小写. 用于 [TmdbSearchResult.matchesTokens]. */
private fun tokenizeForMatch(query: String): List<String> =
    foldCompatibility(query).lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }

/** 标题归一化: 兼容折叠后只保留字母/数字 (假名/汉字也是字母), 小写 —— 忽略标点/空白差异. */
private fun normalizeForMatch(s: String): String =
    foldCompatibility(s).lowercase().filter { it.isLetterOrDigit() }

/**
 * Unicode 兼容折叠 (NFKC 的子集; KMP 无标准库实现, 只覆盖标题里实际见过的类别):
 * 康熙部首 → 汉字, 全角字母/数字 → 半角, 罗马数字字符 → 拉丁字母.
 *
 * TMDB 标题由社区录入, 偶有用"看着一样但码位不同"的字符写的 —— 实测
 * "乙女ゲー世界はモブに厳しい世界です" 的原名开头是康熙部首 ⼄(U+2F04)⼥(U+2F25) 而非
 * 汉字 乙(U+4E59)女(U+5973). 搜索本身能命中 (TMDB 内部做了归一), 但标题校验逐字比较,
 * 不折叠就会把命中的正确条目判为不匹配, 表现为详情页无 backdrop、选集卡片无缩略图,
 * 且结果被负缓存. 查询词与标题两侧都要折叠才能对上.
 */
private fun foldCompatibility(s: String): String {
    if (s.none { it.compatibilityFoldOrNull() != null }) return s // 绝大多数标题无需折叠, 免去分配
    return buildString(s.length) {
        for (ch in s) append(ch.compatibilityFoldOrNull() ?: ch)
    }
}

private fun Char.compatibilityFoldOrNull(): String? = when (code) {
    in 0x2F00..0x2FD5 -> KANGXI_RADICALS[code - 0x2F00].toString()
    // 全角字母/数字与半角相差固定偏移 (全角标点会被 normalizeForMatch 直接滤掉, 无需折叠)
    in 0xFF10..0xFF19, in 0xFF21..0xFF3A, in 0xFF41..0xFF5A -> (code - 0xFEE0).toChar().toString()
    in 0x2160..0x2169 -> ROMAN_NUMERALS[code - 0x2160] // Ⅰ..Ⅹ
    in 0x2170..0x2179 -> ROMAN_NUMERALS[code - 0x2170] // ⅰ..ⅹ
    else -> null
}

/** 康熙部首 (U+2F00..U+2FD5) 按码位顺序对应的 CJK 统一汉字, 214 个. */
private const val KANGXI_RADICALS =
    "一丨丶丿乙亅二亠人儿入八冂冖冫几凵刀力勹匕匚匸十卜卩厂厶又口囗土士夂夊夕大女子宀" +
        "寸小尢尸屮山巛工己巾干幺广廴廾弋弓彐彡彳心戈戶手支攴文斗斤方无日曰月木欠止歹殳毋" +
        "比毛氏气水火爪父爻爿片牙牛犬玄玉瓜瓦甘生用田疋疒癶白皮皿目矛矢石示禸禾穴立竹米糸" +
        "缶网羊羽老而耒耳聿肉臣自至臼舌舛舟艮色艸虍虫血行衣襾見角言谷豆豕豸貝赤走足身車辛" +
        "辰辵邑酉釆里金長門阜隶隹雨靑非面革韋韭音頁風飛食首香馬骨高髟鬥鬯鬲鬼魚鳥鹵鹿麥麻" +
        "黃黍黑黹黽鼎鼓鼠鼻齊齒龍龜龠"

/** 罗马数字字符 (Ⅰ..Ⅹ / ⅰ..ⅹ) 的拉丁写法: 季号常见这种写法 (如 "無職転生Ⅱ" vs TMDB 的 "II"). */
private val ROMAN_NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

/**
 * 查询词的每个分词都出现在该条目的某个标题 (原名/本地化名/[extraTitles] 别名) 里才算匹配.
 *
 * 是"分词 → 标题集合"的并集校验, 不要求单一标题全含: 混写名只能这样匹配 —— 如
 * "BanG Dream! ゆめ∞みた", TMDB 原名是假名写法 (バンドリ！ ゆめ∞みた)、英文名是
 * 罗马字写法 (BanG Dream! YUME∞MITA), 每个标题各覆盖一半分词. 每个分词仍必须
 * 能在官方标题集里找到, 插字/换字的错误条目 (如 "君の魔名は...") 依然会被拒.
 */
private fun TmdbSearchResult.matchesTokens(tokens: List<String>, extraTitles: List<String> = emptyList()): Boolean {
    if (tokens.isEmpty()) return false
    val titles = (listOfNotNull(originalName, originalTitle, name, title) + extraTitles)
        .map(::normalizeForMatch)
    return tokens.all { token -> titles.any { it.contains(token) } }
}

/** 是否有标题与查询完全一致 (归一化后). 用于在多个通过校验的结果中把正主排到外传/衍生作之前. */
private fun TmdbSearchResult.hasExactTitle(queryNormalized: String): Boolean =
    listOfNotNull(originalName, originalTitle, name, title)
        .any { normalizeForMatch(it) == queryNormalized }

/**
 * 动画搜索结果分两档: [primary] 确认为动画 (genre 16, 标题/别名校验通过);
 * [fallback] genre 数据缺失但日语原声的弱信号兜底 —— 调用方须把它排在所有类型的
 * [primary] 之后 (见 `searchBackdropPath`), 否则舞台剧/纪录片会抢在真正的动画前面.
 */
private class TmdbAnimeSearchResults(
    val primary: List<TmdbSearchResult>,
    val fallback: List<TmdbSearchResult>,
)

/** TMDB `/{type}/{id}/alternative_titles` 响应: tv 用 `results` 字段, movie 用 `titles`. */
@Serializable
private data class TmdbAlternativeTitles(
    val results: List<TmdbAltTitle> = emptyList(),
    val titles: List<TmdbAltTitle> = emptyList(),
)

@Serializable
private data class TmdbAltTitle(val title: String? = null)

@Serializable
private data class TmdbTvDetail(
    val seasons: List<TmdbSeasonRef> = emptyList(),
    /** 整部剧的简介 (按请求的 language 本地化; 无该语言翻译时 TMDB 返回空串). */
    val overview: String? = null,
    /** 剧的原语言 (如 "ja"); S0 集名索引按原语言取名, 与 Bangumi 原名可逐字比较. */
    @SerialName("original_language") val originalLanguage: String? = null,
)

@Serializable
private data class TmdbSeasonRef(
    @SerialName("season_number") val seasonNumber: Int = 0,
)

@Serializable
private data class TmdbSeasonDetail(
    val episodes: List<TmdbEpisodeRef> = emptyList(),
)

@Serializable
private data class TmdbEpisodeRef(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    val runtime: Int? = null,
    val overview: String? = null,
    /** 集名 (按请求的 language 本地化); 仅 S0 原语言二次请求时用于建集名索引. */
    val name: String? = null,
)
