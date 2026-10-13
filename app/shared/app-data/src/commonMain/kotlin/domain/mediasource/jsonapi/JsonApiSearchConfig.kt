/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import me.him188.ani.app.domain.mediasource.web.DurationAsMillisSerializer
import me.him188.ani.app.domain.mediasource.web.SelectorAutoMatchConfig
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormat
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import org.intellij.lang.annotations.Language
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * JSON API 数据源的配置: 站点通过 HTTP 接口 (通常是 POST JSON) 提供搜索、剧集与播放地址, 用 JSONPath 从响应里取字段.
 *
 * 配置按数据源的三个阶段组织, 每个阶段一个请求模板加一组 JSONPath:
 *
 * 1. [search]: 按关键词搜索条目;
 * 2. [subject]: 列出一个条目的线路与剧集;
 * 3. [play]: 播放前取视频地址. 这类站点签发的地址有时效, 所以查询时不解析, 每次播放前才请求 (见 `ApiVideoResolver`).
 *    剧集列表里就带视频地址的站点 ([JsonApiEpisodeFields.selectVideoUrl]) 可以不配置这一阶段的请求.
 *
 * 请求地址与请求体是 `{var}` 模板 ([JsonApiTemplate]), 各阶段可用的变量见各阶段类的注释.
 * 条目与剧集的页面地址 ([JsonApiSearchStage.subjectUrlTemplate], [JsonApiEpisodeFields.episodeUrlTemplate]) 也是模板,
 * 并且要能反解: 浏览协议只把条目地址交回数据源, 数据源从中拿回站点 id 才能发起下一步请求. 因此它们必须包含 `{id}`.
 *
 * 自动匹配层 ([autoMatch]) 与 Selector 数据源共用同一套规则.
 *
 * 默认值是一份空白模板: 基础地址、各阶段的请求地址与页面地址模板都留空, 编辑器会标出必填项.
 * 具体站点的配置由订阅或用户填写提供, 代码里不内置任何站点.
 */
@Immutable
@Serializable
data class JsonApiSearchConfig(
    /**
     * 站点 API 的基础地址. 各阶段请求地址为相对地址时以此解析.
     */
    val baseUrl: String = "",
    /**
     * 所有请求附带的请求头, 每行一个 `Name: Value`. 站点要求的 API key 之类写在这里.
     */
    val headers: String = "",
    /**
     * API 请求与播放器请求视频时的 User-Agent. 为空时 HTTP 请求用浏览器风格的默认值, 播放器用 [SelectorSearchConfig.VideoHeaders] 的默认值.
     */
    val userAgent: String = "",
    /**
     * 向站点发起的任意两个请求之间的最小间隔, 搜索、剧集、播放各阶段共用. 这类站点的网关通常对短时间内的大量请求限流.
     */
    val requestInterval: @Serializable(DurationAsMillisSerializer::class) Duration = 1.seconds,
    /**
     * 搜索缓存的有效期. 实际生效值为此值与用户设置中定义的值的较小者. 为 0 时禁用缓存.
     */
    val searchCacheTtl: @Serializable(DurationAsMillisSerializer::class) Duration = 2.hours,
    val search: JsonApiSearchStage = JsonApiSearchStage(),
    val subject: JsonApiSubjectStage = JsonApiSubjectStage(),
    val play: JsonApiPlayStage = JsonApiPlayStage(),
    /**
     * @see me.him188.ani.datasources.api.MediaProperties.resolution
     */
    val defaultResolution: Resolution = Resolution.R1080P,
    val defaultSubtitleLanguage: SubtitleLanguage = SubtitleLanguage.ChineseSimplified,
    /**
     * 只在这些播放器上参与自动匹配: `mpv`, `vlc`, `exoplayer`, `avkit`. 为空表示不限制. 语义与 Selector 数据源相同.
     */
    val onlySupportsPlayers: List<String> = emptyList(),
    /**
     * 自动匹配层, 与 Selector 数据源相同.
     */
    val autoMatch: SelectorAutoMatchConfig = SelectorAutoMatchConfig(),
) {
    /**
     * 实际生效的 [userAgent]: 去掉首尾空白, 为空时为 `null`.
     */
    val userAgentOrNull: String? get() = userAgent.trim().ifEmpty { null }

    /**
     * 站点的网页地址, 从条目页面模板推断, 供展示.
     */
    val websiteUrl: String get() = SelectorSearchConfig.guessBaseUrl(search.subjectUrlTemplate)

    /**
     * 剧集列表里就有视频地址 ([JsonApiEpisodeFields.selectVideoUrl] 非空). 此时播放阶段的请求可以留空.
     */
    val episodeListHasVideoUrl: Boolean get() = subject.episodes.selectVideoUrl.isNotBlank()

    @Stable
    companion object {
        @Stable
        val Default = JsonApiSearchConfig()

        /**
         * 解析 `Name: Value` 形式的请求头行. 空行与没有冒号的行忽略.
         */
        fun parseHeaderLines(text: String): List<Pair<String, String>> = text.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf(':')
                if (index <= 0) return@mapNotNull null
                val name = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim()
                if (name.isEmpty()) null else name to value
            }
            .toList()
    }
}

@Serializable
enum class JsonApiHttpMethod {
    GET,
    POST,
}

/**
 * 一个阶段的 HTTP 请求. [url]、[headers] 的值与 [body] 都是模板; [body] 只在 POST 时发送, 默认按 JSON 发送
 * (`Content-Type: application/json`).
 */
@Immutable
@Serializable
data class JsonApiRequestConfig(
    /**
     * 请求地址模板. 相对地址以 [JsonApiSearchConfig.baseUrl] 解析. 变量按 URL 片段编码代入.
     */
    val url: String = "",
    val method: JsonApiHttpMethod = JsonApiHttpMethod.GET,
    /**
     * 此请求额外的请求头, 每行一个 `Name: Value`, 与 [JsonApiSearchConfig.headers] 合并, 同名时以这里为准. 值里可以用本阶段的变量.
     */
    val headers: String = "",
    /**
     * 请求体模板. 变量按 JSON 字符串内容转义后原样代入, 不加引号:
     * 字符串字段写成 `"key": "{var}"`, 数字字段写成 `"key": {var}`. 发送前会检查代入后是整个合法的 JSON.
     */
    val body: String = "",
) {
    fun isValid(): Boolean = url.isNotBlank()
}

/**
 * 搜索阶段. 请求模板可用变量: `{keyword}` 关键词, `{page}` 页码 (总是 1), `{pageSize}` 每页条数 ([pageSize]).
 */
@Immutable
@Serializable
data class JsonApiSearchStage(
    val request: JsonApiRequestConfig = JsonApiRequestConfig(),
    val pageSize: Int = 20,
    /**
     * 选出条目数组. 结果是数组时每个元素是一个条目, 是对象时视为单个条目.
     */
    @param:Language("jsonpath")
    val selectItems: String = "$[*]",
    /**
     * 相对每个条目: 条目名称.
     */
    @param:Language("jsonpath")
    val selectName: String = "$.title",
    /**
     * 相对每个条目: 站点 id. 后续请求用 `{id}` 引用它.
     */
    @param:Language("jsonpath")
    val selectId: String = "$.id",
    /**
     * 相对每个条目: 别名, 可以是字符串数组或单个字符串. 为空表示站点不提供别名.
     * 自动匹配时条目名或任一别名对上请求的条目名就算匹配, 资源的条目名取对上的那个.
     */
    @param:Language("jsonpath")
    val selectAliases: String = "",
    /**
     * 条目页面地址模板, 变量 `{id}`. 它是条目的稳定标识 (浏览记忆、搜索缓存都按它记), 也用于展示.
     * 站点 id 本身就是完整地址时写 `{id}`.
     */
    val subjectUrlTemplate: String = "",
) {
    fun isValid(): Boolean = request.isValid() &&
            selectItems.isNotBlank() && selectName.isNotBlank() && selectId.isNotBlank() &&
            pageSize > 0 && JsonApiTemplate.variableNames(subjectUrlTemplate).contains("id")
}

/**
 * 条目阶段: 列出线路与剧集. 请求模板可用变量: `{id}` 条目 id, `{url}` 条目页面地址, `{name}` 条目名称.
 *
 * 剧集字段 ([episodes]) 对所有线路格式通用; 线路格式决定响应里怎样分出线路, 见 [JsonApiChannelFormat].
 */
@Immutable
@Serializable
data class JsonApiSubjectStage(
    val request: JsonApiRequestConfig = JsonApiRequestConfig(),
    val episodes: JsonApiEpisodeFields = JsonApiEpisodeFields(),
    val channelFormatId: JsonApiFormatId = JsonApiChannelFormatNoChannel.id,
    val channelFormatGrouped: JsonApiChannelFormatGrouped.Config = JsonApiChannelFormatGrouped.Config(),
    val channelFormatDeclared: JsonApiChannelFormatDeclared.Config = JsonApiChannelFormatDeclared.Config(),
) {
    fun isValid(): Boolean = request.isValid() && episodes.isValid()
}

/**
 * 怎样从响应里读出一集. JSONPath 相对剧集所在的列表元素.
 */
@Immutable
@Serializable
data class JsonApiEpisodeFields(
    /**
     * 选出剧集数组. 线路格式为分组时相对每条线路, 否则相对响应根.
     */
    @param:Language("jsonpath")
    val selectEpisodes: String = "$[*]",
    /**
     * 相对每集: 站点上显示的名称 (标题).
     */
    @param:Language("jsonpath")
    val selectName: String = "$.title",
    /**
     * 相对每集: 站点 id. 播放阶段用 `{id}` 引用它.
     */
    @param:Language("jsonpath")
    val selectId: String = "$.id",
    /**
     * 相对每集: 集号. 为空或取不到时按 [matchEpisodeSortFromName] 从名称解析.
     */
    @param:Language("jsonpath")
    val selectSort: String = "$.episode_number",
    /**
     * 相对每集: 剧集类型 (正片 / SP / OVA 等) 的站点值, 由 [typeMapping] 翻译. 为空时不区分类型.
     * 特别篇常常从 1 重新编号, 不区分类型会把 SP01 当成第 1 集.
     */
    @param:Language("jsonpath")
    val selectType: String = "$.kind",
    /**
     * 站点类型值到 [EpisodeType] 名称的映射, 每行一个 `站点值=类型`. 没有映射到的值按正片处理.
     */
    val typeMapping: String = "main=MainStory\nsp=SP\nova=OVA\noad=OAD\nmovie=MainStory\nex=SP",
    /**
     * 从名称解析集号的正则, 命名分组 `ep`. 没有分组时整个匹配结果就是集号.
     */
    @param:Language("regexp")
    val matchEpisodeSortFromName: String = SelectorChannelFormat.DEFAULT_MATCH_EPISODE_SORT_FROM_NAME,
    /**
     * 显示名称模板, 变量 `{sort}` 集号 (解析不出时为空), `{name}` 站点名称, `{id}`. 为空时直接用站点名称.
     */
    val nameTemplate: String = "{sort} {name}",
    /**
     * 剧集页面地址模板, 变量 `{id}` 剧集 id, `{subjectId}` 条目 id. 它是剧集的稳定标识, 也是分享与打开页面用的地址.
     * 站点 id 本身就是完整地址时写 `{id}`.
     */
    val episodeUrlTemplate: String = "",
    /**
     * 相对每集: 可直接播放的视频地址. 为空表示列表里没有地址, 播放时发播放阶段的请求.
     *
     * 非空时播放阶段的请求可以留空. 播放时不用查询时读到的地址, 而是重新取一次剧集列表读这一集的地址:
     * 列表里的直链同样可能带签名有时效, 而重取一次的代价与签发请求相同.
     */
    @param:Language("jsonpath")
    val selectVideoUrl: String = "",
) {
    fun isValid(): Boolean = selectEpisodes.isNotBlank() && selectName.isNotBlank() && selectId.isNotBlank() &&
            JsonApiTemplate.variableNames(episodeUrlTemplate).contains("id")

    /**
     * 解析 [typeMapping].
     */
    val episodeTypes: Map<String, EpisodeType> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        typeMapping.lineSequence().mapNotNull { line ->
            val (key, value) = line.split('=', limit = 2).map { it.trim() }.takeIf { it.size == 2 } ?: return@mapNotNull null
            val type = EpisodeType.entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: return@mapNotNull null
            key.lowercase() to type
        }.toMap()
    }
}

/**
 * 播放阶段. 请求模板可用变量: `{id}` 剧集 id, `{url}` 剧集页面地址, `{name}` 剧集名称,
 * `{subjectId}`, `{subjectUrl}`, `{subjectName}`, `{channel}` 线路名称, `{channelMatch}` 线路在响应里的匹配值.
 *
 * 响应里可以有多条候选 (多线路). 资源属于某条线路时, 选 [selectCandidateChannel] 等于该线路匹配值的候选;
 * 没有线路时取第一条候选. 没有候选数组时整个响应就是一个视频.
 *
 * 剧集列表自带视频地址时 ([JsonApiEpisodeFields.selectVideoUrl]) 请求可以留空, 只有 [referer] 仍然生效;
 * 配置了请求时, 只对列表里没有地址的剧集发它.
 */
@Immutable
@Serializable
data class JsonApiPlayStage(
    val request: JsonApiRequestConfig = JsonApiRequestConfig(),
    /**
     * 选出候选数组. 为空表示响应本身是单个视频.
     */
    @param:Language("jsonpath")
    val selectCandidates: String = "",
    /**
     * 相对每条候选: 线路匹配值, 与 [JsonApiDeclaredChannel.match] 或分组线路的名称比较.
     */
    @param:Language("jsonpath")
    val selectCandidateChannel: String = "",
    /**
     * 相对候选 (或响应根): 视频地址.
     */
    @param:Language("jsonpath")
    val selectVideoUrl: String = "$.url",
    /**
     * 资源的线路在候选里找不到时, 是否退而用第一条候选.
     *
     * 声明的线路对每个条目都存在, 但站点未必给每个条目的每条线路候选 (例如新番与旧番走不同的线路).
     * 默认退用第一条, 自动选中的线路总能播放; 代价是播的可能不是所选线路, 测试面板会标出这种情况.
     * 关掉则播放失败让选择器换一条线路.
     */
    val fallbackToFirstCandidate: Boolean = true,
    /**
     * 播放器请求视频时的 Referer. 可留空.
     */
    val referer: String = "",
    /**
     * 播放器请求视频时附带的请求头, 每行一个 `Name: Value`, 值里可以用播放阶段的变量. 只随视频请求发送, 不发给接口;
     * 接口的 [JsonApiSearchConfig.headers] 也不发给视频主机, 密钥不流向 CDN. 与 [referer] 同名时以这里为准.
     */
    val videoHeaders: String = "",
) {
    fun isValid(): Boolean = request.isValid() && selectVideoUrl.isNotBlank()
}

/**
 * 声明的一条线路: [name] 是线路的名称, 也是资源的 `alliance`、线路阶级的键和资源 id 的一部分;
 * [match] 是播放响应里标识这条线路的值 (见 [JsonApiPlayStage.selectCandidateChannel]). 站点改线路显示名不影响 [name].
 */
@Immutable
@Serializable
data class JsonApiDeclaredChannel(
    val name: String,
    val match: String = name,
)

@Serializable
@Immutable
@JvmInline
value class JsonApiFormatId(val value: String)
