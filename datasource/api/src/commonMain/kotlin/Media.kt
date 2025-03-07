/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.matches
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.annotations.SerializationOnly

/**
 * 表示从数据源 [MediaSource] 获取到的一个资源的信息, 即一个字幕组发布的资源.
 *
 * [Media] 只包含资源的标题, 字幕组名称, 剧集列表等信息. 它也提供下载链接 [download].
 *
 * ## [Media] 与番剧对应关系
 *
 * - 一个资源可能对应一个番剧的一个剧集 [episodeRange], 新番资源资源大部分都是这样.
 * - 一个资源也可能对应一个番剧的多个剧集 [episodeRange], 尤其是老番的季度全集资源.
 *
 * ## 来源类型
 *
 * [Media] 的[来源类型][kind]可以是:
 * - 在线视频网站 [MediaSourceKind.WEB],
 * - P2P BitTorrent 网络 [MediaSourceKind.BitTorrent],
 * - 本地视频缓存 [MediaSourceKind.LocalCache].
 *
 * 前两个从网络查询的资源返回 [DefaultMedia].
 * 本地视频缓存返回 [CachedMedia].
 */
@Serializable
sealed interface Media {
    /**
     * 该资源的全局唯一 id, 通常需要包含 [mediaSourceId], 例如 "dmhy.1", 以防多数据源查询到同一个资源会容易 crash UI.
     */
    val mediaId: String

    /**
     * 查询出这个资源的数据源的全局唯一 id.
     *
     * @see MediaSource.mediaSourceId
     */
    val mediaSourceId: String // e.g. "dmhy"

    /**
     * 在数据源上的原始链接, 一般是 HTTP URL
     */
    val originalUrl: String

    /**
     * 描述如何下载这个资源
     */
    val download: ResourceLocation // 有关具体下载过程, 参考 app `VideoSourceResolver`, 以及 `MediaCacheEngine`

    /**
     * 该资源包含的剧集列表.
     *
     * - 如果是单集资源, 该列表可能包含 1 个元素, 即 [EpisodeRange.single]
     * - 如果是季度全集资源, 该列表可能包含多个元素, 典型值为 12 个. 季度全集可以是 [EpisodeRange.range] (`1..12`) 或 [EpisodeRange.season] (`S1`).
     *
     * 当解析剧集列表失败时为 `null`.
     * 注意, 如果为 `null`, [MediaMatch.matches] 一定会过滤掉这个资源. 如果你实现的[数据源][MediaSource]使用 [MediaMatch.matches], 则需要小心.
     *
     * 在 [MediaSource.fetch] 时, 如果自定义的数据源查询到的资源没有确定的剧集信息, 可以考虑猜测该资源的剧集为 [MediaFetchRequest.episodeSort].
     *
     * 如果剧集为单集 [EpisodeRange.single], 且类型 [kind] 为 [MediaSourceKind.BitTorrent], 则很有可能会因为默认启用的"完结番隐藏单集资源"功能而被过滤掉.
     * 在 UI 中不会显示. 设计上, 这是为了让用户看到的资源更加整洁. 如果你期望一直显示资源, 考虑将 [kind] 改为 [MediaSourceKind.WEB], 或是在 GitHub 讨论更改这个过滤行为.
     */
    val episodeRange: EpisodeRange?

    /**
     * 字幕组发布的原标题.
     *
     * 为空字符串表示数据源不支持该属性.
     */
    val originalTitle: String // 实现提示: 在播放页会显示在 "正在播放: " 的标签中, 无其他作用

    /**
     * 该资源发布时间, 毫秒时间戳
     *
     * 为 `0` 表示数据源不支持该属性.
     */
    val publishedTime: Long // 为 `0` 不会影响过滤. 只是会在 UI 中不显示发布时间

    /**
     * 用于数据源选择器内过滤的属性.
     * @see MediaProperties
     */
    val properties: MediaProperties

    /**
     * 该资源的额外文件, 例如字幕文件.
     */
    val extraFiles: MediaExtraFiles

    /**
     * 该资源的存放位置.
     *
     * 查看 [MediaSourceLocation.Local] 和 [MediaSourceLocation.Online].
     */
    val location: MediaSourceLocation

    /**
     * 该资源的类型. 例如 [在线视频][MediaSourceKind.WEB] 或 [BT 资源][MediaSourceKind.BitTorrent]
     *
     * @see MediaSource.kind
     */
    val kind: MediaSourceKind
}

/**
 * 找到该 [Media] 的实际来源 [DefaultMedia]
 */
tailrec fun Media.unwrapCached(): DefaultMedia = when (this) {
    is CachedMedia -> origin.unwrapCached() // note, 实际上这里不会循环超过 2 次, 因为 [CachedMedia] 一定是由 [DefaultMedia] 包装而来
    is DefaultMedia -> this
}

/**
 * 一个从数据源查询到的一手资源信息.
 *
 * 一手指的是这个资源没有被缓存到本地. 被缓存的资源为 [CachedMedia].
 */
@Serializable
data class DefaultMedia
@SerializationOnly
constructor(
    override val mediaId: String,
    override val mediaSourceId: String, // e.g. "dmhy"
    override val originalUrl: String,
    override val download: ResourceLocation,
    override val originalTitle: String,
    override val publishedTime: Long,
    override val properties: MediaProperties,
    override val episodeRange: EpisodeRange? = null,
    override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
    override val location: MediaSourceLocation = MediaSourceLocation.Online,
    override val kind: MediaSourceKind = MediaSourceKind.BitTorrent,
    @Transient private val _primaryConstructorMarker: Unit = Unit,
) : Media {
    @OptIn(SerializationOnly::class)
    constructor(
        mediaId: String,
        mediaSourceId: String, // e.g. "dmhy"
        originalUrl: String,
        download: ResourceLocation,
        originalTitle: String,
        publishedTime: Long,
        properties: MediaProperties,
        episodeRange: EpisodeRange?,
        extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
        location: MediaSourceLocation,
        kind: MediaSourceKind,
    ) : this(
        mediaId,
        mediaSourceId,
        originalUrl,
        download,
        originalTitle,
        publishedTime,
        properties,
        episodeRange,
        extraFiles = extraFiles,
        location,
        kind,
        _primaryConstructorMarker = Unit,
    )
}

/**
 * 表示一个已经被缓存到一个非常轻松能访问的地方, 例如本地文件系统 [MediaSourceLocation.Local].
 *
 * [CachedMedia] 一定是用户使用缓存功能, 将一个 [DefaultMedia] 存储到本地而产生的.
 */
class CachedMedia(
    /**
     * 此缓存的来源 [Media], 通常是一个 [DefaultMedia]. 不能是 [CachedMedia].
     */
    val origin: Media,
    cacheMediaSourceId: String,
    override val download: ResourceLocation,
    override val location: MediaSourceLocation = MediaSourceLocation.Local,
    override val kind: MediaSourceKind = MediaSourceKind.LocalCache,
) : Media by origin {
    override val mediaId: String = "${cacheMediaSourceId}:${origin.mediaId}"
    override val mediaSourceId: String = cacheMediaSourceId
}

/**
 * 用于播放或缓存时过滤选择资源的属性.
 * @see Media.properties
 */
@Serializable
class MediaProperties private constructor(
    /**
     * [Media] 所对应的条目的名称. 如果数据源支持, 则可提供此信息帮助后续 Media Selector 过滤.
     * @since 4.2
     */
    val subjectName: String? = null,
    /**
     * [Media] 所对应的剧集的名称. 如果数据源支持, 则可提供此信息帮助后续 Media Selector 过滤.
     * @since 4.2
     */
    val episodeName: String? = null,
    /**
     * 该资源支持的字幕语言列表. 可以有多个.
     *
     * ## APP 行为细节
     *
     * 建议的值: "CHS", "CHT", "JPY", "ENG".
     * 这些值能保证在 APP UI 中显示为 "简体中文" 等本地化名称.
     *
     * 为空字符串表示没有任何字幕. 但这很有可能会导致被数据源选择器忽略掉. 因为默认偏好设置是忽略无字幕的资源 (可在 APP 设置中关闭)
     *
     * 如果数据源不支持检索该属性, 可以返回一个最大努力上的猜测, 例如简体中文视频网站就只返回 `listOf("CHS")`.
     */
    val subtitleLanguageIds: List<String>,
    /**
     * 分辨率, 例如 "1080P", 区分大小写.
     *
     * ## APP 行为细节
     *
     * 建议的值: "720P", "1080P", "2K", "4K".
     *
     * 提供 "1440P" 和 "2160P" 能正确识别, 但建议使用上述四个选项.
     * 提供其他的值可能导致前端 UI 无法正确匹配名称, 也会使得数据源选择器 UI 混乱影响体验.
     * 总是使用大写.
     *
     * 若未知, 可以返回空字符串. 但建议尽可能提供, 可以根据数据源的大致清晰度猜测一个, 例如 "1080P".
     */
    val resolution: String,
    /**
     * 字幕组名称, 例如 "桜都字幕组", "北宇治字幕组".
     * 空字符串可能导致数据源选择器忽略掉这个资源.
     * 
     * 对于在线数据源, 这会是线路名称.
     *
     * 对于无法确定字幕组的数据源, 可以使用数据源的 [MediaSource.mediaSourceId] 代替.
     *
     * 该属性建议比较稳定. 例如对于桜都字幕组资源, 总是返回 "桜都字幕组", 而不要返回相似的 "樱都字幕组", "桜都字幕组2" 等内容.
     * 因为当用户选择字幕组后, 该偏好会保存, 并在下次选择时自动使用. 如果字幕组名称不稳定, 将无法选择正确的偏好资源.
     */
    val alliance: String,
    /**
     * 文件大小, 若未知可以提供 [FileSize.Unspecified] 或 [FileSize.Zero]. 这两个值在 APP 中都会判定为未知大小.
     * 未知大小不会导致数据源被过滤掉, 但是未来可能会影响选择顺序.
     *
     * 若未来实现文件大小排序筛选, 用户可能会倾向于选择更大或更小的文件, 而未知的文件大小在这两个种情况下都将会排序在最后, 导致最低优先级选择. (具体行为待定)
     */ // 提供的话, 在数据源选择器中会有一个标签显示这个大小
    val size: FileSize = 0.bytes, // note: default value only for compatibility
    /**
     * 字幕类型. 为 `null` 表示未知. 注意, `null` 并不代表没有字幕.
     *
     * ## APP 行为细节
     *
     * 由于各个平台的播放器有不同的缺陷, 客户端会强制过滤掉或低优先选择部分类型的资源
     * 详见 <https://github.com/open-ani/ani/issues/615>.
     *
     * 缺陷列表:
     * - macOS 完全不能播放内封类型，只能播放内嵌
     * - Android 播放双语内封时可能有问题，要低优先选择内封
     * - 所有平台均不能播放外挂资源
     *
     * @since 3.7
     */
    val subtitleKind: SubtitleKind? = null, // #615
    @Suppress("unused")
    @Transient private val _primaryConstructorMarker: Unit = Unit,
) {
    constructor(
        // so that caller still need to provide all properties despite we have default values for compatibility
        subjectName: String?,
        episodeName: String?,
        subtitleLanguageIds: List<String>,
        resolution: String,
        alliance: String,
        size: FileSize,
        subtitleKind: SubtitleKind?,
    ) : this(
        subjectName, episodeName, subtitleLanguageIds, resolution, alliance, size, subtitleKind,
        _primaryConstructorMarker = Unit,
    )

    override fun toString(): String {
        return "MediaProperties(subtitleLanguageIds=$subtitleLanguageIds, resolution='$resolution', alliance='$alliance', size=$size)"
    }
}

/**
 * 视频字幕类型
 * @see MediaProperties.subtitleKind
 */
@Serializable
enum class SubtitleKind {
    /**
     * 硬字幕（内嵌字幕），硬字幕是直接嵌入在视频画面中的，无法通过播放器设置来隐藏或去除。
     */
    EMBEDDED,

    /**
     * "内封字幕"是指嵌入在视频文件中的字幕，可以通过播放器的设置来显示或隐藏。这类字幕通常存在于视频文件的字幕轨道中，因此用户可以选择是否显示这些字幕。
     */
    CLOSED,

    /**
     * 通过 [Media.extraFiles] 提供的外挂字幕. 自建数据源通常为此类型.
     */
    EXTERNAL_PROVIDED, // Android 和 Windows 均支持此类型, macOS 无法渲染.

    /**
     * 需要播放器自行在视频目录下搜寻和匹配的的外挂字幕.
     */
    EXTERNAL_DISCOVER, // 所有平台均不支持

    /**
     * 可能是 [CLOSED] 或 [EXTERNAL_DISCOVER].
     */
    CLOSED_OR_EXTERNAL_DISCOVER,
}
