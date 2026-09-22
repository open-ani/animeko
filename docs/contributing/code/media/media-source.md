# MediaSource

数据源 `MediaSource` 是*资源*（[Media][Media]）的提供商。

`MediaSource` 主要提供函数 `fetch`，负责查询一个[条目](../subjects.md)的资源：

```kotlin
interface MediaSource {
    suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> // 可以理解为返回 List<Media>
}
```

查询以条目为单位。`MediaFetchRequest` 携带条目名称与 ID、条目的全部剧集（`episodes`）以及当前剧集的提示；
数据源返回该条目在本源能找到的全部资源：每一集的单集资源、每条线路（字幕组）以及合集，
不按当前剧集裁剪。按当前剧集筛选由 [MediaSelector](media-selector.md) 完成。
数据源须让每个资源的 `episodeRange` 尽量准确，并保证同一资源的 `mediaId` 在多次查询间稳定，
这样播放页切集只需重建选择器，下载可以为多集复用同一次查询。
`MatchKind.EXACT` 表示通过条目 ID 定位到了条目，`FUZZY` 表示由关键字搜索得到。

## 数据源类型

目前支持两种通用数据源和一些特别支持的数据源：

- `SelectorMediaSource`：通用 [CSS Selector][CSS Selector] 数据源；
- `RssMediaSource`：通用 RSS 订阅数据源；
- 特别支持的数据源：
    - `JellyfinMediaSource`、`EmbyMediaSource`：Jellyfin、Emby 媒体库；
    - `DmhyMediaSource`、`MikanMediaSource`：[动漫花园][dmhy]、[蜜柑计划][Mikan] 站点；
    - `IkarosMediaSource`：[Ikaros][Ikaros] 媒体库。

特别支持的数据源只是实现 `MediaSource` 接口以接入对应平台，本文不赘述。
下面我们将着重了解 `SelectorMediaSource` 和 `RssMediaSource`。

### `SelectorMediaSource`

`SelectorMediaSource` 会根据配置，使用 [CSS Selector][CSS Selector] 和正则表达式，从 HTML
页面中提取资源信息及其播放方式。

[//]: # (TODO: SelectorMediaSource)


`MediaFetcher` 先将结果写入共享回放缓存，再发布成功或失败等终态。
完成标记与结果按序通过 `flatMapLatest` 的缓冲区；重试时忽略旧查询的标记，
因此观察到终态时可以读取该次查询的完整结果（失败时为已收到的部分结果）。

## 数据源阶级

> 自 Animeko v4.8。Channel 级阶级自 v4.9。

每个数据源拥有一个阶级 [`MediaSourceTier`][MediaSourceTier]。阶级值越低表示质量越高：`0`
为最高阶级。阶级影响 [MediaSelector](media-selector.md) 的两个环节：

- **排序**：有效阶级低的资源排在前面，详见[排序阶段](media-selector.md#排序阶段)；
- **快速选择**：阶级不超过阈值（目前为 `0`）的 WEB 数据源查询完成且有精确匹配结果后会被立即选择，
  无需等待其他数据源。超过阈值的数据源只能在等待一段时间后通过兜底逻辑被选择。
  入口为 `MediaAutoSelector.select` 的 WEB 阶段。

阶级来源于数据源配置 `MediaSourceArguments.tier`，通常由订阅提供；用户未配置时使用回退值
`MediaSourceTier.Fallback`（`2`）。

### Channel 级阶级

> 自 Animeko v4.9

`SelectorMediaSource` 支持 channel（俗称“线路”）：同一个页面上的多个播放列表。
数据源解析出的 channel 名称会写入资源的 `Media.properties.alliance` 属性。

`SelectorMediaSourceArguments.channelTiers` 可以为单个 channel 指定阶级，覆盖数据源整体的
`tier`；未列出的 channel 回退到数据源阶级。资源的**有效阶级**因此为：

```
有效阶级 = channelTiers[channel 名] ?: 数据源 tier
```

排序与快速选择都按有效阶级进行。这意味着：

- 同一数据源的不同 channel 可以与其他数据源交叉排序；
- 数据源整体阶级较高（数值大），但拥有一个 tier 0 channel 时，该 channel 的资源仍可被快速选择立即选中；
- 反之，数据源整体是 tier 0，但被降级的 channel 的资源不会被立即选中，只能走兜底。

订阅 JSON 中的配置示例（`SelectorMediaSourceArguments` 片段）：

```json
{
  "name": "示例源",
  "tier": 2,
  "channelTiers": {
    "线路A": 0,
    "线路B": 1
  }
}
```

新增字段对旧版本客户端向后兼容：解码器开启了 `ignoreUnknownKeys`，旧客户端会忽略
`channelTiers` 并继续使用数据源级阶级。

## 订阅启用状态

`MediaSourceSubscription.enabled` 持久化订阅的启用状态，默认值为 `true`。
禁用的订阅不参与自动或手动刷新。TV 设置中的订阅开关同时批量启用或禁用该订阅的所有数据源，
单个数据源仍可独立调整启用状态，不改变所属订阅状态。

`MediaSourceSubscriptionUpdater` 在订阅仓库事务之外下载订阅，在事务内检查当前启用状态并应用数据源差异。
订阅开关与差异应用共用仓库的串行更新边界，确保下载期间禁用订阅后不会应用过期响应。

## 扩展数据源支持

有以下多种方法扩展数据源支持：

- （最简单）编写通用的数据源的配置。可以在 APP 内“设置-数据源管理”中添加 `Selector` 和 `RSS`
  类型数据源。只需编写一些 CSS Selector 配置即可使用。
- 实现新的 `MediaSelector`。参考 `IkarosMediaSource`（位于 `datasource/ikaros`）。通常需要为 Animeko
  仓库提交代码，增加一个新的模块。

[Media]: ../../../../datasource/api/src/commonMain/kotlin/Media.kt

[MediaSource]: ../../../../datasource/api/src/commonMain/kotlin/source/MediaSource.kt

[MediaSourceTier]: ../../../../datasource/api/src/commonMain/kotlin/source/MediaSource.kt

[dmhy]: http://www.dmhy.org/

[Mikan]: https://mikanani.me/

[Ikaros]: https://ikaros.run/

[CSS Selector]: https://developer.mozilla.org/zh-CN/docs/Web/CSS/CSS_selectors
