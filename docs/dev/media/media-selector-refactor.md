# Media Selector 重构方案

> 2026-09-06：Web 自动选择已按新的两段截止时间策略实现，见[当前规则](../../contributing/code/media/media-selector.md#web-自动选择)。
> 下文的 Web 四分支竞速、超时放开模糊匹配、ORCH-06 与 MIG-DUAL-02 为历史基线，已由明确阶段决策替代。
> WEB 记忆源优先且阻塞的规则保留；BT 的完成条件、偏好与持久化规则保留。
> 后续收拢为 `MediaAutoSelector` 单一执行循环，WEB 与 BT 均不再使用分支协程竞速。
> `MediaSelectorAutoSelect` 及 `.autoSelect` 已删除；下文保留旧公开方法的约束及相关行号为历史记录。
> 下载缓存调用方直接等待完成并调用 `trySelectDefault`，播放和换源统一调用 `MediaAutoSelector.select`。


> 状态:**方案定稿**;2026-08-02 增补 Phase C(BT/WEB 双模式拆分,方向已批,D8–D11 已定,见 §5/§7);其余决策点待批,批准前不动任何生产代码。
> 日期:2026-07-31 定稿,2026-08-02 修订(Phase C + §3.1 复合主键),基于 main 分支(行号均已核实)。
> 姊妹文档:[行为清单(重构基线)](media-selector-behavior-catalog.md) —— 128 条可测行为断言,本方案所有 "ID"(FILT-01、ORCH-06 等)均指向该清单。
>
> 本方案由多轮多 agent 分析产出:6 个子系统盘点 + 测试缺口交叉分析 + 两份设计 + 两轮对抗验证(破坏性猎手 + 测试计划批评家)。验证发现的 4 个 blocker 与 11 个 major **已全部折入正文**(正文即修订后版本);完整验证记录见 §8。

---

## 0. 目标与约束

**要解决的问题**(按优先级):

1. **决策逻辑与响应式管道纠缠**:`DefaultMediaSelector`(913 行)把 flow 编排、三层偏好合并、DFS 选择算法、事件广播搅在一起;`select {}` 四 clause 竞速 + `CompletableDeferred` 门控让"优先级"依赖并发时序。代码里已有三处为对抗自身架构而存在的 workaround(FILT-05 循环依赖限制、PREF-04 快照竞态补偿、avoid-suspension 注释)。
2. **"上次播放"三条互不知晓的持久化通道**(per-subject MediaPreference JSON / Room `preferred_web_media_source` / autoEnableLastSelected 合并链),天然失步,并阻碍 channel 级记忆功能。
3. 历史堆积:死代码、KDoc 与实现不符、`MediaPreferenceItem.available` 提取 bug 等(默认钉住,修复单列待批)。

**硬约束**:

- **不破坏任何一个现有功能细节**。全部 128 条行为要么有测试锁定,要么在动代码前补齐(Phase 0)。
- 已知 bug / 涌现行为默认**钉住现状**(characterization test + `// PINNED: <ID>` 注释);任何修复都进"故意行为变更清单"(§6)逐条签字,批准前不实施。
- `MediaSelector` 接口、事件流语义、`MediaSelectorAutoSelect` 公开方法签名、构造入口全部保持不变(消费方清单见行为盘点;desktopTest mock 须继续编译)。

**执行顺序**:Phase 0(测试补齐,§1)→ 方向 A(决策核纯函数化,§2)→ 方向 B(持久化统一,§3)→ **Phase C(BT/WEB 双模式拆分,§5)**。A 与 B 的代码接触面几乎不相交(A 不碰持久化挂载方,B 不碰决策核),但串行执行以保证每一步都有完整测试网;Phase C 必须最后执行(排期硬约束见 §5)。

---

## 1. Phase 0:测试补齐(先于一切重构)

> **状态(2026-08-02):P0 全部 18 项 + 大部分 P1 已落地并经对抗审查修复。** app-data desktopTest 共 967 个测试全绿,其中 Phase 0 新增 17 个测试类约 90 个测试。对抗审查(2 blocker + 8 major)全部修复;7 个关键判别点经**变异验证**(改坏生产代码→测试变红→还原→变绿):SAVE-02 早返回、SEL-02 selected 写入时机(精确挪位版)、Room 偏好通道切断、ERR-05 两个时长常数、FIND-10 匹配池来源、生产接线点 savedUserPreference。测试基建 infra#1–#10 全部就绪(exclusionReason DSL、collectEvents、cachingScope 注入、DataStore/Room 夹具、startInBackground+fetchCount、room-testing 迁移基建)。**尚未做:黄金样本 harness(§1.2)**——决策核纯函数化(方向 A Step 1)动工前必须先落地。生产代码改动仅两处 sanctioned:`MediaSelector.kt` cachingScope 参数(默认 null 行为不变)、`build.gradle.kts` desktopTest room-testing 依赖。

现状:128 条行为中约 75 条已覆盖(过滤/排序/tier/trySelect* 分支/秒选时序/fetch 状态机主干/编排主路径——`MediaSelectorAutoSelectUseCaseTest` 有 9 个时序场景,覆盖意外地好),**53 条有缺口:P0×17、P1×24、P2×16(含验证后调级)**。三大盲区恰好都是本次重构的落点:

1. **偏好持久化保存链完全无测试**——onChangePreference 载荷合成(默认值渗入)、debounce 1s、SaveMediaPreferenceExtension、Repository round-trip、Room dao。方向 B 的全部落点,现在等于盲飞。
2. **播放失败换源决策**(`fastSelectWebSources(overrideUserSelection=true)` 唯一生产调用方)仅黑名单一条路径被测。
3. **决策核若干关键规则无测试**:`removePreferencesUntilFirstCandidate`、FIND-04"不为 4K 换语言"、FIND-05"字幕组未命中不降级语言"、FILT-05 workaround、EMG-01 BT 死局、enableCaching=true 生产路径零覆盖。

### 1.1 P0 清单(重构合入门槛)

> 完整的逐条 suggestedTest(构造输入、断言口径、infra 依赖)见行为清单对应条目;此处为汇总。标 ★ 的为对抗验证后**新增或升级**的 P0。

| # | ID | 一句话 | 备注 |
|---|---|---|---|
| 1 | SAVE-03 | debounce 1s 保存;窗口内取消丢写(钉住) | 新建 MediaSelectorEventHandlersTest,虚拟时间 |
| 2 | SAVE-04 | SaveMediaPreferenceExtension 挂载链 | AbstractPlayerExtensionTest |
| 3 | SAVE-02 | 无订阅者静默丢事件(钉住) | turbine |
| 4 | SEL-05 | onChangePreference 载荷:默认值渗入 per-subject 记录 | 方向 B 最关键行为 |
| 5 | TRY-08 | removePreferencesUntilFirstCandidate 逐维移除+复查 | 依赖 must-not-cache 语义,双重必碰 |
| 6 | FIND-04 | 不为 4K 换语言 | DFS 跨维度权衡 |
| 7 | FIND-05 | 字幕组未命中不降级语言 | 同上 |
| 8 | FILT-05 | 用户按条目偏好被过滤阶段忽略(钉住 workaround) | 断言需 exclusionReason DSL(infra#1) |
| 9 | EMG-01 | BT 偏好字幕组消失 → 自动选择彻底不发生(钉住) | |
| 10 | ORCH-06 | clause④ 以 null 结束编排、取消容忍窗内的快速选择 | 竞速终止语义 |
| 11 | MISC-03 | clause③④ 是查询的实际驱动方 | ★写法修正:`configureFetchSession` 会无条件 `startInBackground()` 自驱查询,按原建议写出来**恒真(假安全网)**。必须加 `startInBackground=false` 参数(infra#9),断言口径改用 TestMediaSource 的 fetch 调用计数 |
| 12 | INFRA-01 | enableCaching=true 生产路径双跑 | 前置:cached() scope 可注入(infra#4) |
| 13 | SAVE-06 | Repository round-trip + 坏 JSON 回退(真实 DataStore) | infra#5 |
| 14 | ERR-05 | PlayerLoadErrorHandler 换源决策(黑名单/preferKind 跳过/容忍 1s) | 直接单测 handler |
| 15★ | ROOM-03 | Room dao 真实读写 + FK CASCADE 语义 | 原 P1,升级:与 SAVE-06 同为方向 B 存储基线,FK 级联是破坏性语义,必须有基线 |
| 16★ | ITEM-02 | 会话 override 屏蔽数据库后续更新 | 原 P1,升级:写回反馈环(select→落盘→flow 重发)的唯一屏障,黄金样本完全不覆盖此类时序合并 |
| 17★ | (新增) | **跨会话真实存储闭环**:会话 A 手动 select → debounce 落进真实 `EpisodePreferencesRepositoryImpl` → 重建 session → 会话 B 读回并驱动自动选择(偏好过滤 + autoEnable + clause①) | 现状:生产接线点 `CreateMediaFetchSelectBundleFlowUseCase.kt:136` / `MediaSelectorFactory.kt:80` 从未与真实仓库在测试中相连(全部用裸 MutableStateFlow 顶替);这是"上次播放"最用户可见的行为,方向 B 恰要重写这两处接线 |
| 18★ | (新增) | **Room 迁移测试基建 + 迁移测试**:老用户三通道同时有值(且互相矛盾)→ 迁移后统一记录取谁 | 仓库现有 21 个 AutoMigration、全仓**零**迁移测试;方向 B 的 S1/S2/S6 无此基建不能落地(infra#10) |

另:SEL-02(selectImpl 副作用顺序:onBeforeSelect → 写 selected → 写偏好+onChangePreference(+onPreferWebSource) → onSelect)保持 P1 但**必须在方向 A Step 2 之前落地**——事件顺序是 ERR-04 黑名单与 ROOM-01 写入依赖的外部契约。

P1(24 条)/P2(16 条)按行为清单执行,不阻塞重构启动,但 P1 须在对应代码被触碰前补齐。验证修正:AELS-01 变体 (a) 实际已被 `MediaSelectorAutoSelectUseCaseTest.kt:285-314` 覆盖,只剩变体 (b)(默认值渗透触发 enable);FIND-10 的测试**不能**走 `trySelectFromMediaSources`(它恒传 `alliance=ANY_FILTER`,alliancePatterns 路径根本不执行),必须走 `trySelectDefault` 路径:无会话 alliance 偏好 + alliancePatterns,且 pattern 命中的字幕组只存在于被其他维度从 preferredCandidates 滤掉的 media 中;FIND-06 的 similarity==80 边界不要逆向工程 StringMatcher 凑精确值,改用边界两侧的已知对照对钉住"严格大于"。

### 1.2 黄金样本 harness(纯函数化的主回归网,优先级 = P0)

**范围**:只覆盖纯决策(过滤+排序+偏好筛选+自动查找),不覆盖竞速/时序(那是 0b 定向测试的职责)。快照口径用公开 API,不绑定旧实现:`filteredCandidates.first()`(included/exclusionReason 类名/MatchMetadata 三元组有序列表)、`preferredCandidatesMedia.first()`、`trySelectDefault()` 返回的 mediaId。

**输入生成**:复用 `MediaSelectorTestSuite` 的 media 工厂,约 300–500 场景,维度 = media 列表(kind × alliance × resolution × 字幕语言 × 相似度梯度 × episodeRange × publishedTime × location × subtitleKind)× 偏好(Empty/PlatformDefault/四项明确/alliancePatterns/preferNoValue)× settings(preferKind × preferSeasons × hideSingleEpisodeForCompleted × showWithoutSubtitle)× context(subjectFinished × seriesInfo × subtitlePreferences × 源/channel tiers)。

**验证修正后的设计要点**:

- **种子按场景独立派生**(如 `hash(维度组合)`),不用单一 `Random(42)` 顺序流——否则任何维度增删都重排全部采样,数百个无关 diff 会让"重生成必须逐条 review"流程失效。
- **核心规则用显式枚举网格**兜底(FIND-04/05 跨维度权衡、FILT-11 归因顺序、SORT 全序),随机采样只负责组合爆炸区。
- **明确约束:legacy `@Deprecated` 测试框架在整个重构期间必须保持存活且全绿**(约 75 条已覆盖行为在它里面;黄金样本口径不含 `trySelectFromMediaSources` 两阶段,该口径的组合回归完全依赖 legacy)。删 legacy 框架是重构完成后的独立议题。
- 存储:稳定 JSON 进 commonTest resources(按场景分组多文件);`-Dani.golden.regenerate=true` 显式重生成,**重生成仅 JVM**(写回 src 需项目路径系统属性),Native 只校验;重生成 diff 逐条 review——每个 diff 要么对应故意变更编号、要么是回归。
- 排除不稳定输出:ITEM-04 的 available 顺序(HashSet 迭代序)不进快照;similarity 数值可进(确定性)。
- 成本约 1.5–2 天,全平台可跑,顺带补上 `@DisabledOnNative` 留下的 Native 盲区。

### 1.3 测试基础设施改造(全部为可测性改造,不改生产行为)

1. 断言 DSL 暴露 `exclusionReason`(`MediaSelectorTestAssertions.kt:183` 字段已有未接线;FILT-11/02/01 全靠它;半天)。
2. `collectEvents{}` 事件断言助手(turbine 包一层,**UNDISPATCHED 订阅 + 每步及时消费**——事件流是 replay=0 + DROP_OLDEST,多事件断言否则闪烁)。
3. `TestMediaFetcherSession` Handle 多阶段化(脚本队列:complete 多次 / completeExceptionally / restart 第二批)。
4. `DefaultMediaSelector.cached()` scope 可注入(`MediaSelector.kt:331` 本有 TODO),runner 增加 cachingEnabled 双跑参数。
5. 持久化 round-trip 夹具:内存 DataStore + 真实 `EpisodePreferencesRepositoryImpl`;Room in-memory dao 夹具(BundledSQLiteDriver)。
6. TestMediampPlayer 错误注入口(playbackState=ERROR / videoLoadingState=Failed)。
7. SimpleMediaSelectorTestSuite mediaList 增量发射(当前原地 mutate 不重发射)。
8. Native 覆盖策略:新增测试避免 context parameters;黄金样本作 Native 兜底。
9. ★`configureFetchSession` 增加 `startInBackground=false` 参数(MISC-03 防假安全网,见 §1.1#11)。
10. ★Room 迁移测试基建(MigrationTestHelper 或等价方案,desktopTest 优先落地)。

---

## 2. 方向 A:决策核纯函数化

> 方向已定:把选择决策抽成纯函数、flow 只当触发器,消灭 select{} 竞速与传播竞态 workaround。以下为经对抗验证修订后的设计。

### 2.1 目标分层

```
L4 编排层: AutoSelectDriver — 唯一的 collect 循环, flow=触发器
L3 适配层: DefaultMediaSelector — MediaSelector 接口不变, 公开 flow=快照投影
L2 快照组装: SelectorSnapshot 组装子 — 唯一 shareIn 点, 每次 emission 内同步算全套派生数据
L1 纯决策核 (无 flow、无 suspend、无副作用):
   MediaSelectorFilterSortAlgorithm  (已是纯函数, 一行不动)
   MediaSelectionDecider             (DFS 查找 + 两阶段 + 缓存 + 松弛计划)
   AutoSelectPolicy                  (显式优先级规则序列, 替代 select{} 竞速)
   SelectionExecutor + CommitMode    (副作用与决策分离)
```

新代码放 `app-data/.../domain/media/selector/engine/`(internal,不进稳定 API 面)。

### 2.2 核心数据类型

**输入快照**——一次内部一致的世界视图:

```kotlin
internal data class SourceResultSnapshot(
    val instanceId: String, val mediaSourceId: String, val kind: MediaSourceKind,
    val state: MediaSourceFetchState,   // 沿用现有密封类
    val results: List<Media>,
) { val isFinal get() = state.isFinal }

internal data class SelectorSnapshot(
    val sources: List<SourceResultSnapshot>,   // 缓存链路/旧构造为 emptyList
    val mediaList: List<Media>,                // sources 按序 flatten + distinctBy(mediaId) == FETCH-08
    val filtered: List<MaybeExcludedMedia>,    // filterMediaList + sortMediaList
    val preferred: List<MaybeExcludedMedia>,   // filterByPreference(filtered, merged)
    val savedUserPreference: MediaPreference,
    val savedDefaultPreference: MediaPreference,
    val overrides: PreferenceOverrides,        // 四维 OptionalPreference 会话覆盖
    val mergedPreference: MediaPreference,     // default.copy(四个 finalSelected), MERGE-01 逐字保持
    val settings: MediaSelectorSettings,
    val context: MediaSelectorContext,
    val selected: Media?,
) {
    val availableAlliances: List<String>       // 从 filtered 派生 (FIND-10 全量池语义)
    fun completedConditions(): CompletedConditions
    fun sourceFinal(mediaSourceId: String): Boolean
    fun contextLoaded(): Boolean = context.allFieldsLoaded()
}
```

关键不变式:**merged、filtered、preferred、availables 在同一次 emission 内同步纯计算**——不存在"filtered 已更新而 preferred 未更新"或"state 是 Succeed 而候选流还没吸收结果"的中间态。

**per-source 快照组装(⚠ 验证修订 V-A1,blocker)**:

```kotlin
// 必须用 combine 保持对 results 的单一稳定订阅。
// 禁止 state.flatMapLatest { results.map {...} }:state Idle→Working 转换会对
// results(shareIn WhileSubscribed(0ms))做取消-重订阅,订阅数瞬间 1→0→1,
// STOP 竞态赢时取消进行中的查询 → 源被打成 Abandoned 且按 FETCH-03 永久不重试;
// 若该源恰是偏好 Web 源,还会连带删除用户偏好(数据级放大)。
internal val MediaSourceFetchResult.snapshotFlow: Flow<SourceResultSnapshot>
    get() = combine(state, results) { s, list -> SourceResultSnapshot(instanceId, mediaSourceId, kind, s, list) }
```

一致性论证在 combine 下同样成立:state 置 Succeed 发生在 results 上游正常完结的 onCompletion(`MediaFetcher.kt:240-247`),此刻 replay=1 缓存已含最终列表,combine 的下一次 emission 必然是 (Succeed, 最终列表)。collect `sourceSnapshots` 即 collect 每个源的 results,天然驱动惰性查询——MISC-03 从隐式依赖变成结构保证:**编排在跑,查询就在跑**。

**纯决策核**(`MediaSelectionDecider`):`findUsingPreferenceFromCandidates`(`MediaSelector.kt:545-715`)逐字迁入 `findByPreference(candidates, merged, availableAlliances, mediaSourcePrecedence, shouldPreferSeasons, preferKind)`,两处 suspend 读取提升为参数(context 门 → 类型前置条件;`alliance.available.first()` → `availableAlliances` 参数)。DFS 全部特殊规则逐字保留:分辨率>语言>字幕组>数据源、不为 4K 换语言、字幕组未命中不降级语言、WEB similarity>80 前置(#1521)、季度分支、selectAny 的 `hasSeason()==null` 手误照抄钉住(FIND-08,有测试锁定)。外加 `decideDefault` / `decideFromSources`(bake+两阶段)/ `decideCached` / `planPreferenceRelaxation`(TRY-08 的纯模拟,等价性成立因为 filtered 只依赖 savedDefaultPreference,清会话偏好不改变 filtered)。每个决策附 `SelectionRationale`(仅日志/调试,不进对外事件)。

**副作用分离**(`SelectionExecutor` + `CommitMode { Manual | AutoDefault | AutoOverride }`):副作用顺序逐字保持 SEL-02;`Manual` 写四项 override(subtitleLanguageId 仅 singleOrNull)→ onChangePreference → [WEB] onPreferWebSource → onSelect;`AutoDefault` CAS、不写偏好(SEL-07 孤儿 onBeforeSelect 钉住)。

**⚠ 验证修订 V-A6(major)**:executor 在写 overrides **之后**构造 onChangePreference 载荷时,必须从 `overrides.value`(StateFlow 即时值)+ **未缓存的** savedUser/savedDefault 同步重算 merged——不得取自单点 shareIn 的快照(写后快照尚未重发射,载荷会整体错一拍,落库的偏好变成上一次的值)。`removePreferencesUntilFirstCandidate` 的逐步广播载荷同理。0c 事件测试必须**断言载荷值**,不能只断顺序。

### 2.3 自动选择:显式优先级规则序列

`PolicyInit`(启动一次性快照:settings/tiers/preferredWebSourceId/subjectId,保持现 `.first()` 语义 ORCH-04/MISC-02/PREF-01)+ `PolicyState` + 纯函数 `AutoSelectPolicy.decide(init, state, snapshot, firedDeadlines) -> (newState, actions)`。规则按固定顺序求值 = 现 select{} clause 注册顺序:

| 规则 | 就绪条件(纯谓词) | 对应现状 |
|---|---|---|
| R1 PreferredWeb | 无 subjectId/无偏好源/会话无该 WEB 源 → 立即 Failed;否则等 `sourceFinal(id)`;final 后 `decideFromSources([id], allowNonPreferred=false)`,命中 → Commit,否则 Failed | PREF-01..04 |
| R2 FastSelect | 门控 `fastSelectWebKind && preferKind==WEB`(启动快照)且 R1 已 Failed;Armed 时发 StartDeadline(5s);每快照:候选源=「getBestTier≤0 ∧ Succeed ∧ 有结果」→ `decideFromSources(候选, allowNonPreferred=true, channel 级 tier 过滤)`;deadline 到 → 一次性从全部 Succeed WEB 源放开 tier 选,仍无果 → Exhausted(等其他规则) | FAST-01..06 |
| R3 Cache | `selected==null && decideCached(s)!=null`,每快照重试 | CACHE-01..02 |
| R4 Fallback | **(⚠ V-A5 修订)** `completedConditions()` 满足(preferKind **live** 读,DEF-01)**且(无 Included 候选 ∨ contextLoaded())**;满足后:已有选择 → Finish;否则 decideDefault → Commit;**无论结果是否 null 都 Finish**(ORCH-06) | DEF-01..03 |

V-A5 说明:现状 `trySelectDefault` 是双分支——候选为空时**不等 context** 直接 null(`MediaSelector.kt:717-720`),候选非空才挂起等 `allFieldsLoaded`。若把就绪条件写成"未加载则一律不就绪",在"源全完成 + 候选空 + context 加载失败"场景编排永不 Finish(缓存链路还会持锁挂死);写成"未加载也就绪"则会提前放弃。上表条件同时复刻两支,须入 0b 测试。

**Driver**(唯一非纯部分,约 40 行):`merge(snapshotFlow, deadlineEvents).collect { decide; act }`;Commit → executor;StartDeadline → `launch { delay; emit(token) }`;Finish → 取消策略循环。

**⚠ 验证修订 V-A2(major)**:`autoEnableLastSelected` 必须挂在 **Finish 取消域之外**(与现状一致:外层 scope launch,`cancelScope()` 只取消内层)。若 Finish 连带取消它,"全部源极快到终态"的场景(极端:全部 Disabled → allCompleted 立即成立)会在 autoEnable 读 DataStore 完成前把它杀掉,#355 行为被无声破坏。

**等待条件全部建模为可测状态**:`awaitCompletion` → `sourceFinal(id)` 谓词;`onTimeout(5s)` → deadline token(测试直接把 token 放进 fired 集合,零真实时间);`awaitSelectFromMediaSources` → "无果=无动作,下一快照重评"。测试构造快照序列喂 `decide`,断言 (state, actions) 序列,**不需要虚拟时间即可测全部策略逻辑**。

**⚠ 验证修订 V-A3 / V-A4(major)——公开方法退化为迷你 policy 的语义边界**(`MediaSelectorAutoSelect` 各方法签名不动,被 `SwitchMediaOnPlayerErrorExtension` 与 `EpisodeCacheRequester` 跨界调用):

- **门控只存在于编排级 policy**。方法级 `fastSelectWebSources` 迷你 policy **无 fastSelectWebKind 门控**(现状方法本体不查设置,播放失败换源只查 preferKind——若带上门控,关掉快速选择的用户将无声失去失败换源),`GatedByPreferredWeb` 初始态为已放行,Exhausted **返回 null**(不 awaitCancellation——独立调用时挂起会让 handleError 永久卡死)。
- **`awaitCompletedAndSelectDefault(session)` 的完成条件必须从传入的 session 推导**,不得从 selector 快照取(缓存链路的 selector 经旧 `Flow<List<Media>>` 构造,`sources==emptyList`,若从快照取会退化为"恒完成"→ 在部分结果上提前选择;反向实现则在 `EpisodeCacheRequester.kt:188-192` 的 `stageLock.withLock` 内永久挂起,整个缓存请求器死锁)。该方法还须自行驱动 session 的源查询(现状由 awaitCompletion 内部 Eagerly shareIn 承担)。

**Phase C 前瞻**:策略表将来会增加 mode 维度并新增 R5 跨模式兜底(§5)。方向 A 实现时**不提前实现**任何模式逻辑,但 `PolicyInit`/规则表的结构须保证"按模式裁剪一张表"在 Phase C 是纯配置级改动。

### 2.4 三处竞态 workaround 的消亡论证

1. **FILT-05(filteredCandidates 只能用 default 偏好)**:所谓循环是 **flow 对象图的伪循环**(available 引用 filteredCandidatesMedia),不是语义循环——`finalSelected` 合并链(`MediaSelector.kt:876-891`)完全不依赖 media list。组装子内按 DAG 分阶段同步计算:merged(不需要 media list)→ filterMediaList(**仍用 savedDefault,钉住现状**)→ availables → filterByPreference。workaround 从"被 flow 拓扑逼出来的限制"变成一行显式参数 + 注释 + 测试。
2. **PREF-04(preferredWebSource 四步快照补偿)**:被构造性消灭——快照内 (state==Succeed) 与 (结果已在 filtered 中) 必然同时成立(见 §2.2 combine 论证),"结果还没传播过来"的第三种状态不存在。四步补偿、`awaitCompletedResults` 判空、`first { contains }` 等待全部删除。
3. **avoid-suspension 注释**:竞速模型里优先级由完成时刻决定,任何额外挂起都可能输给低优先 clause;新模型优先级是结构性的(decide 按 R1>R2>R3>R4 固定求值),"多挂起一拍输掉竞争"在模型层面不可能发生。

附带消亡:MERGE-02 must-not-cache 双流(TRY-08 改纯模拟 + V-A6 的显式同步重算)、`awaitSelectFromMediaSources` combine 重试环(退化为 `snapshotFlow.mapNotNull{decide}.first()`)、UI `delay(1000)`(`MediaSelectorState.kt:310-317`,**单独 PR**,须 desktop-ui-verify 录屏帧差确认无"源列表闪空"后再删)、`MediaSelectorSummaryStateProducer` 的时序假设(变为结构不变式)。

### 2.5 微观时序差异(决策结果不变,如实列出,owner 知情)

1. FAST-01 `transformWhile` 终态后停止跟踪 → 新模型持续跟踪:源在 5s 窗口内被手动 restart 成功时,新模型更早重评。
2. 6 处独立 `cached()` 收敛为单点 shareIn:UI 观察到 filtered/preferred 同拍更新,延迟略降。
3. select{} 同刻 clause 偏置 → decide() 固定求值顺序(严格更确定)。
4. ★(V-A7)R4 重评触发面扩大:现状完成条件只被源状态变化驱动,新模型任何快照(含设置/偏好变化)都重评——会话中改 preferKind 会立即触发兜底。0b 钉住该取舍。
5. ★(V-A8)availableAlliances 从独立 cached 流改为同快照派生,取样时点在列表更新瞬间可能不同(数据稳定时等价)。0a 用稳定数据锁定。

### 2.6 增量迁移步骤

每步:编译通过、全测试绿、独立 commit 可单独 revert。**[纯移动]**=低危,**[执行模型]**=改并发结构。

| Step | 内容 | 危险度 | 验证 |
|---|---|---|---|
| 0 | §1 的 0a 决策套件 / 0b 编排套件(虚拟时间;严禁混真实 IO)/ 0c 事件顺序套件,在未改动的 main 上全绿 | — | Step 5/6 合入门槛 = 0a+0b+0c 全绿 |
| 1 | DFS 提取为纯函数(`findByPreference`),逐字搬运 | 纯移动 | 0a + 纯函数直测(FIND-01..10 不再绕 flow) |
| 2 | 提取 SelectionExecutor + CommitMode(须先有 SEL-02 测试) | 纯移动 | 0c 全绿(事件顺序+**载荷值**逐字节一致) |
| 3 | SelectorSnapshot 组装(输入不变),公开 flow 改投影,6 处 cached() 收敛单点 | 执行模型·中 | 全单测 + desktop-ui-verify(选择器 sheet、四维偏好、缓存页;录屏帧差查闪烁) |
| 4 | trySelect*/awaitSelect*/removePreferences 改走快照决策;删 must-not-cache 双流 | 执行模型·中 | 0a TRY 组 + 0c;awaitSelect 永久挂起用虚拟时间 |
| 5 | SourceSnapshot 输入接入播放链路(新构造;缓存链路走兼容包装不动) | 执行模型·高 | 黄金测试:同一 session 上 `sourceSnapshots` 投影 == `cumulativeResults` 逐 emission 终值(mediaList 序必须逐位一致,否则 SORT-00 平局序变);MISC-03 显式测试;MediaFetcherTest 不动全绿 |
| 6 | AutoSelectPolicy + Driver;0b 参数化**双实现对拍**(旧 select{} 与新 driver 同时绿)后切换;旧实现保留一个版本周期 | 执行模型·最高 | 0b 双实现 + decide 直测 + desktop-ui-verify E2E(tier0 秒选/5s 容忍/偏好源优先/缓存胜出/失败换源不回选) |
| 7 | 删 workaround(逐项独立 commit;UI delay(1000) 单独 PR 需录屏证据) | 纯移动 | 每删一项全测 + 对应 E2E |
| 8 | KDoc 修正与死代码清理(§6 批准后) | 纯移动 | — |

---

## 3. 方向 B:统一 last-played 持久化

> 范围:三条通道 → 一条显式 per-subject 记录。全局 `defaultMediaPreference` 不在范围内。

### 3.1 新记录

Room 新表 `media_selector_last_selection`(schema 21→22,AutoMigration,先例:`playback_history_record`):

```sql
CREATE TABLE media_selector_last_selection (
    subjectId          INTEGER NOT NULL,
    sourceKind         TEXT NOT NULL,  -- 'WEB'|'BitTorrent'|'LocalCache'|'UNKNOWN'(backfill 时源未安装)
    mediaSourceId      TEXT,           -- 上次手动选择的源实例 id
    alliance           TEXT,           -- WEB: channel 名; BT: 字幕组名 (行内语义由 sourceKind 决定)
    resolution         TEXT,
    subtitleLanguageId TEXT,
    webSourcePreferred INTEGER NOT NULL DEFAULT 1,  -- 仅 WEB 行有意义; "失败即删"改为置 0
    selectionOrigin    TEXT NOT NULL DEFAULT 'MANUAL', -- D1 预留
    updatedAtMillis    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(subjectId, sourceKind)
);
```

要点:

- **⚠ 修订(D11,2026-08-02):复合主键 `(subjectId, sourceKind)`,按 kind 分行**。这不只是为 Phase C 铺路——单行 PK 即使在不拆模式的世界里也有两个跨 kind 互踩:(a) 手动选 BT 会把行覆盖成 sourceKind=BitTorrent,clause①/R1 读 `kind==WEB` 永落空,等于 BT 选择清掉了 WEB 的 preferred-source 记忆(现状 Room 表在 BT 选择后存活,单行设计反而比现状差);(b) alliance 列 WEB 写 channel 名、BT 写字幕组名,单行互相污染过滤(EMG-01 孪生)。分行后二者天然消失。
- **方向 B 期间(Phase C 之前)的读语义,保证行为与现状逐位一致**:`mediaPreferenceFlow` 的四字段 overlay 读 **max(updatedAtMillis) 的行**(复刻现状单 JSON 通道"最后写入获胜",含跨 kind 污染——污染的消灭属于 Phase C 的 C13,不在方向 B 发生);clause① 恒读 **WEB 行**(与现状 Room 表隔离语义一致)。chip 单维度写入落 overlay 当前选定的行(无任何行时建 'UNKNOWN' 行);Phase C 后 overlay 与 chip 改按设置模式选行。
- **无 FK 无级联**(消灭"未收藏写入抛 SQLiteConstraintException"与"收藏 REFRESH 级联清偏好"两个隐蔽语义,列 C4/C5);取消收藏等显式删除按 subjectId 删全部 kind 行。
- 可空列 = 该维度回退全局默认(与现状 null 语义等价;会话内 preferNoValue 本来就不跨会话持久化);行内 (sourceId, kind, alliance) 是**原子绑定的三元组**——channel/alliance 的语义分离在数据层完成(零行为风险),"alliance 仅对同 kind 生效"的行为利用属于 Phase C(C13)。

选 Room 而非 DataStore:结构化部分更新(chip 单维度)、迁移惯例一致、无 corruption 全清爆炸半径、`updatedAtMillis` 可清理。降级劣势由兼容期双写覆盖(C8)。

**写入时机(基线,保持现状)**:仅手动路径——`selectImpl(updatePreference=true)` 从 candidate 派生;chip prefer/removePreference 单列部分更新;缓存页同一 writer;所有自动选择路径不写(SAVE-01)。

**⚠ 验证修订 V-B1(blocker)**:writer **禁止无条件整行 upsert**。`subtitleLanguageId` 列仅当 candidate 字幕语言 `singleOrNull()` 非 null 时更新,否则**保留原列值**(部分更新或读改写)——否则手动选多语言/零语言资源(BT 常态)会把已存字幕偏好清成 NULL,破坏 SEL-03;现状 onChangePreference 载荷经 finalSelected 链保留 DB 已有值,新 writer 必须等价。

**写入机制两阶段**:兼容期(S3)沿用现有事件管线并联新 writer(时序与丢失窗口与现状完全一致);最终态(S5)改为选择提交点的显式挂起调用(use case 直接持有 subjectId),SAVE-02/SAVE-03/EVT-02 三个丢失窗口消失(C2/C3)。

仓库层:`EpisodePreferencesRepository` 接口位置保留(调用方不动),`mediaPreferenceFlow` 兼容读改为"全局默认 overlay 新表四字段";新增 `lastSelectionFlow` / `saveLastSelection` / `updateLastSelectionField` / `markPreferredWebSourceFailed`。

### 3.2 迁移策略:eager backfill + 兼容期双写 + 读侧分步切换

**Room migration**:22 版 AutoMigration(纯加表,空 spec;提交 `22.json`)。**应用层 backfill**(AutoMigrationSpec 拿不到 DataStore):启动后后台一次,经 DI 取 store(**绝不拼路径**——三端文件名不同),遍历 per-subject key + 联表 Room 行,按矩阵合并;旧数据原样保留到 S6。

**合并矩阵(⚠ 含 V-B2 修订行;D11 复合主键后按 kind 分行,C1 的"分歧裁决"大部分消解为两行共存)**:

| JSON 通道① | Room 通道② | 新表行(按 kind 分行) |
|---|---|---|
| 无/空白/坏 JSON | 无 | 不建行 |
| 有 | 无 | 一行,落在 JSON.mediaSourceId 实例 kind 的行(未安装→'UNKNOWN' 行);flag 仅 WEB 行有意义,取值见 D2 |
| 无/坏 | 有 | 一行 (subjectId,'WEB'):mediaSourceId=Room 行,flag=1,其余 NULL |
| 有,JSON 源 kind==WEB 且 == Room 行 | 有 | 一行 WEB:四维按 JSON,flag=1 |
| ★有且 **`JSON.mediaSourceId 为 null`** | 有 | **兼容态,非分歧**:一行 WEB:mediaSourceId 取 Room,flag=1,其余维度按 JSON。(此形态今天完全有效——chip 清除数据源维度、或 debounce 丢写都会产生) |
| 有,JSON 源是 BT/非 WEB kind | 有 | **两行共存**:BT('UNKNOWN')行按 JSON,WEB 行按 Room(flag=1,其余 NULL)。信息保留,优于单行设计的"丢弃 Room 行" |
| 有,JSON 源 kind==WEB 但 ≠ Room 行 | 有 | 唯一真冲突:JSON 胜出落 WEB 行,Room 行丢弃(现状下 Room 行是"莫特值":clause① 等它但在被 JSON 过滤的候选里永远选不出,只拖延 fastSelect)→ C1 |

**幂等(⚠ V-B4 修订)**:已存在的行**按列合并(仅填 NULL 列)**,不整行跳过——backfill 是后台任务,用户可能在其完成前就 select/点 chip 创建部分行,整行跳过会让该 subject 的历史偏好永不迁入。

**⚠ 验证修订 V-B3(major)——S3 双写必须镜像"删"**:双写期旧删除路径(失败即删、取消收藏级联、收藏 REFRESH deleteAll 级联)只作用于旧表;若新表不镜像,S4b 切读后已删偏好成批"复活"。规定:失败即删镜像为 `markPreferredWebSourceFailed`(flag=0);级联/显式删除镜像为对应新表语义;入 S3 集成测试。

**双写窗口(D4,≥1 个 minor release)**内降级无损;S5 起降级读到停更旧数据(C8;附 V-B5:双写期旧 writer 应保留旧 JSON 非四字段原值,避免降级后读到被全局默认改写过的 JSON)。

### 3.3 旧语义映射(M1–M15 全表见行为清单,此处列"变"的)

| 旧语义 | 新通道 | 变化 |
|---|---|---|
| debounce 1s + 取消丢写 + 无订阅者丢事件 + 缓存页 workaround | 最终态提交点直写 | C2:保存从"尽力而为"变"必达" |
| context 未加载丢 onPreferWebSource(EVT-02) | subjectId 来自会话静态值 | C3:不再丢 |
| 失败即删 Room 行(但 JSON.mediaSourceId 仍 pin 候选、autoEnable 仍启用——三通道不对称删除) | flag=0;clause① 读 `kind==WEB && flag`;过滤与 autoEnable 读 mediaSourceId 列不看 flag | 不变(flag 精确复刻不对称性) |
| 取消收藏/REFRESH 级联清偏好 | 无 FK,记录存活 | C4(用户可感知:取消再收藏后 clause① 仍等上次的源) |
| 未收藏 upsert 抛 SQLiteConstraintException | 正常写入 | C5(修 bug) |
| 默认值渗入 per-subject 记录(SEL-05) | 只写显式来源的值;未设维度保持 NULL,全局默认后续变更即时生效 | C6(语义决定) |
| autoEnableLastSelected / clause① 快照语义 / SEL-03 字段集 | 合并链原封不动,数据源换新表投影 | 不变 |

### 3.4 增量步骤

```
S0 防回归测试(并入 Phase 0:SAVE-06/ROOM-03/迁移基建/跨会话闭环 等)
S1 Room schema 22(Entity+DAO+空 spec+22.json)          — DAO 单测
S2 backfill                                              — 合并矩阵 6 行(含 V-B2)+ 坏 JSON + 幂等按列合并 参数化单测
S3 双写(写 + ★镜像删)                                    — 新旧一致集成测试;S0 全量回归
S4 读侧切换(三个独立 commit,各自可 revert):
   4a mediaPreferenceFlow → 新表 overlay                  — 新旧路径对拍(同数据断言四个 finalSelected 一致);C6/C7 新断言
   4b clause①/UseCase → 新表投影;失败即删 → flag          — S0 clause① 测试在新数据源下全绿;C4 断言
   4c autoEnable 自动跟随 4a                              — 端到端断言;可选 debug shadow-read 对拍日志跑一个内测周期
   + android/desktop-ui-verify smoke:选源→杀进程→重进自动选回同源同线路
S5 写侧收敛(提交点直写,移除旧 writer;C2/C3/C8 生效)      — 丢失窗口测试改必达断言
S6 清理(下一 release:schema 23 DROP 旧表;DataStore key 惰性删;删旧 API;backfill 保留) — 21/22 两起点升级 smoke
```

---

## 4. 方向 A × 方向 B 的顺序

**A 先 B 后,Phase C 最后**。理由:B 的 S5(提交点直写)最干净的落点是 A 的 SelectionExecutor(单点提交);A 期间持久化挂载方一律不动(设计 A §2.6 明确),两者接触面不相交,每一步都只有一个变量在动。B 的 S1–S2(加表、backfill)不依赖 A,若想提前准备数据可在 A 的 Step 3 之后穿插,但双写(S3)及以后必须等 A 完成。Phase C(§5)依赖 A 的 policy 结构与 B 的分行存储,且其行为变更(C12–C16)必须落在 Phase 0/A/B 建成的"现状已被完整锁定"的基线之上。

---

## 5. Phase C:BT/WEB 双模式拆分(方向已批 2026-08-02,A/B 之后执行)

### 5.1 依据(专项分析:3 调查 + 1 对抗审视,引用经核实)

- **现状已是两个算法被编织在一起**:41 个行为单元中真正 kind 无关仅约 27%;编排层今天就按 kind 分叉(clause①② 纯 WEB、③ 纯 LocalCache、④ 含 kind 条件;播放失败换源整条 WEB-only,BT 用户没有该功能)。9 处 WEB-only 逻辑(#1521 similarity>80 等)被迫写成通用代码里的条件分支。现状 = "BT 形状的核心算法 + WEB 旁路补丁网"。
- **WEB media 属性实况证明老 DFS 对 WEB 有效信息量≈0**:Selector 源 resolution 恒等于源配置常量(网页不解析)、字幕语言兜底恒 CHS(生肉过滤对 Selector/Jellyfin/Emby 结构性不可能触发)、publishedTime 恒 0、alliance=channel 名且主路径已用 ANY_FILTER 绕开。保留三维偏好对 WEB 只剩污染通道(BT 4K/CHT/字幕组写入偏好后灭掉 WEB 候选;channel 名跨源误命中)。RSS 源 kind 恒为 BitTorrent,归 BT 模式,无边界含糊。
- **"硬拆失去 WEB→BT 软兜底"不成立**:现状兜底是残缺的时序彩票——preferKind=WEB 时 clause④ 只等 WEB kind 完成(Failed 也算完成),"WEB 全灭"恰是完成条件最快成立的时刻,BT 结果通常未到,trySelectDefault 返回 null 即永久终结编排;且选过 WEB 后 channel 名写入 alliance,BT 候选早被硬过滤。显式 R5 是**行为改进**而非拆分代价。

### 5.2 选定形态:L2(光谱 L0–L3;L3"完全两个 selector"明确不做——LocalCache 四处特权要复制两份、缓存页矛盾尖锐、无受益方)

1. **设置层模式二态(WEB/BT)取代 preferKind 三态**,自动选择按设置模式走。**null→WEB(D8 已定)**,连带:设置页删除"无偏好"选项;iOS 隐藏 BT 模式(无 BT 源,SESS-02);PikPak 弹窗从"改偏好"升格为"切整套算法",文案与语义重审。
2. **策略表按模式裁剪**(建立在方向 A 的 R1–R4 policy 结构上,落地成本≈裁剪一张表):
   - WEB 模式 = R1(上次 Web 源)+ R2(fastSelect)+ R3(缓存)+ R4_WEB(tier + 上次 (源, channel) + similarity;**跳过 resolution/字幕语言/字幕组三维**)+ **R5 跨模式兜底**(WEB 穷尽 → 等 allCompleted → 在 BT 候选上跑 BT 侧算法;iOS 恒空退化)。
   - BT 模式 = R3 + R4_BT(老四维 DFS 逐字保留)+ R5'(对称:BT 穷尽 → WEB 候选)。
   - R4 的 waitForKind 与候选池按模式显式定义;**任一模式不得裁掉 R4**(查询驱动方,MISC-03)。
3. **数据层按 kind 分行**(§3.1 复合主键,已折入方向 B):WEB 行记 (sourceId, channel),BT 行记四维偏好,互不污染。
4. **UI 三 tab(D9/D10 已定)**:**简单模式(WEB)/ 详细模式(仅 WEB 源)/ BT(单独展示 BT 源结果)**。tab 是纯展示选项:切 tab 不影响正在播放的内容、不改设置模式。BT tab 手动选择是一等操作:写 BT 行记忆、模式不变(该记忆经 R5 或用户切设置模式后生效)。注意两处用户可见变化须列入变更清单:现"详细模式"是全 kind 混排,改为仅 WEB(C15);BT 模式需要自己的 AutoSelecting banner(现状 banner 只支持 WEB 源图标,BT 自动选择运行期恒显示"需手动选择",`MediaSelectorSummaryStateProducer.kt:56` FIXME)。
5. **两条共享边界定调**:LocalCache 是**两模式共享的前置特权层**(过滤/偏好豁免、置顶、独立竞速全部 kind 无关,不属于任何模式,拆分不得触碰;WEB 模式下 BT 出身的本地缓存仍置顶可选)。**缓存页不属于模式**(模式只管播放场景的自动选择;缓存页保持全量双 kind、手动为主;已核实 WEB 资源三端可缓存、"等全部 kind"的缓存自动选择 API 无生产调用方;缓存页 writer 按所选 media.kind 落对应行)。
6. **测试**:Phase 0 黄金样本 v1 锁现状单算法,职责不变;Phase C 落地后另起 v2(mode 轴替代 preferKind 轴);0b 编排套件按模式参数化;legacy 测试保活条款不变。

### 5.3 原 G1/G2/G3 的归属

| 差距 | 状态 |
|---|---|
| G1 等整源 → 只等上次 channel | Phase C 内:R1 改用 `candidateMediaFilter` 按 channel 流式命中即选(管道已存在) |
| G2 显式 (sourceId, channel) 记忆 | **方向 B 完成**(复合主键行内原子绑定) |
| G3 自动选择不落库 | 仍由 D1 决定(`selectionOrigin` 列已预留) |

### 5.4 排期硬约束

Phase C 必须在 Phase 0 → A → B 全部完成后作为独立阶段执行。提前或与 A 并行会作废 Phase 0 基线口径与 Step 6 双实现对拍——那等于推翻定稿方案。Phase C 启动前需要一轮细化设计(R5 精确语义、三 tab 交互稿、C12–C16 逐条复核),此细化不在本文档范围。

---

## 6. 故意行为变更清单(逐条签字,批准前全部钉住现状)

**方向 B 附带(设计内在,批准方案 = 批准这些)**:C1 失步态消失(D11 后大部分退化为两行共存)/ C2 保存必达 / C3 EVT-02 消失 / C4 取消收藏不清记录 / C5 不再抛 FK 异常 / C6 默认值不渗入 / C7 坏 JSON 路径消失 / C8 兼容期后降级丢 last-played / C9 迁移裁决 / C10 旧表旧 API 删除。

**Phase C 附带(方向已批,细化设计时逐条复核)**:C12 preferKind 三态→二态模式,null/"无偏好"选项删除并迁 WEB(D8)/ C13 WEB 模式移除三维偏好参与(过滤+DFS;跨 kind/跨源污染就此消灭)/ C14 生肉过滤(showWithoutSubtitle)降为 BT-only(对主流 WEB 源零实际影响,属性上不可能触发)/ C15 "详细模式" tab 从全 kind 混排改为仅 WEB,BT 独立成 tab(D9)/ C16 新增 R5/R5' 跨模式兜底(相对现状"残缺时序彩票"基线是改进,基线描述见 §5.1)。

**独立修复候选(可单独批、单独不批)**:

| # | 项 | 建议 | 用户可感知? |
|---|---|---|---|
| F1 | ITEM-04:subtitleLanguageId.available 排序表写的是分辨率字样,实际顺序=HashSet 迭代序 | 改语言优先表 | 仅 UI 列表顺序 |
| F2 | ITEM-05:mediaSourceId.available 误取 resolution | 改取 mediaSourceId | 否(UI 未消费) |
| F3 | MP-02:`MediaPreference.merge()` 死代码丢字段 | 删除 | 否 |
| F4 | MISC-01:`GetPreferredMediaSourceSortingUseCase` 死代码 | 删除+移除 Koin 注册 | 否 |
| F5 | SEL-06:`SelectEvent.subtitleLanguageId` 恒 null 死字段 | 删除 | 否 |
| F6 | EVT-01/FETCH-07/TIER-03:三处 KDoc 与实现不符 | 改 KDoc 从实现 | 否 |
| F7 | FIND-08:selectAny `hasSeason()==null` 疑手误(应为 ==true?) | 若批准改,兜底层真正优先季度全集,需同步改锁定测试 | **是**(完结番+空偏好的兜底选择变化) |
| F8 | SEL-07:selectDefault CAS 失败孤儿 onBeforeSelect | 建议钉住不改 | 几乎否 |
| F9 | ERR-02:设置流未 distinctUntilChanged,无关设置变更取消在途换源 | 加 distinctUntilChanged | 边缘场景 |
| F10 | EMG-01:BT 偏好字幕组消失致自动选择死局 | 移交方向 B 后评估(为 BT 补偿放宽 alliance) | **是** |
| F11 | UI delay(1000)(MediaSelectorState) | A Step 7 单独 PR,需录屏证据 | 源列表提前 ≤1s |
| F12 | ★BT resolution displayName/id 失配(2026-08-02 新发现,行为清单已补录 RES-01):BT media 存 displayName("4K"/"2K"),偏好 fallback 列表用 id("2160P"/"1440P"),裸字符串比较永不相等 → 默认偏好下 DFS 的 4K/2K 两层对 BT 恒空;标题写"4K"字样的资源解析不出、兜底记 1080P | Phase C 的 BT 模式细化设计时一并定夺(修复会改变 BT 4K 偏好行为) | **是** |

## 7. Owner 决策点汇总

**已定(2026-08-02)**:

| ID | 决策 |
|---|---|
| D8 | preferKind=null → **WEB**(含删除设置页"无偏好"选项;BT-only 用户经 R5 兜底仍可达 BT) |
| D9 | 播放页选择器 UI 拆三 tab:**简单模式(WEB)/ 详细模式(仅 WEB 源)/ BT(独立展示 BT 源)**;tab 是纯展示选项,切换不影响正在播放的内容、不改设置模式;自动选择只看设置里的模式 |
| D10 | BT tab 手动选择:写 BT 行记忆、模式不变(经 R5 或切设置模式后生效) |
| D11 | 新表复合主键 **(subjectId, sourceKind)**,sourceKind NOT NULL,backfill 未知 kind 用 'UNKNOWN' sentinel(§3.1/§3.2 已按此修订) |

**待批**:

| ID | 问题 | 推荐 |
|---|---|---|
| D1 | 自动选择是否回写 last-selection(G3) | 默认关,列已预留 |
| D2 | backfill 中"JSON 为 WEB 源但 Room 无行"的 flag 取值 | 保守 =0(精确保现状) |
| D3 | 失败即删 → 置 flag(保持不对称)还是清整源(简化但变行为) | flag |
| D4 | 双写窗口长度 | ≥1 个 minor release |
| D5 | 旧表/旧 key 删除时机 | S5 后下一个 release |
| D6 | §6 的 F1–F12 逐项批复 | F3/F4/F5/F6 建议批(零风险);F7/F10/F12 建议单独讨论(用户可感知) |
| D7 | FILT-05 是否在 A 完成后让用户按条目偏好覆盖生肉过滤(一行参数) | 本次不改,列观察 |

## 8. 对抗验证记录

两轮独立验证(全部结论已在源码逐行核实后才采纳):

**破坏性猎手**(对两份设计逐条过 128 条行为清单找"会被无声破坏"项):2 blocker(V-A1 snapshotFlow 自取消竞态、V-B1 整行 upsert 清字幕偏好)+ 7 major(V-A2 autoEnable 取消域、V-A3 迷你 policy 门控/终止、V-A4 缓存链路完成条件死锁、V-A5 R4 双分支、V-A6 载荷即时重算、V-B2 合并矩阵漏行、V-B3 双写不镜像删)+ 4 minor(V-A7/A8 微观差异、V-B4 backfill 遮蔽、V-B5 降级 JSON 改写)。**全部已折入正文**(正文中 ⚠ 标记处)。

**测试计划批评家**:2 blocker(Room 迁移测试整类遗漏、MISC-03 假安全网)+ 4 major(跨会话闭环缺失、FIND-10 测试入口错误、ITEM-02/ROOM-03 应升 P0、黄金样本种子/口径/legacy 保活)+ 3 minor(AELS-01 误记、事件断言工程注意、FIND-06 边界构造)。**全部已折入 §1**。

**第三轮(2026-08-02,BT/WEB 拆分专项)**:3 个调查(kind 分叉量化 41 单元、media 属性实况、拆分波及面)+ 1 个对抗审视(逐条评估反对理由,抽查全部引用属实)。产出:L2 形态推荐与 L3 否决、R5 兜底论证("现状软兜底是残缺时序彩票"的动态时序分析)、D11 复合主键的独立佐证(两个跨 kind 互踩)、F12 新 bug(BT resolution displayName/id 失配)、null 迁移三案评估。结论已折入 §5 与 §3.1/§3.2 修订。

盘点数据(行为清单、测试覆盖映射、API 消费方、存储格式)的原始 JSON 归档于分析会话,行为清单已固化为姊妹文档。
