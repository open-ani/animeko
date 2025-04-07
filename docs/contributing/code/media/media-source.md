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

> 自 Animeko v4.8

## 扩展数据源支持

有以下多种方法扩展数据源支持：

- （最简单）编写通用的数据源的配置。可以在 APP 内“设置-数据源管理”中添加 `Selector` 和 `RSS`
  类型数据源。只需编写一些 CSS Selector 配置即可使用。
- 实现新的 `MediaSelector`。参考 `IkarosMediaSource`（位于 `datasource/ikaros`）。通常需要为 Animeko
  仓库提交代码，增加一个新的模块。

[Media]: ../../../../datasource/api/src/commonMain/kotlin/Media.kt

[MediaSource]: ../../../../datasource/api/src/commonMain/kotlin/source/MediaSource.kt

[dmhy]: http://www.dmhy.org/

[Mikan]: https://mikanani.me/

[Ikaros]: https://ikaros.run/

[CSS Selector]: https://developer.mozilla.org/zh-CN/docs/Web/CSS/CSS_selectors
