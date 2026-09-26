/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.paging.SizedSource
import kotlin.jvm.JvmInline

/**
 * 一个查询条目的可下载的资源 [Media] 的服务, 称为数据源 [MediaSource].
 *
 * 数据源不提供条目数据, 而是依赖条目服务 (即 Bangumi) 提供的条目数据.
 * 数据源的查询 [fetch] 可以拿到包含条目信息的 [MediaFetchRequest].
 * 数据源只需要支持使用 [MediaFetchRequest] 中的信息, 查询该条目在此源上的所有可下载资源 [Media].
 *
 * [MediaSource] 是一个抽象的来源. 它不一定都是来自网络和 BT, 也可以是本地文件系统.
 * 用户保存的视频下载由下载管理器 `MediaDownloadManager` 管理, 然后能通过一个专门查询本地下载的 [MediaSource] 查询到.
 *
 * ## 两种使用方式: 浏览与自动匹配
 *
 * 数据源像一个网站: 按关键字搜索得到条目列表 ([searchSubjects]), 打开条目得到线路与剧集列表 ([browseSubject]),
 * 选中一集得到可播放的资源 ([createMedia]). 这是数据源的基础形态, 只要求把站点上的东西"列出来",
 * 不要求判断条目是否属于请求、剧集是不是第几集; 由用户 (而不是数据源) 决定哪一条对应正在观看的剧集.
 *
 * [fetch] 是建立在浏览之上的自动模式: 数据源自己搜索、筛选条目、解析集号, 返回带准确 [Media.episodeRange] 的资源列表,
 * 交给数据源选择器 `MediaSelector` 自动选择. 自动模式解析不出的资源 (例如站点上名字不含集号的 OVA), 用户仍可以通过浏览手动选中.
 * 同一集不论从哪条路径得到, [Media.mediaId] 必须相同.
 *
 * ## [MediaSource] 只负责查询资源 ([Media]) 列表
 *
 * 对于资源的下载, 缓存, 以及播放, 都是由其他模块负责. 具体内容可查看:
 * - 下载过程: `MediaCacheEngine`
 * - 管理下载列表: `MediaDownloadManager`
 * - 解析 [Media] 为可播放的视频数据: `VideoSourceResolver`
 *
 * ## 资源信息
 *
 * 数据源查询到的资源, 为 [Media]. 详细查看 [Media]. 对于在线数据源, 通常为 [DefaultMedia].
 * [CachedMedia] 只有缓存数据源才会返回.
 *
 * ## 数据源全局唯一
 *
 * 每个数据源都拥有全局唯一的 ID [mediaSourceId], 可用于保存用户偏好, 识别缓存资源的来源等.
 *
 * ## 加载和配置数据源
 *
 * [MediaSource] 实际上需要通过工厂 [MediaSourceFactory.create] 构造.
 *
 * [MediaSourceFactory] 为数据源定义了可配置参数 [MediaSourceFactory.parameters], 并能使用这些参数[创建][MediaSourceFactory.create]一个示例.
 *
 * 详细查看 [MediaSourceFactory].
 *
 * ### 使 APP 能够检测到新的 [MediaSource] 的示例步骤
 *
 * 假设你已经实现了一个数据源, 名为 `foo`, 模块位置为 `:data-sources:foo`.
 * 1. 在 `data-sources/foo/resources/META-INF/services` 目录下创建一个名为 `me.him188.ani.datasources.api.source.MediaSourceFactory` 的文件
 * 2. 在文件中写入你的 `MediaSourceFactory` 的全限定类名, 例如 `me.him188.ani.datasources.api.source.impl.MyMediaSourceFactory`
 * 3. 在 `MyMediaSourceFactory` 中实现 `create` 方法, 根据传入的 [MediaSourceConfig], 构造并返回你的 [MediaSource] 实例
 * 4. 在 `:app:shared` 中的 `build.gradle.kts` 搜索 `api(projects.datasource.core)`, 找到现有数据源的依赖定义,
 * 仿照着增加一行你的模块: `api(projects.datasource.foo)`
 * 5. 现在启动 app 便可以自动加载你的数据源了, 可在设置中验证
 *
 * @see MediaSourceConfig
 * @see MediaSourceFactory
 */
interface MediaSource : AutoCloseable {
    /**
     * 全局唯一的 ID. 可用于保存用户偏好, 识别缓存资源的来源等.
     */
    val mediaSourceId: String

    /**
     * 数据源 [MediaSource] 以及资源 [Media] 的存放位置,
     * 因为一个资源既可以来源于网络, 也可以来自本地文件系统等.
     */
    val location: MediaSourceLocation
        get() = MediaSourceLocation.Online

    /**
     * 数据源类型. 不同类型的资源在缓冲速度上可能有本质上的区别.
     */
    val kind: MediaSourceKind

    /**
     * 此数据源的描述信息
     */
    val info: MediaSourceInfo

    /**
     * 检查该数据源是否可用.
     *
     * @see Boolean.toConnectionStatus
     */ // 这会在设置的数据源测试中使用.
    suspend fun checkConnection(): ConnectionStatus

    /**
     * 使用 [MediaFetchRequest] 中的信息, 尽可能多地查询一个**条目**在此源上的所有可下载的资源, 返回一个分页的资源列表.
     *
     * 一次查询的结果供该条目的所有剧集使用: 播放页切集与批量下载都不会为每一集重新查询. 数据源承诺三件事:
     *
     * ### 完整性
     * 返回该条目的所有集, 所有线路 / 字幕组, 单集与合集资源, 不得按请求中的当前剧集 ([MediaFetchRequest.episodeSort] 等) 剔除.
     * 按集裁剪由数据源选择器 `MediaSelector` 完成. 数据源仍应剔除**完全**肯定属于其他条目的资源.
     *
     * ### [Media.episodeRange] 准确
     * 单集资源为 [me.him188.ani.datasources.api.topic.EpisodeRange.single], 已知集数的合集为
     * [me.him188.ani.datasources.api.topic.EpisodeRange.range], 只知道是整季时才用
     * [me.him188.ani.datasources.api.topic.EpisodeRange.season]. 解析不出剧集时保留 `null`, 不要猜测为请求中的当前剧集.
     * 选择器只会向用户展示剧集范围包含当前剧集的资源, 范围为 `null` 的资源不会被展示.
     *
     * ### [Media.mediaId] 稳定
     * 同一资源在多次查询之间返回相同的 id, id 不包含请求信息. 下载去重与已下载合集的复用依赖它.
     *
     * ## 匹配等级
     *
     * - 通过条目 ID 精确定位到条目的结果, 标记为 [MatchKind.EXACT].
     * - 通过关键字搜索得到, 无法 100% 确定属于该条目的结果, 标记为 [MatchKind.FUZZY].
     *
     * 所有 [fetch] 返回的资源, 都将会被数据源选择器 `MediaSelector` 接收.
     * 数据源选择系统有一系列过滤选项 (APP 设置中 "播放与缓存" 的 "高级设置"), 例如隐藏生肉.
     *
     * @throws kotlinx.io.IOException
     * @throws kotlin.coroutines.cancellation.CancellationException
     */
    suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch>

    /**
     * 是否实现了浏览协议 ([searchSubjects], [browseSubject], [createMedia]).
     *
     * 为 `false` 的数据源不出现在手动查找的源列表中, 其浏览方法保持默认实现 (空结果 / 抛出异常) 只用于兼容;
     * 浏览记忆回放也只对为 `true` 的源生效.
     * @since 6.2
     */
    val supportsBrowsing: Boolean get() = false

    /**
     * 按关键字搜索, 返回站点上的条目列表. 不做筛选, 关键字原样使用.
     *
     * 尚未迁移到浏览形态的数据源返回空列表; 新数据源必须实现.
     *
     * @return 空列表表示没有结果.
     * @throws kotlinx.io.IOException
     * @throws kotlin.coroutines.cancellation.CancellationException
     * @since 6.2
     */
    suspend fun searchSubjects(keyword: String): List<BrowseSubject> = emptyList()

    /**
     * 打开一个条目, 返回它的全部线路与各线路的剧集列表, 顺序与站点页面一致, 同名线路不合并.
     *
     * 没有线路概念的站点返回一个 [BrowseChannel.name] 为 `null` 的线路.
     * 尚未迁移到浏览形态的数据源返回空列表; 新数据源必须实现.
     *
     * @return 空列表表示条目页面不存在或没有剧集.
     * @throws kotlinx.io.IOException
     * @throws kotlin.coroutines.cancellation.CancellationException
     * @since 6.2
     */
    suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> = emptyList()

    /**
     * 把浏览到的一集转换为可播放、可下载的 [Media].
     *
     * 尚未迁移到浏览形态的数据源抛出 [UnsupportedOperationException]; 新数据源必须实现.
     *
     * @param channelName 该集所在线路 [BrowseChannel.name].
     * @param episodeSort 该资源对应条目服务的哪一集, 决定 [Media.episodeRange]. 用户手动选择时由调用方给出当前剧集;
     * `null` 表示不知道, 这样的资源不会被自动选择, 只能临时播放.
     * @since 6.2
     */
    fun createMedia(
        subject: BrowseSubject,
        channelName: String?,
        episode: BrowseEpisode,
        episodeSort: EpisodeSort?,
    ): Media = throw UnsupportedOperationException("MediaSource '$mediaSourceId' does not support browsing")

    override fun close() {}
}

class MediaSourceInfo(
    val displayName: String,
    val description: String? = null,
    val websiteUrl: String? = null,
    val iconUrl: String? = null,
    val iconResourceId: String? = null, // not very good be fine for now
    /**
     * 例如本地缓存
     */
    val isSpecial: Boolean = false,
    val tier: MediaSourceTier? = null,
)

/**
 * 数据源的等级.
 *
 * 等级主要用来排序, 影响自动选择数据源. 等级的值越低, 越高优先使用.
 *
 * 具体算法参考 [DefaultMediaSelector].
 *
 * @since 4.7
 */
@JvmInline
@Serializable // serialized as Int
value class MediaSourceTier(val value: UInt) : Comparable<MediaSourceTier> {
    override fun compareTo(other: MediaSourceTier): Int = this.value.compareTo(other.value)

    companion object {
        /**
         * 当数据源订阅没有指定 tier, 并且用户没有手动设置 tier 时的 fallback 值.
         *
         * 默认在范围内 [me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelect.InstantSelectTierThreshold].
         */
        val Fallback = MediaSourceTier(2u)

        val MaximumValue = MediaSourceTier(UInt.MAX_VALUE)
    }
}
