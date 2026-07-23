# MediaSource

数据源 `MediaSource` 是*资源*（[Media][Media]）的提供商。

`MediaSource` 主要提供函数 `fetch`，负责查询[剧集](../subjects.md#剧集)的资源：

```kotlin
interface MediaSource {
    suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> // 可以理解为返回 List<Media>
}
```

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


[//]: # (TODO: MediaFetcher? 考虑状态、错误处理、重试)

## 数据源阶级

> 自 Animeko v4.8。Channel 级阶级自 v4.9。

每个数据源拥有一个阶级 [`MediaSourceTier`][MediaSourceTier]。阶级值越低表示质量越高：`0`
为最高阶级。阶级影响 [MediaSelector](media-selector.md) 的两个环节：

- **排序**：有效阶级低的资源排在前面，详见[排序阶段](media-selector.md#排序阶段)；
- **快速选择**：阶级不超过阈值（目前为 `0`）的 WEB 数据源查询完成后会被立即选择，
  无需等待其他数据源。超过阈值的数据源只能在等待一段时间后通过兜底逻辑被选择。
  入口为 `MediaSelectorAutoSelect.fastSelectWebSources`。

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
