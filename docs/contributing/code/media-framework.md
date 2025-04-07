# Media Framework

本文档系统性地介绍 Ani 的 Media 框架。
文档更多关注模块、组件之间的交互，而不是代码细节。代码细节可以参阅源码内文档。

Media 框架是 Ani 对视频播放和缓存功能至关重要的系统，涵盖数据源、查询、选择、以及下载流程。

> [!IMPORTANT]
> 请确保已阅读[条目系统](subjects.md)。

Media 框架的组成
--------------

Media 框架由以下组件组成：

- [数据源 `MediaSource`](media/media-source.md)：*资源*（`Media`）的提供商。
  `MediaSource` 主要负责查询[剧集](subjects.md#剧集)的资源；
- 数据源管理器 `MediaSourceManager`：负责管理多个 `MediaSource` 实例：处理启用/禁用等；
- 资源查找器 `MediaFetcher`：负责从多个 `MediaSource` 中同时查询资源并整合结果；
- [资源选择器 `MediaSelector`](media/media-selector.md)：负责根据用户的偏好设置，从查询到的资源中的选择合适的。

> [!IMPORTANT]
> **请注意区分“数据源”和“资源”两个概念。**
>
> 在与普通用户沟通时，“数据源”和“资源”的概念是模糊的。“资源选择器”可能也会说成“数据源选择器”。
>
> 在本技术文档中，我们会显式区分“数据源”和“资源”。“数据源”一定指代 `MediaSource`；“资源”一定指代
`Media`。

### 资源“查询-选择-播放”流程

为了帮助理解，我们先了解在播放视频时的“查询-选择-播放”全流程。我们将在之后介绍各个组件的细节。

在 APP 启动时，`MediaSourceManager` 会初始化所有 `MediaSource` 实例。这通常是从订阅中获取数据源列表，然后为它们分别创建
`MediaSource`。
APP 可能会有 50 个左右的 `MediaSource` 实例。

从用户进入播放页面到开始播放之间发生的事情，可以拆解为：

1. **创建查询请求**。页面会使用条目信息和剧集信息创建一个查询请求。查询请求会包含条目的名称、ID、剧集序号和名称等信息，供
   `MediaSource` 可选使用；
2. **开始查询**。页面会使用 `MediaSourceManager` 创建一个 `MediaFetcher`。该 fetcher 将会在后台并发查询所有数据源；
   `MediaSource` 会使用查询请求中的部分或全部信息进行查询。
3. **合并结果**。每当有一个数据源查询完成，`MediaFetcher` 都会合并该数据源的结果到一个综合结果列表，广播结果列表的更新；
4. **过滤和排序结果**。`MediaSelector` 会监听 `MediaFetcher` 的结果，每当有结果更新时都会重新过滤和排序
   Media。Media 将会被划分为“包含”和“排除”两类，然后按用户偏好和排序规则排序；
5. **自动选择结果**。播放页面会在合适的时机，让 `MediaSelector` 自动选择一个资源；
6. **播放**。被选择的资源将会触发播放器重新加载并播放该资源。

资源 `Media`
-----------

一个资源 `Media` 是从一个数据源中查询得到的，某个剧集的播放链接和相关信息。Media 包含以下重要属性：

- 下载信息：
    - 下载方式：磁力链接、HLS 流式传输资源（如 m3u8）等；
    - 类型 `kind`：目前可以是在线视频 `WEB` 和 `BitTorrent`；
- 元数据：
    - 番剧标题（注意，这是数据源提供的番剧标题，不一定对应 Bangumi 条目名称）；
    - 剧集标题（同上，不一定对应 Bangumi）；
    - 包含的剧集范围 `episodeRange: EpisodeRange`；
    - 字幕语言类型（简中、繁中）；
    - 字幕类型（内嵌、内封、外挂）；
    - 原始标题（字幕组发布的标题）；

播放一个剧集时，在技术上其实是播放了包含该剧集的 `Media`。元数据十分重要，它会直接影响后续的过滤和排序。

> [!NOTE]
> **设计缺陷**
>
> 你可能已经注意到了，`Media` 有一些设计缺陷：它可以支持多个剧集范围，但却只有一个标量“剧集名称”
> 属性，没办法表示多个剧集名称。目前只有在剧集范围为单个剧集的情况下，数据源才会提供“剧集名称”。
> 剧集的序号也有一些缺陷。我们计划在不久后的将来解决这些问题。

### Media 与条目和剧集的对应关系

在设计上，一个 `Media` 可以包含多个视频文件，每个分别对应一个剧集，但这些剧集都必须属于同一个条目。

通常来说：

- 对于 WEB 类型资源，`Media` 只包含一个剧集；
- 对于 BT 类型的单集资源，`Media` 只包含一个剧集；
- 对于 BT 类型的季度全集资源，`Media` 可能包含一个条目的所有剧集。

资源选择算法
----------

参见 [MediaSelector](media/media-selector.md)

----

> [!TIP]
>
> 更多 Media 相关文档：
> - [缓存](media/media-cache.md)


[无职转生]: https://bangumi.tv/subject/277554

[Media]: ../../../datasource/api/src/commonMain/kotlin/Media.kt

[MediaSource]: ../../../datasource/api/src/commonMain/kotlin/source/MediaSource.kt
