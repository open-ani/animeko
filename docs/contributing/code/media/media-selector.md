# MediaSelector

MediaSelector 是用于管理一组 `Media`
，通过对其进行过滤、应用用户偏好以及上下文信息，最终选择出单个 `Media` 资源的选择器接口。

MediaSelector 主要包含以下四个阶段：

1. **过滤**：基于条目和剧集信息，遍历每个 media，决定是保留还是排除一个
   media。当被过滤时，会携带排除原因。
2. **排序**：通过第一阶段的 media，将会按阶级（数据源或 channel 级，代表质量）、字幕类型等属性排序。
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

过滤算法可以用以下简化的代码描述：

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

## 排序阶段

排序入口为 [`MediaSelectorFilterSortAlgorithm.sortMediaList`][MediaSelectorFilterSortAlgorithm]。
排序是**稳定**的，按以下优先级逐级比较，前一级相等才比较下一级：

1. **是否被排除**：`Included` 在前，`Excluded` 全部排在最后；
2. **播放器兼容性**：当前平台播放器不能正常播放的字幕类型靠后；
3. **资源类型**：本地缓存永远最前；其余按用户偏好的类型（`preferKind`，WEB 或 BT）排序；
4. **下载代价**：`Local` < `Lan` < `Online`；
5. **阶级（tier）**：按资源的*有效阶级*升序。有效阶级优先取该资源所属 channel 的阶级
   （`MediaSelectorSourceTiers.channelTiers`，以 `Media.properties.alliance` 为 channel 名），
   未配置时回退到数据源阶级，详见[数据源阶级](media-source.md#数据源阶级)；
6. **发布时间**：新的在前；
7. **条目名称相似度**：高的在前。

由于 channel 阶级参与第 5 级比较，同一数据源不同 channel 的资源可以与其他数据源交叉排序。
例如 A 源的 channel A/B 为 tier 0、B 源的 channel C 为 tier 1 时，排序为
`A/channelA、A/channelB → B/channelC`，而不是按数据源整体分块。

### Web 自动选择 (决策核)

偏好 WEB 时的自动选择由 `domain/media/selector/engine/` 下的三层组成，取代了旧的 `select{}` 四路竞速：

| 层 | 文件 | 职责 |
|---|---|---|
| 快照 | `AutoSelectSnapshot.kt` | `MediaFetchSession.sourceSnapshots()` 观察每个源的 (状态, 结果)；`MediaSelector.autoSelectSnapshots()` 在同一次 emission 内同步算出过滤、排序、偏好筛选，产出 `AutoSelectSnapshot`。不经过 UI 用的 `filteredCandidates` 等带缓存的派生流，因此没有“源已完成但候选还没吸收结果”的中间态。 |
| 纯决策 | `WebAutoSelectPolicy.kt`、`MediaSelectionDecider.kt` | `decideWebAutoSelect(snapshot, config, stage)` 是无 suspend、无副作用、无时间的纯函数，返回 `Wait` / `ReleasePreferredSourceGate` / `Select` / `Finish`。`findMediaByPreference` 是原 DFS 偏好选择的纯函数版本。 |
| 执行 | `WebAutoSelectDriver.kt` | `runWebAutoSelect` 是唯一的非纯部分：`combine(快照, 阶段, 当前选择)` 每次变化调用一次策略并执行决定；两个计时器在记忆源放行后推进阶段；提交用 `selectAutomatically` 做 CAS，不更新偏好。 |

`MediaSelectorAutoSelect` 的 `autoSelectWeb`、`fastSelectWebSources`、`trySelectPreferredWebSource` 都是同一策略的不同配置
（`WebAutoSelectConfig`）。偏好 BT 或无偏好时仍沿用原有编排（记忆源、缓存、兜底三条路径竞速）。

策略按固定顺序求值，优先级是结构性的，不依赖并发时序：

1. 开启 `selectCache` 时任何阶段有本地缓存就选缓存；
2. `PREFERRED_SOURCE` 阶段：记忆的 web 源未结束则等；结束后只在它的偏好候选里选；选不出则放行并开始计时；
3. 分阶段规则（所有 WEB 源都结束时直接按 FUZZY 评估）：

| 阶段 | 时间（从放行起算） | 可选的资源 | 组的顺序 |
|---|---|---|---|
| INSTANT | 0 到 `fastSelectWebLowTierToleranceDuration`（默认 5 秒） | 有效阶级 ≤ 0 且条目名称精确匹配 | 单组 |
| EXACT_ONLY | 至 `DefaultFuzzyFallbackDuration`（15 秒） | 任意阶级，只要精确匹配 | 按有效阶级升序逐组 |
| FUZZY | 15 秒后，或所有 WEB 源都已结束 | 任意 | 精确匹配各阶级升序，再模糊匹配各阶级升序 |

   组内先在用户偏好候选里按偏好选（字幕组放开），再放开全部偏好选；严格逐组保证阶级优先级不会被数据源列表顺序或分辨率、语言偏好跨阶级覆盖。覆盖模式（播放失败换源）下当前选择若在组内则保留它。
4. 所有 WEB 源都结束仍选不出：编排入口按偏好从偏好候选（含非 WEB）里选一个（旧 `trySelectDefault` 语义），否则结束。
   “都结束”的口径与旧兜底一致：一个 WEB 源都没启用时改为等所有源结束，只用 BT 或缓存的用户仍能得到默认选择。

“精确匹配”指 `MatchMetadata.subjectMatchKind == EXACT`；数据源搜到 OVA 剧集时条目名加 “OVA” 也算精确。阶级判定按 channel 粒度。

与旧实现相比的故意变更：所有 WEB 源结束后会放宽偏好选择（旧实现只看偏好候选，偏好字幕组或偏好源不在候选中时什么都不选）；
模糊匹配的 T0 资源不再被秒选；所有 WEB 源都结束后不再等容忍窗。

[MediaSelectorFilterSortAlgorithm]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt

[MaybeExcludedMedia]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MaybeExcludedMedia.kt

