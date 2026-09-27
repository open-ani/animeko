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

#### 列表模式与自动匹配

> 自 Animeko v6.2。

数据源像一个网站：搜索得到条目列表，打开条目得到线路与剧集列表，选中一集得到可播放的资源。
这是数据源的基础形态，只要求把站点上的东西列出来，不要求判断条目是否属于请求、剧集是不是第几集，
由用户决定哪一条对应正在观看的剧集。自动匹配是这之上的可选一层。

Selector 配置据此分两层：

- **列表规则**：搜索结果怎么列、条目页的线路和剧集怎么列、播放页怎么提取视频。只写这一层，
  数据源就可以浏览和手动选集。
- **自动匹配**（`searchConfig.autoMatch`，以及 `tier` / `channelTiers`）：在列表之上让 `fetch`
  自动搜索并筛选出当前剧集的资源。全部可选，缺省时按默认策略自动匹配；`enabled: false`
  表示该源只供浏览，`fetch` 不发起任何请求。这类源在自动匹配页没有结果，在手动查找中照常可用。

拆分的原因：自动选择要在十几个数据源之间挑出正确的条目和正确的一集，需要条目名过滤、集号正则、
线路名正则、阶级等一整套判断规则；把它们与列表规则混在一起，编写门槛高，而站点上名字不含集号的资源
（OVA、特典）无论怎么配也自动选不出来。列表规则独立后，这些资源可以通过浏览手动选中。

运行时协议上，浏览是 `MediaSource` 本身的一部分：按关键字搜索条目、打开条目得到线路与剧集、
把一集转换为 `Media`。它不做任何匹配，关键字由调用方给出，结果原样返回，同名线路也不合并；
`fetch` 是建立在它之上的自动模式。所有数据源都应当是这种形态；尚未迁移的数据源（RSS、BT、媒体库）
以“搜索不到任何条目”的默认实现过渡，新数据源必须实现浏览。手动选中一集时由调用方指定它对应哪一集，
`Media.episodeRange` 由此而来。同一集不论从哪条路径得到，`mediaId` 相同，下载去重与偏好记忆因此不区分来源。

支持浏览的数据源通过 `supportsBrowsing` 声明：接口上的浏览方法有默认实现（空结果 / 抛出异常），
运行时无法区分「支持但没搜到」与「不支持」，所以未声明的源不出现在手动查找的源列表中，浏览记忆也不对它回放，
默认实现只用于兼容。线路标识 `BrowseChannel.name` 在同一条目内可以重复，站点没有线路概念时为 null，
调用方以线路在列表中的位置区分，记住用户的选择时同时记录位置与标识。`BrowseEpisode.episodeSort`
是数据源对集号的解析结果，供调用方在找下一集时优先按集号比较，解析不出时才按位置推断；界面层不自行解析。
手动查找与记忆回放的取舍见[选源界面](media-selector-ui.md)。

浏览不读写搜索缓存：缓存按请求条目与关键字组织，服务于切集时不重复请求；
浏览的关键字由用户给出，缓存命中率低而语义含混，不值得共享。

条目格式按页面顺序返回结果，`autoMatch.preferShorterName` 只在自动匹配阶段把名称短的条目排到前面。
集号正则 `matchEpisodeSortFromName` 允许为空：列表规则不要求解析集号，空表示整个剧集名就是集号文本。

#### 配置的兼容形式

订阅 JSON 会被所有版本的客户端读取，而导出格式的版本号一旦升高，不认识该版本的客户端就会整个拒绝该源。
因此自动匹配字段除了 `autoMatch` 这一处，还接受平铺在 `searchConfig` 顶层的写法，`preferShorterName`
还接受写在各条目格式配置里的写法，导出格式版本号不变：读取时没有 `autoMatch` 键就按平铺字段组装，
两者都有时以 `autoMatch` 为准；写出时两处都写，只认识平铺写法的客户端仍能读到，
只是不认识 `enabled`，会把只供浏览的源当作普通源使用。平铺写法待这类客户端淘汰后移除。

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
