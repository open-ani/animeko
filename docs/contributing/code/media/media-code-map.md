# Media 框架代码地图

本文档是 [Media 框架](../media-framework.md)的代码级索引：把概念映射到具体的类、函数和调用链。
适合在需要修改数据源、资源选择或播放相关代码时快速定位入口点。

> [!NOTE]
> 本文档由 AI 生成（2026-07，类名与调用链已对照代码核对）。代码演进后可能过时，发现不一致请以源码为准并更新本文。

## 术语与核心类型

请在代码和讨论中精确使用以下术语：

- `MediaSource` = 资源提供商。它只负责根据 `MediaFetchRequest` 查询某一剧集的 `Media`。见
  `datasource/api/src/commonMain/kotlin/source/MediaSource.kt`。
- `Media` = 数据源返回的一个可播放/可缓存资源，携带 `download`、`episodeRange`、`properties`、
  `kind` 等属性。见 `datasource/api/src/commonMain/kotlin/Media.kt`。
- `MediaSourceFactory` = 数据源类型/模板。它根据 `MediaSourceConfig` 创建具体的 `MediaSource` 实例。
- `MediaSourceInstance` = `MediaSource` + `MediaSourceConfig` + 启用状态。持久化为 `MediaSourceSave`。
- `MediaSourceSubscription` = 远程数据源列表的订阅更新规则，不是可播放的数据源实例。

## 核心组件与职责

- `MediaSourceManagerImpl`（`app/shared/app-data/.../domain/media/fetch/MediaSourceManager.kt`）
  加载所有 factory，从 `MediaSourceInstanceRepository` 恢复持久化的实例，注入本地缓存数据源，
  创建真正的 `MediaSourceInstance`。
- `MediaSourceInstanceRepository` 存储本地数据源实例；`MediaSourceSubscriptionRepository` 存储订阅规则。
- `MediaSourceCodecManager` 编解码导出的数据源定义。目前注册的 codec 有 `RssMediaSourceCodec` 和
  `SelectorMediaSourceCodec`。
- `MediaSourceSubscriptionUpdater` 下载订阅内容，按 `subscriptionId` 与现有实例 diff，
  然后增/改/删/重排数据源实例。
- `MediaSourceSubscriptionRequesterImpl` 先直接拉取订阅 JSON，失败后回退到
  `SubscriptionsAniApi.proxy(...)`。

## 重要的通用数据源实现

- `SelectorMediaSource`（`app/shared/app-data/.../domain/mediasource/web/SelectorMediaSource.kt`）：
  通用 HTML/CSS-selector 网页数据源。
- `RssMediaSource`（`app/shared/app-data/.../domain/mediasource/rss/RssMediaSource.kt`）：
  通用 RSS BitTorrent 数据源。
- 本地缓存以数据源形式暴露：`MediaCacheStorageSource`
  （`app/shared/app-data/.../domain/media/cache/storage/MediaCacheStorage.kt`）。

### Selector 数据源查询/解析管线

1. `SelectorMediaSource.fetch(...)`
2. `DefaultSelectorMediaSourceEngine.search(...)`
3. `searchSubjects(...)`
4. `selectSubjects(...)`
5. `doHttpGet(...)` 请求条目页面
6. `selectEpisodes(...)`
7. `selectMedia(...)`

注意：

- `selectMedia(...)` 产出的 `DefaultMedia` 带有 `download = ResourceLocation.WebVideo(...)`。
- `SelectorMediaSource.matcher` 提供数据源专属的 `WebVideoMatcher`，供之后播放时提取 URL 使用。

### RSS 数据源查询/解析管线

1. `RssMediaSource.fetch(...)`
2. `startSearch(...)`
3. `DefaultRssMediaSourceEngine.search(...)`
4. 拉取 XML 并用 `RssParser` 解析
5. `convertItemToMedia(...)`

## 单个剧集的播放时序

1. `EpisodeSession.fetchSelectFlow` 由 `CreateMediaFetchSelectBundleFlowUseCaseImpl` 创建。
2. 它根据条目/剧集信息构建 `MediaFetchRequest`。
3. `MediaSourceManager.createFetchFetchSession(...)` 创建 `MediaFetchSession`。
4. `MediaSourceMediaFetcher` 并发调用每个启用实例的 `instance.source.fetch(...)`，
   合并进 `cumulativeResults`。
5. `DefaultMediaSelector` 对查询到的 `Media` 过滤、排序、选择。
6. `MediaSelectorAutoSelectUseCaseImpl` 执行自动选择策略：偏好的 web 源、fast web select、
   缓存优先，最后兜底。
7. `EpisodeFetchSelectPlayState.LoadMediaOnSelectExtension` 监听 `mediaSelector.selected` 并调用
   `PlayerSession.loadMedia(...)`。
8. `PlayerSession.loadMedia(...)` 通过 `MediaResolver.resolve(...)` 解析，打开得到的
   `MediaDataProvider`，然后调用 `player.setMediaData(...)`。

## 过滤与选择

- 过滤/排序主实现：`MediaSelectorFilterSortAlgorithm`。算法细节见
  [MediaSelector](media-selector.md)。
- 选择器主实现：`DefaultMediaSelector`。
- 阶级（tier）：`MediaSelectorSourceTiers`
  （`app/shared/app-data/.../domain/media/selector/MediaSelectorContext.kt`）
  持有数据源级 `tiers` 与 channel 级 `channelTiers`，由
  `MediaSourceManagerImpl.mediaSourceTiersFlow()` 从各实例的
  `MediaSourceArguments.tier` / `channelTiers` 汇总。排序按
  `(mediaSourceId, alliance)` 查有效阶级；快速选择
  `MediaSelectorAutoSelect.fastSelectWebSources` 用 `getBestTier(...)` 判定候选数据源，
  并通过 `awaitSelectFromMediaSources` 的 `candidateMediaFilter` 参数把秒选限制在低阶级
  channel 的资源上。
- 查询结果并不天然“正确”；数据源实现应尽量返回准确的 `episodeRange`，`MediaSelector`
  只是在其上做额外的过滤和排序。

## 播放时使用的 Resolver

- `TorrentMediaResolver`：解析磁力/种子资源，并在种子内选择具体视频文件。
- `HttpStreamingMediaResolver`：解析直接 HTTP 流媒体文件。
- 平台 web resolver：
    - Android：`AndroidWebMediaResolver`
    - Desktop：`DesktopWebMediaResolver`
    - Apple/iOS：`IosWebMediaResolver`

### Web 播放的重要细节

- `ResourceLocation.WebVideo` 不能直接播放。
- 平台 web resolver 通过 `MediaSourceManager.webVideoMatcherLoader` 加载数据源提供的
  `WebVideoMatcher`。
- 对于 Selector 数据源，matcher 来自 `SelectorMediaSource.matcher`，它使用配置的正则/Cookie
  提取最终的流 URL 和请求头。
