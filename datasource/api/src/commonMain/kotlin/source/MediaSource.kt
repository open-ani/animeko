/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.paging.SizedSource

/**
 * 一个查询单个剧集的可下载的资源 [Media] 的服务, 称为数据源 [MediaSource].
 *
 * 数据源不提供条目数据, 而是依赖条目服务 (即 Bangumi) 提供的条目数据.
 * 数据源的查询 [fetch] 可以拿到包含条目信息的 [MediaFetchRequest].
 * 数据源只需要支持使用 [MediaFetchRequest] 中的信息, 查询该剧集的所有可下载资源 [Media].
 *
 * [MediaSource] 是一个抽象的来源. 它不一定都是来自网络和 BT, 也可以是本地文件系统.
 * 用户使用缓存功能创建的缓存, 就会存储到缓存管理器 `MediaCacheManager`, 然后能通过一个专门查询本地缓存的 [MediaSource] 查询到.
 *
 * ## [MediaSource] 只负责查询资源 ([Media]) 列表
 *
 * 对于资源的下载, 缓存, 以及播放, 都是由其他模块负责. 具体内容可查看:
 * - 下载过程: `MediaCacheEngine`
 * - 管理缓存列表: `MediaCacheManager`
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
     * 使用 [MediaFetchRequest] 中的信息, 尽可能多地查询一个剧集的所有可下载的资源, 返回一个分页的资源列表.
     *
     * 数据源应当尽可能*精准*地返回结果:
     * - 对于**完全**肯定匹配的资源, 标记为 [MatchKind.EXACT].
     * - 对于**完全**肯定不不配的资源, 需要剔除.
     * - 对于无法 100% 区分的, 则应当返回, 并标记为 [MatchKind.FUZZY].
     *
     * ## 数据源选择的实现细节
     *
     * ### 数据源需要负责区分剧集的正确性
     * 若请求 [MediaFetchRequest.episodeSort] 为 "01", 但 [fetch] 返回 "02", 该剧集**不会**被后续流程自动剔除, 它会被原封不动地展示给用户.
     *
     * 所有 [fetch] 返回的资源, 都将会被数据源选择器 `MediaSelector` 接收并能够显示.
     * 但需要注意数据源选择系统有一系列过滤选项 (APP 设置中 "播放与缓存" 的 "高级设置")
     *
     * 当用户关闭设置中的所有自动过滤选项时, 将能够看到所有 [fetch] 返回的资源.
     *
     * @throws kotlinx.io.IOException
     * @throws kotlin.coroutines.cancellation.CancellationException
     */
    suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch>

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
)
