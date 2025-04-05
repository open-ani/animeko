# MediaSelector

MediaSelector 是用于管理一组 `Media`
，通过对其进行过滤、应用用户偏好以及上下文信息，最终选择出单个 `Media` 资源的选择器接口。

MediaSelector 主要包含以下四个阶段：

1. **过滤**：基于条目和剧集信息，遍历每个 media，决定是保留还是排除一个
   media。当被过滤时，会携带排除原因。
2. **排序**：通过第一阶段的 media，将会按数据源的阶级（质量）、字幕类型等属性排序。
3. **偏好**：根据用户对这个番剧的偏好，只采取满足用户喜好的 media。
4. **选择**：支持手动或自动方式来选中某个 [Media]：
    - 手动调用 `select` 方法。
    - 自动通过 `trySelectDefault`、`trySelectCached` 或 `trySelectFromMediaSources` 等方法完成。

   最终选定的资源会存入 `selected: StateFlow<Media>`，并通过 `events` Flow 广播变更。

## 前提问题

在详细介绍算法之前，我们先解决一些动机问题。

### 为什么需要过滤和排序

- 数据源的条目搜索是不准确的。假设正在观看“日常”第一集，向数据源搜索“日常”，会得到“日常”以及其他任何包含“日常”的条目，如“坂本日常”；
- 数据源的剧集是不准确的。详见：[为什么要考虑两种序号](#为什么要考虑两种序号)。

### 为什么要考虑两种序号

因为数据源对于有分割放送的系列的查询是不准确的。数据源给出的序号是可能有歧义的，届时我们必须选取 `ep`
或 `sort` 匹配。

例如“无职转生”系列，在播放 `无职转生 第2部分` 的第 2 集（`ep=2`, `sort=13`）时，数据源可能会返回以下情况：

- (Q1). `无职转生`（01 ~ 11 话）和 `无职转生 第2部分`
  （01 ~ 12 话）
- (Q2). `无职转生`（01 ~ 11 话）和 `无职转生 第2部分`
  （12 ~ 23 话）
- (Q3). `无职转生`（01 ~ 23 话）

为了播放正确的剧集，我们可以使用条目内序号 `ep` 或者系列内序号 `sort` 匹配。一个正确的匹配算法应当：

- 对于 Q1 情况，根据名称*精确匹配*到 `第2部分` 的番剧，然后播放其中的 `02`（匹配 `ep`）。
- 对于 Q2 情况，根据名称*精确匹配*到 `第2部分` 的番剧，然后播放其中的 `13`（匹配 `sort`）。
- 对于 Q3 情况，根据名称*模糊匹配*到番剧，然后播放其中的 `13`（匹配 `sort`）。

#### sort 和 ep 匹配优先级的考虑

在上面的示例中，注意到有时候需要使用 `sort` 匹配，有时候又需要使用 `ep`。

一个合理的优先级方案是：

- 当精确匹配条目（番剧）标题时，优先使用 `ep` 匹配，其次使用 `sort`。
- 当模糊匹配条目（番剧）标题时，优先使用 `sort`，其次使用 `ep`。

更准确的剧集选择需要数据源能识别到季度信息和分割放送。

> 考虑边界情况：使用上述示例，但假设正在 `无职转生 第2部分` 的第 1 集（`ep=1`,
`sort=12`），如果优先级不是上述方案，则会匹配错误。

> [!WARNING]
>
> 此行为暂未在 Ani 4.8.0 中实现。4.8.0 实现的算法总是优先匹配 `sort`。
> 此问题在 [#1448](https://github.com/open-ani/animeko/issues/1448) 中跟踪。

## 过滤阶段

在整个[资源查询-选择-播放流程](../media-framework.md#资源查询-选择-播放流程)中，资源主要是在
`MediaSelector` 环节过滤和排序。

> 这里说“主要是”，是因为 `MediaSource` 自身可以进行一些过滤操作。但是这只会进行一些非常保守的过滤。
> 而且让 `Source` 自己过滤的效果并不好，[#492](https://github.com/open-ani/animeko/issues/492)
> 可能会将所有过滤算法移入 MediaSelector 阶段。

> [!TIP]
>
> 所有的过滤和排序算法的代码入口点位于 [MediaSelectorFilterSortAlgorithm][MediaSelectorFilterSortAlgorithm]。

过滤阶段目前是独立考虑每个 media 的。

过滤算法可以用一下简化的代码描述：

```kotlin
// class MediaSelectorFilterSortAlgorithm

fun filterMediaList(
    list: List<Media>,
    preference: MediaPreference,
    settings: MediaSelectorSettings,
    context: MediaSelectorContext,
): List<MaybeExcludedMedia> =
    list.filter { filterMedia(it, preference, settings, context) }

private fun filterMedia(
    media: Media,
    context: MediaSelectorContext,
    settings: MediaSelectorSettings,
    preference: MediaPreference,
    mediaListFilterContext: MediaListFilterContext?
): MaybeExcludedMedia {
    if (rule1()) return exclude()
    if (rule2()) return exclude()
    if (rule3()) return exclude()
    // ...
    return include()
}
```

### `MaybeExcludedMedia`

Sealed class [`MaybeExcludedMedia`][MaybeExcludedMedia] 表示一个可能被排除的资源，包含其被排除的原因。它包装一个
`Media`, 并将其标记为包含或者排除：

- 如果是包含（`MaybeExcludedMedia.Included`），还会携带一些元数据 `MatchMetadata`，方便后续排序：
   ```kotlin
   data class MatchMetadata(
       val subjectMatchKind: SubjectMatchKind, // FUZZY or EXACT
       val episodeMatchKind: EpisodeMatchKind, // NONE, EP, SORT
       /** 条目名称相似度 */
       val similarity: @Range(from = 0L, to = 100L) Int,
   )
   ```
- 如果是排除（
  `MaybeExcludedMedia.Included`），还会携带被排除的原因。所有可能的原因将在 [过滤阶段](#过滤阶段) 列举。

### 过滤规则列表

参考代码中 [`MediaSelectorFilterSortAlgorithm.filterMediaList`][MediaSelectorFilterSortAlgorithm]。

[MediaSelectorFilterSortAlgorithm]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt

[MaybeExcludedMedia]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MaybeExcludedMedia.kt

