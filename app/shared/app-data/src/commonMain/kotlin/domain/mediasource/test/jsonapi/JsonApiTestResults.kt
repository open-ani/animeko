/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test.jsonapi

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.MediaListFilterContext
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiEpisodeInfo
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiPreparedRequest
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiResponse
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiSearchConfig
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiSubjectInfo
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiVariables
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiVideoResult
import me.him188.ani.app.domain.mediasource.jsonapi.subjectNameCandidate
import me.him188.ani.app.domain.mediasource.jsonapi.toWebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.test.RefreshResult
import me.him188.ani.app.domain.mediasource.test.TestEpisodePresentation
import me.him188.ani.app.domain.mediasource.test.TestSubjectPresentation
import me.him188.ani.app.domain.mediasource.test.buildMatchTags
import me.him188.ani.app.domain.mediasource.web.matchingEpisodeSort

/**
 * 测试页里 JSON API 数据源的一集: 通用的展示信息加上发播放请求所需的站点信息.
 */
@Immutable
class JsonApiTestEpisode(
    val presentation: TestEpisodePresentation,
    val subject: JsonApiSubjectInfo,
    val episode: JsonApiEpisodeInfo,
) {
    /**
     * 播放请求的变量, 与正式播放时资源上保存的一致.
     */
    val playVariables: Map<String, String> get() = JsonApiVariables.play(subject, episode)

    val channelMatch: String? get() = episode.channelMatch ?: episode.channel
}

/**
 * 播放阶段的测试结果.
 */
@Immutable
sealed class JsonApiPlayTestResult : RefreshResult {
    /**
     * 请求已发出. [response] 为 `null` 表示站点返回 404; [video] 为 `null` 表示响应不是 JSON 或播放阶段配置无效.
     */
    @Immutable
    data class Success(
        val request: JsonApiPreparedRequest,
        val response: JsonApiResponse?,
        val video: JsonApiVideoResult?,
    ) : JsonApiPlayTestResult(), RefreshResult.Success

    /**
     * 视频地址来自剧集列表, 没有发播放请求. [videoUrl] 为 `null` 表示列表里没有这一集的地址, 而播放阶段也没有配置请求.
     */
    @Immutable
    data class EpisodeListVideo(
        val videoUrl: String?,
    ) : JsonApiPlayTestResult(), RefreshResult.Success

    @Immutable
    data class ApiError(
        override val exception: RepositoryException,
    ) : JsonApiPlayTestResult(), RefreshResult.ApiError

    @Immutable
    data object InvalidConfig : JsonApiPlayTestResult(), RefreshResult.InvalidConfig

    @Immutable
    data class UnknownError(
        override val exception: Throwable,
    ) : JsonApiPlayTestResult(), RefreshResult.UnknownError
}

/**
 * 标签标出条目名或别名是否能对上搜索关键字, 站点给出的别名, 以及站点 id.
 */
fun TestSubjectPresentation.Companion.compute(
    info: JsonApiSubjectInfo,
    filterContext: MediaListFilterContext,
): TestSubjectPresentation = TestSubjectPresentation(
    name = info.name,
    subjectDetailsPageUrl = info.url,
    origin = null,
    tags = buildMatchTags {
        with(filterContext) {
            val matched = (listOf(info.name) + info.aliases).any {
                MediaListFilters.ContainsSubjectName.applyOn(subjectNameCandidate(it))
            }
            emit("标题", isMatch = matched)
        }
        if (info.aliases.isNotEmpty()) emit("别名: ${info.aliases.joinToString(" / ")}")
        emit("ID: ${info.id}")
    },
)

/**
 * 标签标出集号是否与请求一致 (开启按集号筛选时), 所在线路及其匹配值, 站点 id, 以及列表里给出的视频地址.
 */
fun TestEpisodePresentation.Companion.compute(
    info: JsonApiEpisodeInfo,
    filterContext: MediaListFilterContext,
    config: JsonApiSearchConfig,
): TestEpisodePresentation = TestEpisodePresentation(
    channel = info.channel,
    name = info.name,
    episodeSort = info.episodeSortOrEp,
    playUrl = info.url,
    tags = buildMatchTags {
        val sort = info.episodeSortOrEp
        if (config.autoMatch.filterByEpisodeSort) {
            if (sort == null) {
                emit("缺失 EP", isMissing = true)
            } else {
                val effective = info.toWebSearchEpisodeInfo()
                    .matchingEpisodeSort(filterContext.episodeSort, filterContext.episodeEp)
                val expectedEp = filterContext.episodeEp
                emit(
                    "EP: $sort",
                    isMatch = effective == filterContext.episodeSort || (expectedEp != null && effective == expectedEp),
                )
            }
        } else if (sort != null) {
            emit("EP: $sort")
        }
        if (info.channel != null) {
            val match = info.channelMatch
            emit(if (match != null && match != info.channel) "线路: ${info.channel} ($match)" else "线路: ${info.channel}")
        }
        emit("ID: ${info.id}")
        if (info.url.startsWith("http")) {
            emit(info.url, isMatch = true)
        } else {
            emit("播放地址: ${info.url}", isMatch = false)
        }
        info.videoUrl?.let { emit("视频: $it", isMatch = true) }
    },
    origin = null,
)
