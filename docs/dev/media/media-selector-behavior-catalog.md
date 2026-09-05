# Media Selector 行为清单(重构基线)

> 生成自 2026-07-31 对 main 分支的全量代码盘点(全部行号已核实)。
> 这是重构的"不可破坏行为"基线:每一条要么已有测试覆盖,要么在重构前按[重构方案](media-selector-refactor.md)补齐。
> 共 128 条;已覆盖 70 条,缺口 56 条(P0 14 / P1 26 / P2 16)。
> 2026-09 Web 自动选择重构(方向 A 落地于 WEB 路径): ORCH-02~07、PREF-04、FAST-01~06、TRY-06、FIND-01、ERR-05、TIER-03、MIG-DUAL-02 已按新实现更新; ORCH-06 与 MIG-DUAL-02 为故意行为变更。

> kind 说明:**设计行为**=有意设计;**已知bug**=现存缺陷,先钉住,修复须列入故意变更清单;**workaround**=为绕竞态/循环依赖存在;**涌现**=无人设计的涌现行为。


## 过滤-排除规则

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| FILT-01 | 设计行为 | ❌ P1 | 本地缓存特权一: media.kind == LocalCache 的资源在 filterMedia 中第一步无条件返回 Included(附带 MatchMetadata), 即使它无字幕、属于续集季度或标题完全不匹配, 也绝不会被任何后续排除规则排除 _(注:注释: 本地缓存总是要显示)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:108` |
| FILT-02 | 设计行为 | ❌ P1 | 当 settings.hideSingleEpisodeForCompleted=true 且 context.subjectFinished==true 且 media.kind==BitTorrent 时: episodeRange==null 的资源被排除为 SingleEpisodeForCompleteSubject(episodeRange=null); episodeRange.isSingleEpisode() 的资源被排除为 SingleEpisodeForCompleteSubject(episodeRange=range)。WEB 与 LocalCache 资源不受此规则影响 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:110-120` |
| FILT-03 | 设计行为 | ❌ P2 | subjectFinished==null(条目完结信息尚未加载)时, 完结番隐藏单集规则不生效, BT 单集资源先正常显示; 该规则仅在明确 ==true 时触发 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:111` |
| FILT-04 | 设计行为 | ✅ 已覆盖 | 当 preference.showWithoutSubtitle==false 且 media.properties.subtitleLanguageIds 为空 且 media.extraFiles.subtitles 为空时, 资源被排除为 MediaWithoutSubtitle; 两个字幕来源任一非空即不排除 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:122-127` |
| FILT-05 | workaround | ❌ P0 | filterMediaList 收到的 preference 只来自 savedDefaultPreference(全局默认), 不含用户会话/按条目偏好——因此 showWithoutSubtitle 只能由全局设置控制, 用户按条目的偏好无法覆盖生肉过滤。原因是若依赖 merged 偏好会产生循环依赖 mediaList -> mediaPreferenceItem -> newPreferences -> mediaList _(注:重构目标: 决策核抽成纯函数后此约束应被显式建模或消除)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:339-348` |
| FILT-06 | 设计行为 | ✅ 已覆盖 | 当 context.subtitlePreferences 非 null 且 media.properties.subtitleKind 非 null 且该 kind 的偏好为 HIDE 时, 排除为 UnsupportedByPlatformPlayer; LOW_PRIORITY 不排除(仅影响排序); subtitlePreferences==null 或 subtitleKind==null 时跳过此规则 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:129-134` |
| FILT-07 | 设计行为 | ✅ 已覆盖 | 季度排除路径 A(media.properties.subjectName 非 null): 若 subjectName 与当前条目 allNames 任一 specialEquals 则绝不排除; 否则将 subjectName 及其 SEASON_TAILING 正则(第\s*(?<season>.+)\s*[部季] -> ${season})简化形式与 subjectSeriesInfo.seriesSubjectNamesWithoutSelf 逐个 specialEquals, 命中且名字在 sequelSubjectNames 内 -> FromSequelSeason, 否则 -> FromSeriesSeason。明确不允许 edit-distance 模糊匹配(天降之物 vs 天降之物f) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:76`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:136-168` |
| FILT-08 | 设计行为 | ✅ 已覆盖 | 季度排除路径 B(subjectName 为 null, 保守排除): 若任一非空 sequelSubjectName 被 originalTitle 普通 contains(ignoreCase=true) 包含, 则排除为 FromSeriesSeason; 此处刻意不用 specialContains, 因续集可能只比前传多一个特殊字符(#1912) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:169-182` |
| FILT-09 | 设计行为 | ❌ P1 | 标题须含条目名规则仅对 WEB 生效: 用 MediaListFilters.ContainsSubjectName 在 candidate(subjectName = properties.subjectName ?: originalTitle) 上匹配, 失败则排除为 SubjectNameMismatch; BitTorrent 与 LocalCache 在此规则恒 allow。OVA 特例: 若 episodeRange 包含 EpisodeSort("OVA"), 额外用 "properties.subjectName + \" OVA\"" 再匹配一次, base\|\|ova 任一通过即可(properties.subjectName 为 null 时 OVA 分支返回 false) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:184-215` |
| FILT-10 | 设计行为 | ✅ 已覆盖 | MediaListFilterContext 仅当 subjectInfo 非 Empty 且有非空白名称 且 episodeInfo 非 Empty 时构造; 任一未加载时整个 WEB 标题匹配规则被跳过(所有 kind 都通过), 即条目/剧集信息加载前不会因标题不匹配排除任何资源 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:56-68`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:184` |
| FILT-11 | 设计行为 | ❌ P1 | 排除规则的判定顺序固定为: LocalCache 豁免 -> 完结番BT单集 -> 无字幕 -> 平台字幕类型HIDE -> 季度/续集 -> WEB标题匹配; 同时满足多条排除条件时 exclusionReason 归因于顺序靠前者(如无字幕的 BT 单集在完结番下报 SingleEpisodeForCompleteSubject 而非 MediaWithoutSubtitle) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:81-218` |

## 过滤-元数据

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| FILT-12 | 设计行为 | ✅ 已覆盖 | Included 携带的 MatchMetadata: subjectMatchKind==EXACT 当且仅当任一 context 条目名与 media 条目名(properties.subjectName ?: originalTitle) specialEquals, 否则 FUZZY; episodeMatchKind 优先判 SORT(context episodeSort 在 episodeRange 内), 次判 EP(context ep 在 range 内), 否则 NONE(episodeRange==null 恒 NONE); similarity = 所有 context 条目名(并入一个空串)与 media 条目名 calculateMatchRate 的最大值(0..100) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:220-256`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MaybeExcludedMedia.kt:117-151` |
| META-01 | 设计行为 | ✅ 已覆盖 | MaybeExcludedMedia.Included.result 为原 Media 且 exclusionReason==null; Excluded.result 恒为 null 且携带 exclusionReason; 两者的 original 都返回原 Media(需 opt-in UnsafeOriginalMediaAccess)。isPerfectMatch() == (Included 且 subjectMatchKind==EXACT 且 episodeMatchKind>=EP); EpisodeMatchKind 枚举序 NONE<EP<SORT 有比较语义 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MaybeExcludedMedia.kt:50-70`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MaybeExcludedMedia.kt:136-150`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MaybeExcludedMedia.kt:162-168` |

## 排序

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| SORT-00 | 设计行为 | ✅ 已覆盖 | sortMediaList 使用 sortedWith 稳定排序: 所有比较 key 相等的元素保持输入相对顺序不变; 被排除的资源不丢弃而是排到列表尾部供 UI 展示原因 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:271-273` |
| SORT-01 | 设计行为 | ✅ 已覆盖 | 排序第 1 级: Included(key=0) 恒排在 Excluded(key=1) 之前 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:275-280` |
| SORT-02 | 设计行为 | ✅ 已覆盖 | 排序第 2 级: 若 context.subtitlePreferences 非 null 且 media.subtitleKind 非 null 且偏好 != NORMAL(即 LOW_PRIORITY 或 HIDE), key=1 排后, 否则 key=0; subtitlePreferences==null 时所有资源 key=0 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:282-290` |
| SORT-03 | 设计行为 | ✅ 已覆盖 | 排序第 3 级(降序): LocalCache 恒 =2 置顶 > kind 命中 settings.preferKind 的 =1 > 其他 =0; settings.preferKind==null 时所有非缓存资源同为 0(即缓存 > 用户偏好类型 > 不偏好, #1522) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:292-312` |
| SORT-04 | 设计行为 | ✅ 已覆盖 | 排序第 4 级(升序): costForDownload 由 MediaSourceLocation 决定: Local=0 < Lan=1 < 其他(Online)=2 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:313-315`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:335-340` |
| SORT-05 | 设计行为 | ✅ 已覆盖 | 排序第 5 级(升序): 有效 tier = context.mediaSourceTiers.get(mediaSourceId, properties.alliance), channel(alliance) 级 tier 命中优先于数据源级; context.mediaSourceTiers==null(未加载)时所有资源取 MediaSourceTier.MaximumValue, 该级不产生区分 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:316-321` |
| SORT-06 | 设计行为 | ✅ 已覆盖 | 排序第 6 级(降序): publishedTime 越新越靠前; 第 7 级(降序): 条目名相似度 similarity 越高越靠前, Excluded 的 similarity 记为 0。字幕语言、分辨率、文件大小完全不参与排序 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:322-331` |

## tier 查询

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| TIER-01 | 设计行为 | ✅ 已覆盖 | MediaSelectorSourceTiers.get(mediaSourceId, channel): channel 非 null 且非空串且 channelTiers[sourceId][channel] 存在时返回 channel tier; 否则返回 tiers[sourceId] ?: fallback(sourceId)(fallback 默认返回 MediaSourceTier.Fallback=2)。getBestTier(sourceId) = min(数据源 tier, 所有 channel tier 最小值), 无 channel tier 时即数据源 tier | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorContext.kt:142-169` |

## 偏好筛选 filterByPreference

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| PF-01 | 设计行为 | ✅ 已覆盖 | matches 语义(标量): 偏好值为 null、或等于资源属性值、或等于 ANY_FILTER("*") 时匹配; matches 语义(列表, 用于 subtitleLanguageId): 偏好值为 null、或 in 资源的 subtitleLanguageIds 列表、或等于 ANY_FILTER 时匹配 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:351-355` |
| PF-02 | 设计行为 | ❌ P2 | filterByPreference 要求 alliance、resolution、subtitleLanguageId、mediaSourceId 四项同时(AND)matches 才保留; 判定基于 original(Excluded 条目也按其 original 属性参与筛选, 不匹配的 Excluded 也会被移出 preferredCandidates) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:360-374` |
| PF-03 | 设计行为 | ✅ 已覆盖 | 本地缓存特权二: isLocalCache 的资源在 filterByPreference 中无条件保留(不论四项偏好是否匹配), 以保证默认选择能选到缓存 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt:361-363` |

## 偏好合并 OptionalPreference

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| OPT-01 | 设计行为 | ✅ 已覆盖 | OptionalPreference 三态: prefer(value)=偏好某值; preferNoValue=显式偏好无值(不过滤); noPreference=无偏好。orElse(default) 语义: preferValue -> 该值; preferNoValue -> null(不回落 default); noPreference -> default()。flatMapNoPreference 仅在 noPreference 时才求值 default | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/OptionalPreference.kt:27-55`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/OptionalPreference.kt:73-93` |

## 偏好项 MediaPreferenceItem

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| ITEM-01 | 设计行为 | ✅ 已覆盖 | 三层合并链: userSelected = 会话内 overridePreference.flatMapNoPreference { preferIfNotNull(数据库 savedUserPreference 对应字段) }(会话覆盖 > 数据库用户偏好); finalSelected = userSelected.orElse { savedDefaultPreference 对应字段 }。因此 removePreference(preferNoValue)后 finalSelected==null, 既屏蔽数据库用户偏好也屏蔽全局默认, 该维度不再过滤 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:876-891`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:893-898` |
| ITEM-02 | 设计行为 | ❌ P1 | prefer(value) 将会话 override 置为 prefer(value) 并广播 onChangePreference; removePreference() 将 override 置为 preferNoValue 并广播 onChangePreference; preferWithoutBroadcast 只写 override 不广播。一旦会话内有 override, 数据库 savedUserPreference 的后续更新不再影响该项 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:869-871`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:900-909` |
| ITEM-03 | 设计行为 | — | available 从 filteredCandidatesMedia(过滤后、偏好筛选前)提取: alliance = 去重后按字典序升序; resolution = 去重后按字典序升序 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:357-372`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:865-867` |
| ITEM-04 | 已知bug | ❌ P1 | subtitleLanguageId.available 的排序权重表写的是分辨率字样(8K/4320P/4K/1080P...), 而被排序的值是字幕语言 ID(CHS/CHT 等), 全部落入 else -> -1, 排序实际无效, 结果顺序等于 HashSet 迭代顺序(不稳定) _(注:仅影响 UI 展示的 available 列表顺序, 不影响 finalSelected 过滤链)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:373-391` |
| ITEM-05 | 已知bug | ❌ P1 | mediaSourceId.available 的 getFromMediaList 误取 it.properties.resolution 而非 it.mediaSourceId, 导致'可用数据源列表'实际返回的是去重排序后的分辨率字符串集合 _(注:getFromPreference 正确取 mediaSourceId, 故 userSelected/finalSelected 不受影响)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:392-399` |

## 偏好合并 newPreferences

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| MERGE-01 | 设计行为 | — | newPreferences(当前会话生效偏好) = savedDefaultPreference.copy(alliance/resolution/subtitleLanguageId/mediaSourceId 用四个 finalSelected 覆盖, 可为 null); 其余字段(alliancePatterns、fallbackResolutions、fallbackSubtitleLanguageIds、showWithoutSubtitle 等)恒来自全局默认偏好, 用户按条目偏好中的这些字段在过滤/自动选择中被忽略 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:404-417` |
| MERGE-02 | workaround | ✅ 已覆盖 | newPreferences、userSelected、finalSelected 三个 flow 明确标注 must not cache: removePreferencesUntilFirstCandidate 依赖'写入 override 后立刻 first() 能读到新值'的同步语义; 加缓存会引入延迟导致该方法逻辑失效 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:417`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:873-875`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:886-888` |

## 偏好数据模型

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| MP-01 | 设计行为 | ❌ P2 | MediaPreference.PlatformDefault 的默认值: fallbackResolutions=[2160P,1440P,1080P,720P], fallbackSubtitleLanguageIds=[CHS,CHT], showWithoutSubtitle=false, 其余 null; Empty 将两个 fallback 列表也置 null; ANY_FILTER 常量为 "*" | `app/shared/app-data/src/commonMain/kotlin/data/models/preference/MediaPreference.kt:29-106` |
| MP-02 | 已知bug | ❌ P2 | MediaPreference.merge() 是死代码(全仓库无调用方), 且含潜在 bug: 构造新对象时遗漏 showWithoutSubtitle(恒重置为 false)和 fallbackMediaSourceIds(恒丢弃); 若未来复用会静默丢字段 _(注:重构时可直接删除; 若保留须补字段并加测试)_ | `app/shared/app-data/src/commonMain/kotlin/data/models/preference/MediaPreference.kt:108-121` |

## select 手动选择

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| SEL-01 | 设计行为 | ✅ 已覆盖 | select(candidate) == selectImpl(updatePreference=true): 重复选择同一个 Media(data class equals 相等)且非 force 时立即返回 false, 不发出任何事件(包括 onBeforeSelect); 注意 MediaSelectorEvents.onBeforeSelect 的 KDoc 声称重复选择也会广播, 与实现矛盾, 以实现为准 _(注:KDoc 与实现不一致, 重构时二选一并固化)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:432-434`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:456-457`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorEvents.kt:25-30` |
| SEL-02 | 设计行为 | ❌ P1 | selectImpl 成功路径的副作用顺序严格为: (1) emit onBeforeSelect(media=candidate, previousMedia=旧值) -> (2) selected.value = candidate -> (3) 若 updatePreference: 写四项偏好 + emit onChangePreference (+ WEB 时 emit onPreferWebSource) -> (4) emit onSelect(同载荷), 返回 true | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:449-495` |
| SEL-03 | 设计行为 | ✅ 已覆盖 | select 写入会话偏好的精确字段集: alliance <- candidate.properties.alliance、resolution <- properties.resolution、mediaSourceId <- candidate.mediaSourceId 三项恒写; subtitleLanguageId 仅当 properties.subtitleLanguageIds.singleOrNull() 非 null(恰好一个语言)时写入, 多语言或零语言资源不改变字幕语言偏好 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:471-478` |
| SEL-04 | 设计行为 | ✅ 已覆盖 | 仅当 candidate.kind==WEB 且 updatePreference=true 时 emit onPreferWebSource, 载荷为 PreferWebSourceEvent(subjectId, mediaSourceId); 若 mediaSelectorContext.first().subjectInfo?.subjectId 为 null 则静默不发(选 BT/缓存不发此事件) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:480-482`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:532-539` |
| SEL-08 | 设计行为 | ❌ P2 | unselect() 仅把 selected 置为 null: 不发任何事件、不清除会话偏好、不触碰持久化 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:497-499` |

## 事件

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| SEL-05 | 设计行为 | ❌ P0 | onChangePreference 载荷 = savedUserPreference(未缓存 flow 的当前值).copy(四字段取自 newPreferences 当前值): 即 alliancePatterns/fallback 列表/showWithoutSubtitle 等保留数据库用户偏好原值, 而四个核心字段是合并结果——用户无会话选择的字段会携带全局默认值被广播(进而可能被持久化到按条目偏好); 且当 onChangePreference.subscriptionCount==0 时跳过计算与广播 _(注:对'统一持久化通道'重构关键: 默认值会渗入 per-subject 记录)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:518-530` |
| SEL-06 | 设计行为 | ❌ P2 | SelectEvent 载荷: media=被选中项, previousMedia=选择前的 selected 值(selectDefault 路径恒 null), subtitleLanguageId 字段在所有 emit 点恒为 null(死字段); broadcastChangePreference 的 overrideLanguageId 参数所有调用点均传 null(死参数) _(注:死代码清理候选)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorEvents.kt:55-59`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:460-466`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:518` |
| EVT-01 | 设计行为 | ❌ P1 | 四个事件流(onSelect/onBeforeSelect/onChangePreference/onPreferWebSource)均为 MutableSharedFlow(replay=0, extraBufferCapacity=1, DROP_OLDEST): emit 永不挂起, 无订阅者时事件直接丢失, 慢订阅者会丢最旧事件; 晚订阅者收不到历史事件 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorEvents.kt:71-84` |
| EVT-01 | 已知bug | ❌ P1 | select() 重复选择同一 media(force=false 且 previous==candidate)时直接返回 false、不发任何事件;而 onBeforeSelect 的 KDoc 声称'只要 select 被调用就会广播此事件,包括重复选择同一个 media'——文档与实现不符。另:selectDefault 先发 onBeforeSelect 再 CAS,CAS 失败时 onSelect 不会跟进,onBeforeSelect 可能无配对的 onSelect(这点与文档一致)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:456-457`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorEvents.kt:25-30`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:509-512` |
| EVT-02 | 已知bug | ❌ P1 | onPreferWebSource 的 subjectId 取自 mediaSelectorContext.first().subjectInfo,为 null(context 未加载完)时事件被静默丢弃——用户在 context 加载完成前手动选 WEB 源,则该次'偏好此 Web 源'不会被记录。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:532-539`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:480-482` |

## select 自动落点

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| SEL-07 | 设计行为 | ❌ P2 | selectDefault(candidate): selected 已非 null 时直接返回 null; 否则先 emit onBeforeSelect(previousMedia=null), 再 compareAndSet(null, candidate)——CAS 失败(竞态)返回 null 且不发 onSelect(但 onBeforeSelect 已发出); 成功则 emit onSelect 并返回 candidate; 全程不写任何偏好 _(注:onBeforeSelect 可能先于失败的 CAS 发出——事件与状态可不一致)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:501-516` |

## 自动查找 findUsingPreferenceFromCandidates

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| FIND-01 | 设计行为 | ✅ 已覆盖 | DFS 偏好选择已抽成纯函数 findMediaByPreference(候选, 偏好, 可用字幕组, context, settings), 规则逐字保留; DefaultMediaSelector.findUsingPreferenceFromCandidates 只负责等 context.allFieldsLoaded() 并取 alliance.available 再调用它; 策略函数则在 context 未加载时返回 Wait。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/MediaSelectionDecider.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt` |
| FIND-02 | 设计行为 | ✅ 已覆盖 | 四个候选序列: resolutions = [selectedResolution](有明确偏好时只有它, 不回落) 否则 fallbackResolutions; languageIds = [selectedSubtitleLanguageId] 否则 fallbackSubtitleLanguageIds; alliances = [selectedAlliance] 否则(alliancePatterns 为空 -> [ANY_FILTER]; 否则对每个 regex × 每个 available 字幕组, regex.find 命中即 yield, 按 pattern 顺序优先); mediaSources = [selectedMediaSource] 否则 mediaSourcePrecedence 顺序 + 末尾 null(任意) _(注:明确偏好值存在时该维度无 fallback: 例如 selectedResolution 无匹配则整个 DFS 返回 null)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:568-608` |
| FIND-03 | 设计行为 | ✅ 已覆盖 | DFS 嵌套顺序为 分辨率 > 字幕语言 > 字幕组 > 数据源: 外层逐个分辨率过滤(ANY_FILTER 不过滤), 空则换下一分辨率; 语言按 languageId in subtitleLanguageIds 过滤; 命中语言后先按字幕组精确相等过滤再按数据源过滤, 任一步为空 continue 下一候选值; mediaSource==null 匹配任意数据源(#174 规则) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:641-688` |
| FIND-04 | 设计行为 | ❌ P0 | 不为 4K 换语言: 某分辨率层若存在资源但没有任何想要的字幕语言, 则跳过该分辨率换下一个(宁选 1080P 简中不选 4K 生肉/其他语言); 分辨率 fallback 序列内 1080P >> 720P 优先级由列表顺序保证 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:642-652`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:618-623` |
| FIND-05 | 设计行为 | ❌ P0 | 字幕组未命中不降级语言: 某语言层的所有偏好字幕组都无匹配时, 不换语言, 而是在同语言集合上再按 mediaSources 顺序选一轮(放弃字幕组偏好保住语言); 仅当该语言完全无资源才换下一语言 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:654-682` |
| FIND-06 | 设计行为 | ❌ P1 | preferKind 前置分支: settings.preferKind 非 null 时先只在该 kind 的候选内跑 DFS; 若 preferKind==WEB 则更先在 similarity>80(严格大于)的 WEB 子集内跑一轮(#1521 防止快速选择选中高优先数据源的错误资源), 未果再在全部 WEB 内跑; 都未果才进入后续分支 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:693-705` |
| FIND-07 | 设计行为 | ✅ 已覆盖 | 完结番季度全集分支: shouldPreferSeasons(context.subjectFinished==true 且 settings.preferSeasons)时, 在 episodeRange?.hasSeason()==true 的子集内先跑一轮 DFS; 未果再对全量候选跑 DFS; 最终兜底 selectAny(候选全列表) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:563-564`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:707-714` |
| FIND-08 | 涌现 | ✅ 已覆盖 | selectAny 兜底(也是每次 DFS 命中集合的最终取元逻辑): 列表空返回 null; shouldPreferSeasons 时返回第一个 episodeRange?.hasSeason()==null 的资源(即 episodeRange 为 null / 范围未知的资源)?: 列表第一个——谓词写的是 ==null 而非 ==true, '优先季度全集'在兜底层实际优先的是无法解析剧集范围的资源; 该行为被测试 `do not prefer season if not matched` 锁定(空偏好时选第一个而非季度全集) _(注:疑似手误(==true 才符合注释意图)但已被测试固化; 若改动须列为故意行为变更)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:625-634`<br>`app/shared/app-data/src/commonTest/kotlin/domain/media/selector/legacy/DefaultMediaSelectorTest.kt:592-610` |
| FIND-09 | 设计行为 | ✅ 已覆盖 | languageIds 序列为空(subtitleLanguageId==null 且 fallbackSubtitleLanguageIds 为 null/空, 如 MediaPreference.Empty)时, DFS 内层循环永不执行, 所有 selectImpl 分支返回 null, 最终一定落到 selectAny 选第一个 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:568-574`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:648` |
| FIND-10 | 设计行为 | ❌ P1 | alliances 正则匹配用的候选池是 alliance.available.first()(来自全量 filteredCandidatesMedia), 而非传入的 candidates 子集; 正则匹配为 regex.find(部分匹配)且无缓存, 序列被多次消费会重复跑正则; 本函数纯查找, 不修改 selected 也不写偏好 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:553-554`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:587-597`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:541-544` |

## 选择入口

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| TRY-01 | 设计行为 | ✅ 已覆盖 | trySelectDefault: selected 已非 null 立即返回 null; preferredCandidates 快照中无任何 Included 返回 null; 否则用 newPreferences 快照对 Included 子集跑 findUsingPreferenceFromCandidates, 命中则 selectDefault(不写偏好), CAS 竞态失败仍返回 null | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:717-728` |
| TRY-02 | 设计行为 | ✅ 已覆盖 | trySelectFromMediaSources 的 bake 语义: 只保留 mediaSourceId ∈ candidateSources 且 mediaId ∉ blacklistMediaIds 且 candidateMediaFilter(为 null 视为恒 true)通过的 Included, 再按 candidateSources 中的下标稳定排序(同源资源保持原有排序序); candidateSources 为空列表时整个函数直接返回 null | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:737-745` |
| TRY-03 | 设计行为 | ✅ 已覆盖 | trySelectFromMediaSources 两阶段: 阶段一在 preferredCandidates 快照上以 mergedPreference.copy(alliance=ANY_FILTER) 查找(即使在偏好阶段, 字幕组偏好也被放宽为任意, 但 resolution/语言/数据源偏好仍生效); 仅当阶段一无果且 allowNonPreferred=true 时, 阶段二在 filteredCandidates 快照上以四项全 ANY_FILTER 查找(完全放弃偏好, 只按数据源顺序+fallback 列表) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:747-768` |
| TRY-04 | 设计行为 | ✅ 已覆盖 | trySelectFromMediaSources 落点: overrideUserSelection=true 走 selectImpl(updatePreference=false, force=false)——覆盖已有选择但不写偏好, 若查找结果恰等于当前 selected 则 selectImpl 返回 false 且函数返回 null; overrideUserSelection=false 走 selectDefault, 用户已有选择时不做任何事返回 null | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:771-781` |
| TRY-05 | 设计行为 | ✅ 已覆盖 | trySelectFromMediaSources 基于调用时 filteredCandidates 的一次 first() 快照选择(偏好列表从同一份快照用 filterByPreference 现算), 选择过程中 flow 更新不影响本次结果, 函数快速返回。现仅由 preferKind!=WEB 的编排路径及外部调用方使用; WEB 自动选择不再经过它。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt` |
| TRY-06 | 已移除 | — | awaitSelectFromMediaSources 已删除; '跟随最新数据重评' 由执行循环 runWebAutoSelect 承担。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectDriver.kt` |
| TRY-07 | 设计行为 | ✅ 已覆盖 | 本地缓存特权三: trySelectCached 使用 original 而非 result——即使缓存被过滤/偏好筛选标记为 Excluded 也可被选中; 顺序: selected 非 null -> null; 先在 preferredCandidates 快照找第一个 isLocalCache(尽量满足偏好), 否则在 filteredCandidates 快照找, 都没有返回 null; 命中走 selectDefault(不写偏好) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:834-843` |
| TRY-08 | 设计行为 | ❌ P0 | removePreferencesUntilFirstCandidate: 若 preferredCandidatesMedia 已非空直接返回; 否则按固定顺序 alliance -> resolution -> subtitleLanguageId -> mediaSourceId 逐项 removePreference(每步都会广播 onChangePreference), 每步后用未缓存的 preferredCandidatesNotCached.first() 复查, 一旦非空立即停止; 最后一步 mediaSourceId 移除后不再复查 _(注:首查用 cached 流、后续用 not-cached 流是刻意的(见 MERGE-02))_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:845-854` |

## 自动选择整体

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| EMG-01 | 涌现 | ❌ P0 | (仅 preferKind!=WEB 路径仍存在) 偏好字幕组消失导致自动选择整体落空: select 会把 alliance 写入偏好并持久化; 换集后若该字幕组不在新列表中, filterByPreference 使 preferredCandidates 变空(本地缓存除外), trySelectDefault 因无 Included 直接返回 null; BT 用户无人放宽 alliance, 需手动或 removePreferencesUntilFirstCandidate 解救。preferKind==WEB 路径已由策略在所有 WEB 源结束后放宽偏好(见 ORCH-06)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt` |

## 上下文

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| CTX-01 | 设计行为 | ✅ 已覆盖 | MediaSelectorContext.allFieldsLoaded() 要求 subjectFinished/mediaSourcePrecedence/subtitlePreferences/subjectSeriesInfo/subjectInfo/episodeInfo/mediaSourceTiers 全部非 null; Initial 为全 null(查询中) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorContext.kt:27-60` |
| CTX-02 | workaround | ❌ P1 | MediaSelectorContextFlowProducer.flow 整体 onStart emit Initial(否则剧集信息一直未到会无法选集, #385), 各上游 flow 各自 onStart emit null(便于 debug 定位谁没 emit); subjectSeriesInfo 失败时 retryWithBackoffDelay 且每次失败 emit SubjectSeriesInfo.Fallback(RepositoryUnknownException 记 warn, 其他记 error) | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorContext.kt:87-117` |

## 平台字幕偏好

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| SUB-01 | 设计行为 | ❌ P2 | forPlatform 平台表: macOS 将 CLOSED/EXTERNAL_DISCOVER/CLOSED_OR_EXTERNAL_DISCOVER 设为 HIDE(EMBEDDED/EXTERNAL_PROVIDED NORMAL); Windows/Linux/Android 仅 EXTERNAL_DISCOVER 为 HIDE; iOS 全部 NORMAL; AllNormal(测试用)全部 NORMAL; 无任何平台使用 LOW_PRIORITY | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorSubtitlePreferences.kt:40-105` |

## 基础设施

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| INFRA-01 | workaround | ❌ P0 | enableCaching=true(生产默认)时 cached() = shareIn(独立 CoroutineScope, WhileSubscribed(5000), replay=1)——作用于 settings/context/mediaList/filteredCandidates/preferredCandidates/available/defaultSelected; 引入少许延迟(测试须关闭), scope 生命周期无人管理(代码内 TODO 标记应由调用方控制); mediaList 必须 cache 否则偏好变更会重新触发网络加载 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:326-348` |
| INFRA-02 | 设计行为 | ❌ P2 | 构造契约: savedUserPreference 与 savedDefaultPreference 不能为空 flow, 否则 select/broadcastChangePreference 中的 first() 永久挂起; selected 是 MutableStateFlow(null), 暴露为 StateFlow, 不必属于 preferredCandidatesMedia | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:307-317`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:429`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:176-179` |

## 管线组装

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| PIPE-01 | 设计行为 | ✅ 已覆盖 | filteredCandidates = combine(mediaList, savedDefaultPreference, settings, context) 经 filterMediaList 后立即 sortMediaList(排序发生在偏好筛选之前); preferredCandidates = combine(filteredCandidates, newPreferences) 经 filterByPreference(不再重排序, 保持 filtered 的顺序); *Media 变体 = mapNotNull { it.result } 只留 Included | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:339-352`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:420-427` |

## 编排

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| ORCH-01 | 设计行为 | ✅ 已覆盖 | AutoSelectExtension 在每个 EpisodeSession 启动后,对 sessionFlow.flatMapLatest{fetchSelectFlow} 的每个非 null bundle 调用一次 MediaSelectorAutoSelectUseCase(session, mediaSelector);bundle 被替换时旧编排整体被取消 (collectLatest)。 _(注:编排生命周期 = fetchSelect bundle 生命周期)_ | `app/shared/app-data/src/commonMain/kotlin/domain/player/extension/AutoSelectExtension.kt:34-39` |
| ORCH-02 | 设计行为 | ✅ 已覆盖 | preferKind==WEB 时编排不再竞速: 调用一次 `MediaSelectorAutoSelect.autoSelectWeb` = 纯策略 `decideWebAutoSelect` + 执行循环 `runWebAutoSelect`, 记忆源/缓存/分阶段/兜底全部是策略内按固定顺序求值的规则, 返回即编排结束。preferKind!=WEB 时沿用 select{} 三 clause 竞速(记忆源、缓存、兜底)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelectUseCase.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectPolicy.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectDriver.kt` |
| ORCH-03 | 设计行为 | ✅ 已覆盖 | 记忆源优先且阻塞: 策略 PREFERRED_SOURCE 阶段等该源结束并只在它的偏好候选里选; 选不出返回 ReleasePreferredSourceGate, 执行循环此时才启动两段计时器——即两段截止时间都从记忆源放行后起算。记忆源不在会话中时立即放行。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectPolicy.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectDriver.kt` |
| ORCH-04 | 设计行为 | ✅ 已覆盖 | 设置在编排启动时 .first() 快照: fastSelectWebKind→config.fastSelect(关闭时不按时间推进阶段, 只在所有 WEB 源结束后按 FUZZY 规则选一次), fastSelectWebLowTierToleranceDuration→第一段, 第二段=max(15s, 第一段)。运行中修改设置不影响已启动的编排。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelectUseCase.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelect.kt` |
| ORCH-05 | 设计行为 | ✅ 已覆盖 | 本地缓存随时可胜出: config.selectCache 时策略每个快照第一步检查缓存(偏好候选中的优先, 否则任意), 包括记忆源仍在等待期间。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectPolicy.kt` |
| ORCH-06 | 故意变更 | ✅ 已覆盖 | 旧: 兜底 clause 在所有 WEB 源完成时以 trySelectDefault 的 null 结束编排并取消在途快速选择, 偏好字幕组/偏好源不在候选中时什么都不选(且结果取决于源完成得快不快)。新: 所有 WEB 源结束后按 FUZZY 规则放宽偏好选择, 仍选不出才按偏好候选做默认选择, 再选不出 Finish。MIG-DUAL-02 同此变更。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectPolicy.kt`<br>`app/shared/app-data/src/commonTest/kotlin/domain/media/selector/MediaSelectorOrchestrationCharacterizationTest.kt` |
| ORCH-07 | 设计行为 | ❌ P2 | 与自动选择并行地 launch autoEnableLastSelected(挂在外层 scope, 不随选择结束取消),仅当设置 autoEnableLastSelected==true 时执行;它只 enable 数据源,不参与选择。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelectUseCase.kt` |

## 偏好源路径

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| PREF-01 | 设计行为 | ✅ 已覆盖 | trySelectPreferredWebSource:preferredWebMediaSourceId 为 null 时立即返回 null;该 id 在编排启动时经 getPreferredWebMediaSource(subjectId).first() 快照读取,会话中途偏好变更不重新触发。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelect.kt:186-190`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelectUseCase.kt:81-83` |
| PREF-02 | 设计行为 | ✅ 已覆盖 | trySelectPreferredWebSource 要求会话中存在 mediaSourceId 匹配且 kind==WEB 的 fetch result,否则返回 null;存在时以 stopAfterPreferredSource 配置运行策略: 只等该单一源结束(不等待其他源), 选不出即 Finish。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelect.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectPolicy.kt` |
| PREF-03 | 设计行为 | ✅ 已覆盖 | 记忆源阶段只在 snapshot.preferred(用户偏好过滤后, 含 JSON 偏好的 mediaSourceId 过滤)中该源的候选里选, alliance 放开为 ANY, 不做全量放开;已有选择时 runWebAutoSelect 开头即返回 null 不覆盖。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectPolicy.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectDriver.kt` |
| PREF-04 | 已移除 | — | 旧的 '快照-判空-等待-重试' 补偿结构已删除: 快照由 sourceSnapshots 与偏好/设置/context 同步组装, (源已 Succeed) 与 (其结果在候选中) 必然同时成立; MediaFetcher 另保证 Succeed 时 results replayCache 完整。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/AutoSelectSnapshot.kt` |

## 快速选择

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| FAST-01 | 设计行为 | ✅ 已覆盖 | fastSelectWebSources 是策略的一个配置(无记忆源、不选缓存、defaultWhenAllCompleted=false); 会话无 WEB 源时立即返回 null。执行循环 combine(autoSelectSnapshots(sourceSnapshots), 阶段, selected), 每次变化调用一次纯函数 decideWebAutoSelect。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelect.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectDriver.kt` |
| FAST-02 | 设计行为 | ✅ 已覆盖 | INSTANT 阶段: 只允许来自 Succeed WEB 源、未拉黑、subjectMatchKind==EXACT 且有效 tier(channel 优先) <= instantSelectTierThreshold(0) 的资源, 单组。模糊匹配的 T0 不秒选。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectPolicy.kt` |
| FAST-03 | 设计行为 | ✅ 已覆盖 | EXACT_ONLY 阶段: 任意 tier, 仅 EXACT, 按有效 tier 升序分组逐组尝试; 组内先偏好候选(alliance 放开)再全部放开。逐组保证 tier 优先级不被源列表顺序或分辨率/语言偏好跨 tier 覆盖。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectPolicy.kt` |
| FAST-04 | 设计行为 | ✅ 已覆盖 | FUZZY 阶段: EXACT 各 tier 升序, 再 FUZZY 各 tier 升序。所有 WEB 源 final 时无论当前阶段都按 FUZZY 评估。时长可为 INFINITE(该阶段永不到达); 第二段 < 第一段时视为相等。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectPolicy.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectDriver.kt` |
| FAST-05 | 设计行为 | ✅ 已覆盖 | 快照一致性由构造保证: sourceSnapshots 对终态源直接读 results replayCache(MediaFetcher 保证 Succeed 时已完整), autoSelectSnapshots 在同一 emission 内同步算过滤/排序/偏好筛选。context 未加载而有候选时策略返回 Wait。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/AutoSelectSnapshot.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt` |
| FAST-06 | 设计行为 | ✅ 已覆盖 | 终止: Select→selectAutomatically CAS(基准=config.currentSelection)提交并返回; CAS 失败或 selected 与基准不同(被外部改掉)→返回 null; Finish→null; 否则挂起。覆盖模式(currentSelection 非 null)下当前选择在某组内时保留它(返回它, 不发事件)。自动提交不更新偏好、不发 onChangePreference/onPreferWebSource。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/WebAutoSelectDriver.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt` |

## 缓存路径

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| CACHE-01 | 设计行为 | ✅ 已覆盖 | selectCached 是流式重试:每次 cumulativeResults emission 都重试一次 trySelectCached;若 selected 已非 null 立即停止返回 null;选中即停止返回 media;失败累计达 maxAttempts(编排中为 Int.MAX_VALUE,即不限)才停止。collect cumulativeResults 本身会驱动所有数据源开始查询。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelect.kt:231-266`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetchSession.kt:43-45` |
| CACHE-02 | 设计行为 | ✅ 已覆盖 | trySelectCached 优先选 preferredCandidates 中首个 LocalCache,否则 filteredCandidates 中首个 LocalCache;使用 MaybeExcludedMedia.original 即缓存即使被过滤规则排除也可入选;无缓存返回 null。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:834-843` |

## 兜底

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| DEF-01 | 设计行为 | ✅ 已覆盖 | awaitCompletedAndSelectDefault(waitForKind=preferKind flow):waitForKind 当前值非 null 时只等该 kind 的 CompletedConditions[kind]==true 即可继续;waitForKind 为 null 或该 kind 条件为 null(该类源全禁用或不存在)时,退化为等待 allCompleted()。preferKind==WEB 路径中同一口径由 AutoSelectSnapshot.allSourcesFinalForPreferredWeb 表达(有启用 WEB 源看 WEB, 否则等全部源)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelect.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/AutoSelectSnapshot.kt` |
| DEF-02 | 设计行为 | ✅ 已覆盖 | CompletedConditions 按 kind 计算:该 kind 全部源 Disabled → null;全部源 Completed\|Disabled → true;否则 false。allCompleted() 把 null 当 true(全禁用视为完成),这是刻意的,否则 awaitCompletion 永远无法结束。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:463-482`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:509` |
| DEF-03 | 设计行为 | ✅ 已覆盖 | 等待完成后,若 mediaSelector.selected 已非 null(用户或其他 clause 已选),awaitCompletedAndSelectDefault 返回 null 不做任何选择;否则调用 trySelectDefault(不写偏好)并返回其结果。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelect.kt:76-80`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:717-728` |

## 自动启用(#355)

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| AELS-01 | 设计行为 | ❌ P1 | autoEnableLastSelected 读取 mediaSelector.mediaSourceId.finalSelected.first()(合并顺序:会话内覆盖 > MediaPreference.mediaSourceId(DataStore JSON) > 全局默认;明确不要求该值在当前可用选项中),找到会话中对应源的 fetch result 并调用 enable();找不到则无操作。 _(注:注意:此'上次选择'来源是 MediaPreference JSON 通道而非 Room preferred_web_media_source——统一持久化通道时须保留该语义)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelect.kt:268-275`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:289-291`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:889-891` |
| AELS-02 | 设计行为 | ✅ 已覆盖 | enable() 仅当 state==Disabled 且 restartCount CAS(0→1) 成功时把状态置为 Idle;若该源已被 restart 过(restartCount!=0)则无任何效果;enable 后仍需 results 有 collector 才实际发起查询(惰性)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:373-385`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceFetchResult.kt:68-74` |

## fetch 状态机

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| FETCH-01 | 设计行为 | ✅ 已覆盖 | 每源状态机:初始 disabled 实例为 Disabled 否则 Idle;开始查询置 Working;正常完结时内层流发 FetchEvent.Completed(restartCount) 标记, 由 shareIn 收集侧在收到全部结果之后置 Succeed(restartCount)(保证观察到 Succeed 时 results replayCache 已是完整列表; 标记的 restartCount 与当前不符时忽略, 防止 restart 后旧查询的标记误标新查询);上游 BlockedException(Captcha)→CaptchaRequired,BlockedException(RateLimited)→RateLimited(retryAt=now+retryAfter,缺省 30s),其余上游异常→Failed;下游 collector 异常/取消→Abandoned(状态已是 Failed 时不覆盖)。已知窗口: 标记送达前 shareIn 收集协程被取消(无订阅者)时 state 停留在 Working, 下次订阅 results 会重新查询并恢复。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceFetchState.kt:19-68` |
| FETCH-02 | 设计行为 | ✅ 已覆盖 | Disabled 源在 restartCount==0 时 collect results 得到 flowOf(emptyList()),不发起任何网络查询;必须 restart()/enable() 后才可能查询。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:188-189` |
| FETCH-03 | 设计行为 | ✅ 已覆盖 | Completed.id 记忆:当 state 为 Completed 且其 id == 当前 restartCount 时,重新 collect results 返回 emptyFlow 复用 replayCache,不重新查询——对 Succeed/Failed/CaptchaRequired/RateLimited/Abandoned 一视同仁,即失败结果也被记忆,不因重新订阅而自动重试。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:191-198` |
| FETCH-04 | 设计行为 | ❌ P1 | RateLimited 自动重试预算为每源 1 次:进入 RateLimited 时若预算>0 则扣减并调度延时任务,到 retryAt 时刻若 state 仍是同一个 RateLimited 实例则自动 restart() 一次;重试后再被限流不再自动重试;预算只在该源成功(置 Succeed 前)重置回 1。 _(注:调度用非结构化 CoroutineScope(flowContext).launch,不随会话取消;restart 本身无副作用所以无害)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:216-220`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:320-345`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:241-247` |
| FETCH-05 | 设计行为 | ✅ 已覆盖 | restart() 在 synchronized 内 CAS 循环:state 为 Completed\|Disabled 时 restartCount+1 并 CAS 置 Idle(CAS 失败即有人在跑,接受竞态直接退出);Idle\|Working 时 no-op(进行中的请求继续,不排队第二次重启);重启后查询仍是惰性的。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:347-371`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceFetchResult.kt:56-66` |
| FETCH-06 | workaround | ❌ P1 | results 流 onStart 先 emit(emptyList()):确保 replayCache 至少有一个空列表,防止'首个列表 emit 前被取消'导致下游 combine 永久拿不到值;代价是被取消时 Working 源被记为 Abandoned(Completed 之一),重新订阅时按 FETCH-03 复用空 replayCache 而不重试,需手动 restart。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:235-239`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:248-258` |
| FETCH-07 | 已知bug | ❌ P2 | results 为 shareIn(WhileSubscribed(), replay=1):最后一个订阅者离开后上游立即取消(0ms),replayCache 永久保留;而 MediaFetchSession KDoc 声称'停止 collect 几秒后查询将被中断'——文档与实现(立即中断)不符。 _(注:行为断言应以实现为准:无订阅立即取消)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:260-262`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetchSession.kt:43-44` |
| FETCH-08 | 设计行为 | ✅ 已覆盖 | cumulativeResults = 所有源 results 的 combine + flatten + 全局 distinctBy(mediaId);它不是 SharedFlow,每个 collector 独立计算且首个 emission 即当前瞬时结果;源列表为空时恒为 emptyList 流;该 flow 设计上永不正常完结(完结会打 error 日志)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:428-461`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetchSession.kt:53-56` |

## fetch 会话

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| SESS-01 | 设计行为 | ❌ P1 | 会话 request 只采用 requestLazy 的第一个元素(take(1)+shareIn(Lazily, replay=1));setFetchRequest 传入与当前 override 相同的值时 no-op,不同值时设置 override 并 restartAll()(全部源 restart,已完成的重查,查询仍惰性)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:393-406`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:484-490`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetchSession.kt:95-99` |
| SESS-02 | 设计行为 | ❌ P2 | iOS 上 BitTorrent kind 的源被过滤,不进入 MediaFetchSession(enableBTFetcher = currentPlatform != Ios);其他平台全量进入。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt:408-411`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceManager.kt:302-307` |
| SESS-03 | 设计行为 | ✅ 已覆盖 | 会话冻结于创建时的 MediaFetcher 快照(createFetchFetchSession 用 mediaFetcher.first());数据源列表/配置变更不会重建或影响已创建的会话,需要调用方显式建新会话。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceManager.kt:201-212` |
| SESS-04 | 设计行为 | ✅ 已覆盖 | awaitCompletion 通过在内部 scope 里 Eagerly shareIn cumulativeResults 驱动全部源查询,并等待 hasCompleted 谓词满足后取消内部 scope;单源 awaitCompletion 同理但 Disabled 源直接返回(不启动查询、结果视为空)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetchSession.kt:110-132`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceFetchResult.kt:87-110` |

## 偏好保存

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| SAVE-01 | 设计行为 | ✅ 已覆盖 | 只有手动 select(updatePreference=true)才把 media 的 alliance/resolution/mediaSourceId(以及仅当恰有单一字幕语言时的 subtitleLanguageId)写入会话内覆盖并广播 onChangePreference;若该 media kind==WEB 还会广播 onPreferWebSource;所有自动选择路径(selectDefault、overrideUserSelection=true 的 selectImpl(updatePreference=false))都不广播、不写偏好。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:432-495`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:501-516` |
| SAVE-02 | 涌现 | ❌ P0 | onChangePreference 在 subscriptionCount==0 时直接跳过计算与广播——若保存挂载(订阅)晚于用户选择完成,该次偏好变更被静默丢弃;事件流为 replay=0、extraBufferCapacity=1、DROP_OLDEST,慢消费者也可能丢事件。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:518-519`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorEvents.kt:71-84` |
| SAVE-03 | 设计行为 | ❌ P0 | savePreferenceOnSelect = onChangePreference.debounce(1000) 后调用 save:1 秒窗口内连续多次偏好变更只保存最后一次;若挂载 scope 在 debounce 期间被取消(切集/bundle 替换/关缓存对话框),该次变更不落盘。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorEventHandlers.kt:23-29` |
| SAVE-04 | 设计行为 | ❌ P0 | 播放页挂载:SaveMediaPreferenceExtension 对每个非 null bundle 调 MediaSelectorEventSavePreferenceUseCase,把 debounce 后的 MediaPreference 经 EpisodePreferencesRepository.setMediaPreference(context.subjectId, pref) 写入 DataStore(key=subjectId 字符串,值为整份 MediaPreference JSON)。 | `app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SaveMediaPreferenceExtension.kt:30-36`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorEventSavePreferenceUseCase.kt:24-30`<br>`app/shared/app-data/src/commonMain/kotlin/data/repository/media/EpisodePreferencesRepository.kt:69-75` |
| SAVE-05 | workaround | ❌ P1 | 缓存页挂载:SubjectCacheScene 监听首个 stage 为 Working/Done 的剧集的 cacheRequester,进入 SelectMedia 阶段时 launch savePreferenceOnSelect;再次进入 SelectMedia 时先 cancel 旧 job;但进入 Done 阶段刻意不 cancel——因为 SelectMedia 后可能立即到 Done,debounce 中的保存还没来得及执行,取消会丢保存(代码注释明示)。 | `app/shared/ui-cache/src/commonMain/kotlin/ui/cache/subject/SubjectCacheScene.kt:239-281` |
| SAVE-06 | 设计行为 | ❌ P0 | mediaPreferenceFlow(subjectId) 读回:DataStore 无记录、空白串或 JSON 反序列化失败时,一律回退为全局默认 defaultMediaPreference(不抛错、不清理坏数据)。 | `app/shared/app-data/src/commonMain/kotlin/data/repository/media/EpisodePreferencesRepository.kt:53-67` |

## Room Web 源偏好

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| ROOM-01 | 设计行为 | ✅ 已覆盖 | ObserveWebMediaSourcePreferenceExtension 处理 PreferWebSourceEvent:仅当 event.subjectId==context.subjectId;先读现值,与 event.mediaSourceId 不同才写入(避免重复 upsert);写入经 @Upsert 存 Room 表 preferred_web_media_source(subjectId 主键+唯一索引)。 | `app/shared/app-data/src/commonMain/kotlin/domain/player/extension/ObserveWebMediaSourcePreferenceExtension.kt:54-63`<br>`app/shared/app-data/src/commonMain/kotlin/data/persistent/database/dao/PreferredWebMediaSourceDao.kt:22-31` |
| ROOM-02 | 设计行为 | ✅ 已覆盖 | 失败自动删除:当该 subject 存在 Web 源偏好(flow filterNotNull 门控)、且任一 WEB 源状态变化时检查——若偏好源当前 state 为 Failed 或 Abandoned(不含 CaptchaRequired/RateLimited),删除该 subject 的偏好记录。 _(注:Abandoned 可仅由 results 中途失去订阅产生(FETCH-06),存在'非源本身故障却删偏好'的理论路径,重构时应验证)_ | `app/shared/app-data/src/commonMain/kotlin/domain/player/extension/ObserveWebMediaSourcePreferenceExtension.kt:65-86`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceFetchState.kt:73` |
| ROOM-03 | 设计行为 | ❌ P1 | preferred_web_media_source 表对 SubjectCollectionEntity 有 FK(CASCADE):删除该条目收藏缓存记录会级联删除其 Web 源偏好;反之,若 subjectId 不在收藏表中,upsert 会因外键约束失败。 _(注:统一持久化通道(重构方向 2)时须决定是否保留 CASCADE 语义与 FK 失败行为)_ | `app/shared/app-data/src/commonMain/kotlin/data/persistent/database/dao/PreferredWebMediaSourceDao.kt:33-50` |
| ROOM-04 | 设计行为 | ✅ 已覆盖 | SetPreferredWebMediaSourceUseCase(subjectId, null) 语义为删除偏好(dao delete),非 null 为 upsert;GetPreferredWebMediaSourceUseCase 返回 Flow<String?>,无记录时 emit null。 | `app/shared/app-data/src/commonMain/kotlin/domain/mediasource/PreferredWebMediaSourceUseCase.kt:16-45`<br>`app/shared/app-data/src/commonMain/kotlin/data/repository/media/EpisodePreferencesRepository.kt:77-88` |

## 播放失败回退

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| ERR-01 | 设计行为 | ❌ P1 | 触发条件:videoLoadingState is Failed \|\| playbackState==ERROR,经 distinctUntilChanged 去抖;isError 变回 false 时 collectLatest 取消在途换源处理(含 1s delay 中的);总开关为 VideoScaffoldConfig.autoSwitchMediaOnPlayerError(默认 true),关闭时完全不监听。 | `app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SwitchMediaOnPlayerErrorExtension.kt:85-131`<br>`app/shared/app-data/src/commonMain/kotlin/data/models/preference/VideoScaffoldConfig.kt:83` |
| ERR-02 | 涌现 | ❌ P1 | 设置流 getVideoScaffoldConfigUseCase().map{autoSwitchMediaOnPlayerError} 未加 distinctUntilChanged 就 collectLatest:VideoScaffoldConfig 任意无关字段变更都会重启错误监听内层 scope,取消正在进行的换源处理(如 1s delay 中)。 | `app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SwitchMediaOnPlayerErrorExtension.kt:87-109` |
| ERR-03 | 设计行为 | ❌ P1 | 黑名单为 media(mediaId)级、会话内存态、不持久化:PlayerLoadErrorHandler 每个 EpisodeSession 一个实例,切换 EpisodeSession 后黑名单清空;handler 存活期间跨设置开关切换保留。 | `app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SwitchMediaOnPlayerErrorExtension.kt:58-66`<br>`app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SwitchMediaOnPlayerErrorExtension.kt:141-145` |
| ERR-04 | 设计行为 | — | 每个 onSelect 事件的 previousMedia 都被加入黑名单——包括用户手动换源(拉黑被换掉的 previousMedia,防自动选择切回)和自动覆盖换源;selectDefault 产生的 onSelect previousMedia 为 null 不拉黑。 | `app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SwitchMediaOnPlayerErrorExtension.kt:147-157`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelector.kt:486-492` |
| ERR-05 | 设计行为 | ✅ 已覆盖 | handleError 顺序:先把当前 selected.mediaId 拉黑 → delay 1s(让用户看到错误)→ 并行读 preferKind 与 sourceTiers 快照 → preferKind != WEB 时记录日志并跳过(BT 偏好用户无自动换源)→ 否则 fastSelectWebSources(overrideUserSelection=true, blacklist=累计黑名单, lowTierToleranceDuration=1s, fuzzyFallbackDuration=3s)。所有 WEB 源都已结束时(播放出错的常见情形)不等容忍窗, 1s 后直接按 FUZZY 阶段规则换源。 | `app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SwitchMediaOnPlayerErrorExtension.kt` |

## tier

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| TIER-01 | 设计行为 | ✅ 已覆盖 | MediaSourceManager.mediaSourceTiersFlow 聚合:遍历持久化实例仓库(含禁用实例;不含 additionalSources/本地源),getArgumentOrNull 解码 serializedArguments 成功的实例贡献 mediaSourceId→argument.tier;channelTiers 仅收非空 map;不在表中的源查询时走 fallback=MediaSourceTier.Fallback(2)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceManager.kt:368-382`<br>`app/shared/app-data/src/commonMain/kotlin/domain/mediasource/codec/MediaSourceCodecManager.kt:182-183`<br>`datasource/api/src/commonMain/kotlin/source/MediaSource.kt:155` |
| TIER-02 | 设计行为 | ✅ 已覆盖 | MediaSelectorSourceTiers 查询语义:get(id)=tiers[id]?:fallback;get(id,channel):channel 非空且 channelTiers[id][channel] 存在时用之,否则退回 get(id);getBestTier(id)=min(源 tier, 该源全部 channel tier 的最小值)——channelTiers 有记录时即使 tiers 无该源也参与取最小。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorContext.kt:126-174` |
| TIER-03 | 设计行为 | ✅ 已覆盖 | MediaSourceTier.Fallback=2 高于 InstantSelectTierThreshold(0):无 tier 配置的源不会被快速选择秒选, 只在第一段超时后按 tier 参与选择。KDoc 已更正。 | `datasource/api/src/commonMain/kotlin/source/MediaSource.kt` |
| TIER-04 | 设计行为 | ❌ P1 | UI 源列表排序(MediaSourceResultsFilterer.filteredSourceResults)使用 sourceInfo.tier(MediaSourceInfo 内静态 tier,可为 null,null 排最前)而非 MediaSelectorSourceTiers 聚合值,不感知 channel 级 tier;禁用源按 UInt.MAX 排最后,showDisabled=false 时过滤掉禁用源,同 tier 按 mediaSourceId 稳定排序。 _(注:两套 tier 来源不一致是重构时的统一点,但当前行为需各自保持)_ | `app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceResultsFilterer.kt:58-90` |

## 杂项/死代码

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| MISC-01 | 已知bug | ❌ P2 | GetPreferredMediaSourceSortingUseCase 在 Koin 注册(UseCaseModules.kt:83)但全仓库无任何调用点——死代码;且其实现只是返回 allInstances 的 instanceId 列表,与命名暗示的'偏好排序'无关。清理属于'故意的行为变更'清单(实际无行为影响)。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/GetPreferredMediaSourceSortingUseCase.kt:21-39`<br>`app/shared/app-data/src/commonMain/kotlin/domain/usecase/UseCaseModules.kt:83` |

## 杂项

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| MISC-02 | 设计行为 | ❌ P2 | 编排内快速选择的 sourceTiers 经 getMediaSelectorSourceTiers().first() 启动时快照;播放失败换源路径同样 first() 快照——会话运行中 tier 配置变更不影响已启动的选择流程。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelectUseCase.kt:105-111`<br>`app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SwitchMediaOnPlayerErrorExtension.kt:174-177` |
| MISC-03 | 设计行为 | ✅ 已覆盖 | preferKind==WEB: 执行循环 collect `sourceSnapshots()` 即 collect 每个源的 results, 编排在跑查询就在跑(结构保证)。preferKind!=WEB: 仍由 selectCached(collect cumulativeResults)与 awaitCompletion 内的 Eagerly shareIn 驱动。 | `app/shared/app-data/src/commonMain/kotlin/domain/media/selector/engine/AutoSelectSnapshot.kt`<br>`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorAutoSelect.kt` |

## 补录(2026-08-02,BT/WEB 拆分专项分析发现)

| ID | 类型 | 测试 | 行为 | 代码 |
|---|---|---|---|---|
| RES-01 | 已知bug | ❌ P1 | BT/RSS media 的 `properties.resolution` 存 `Resolution.toString()` 即 displayName("4K"/"2K"),而偏好默认 fallbackResolutions 用 id("2160P"/"1440P"),DFS/filterByPreference 是裸字符串相等比较,两者永不匹配 → 默认偏好下 DFS 分辨率序列的 4K/2K 两层对 BT 候选恒为空集;仅当用户手动选过 4K 资源(SEL-03 把 "4K" 写入偏好)才可达。且标题写"4K"字样的资源 `Resolution.tryParse` 解析不出(只认 id/otherNames),兜底记为 "1080P"。修复列 F12,待批。 | `datasource/api/.../topic/Resolution.kt`(toString=displayName)<br>`datasource/api/src/commonMain/kotlin/source/TopicMediaSource.kt`<br>`app/shared/app-data/.../domain/media/selector/MediaSelector.kt:610-623` |
| EMG-02 | 涌现 | ❌ P1 | preferKind=WEB 时"WEB 全灭→兜底选 BT"是时序彩票而非可靠行为:clause④ waitForKind 只等 WEB kind 完成(Failed 也算 Completed),"全部 WEB 快速失败"恰是完成条件最快成立的场景,此刻 BT 通常仍在查询,preferredCandidates 无 Included → trySelectDefault 返回 null → clause④ 以 null 终结整个编排(ORCH-06),此后本会话永无自动选择;仅当 BT 结果恰好先于 WEB 完成流入时兜底才发生。叠加 alliance 污染(选过 WEB 后 channel 名硬过滤掉全部 BT)则兜底恒空。Phase C 的 R5 以此为对照基线(C16)。 | `app/shared/app-data/.../selector/MediaSelectorAutoSelectUseCase.kt:124-132`<br>`app/shared/app-data/.../selector/MediaSelector.kt:717-720`<br>`app/shared/app-data/.../fetch/MediaFetcher.kt:463-482` |
| PROP-01 | 设计行为 | ❌ P2 | Selector WEB media 属性近常量:resolution 恒= `config.defaultResolution.id`(网页不解析,默认 "1080P");subtitleLanguageIds 先从 channel 名/剧集名猜测、猜不出兜底 `config.defaultSubtitleLanguage.id`(默认 CHS),结构上永不为空(→ 生肉过滤 FILT-04 对 Selector/Jellyfin/Emby 永不触发);publishedTime 恒 0(SORT-06 对 WEB 内部恒平局;preferKind=null 时有真实时间戳的 BT 恒排在 WEB 前是其涌现副作用);subtitleKind 恒 EMBEDDED;size 恒 Unspecified。 | `app/shared/app-data/.../domain/mediasource/web/SelectorMediaSourceEngine.kt:236-248` |
