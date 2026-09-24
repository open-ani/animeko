/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.ktor.http.URLBuilder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorFormatConfig
import me.him188.ani.app.domain.mediasource.web.format.SelectorFormatId
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed
import me.him188.ani.app.domain.mediasource.web.format.parseOrNull
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import org.intellij.lang.annotations.Language
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Selector 数据源的配置.
 *
 * 配置分两层:
 *
 * - **列表规则** (其余全部字段): 怎样把站点上的搜索结果、线路与剧集列出来, 以及怎样从播放页提取视频.
 *   只写这一层的数据源就已经可以浏览和手动选集 (`MediaSource.searchSubjects` / `browseSubject` / `createMedia`).
 * - **自动匹配** ([autoMatch]): 在列表之上, 让 [me.him188.ani.datasources.api.source.MediaSource.fetch]
 *   自动搜索并筛选出对应当前剧集的资源. 全部可选, 缺省时按默认策略自动匹配.
 *
 * 序列化兼容: [autoMatch] 内的字段还接受平铺在本类顶层的写法. 反序列化时没有 `autoMatch` 键则读取平铺键;
 * 序列化时两处都写, 让只认识平铺写法的客户端继续能读. 见 [SelectorSearchConfigSerializer].
 */
@Immutable
@Serializable(with = SelectorSearchConfigSerializer::class)
data class SelectorSearchConfig(
    // Phase 1, search
    val searchUrl: String = "", // required
    val rawBaseUrl: String = "", // if empty, guess
    /**
     * 两个搜索请求之间的间隔时间
     * @since 4.2
     */
    val requestInterval: @Serializable(DurationAsMillisSerializer::class) Duration = 3.seconds,
    /**
     * 播放 session 搜索缓存的有效期. 实际生效值为此值与用户设置中定义的值的较小者. 为 0 时禁用缓存.
     */
    val searchCacheTtl: @Serializable(DurationAsMillisSerializer::class) Duration = 2.hours,
    // Phase 2, for search result, select subjects
    val subjectFormatId: SelectorFormatId = SelectorSubjectFormatA.id,
    val selectorSubjectFormatA: SelectorSubjectFormatA.Config = SelectorSubjectFormatA.Config(),
    val selectorSubjectFormatIndexed: SelectorSubjectFormatIndexed.Config = SelectorSubjectFormatIndexed.Config(),
    val selectorSubjectFormatJsonPathIndexed: SelectorSubjectFormatJsonPathIndexed.Config = SelectorSubjectFormatJsonPathIndexed.Config(),
    // Phase 3, for each subject, select channels
    val channelFormatId: SelectorFormatId = SelectorChannelFormatNoChannel.id,
    val selectorChannelFormatFlattened: SelectorChannelFormatIndexGrouped.Config = SelectorChannelFormatIndexGrouped.Config(),
    val selectorChannelFormatNoChannel: SelectorChannelFormatNoChannel.Config = SelectorChannelFormatNoChannel.Config(),
//    /**
//     * Regex. Group names:
//     * - `<ch>`: channel name
//     * - `<ep>`: episode name
//     *
//     * E.g. 用于匹配 "线路1 第1集":
//     * ```regex
//     * (?<ch>.+)\s*第(?<ep>\d+)集
//     * ```
//     *
//     * 匹配方式为 find 而不是 matchEntire.
//     * @see SelectorChannelFormat.FLATTENED
//     */
//    val matchChannelFromEpisodeText: String = "",
//    val selectNameFromEpisode: String = "",
//    val selectPlayUrlFromEpisode: String = "",

    /**
     * @see MediaProperties.resolution
     */
    val defaultResolution: Resolution = Resolution.R1080P,
    /**
     * @since 4.9
     */
    val defaultSubtitleLanguage: SubtitleLanguage = SubtitleLanguage.ChineseSimplified,
    /**
     * `mpv`, `vlc`, `exoplayer`, `avkit`
     *
     * 桌面端匹配 `mpv` 或 `vlc` (兼容旧订阅).
     *
     * @since 4.9
     */
    val onlySupportsPlayers: List<String> = emptyList(),

    // When playing a media:
    val selectMedia: SelectMediaConfig = SelectMediaConfig(),
    val matchVideo: MatchVideoConfig = MatchVideoConfig(),

    /**
     * 自动匹配层. 见类注释.
     * @since 6.2
     */
    val autoMatch: SelectorAutoMatchConfig = SelectorAutoMatchConfig(),
) { // TODO: add Engine version capabilities
    val finalBaseUrl by lazy(LazyThreadSafetyMode.PUBLICATION) {
        rawBaseUrl.ifBlank { guessBaseUrl(searchUrl) }
    }

    @Serializable
    @Suppress("RegExpRedundantEscape")
    data class MatchVideoConfig(
        val enableNestedUrl: Boolean = true,
        @param:Language("regexp")
        val matchNestedUrl: String = """^.+(m3u8|vip|xigua\.php).+\?""",
        @param:Language("regexp")
        val matchVideoUrl: String = """(^http(s)?:\/\/(?!.*http(s)?:\/\/).+((\.mp4)|(\.mkv)|(m3u8)).*(\?.+)?)|(akamaized)|(bilivideo.com)""",
        val cookies: String = """quality=1080""",
        val addHeadersToVideo: VideoHeaders = VideoHeaders(),
    ) {
        val matchNestedUrlRegex by lazy {
            Regex.parseOrNull(matchNestedUrl)
        }
        val matchVideoUrlRegex by lazy {
            Regex.parseOrNull(matchVideoUrl)
        }
    }

    @Serializable
    data class VideoHeaders(
        val referer: String = "",
        val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
    )

    @Serializable
    data class SelectMediaConfig(
        val distinguishSubjectName: Boolean = true,
        val distinguishChannelName: Boolean = true,
    )


//    val matchChannelFromEpisodeRegex by lazy(LazyThreadSafetyMode.PUBLICATION) {
//        matchChannelFromEpisodeText.toRegex()
//    }

    // These classes are nested to limit namespace

    @Stable
    companion object {
        @Stable
        val Empty = SelectorSearchConfig()

        fun guessBaseUrl(searchUrl: String): String {
            return kotlin.runCatching {
                URLBuilder(searchUrl).apply {
                    pathSegments = emptyList()
                    parameters.clear()
                }.toString()
            }.getOrElse {
                val schemaIndex = searchUrl.indexOf("//")
                if (schemaIndex == -1) {
                    searchUrl.removeSuffix("/")
                } else {
                    val slashIndex = searchUrl.indexOf('/', startIndex = schemaIndex + 2)
                    if (slashIndex == -1) {
                        searchUrl.removeSuffix("/")
                    } else {
                        searchUrl.substring(0, slashIndex)
                    }
                }
            }
        }
    }
}

/**
 * 获取该 [SelectorSubjectFormat] 的配置 [C].
 */
fun <C : SelectorFormatConfig> SelectorSearchConfig.getFormatConfig(format: SelectorSubjectFormat<C>): C {
    @Suppress("UNCHECKED_CAST")
    return when (format) {
        SelectorSubjectFormatA -> selectorSubjectFormatA as C
        SelectorSubjectFormatIndexed -> selectorSubjectFormatIndexed as C
        SelectorSubjectFormatJsonPathIndexed -> selectorSubjectFormatJsonPathIndexed as C
    }
}

/**
 * 获取该 [SelectorChannelFormat] 的配置 [C].
 */
fun <C : SelectorFormatConfig> SelectorSearchConfig.getFormatConfig(format: SelectorChannelFormat<C>): C {
    @Suppress("UNCHECKED_CAST")
    return when (format) {
        SelectorChannelFormatIndexGrouped -> selectorChannelFormatFlattened as C
        SelectorChannelFormatNoChannel -> selectorChannelFormatNoChannel as C
    }
}


/**
 * [SelectorSearchConfig] 的自动匹配层: [me.him188.ani.datasources.api.source.MediaSource.fetch] 在列表规则之上怎样自动找到当前剧集.
 *
 * 数据源级与线路级阶级 (`tier`, `channelTiers`) 也属于自动匹配, 但它们在 `SelectorMediaSourceArguments` 上, 与其他类型的数据源共用.
 *
 * @since 6.2
 */
@Immutable
@Serializable
data class SelectorAutoMatchConfig(
    /**
     * 是否参与自动匹配. 为 `false` 时 `fetch` 不发起任何请求, 数据源只能通过浏览手动选集.
     */
    val enabled: Boolean = true,
    /**
     * 搜索关键字只取条目名的第一个词.
     */
    val searchUseOnlyFirstWord: Boolean = true,
    /**
     * 搜索前移除条目名中的特殊字符与 "剧场版" 等标记词.
     */
    val searchRemoveSpecial: Boolean = true,
    /**
     * 搜索时, 使用多少个 subjectName. 至少需要有 1. 排序为:
     * - 主中文名
     * - 日文原名
     * - 其他别名, 无特定顺序
     */
    val searchUseSubjectNamesCount: Int = 1,
    /**
     * 搜索结果按条目名长度排序, 短的在前: 搜索 "第一季" 时避免先匹配到 "第二季".
     *
     * 平铺写法里这是各条目格式配置的字段, 序列化器双向镜像.
     */
    val preferShorterName: Boolean = true,
    /**
     * 按条目名筛选搜索结果.
     */
    val filterBySubjectName: Boolean = true,
    /**
     * 只保留集号与当前剧集一致的资源.
     */
    val filterByEpisodeSort: Boolean = true,
) {
    companion object {
        val Default = SelectorAutoMatchConfig()
    }
}

/**
 * 与平铺写法双向兼容的序列化器, 语义见 [SelectorSearchConfig] 类注释.
 */
object SelectorSearchConfigSerializer : KSerializer<SelectorSearchConfig> {
    override val descriptor: SerialDescriptor get() = SelectorSearchConfigSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SelectorSearchConfig) {
        encoder.encodeSerializableValue(SelectorSearchConfigSurrogate.serializer(), SelectorSearchConfigSurrogate(value))
    }

    override fun deserialize(decoder: Decoder): SelectorSearchConfig {
        return decoder.decodeSerializableValue(SelectorSearchConfigSurrogate.serializer()).toConfig()
    }
}

/**
 * [SelectorSearchConfig] 的 JSON 形态: [autoMatch] 与平铺字段并存.
 * 各条目格式配置里的 `preferShorterName` 同样双向镜像.
 */
@Serializable
@SerialName("SelectorSearchConfig")
private class SelectorSearchConfigSurrogate(
    val searchUrl: String = "",
    val searchUseOnlyFirstWord: Boolean = SelectorAutoMatchConfig.Default.searchUseOnlyFirstWord,
    val searchRemoveSpecial: Boolean = SelectorAutoMatchConfig.Default.searchRemoveSpecial,
    val searchUseSubjectNamesCount: Int = SelectorAutoMatchConfig.Default.searchUseSubjectNamesCount,
    val rawBaseUrl: String = "",
    val requestInterval: @Serializable(DurationAsMillisSerializer::class) Duration = 3.seconds,
    val searchCacheTtl: @Serializable(DurationAsMillisSerializer::class) Duration = 2.hours,
    val subjectFormatId: SelectorFormatId = SelectorSubjectFormatA.id,
    val selectorSubjectFormatA: SelectorSubjectFormatA.Config = SelectorSubjectFormatA.Config(),
    val selectorSubjectFormatIndexed: SelectorSubjectFormatIndexed.Config = SelectorSubjectFormatIndexed.Config(),
    val selectorSubjectFormatJsonPathIndexed: SelectorSubjectFormatJsonPathIndexed.Config = SelectorSubjectFormatJsonPathIndexed.Config(),
    val channelFormatId: SelectorFormatId = SelectorChannelFormatNoChannel.id,
    val selectorChannelFormatFlattened: SelectorChannelFormatIndexGrouped.Config = SelectorChannelFormatIndexGrouped.Config(),
    val selectorChannelFormatNoChannel: SelectorChannelFormatNoChannel.Config = SelectorChannelFormatNoChannel.Config(),
    val defaultResolution: Resolution = Resolution.R1080P,
    val defaultSubtitleLanguage: SubtitleLanguage = SubtitleLanguage.ChineseSimplified,
    val onlySupportsPlayers: List<String> = emptyList(),
    val filterByEpisodeSort: Boolean = SelectorAutoMatchConfig.Default.filterByEpisodeSort,
    val filterBySubjectName: Boolean = SelectorAutoMatchConfig.Default.filterBySubjectName,
    val selectMedia: SelectorSearchConfig.SelectMediaConfig = SelectorSearchConfig.SelectMediaConfig(),
    val matchVideo: SelectorSearchConfig.MatchVideoConfig = SelectorSearchConfig.MatchVideoConfig(),
    /**
     * `null` 表示 JSON 里没有这个键 (平铺写法), 此时自动匹配层由平铺字段组成.
     */
    val autoMatch: SelectorAutoMatchConfig? = null,
) {
    @Suppress("DEPRECATION")
    constructor(config: SelectorSearchConfig) : this(
        searchUrl = config.searchUrl,
        searchUseOnlyFirstWord = config.autoMatch.searchUseOnlyFirstWord,
        searchRemoveSpecial = config.autoMatch.searchRemoveSpecial,
        searchUseSubjectNamesCount = config.autoMatch.searchUseSubjectNamesCount,
        rawBaseUrl = config.rawBaseUrl,
        requestInterval = config.requestInterval,
        searchCacheTtl = config.searchCacheTtl,
        subjectFormatId = config.subjectFormatId,
        // 镜像给只认识平铺写法的客户端
        selectorSubjectFormatA = config.selectorSubjectFormatA.copy(preferShorterName = config.autoMatch.preferShorterName),
        selectorSubjectFormatIndexed = config.selectorSubjectFormatIndexed.copy(preferShorterName = config.autoMatch.preferShorterName),
        selectorSubjectFormatJsonPathIndexed = config.selectorSubjectFormatJsonPathIndexed.copy(preferShorterName = config.autoMatch.preferShorterName),
        channelFormatId = config.channelFormatId,
        selectorChannelFormatFlattened = config.selectorChannelFormatFlattened,
        selectorChannelFormatNoChannel = config.selectorChannelFormatNoChannel,
        defaultResolution = config.defaultResolution,
        defaultSubtitleLanguage = config.defaultSubtitleLanguage,
        onlySupportsPlayers = config.onlySupportsPlayers,
        filterByEpisodeSort = config.autoMatch.filterByEpisodeSort,
        filterBySubjectName = config.autoMatch.filterBySubjectName,
        selectMedia = config.selectMedia,
        matchVideo = config.matchVideo,
        autoMatch = config.autoMatch,
    )

    @Suppress("DEPRECATION")
    fun toConfig(): SelectorSearchConfig {
        val autoMatch = autoMatch ?: SelectorAutoMatchConfig(
            searchUseOnlyFirstWord = searchUseOnlyFirstWord,
            searchRemoveSpecial = searchRemoveSpecial,
            searchUseSubjectNamesCount = searchUseSubjectNamesCount,
            // 平铺写法: 以生效的条目格式为准
            preferShorterName = when (subjectFormatId) {
                SelectorSubjectFormatIndexed.id -> selectorSubjectFormatIndexed.preferShorterName
                SelectorSubjectFormatJsonPathIndexed.id -> selectorSubjectFormatJsonPathIndexed.preferShorterName
                else -> selectorSubjectFormatA.preferShorterName
            },
            filterBySubjectName = filterBySubjectName,
            filterByEpisodeSort = filterByEpisodeSort,
        )
        return SelectorSearchConfig(
            searchUrl = searchUrl,
            rawBaseUrl = rawBaseUrl,
            requestInterval = requestInterval,
            searchCacheTtl = searchCacheTtl,
            subjectFormatId = subjectFormatId,
            // 内存里的旧字段与 autoMatch 保持一致, 避免两处不同
            selectorSubjectFormatA = selectorSubjectFormatA.copy(preferShorterName = autoMatch.preferShorterName),
            selectorSubjectFormatIndexed = selectorSubjectFormatIndexed.copy(preferShorterName = autoMatch.preferShorterName),
            selectorSubjectFormatJsonPathIndexed = selectorSubjectFormatJsonPathIndexed.copy(preferShorterName = autoMatch.preferShorterName),
            channelFormatId = channelFormatId,
            selectorChannelFormatFlattened = selectorChannelFormatFlattened,
            selectorChannelFormatNoChannel = selectorChannelFormatNoChannel,
            defaultResolution = defaultResolution,
            defaultSubtitleLanguage = defaultSubtitleLanguage,
            onlySupportsPlayers = onlySupportsPlayers,
            selectMedia = selectMedia,
            matchVideo = matchVideo,
            autoMatch = autoMatch,
        )
    }
}

private object DurationAsMillisSerializer : KSerializer<Duration> {
    override val descriptor = PrimitiveSerialDescriptor("Duration", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Duration) {
        Long.serializer().serialize(encoder, value.inWholeMilliseconds)
    }

    override fun deserialize(decoder: Decoder): Duration = Long.serializer().deserialize(decoder).milliseconds
}
