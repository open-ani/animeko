# :utils:selector-workflow

「数据源选择流程」示意动画的**数据逻辑层**。不含任何绘制代码——它产出一份可采样的
[`SelectorWorkflowState`](src/commonMain/kotlin/State.kt)，由 Compose Canvas 读取后画成图。

## 分层

```
SelectorWorkflowConfig          ← 唯一输入：几个源、各搜多久、几条结果/候选、两个超时预算、三个开关
        │  buildTimeline()
        ▼
   Storyboard (DSL)             ← 剧本按拍子对「单元句柄」下指令
        │  build()
        ▼
SelectorWorkflowTimeline        ← 每个单元一组关键帧轨道；纯数据、可重复采样
        │  sampleAt(t)
        ▼
 SelectorWorkflowState          ← 某一帧要画的全部东西
        ▲
TimelinePlayer / ViewModel      ← 把帧时刻变成播放位置
```

关键性质：**时间线是纯函数**。同一个 `Duration` 采出来永远是同一份状态，所以拖进度条、
定格截图、单元测试都能直接用，也不需要在 Canvas 里保存任何动画状态。

## 九种基础可控制单元

| # | 单元 | 出现在 | 可动属性 |
|---|------|--------|----------|
| 1 | `SourceNodeState` | 第一步 | `alpha`、`pulsing` |
| 2 | `LineState` | 第一步的连线 / 第二步末尾的交棒线 | `progress`、`alpha` |
| 3 | `ResultChipState` | 第二步 | `alpha`、`tone`、`scale` |
| 4 | `RippleState` | 第二步 | `scale`、`alpha` |
| 5 | `CursorState` | 第二步 | `cell`（浮点，可插值）、`alpha` |
| 6 | `ClockState` | 第一/二步、第三步各一个 | `sweep`、`alpha`、`tone`、`overlayAlpha` |
| 7 | `WindowState` | 第三步 | `tone` |
| 8 | `RequestRowState` | 第三步 | `alpha`、`icon`、`tone` |
| 9 | `ScrollState` | 第三步 | `rowOffset` |

结果容器边框、mac 三圆点、地址栏是静态装饰，不算可控制单元。
候选圆点与高优先级菱形是结果块上的**静态标记**（`candidate` / `priority` 两个布尔），不单独成为单元。

单元只暴露与画法无关的量：语义色用 `ChipTone` / `WindowTone` / `ClockTone` 这类枚举，
由 Canvas 层映射到 M3 token；位置用 `cell` 序号，由 Canvas 层按网格算坐标。

## DSL

[`Storyboard`](src/commonMain/kotlin/Storyboard.kt) 拿着单元句柄按拍子写：

```kotlin
sources[0].beginSearch()
linkOf(0).draw(over = latency)
advance(latency)
sources[0].settle()
chipsOf(0).forEach { it.appear() }
```

所有指令都落在当前时刻 `now` 上，时间只由 `advance()` / `at()` 推动。
轨道写入是**截断覆盖**的：在某个时刻打帧会丢掉它之后的所有帧。于是
「先安排好将来的动作，到时候再中途叫停」可以直接写出来——计时器起转时先按走满一圈排好，
真拦到了再在当时的位置把它钉住；被抢先命中的 cursor 同理。

## 选源规则

规则只有两条，实现在 [`SelectionEngine`](src/commonMain/kotlin/Selection.kt)，剧本不重复判断：

1. cursor 遍历到**候选结果**就选它；
2. 已经选过一个就不再选。

于是「谁先走到候选」就是唯一的胜负判据——赢家是**算出来的**，不是写死的。
高优先级门是这两条之上的一层闸：门开之前所有 cursor 都不许起步。

## 没有硬编码的时间

两个计时器的表盘一整圈就是配置里填的预算，指针停在哪由 `已用 / 预算` 得出：

```kotlin
config.interceptStopFraction()                       // 拦截成功时指针停在哪 (0..1)
config.budgetForInterceptStopFraction(7.5f / 12f)    // 想停在钟面 7 点半, 预算该填多少
```

高优先级计时器同理，停的位置直接由高优先级源的 `latency` 决定。

## 三个开关

| 开关 | ViewModel | 影响 |
|------|-----------|------|
| 抢先选源 | `setEagerSelect()` | 第二步：一个全局 cursor ↔ 每个源各起一个 |
| 最大等待高优先级源的时长 | `setPriorityWait()` / `setPriorityWaitSeconds()` | 第一/二步：加一道闸 + 连演「等到了」「等超时」两条路径 |
| 拦截播放链接的特殊动画 | `setResolveDemo()` / `setInterceptBudgetSeconds()` | 第三步：只演成功 ↔ 连演成功 / 超时 / 换下一个候选再成功 |

三个开关组合出的八条路径不是八份脚本——它们是同一份剧本在不同配置下编译出的八条时间线。

## UI 侧接法

```kotlin
val vm = viewModel { SelectorWorkflowViewModel() }
LaunchedEffect(Unit) { while (true) withFrameNanos { vm.onFrame(it) } }
Canvas(Modifier.fillMaxWidth().aspectRatio(254f / 86f)) {
    drawWorkflow(vm.state)   // 待实现
}
```
