# :utils:selector-workflow

「数据源选择流程」示意动画：数据逻辑层 + Compose Canvas 绘制层。

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
        ▲                    ╲
TimelinePlayer / ViewModel      ╲  drawSelectorWorkflow(state, layout, palette)
   ← 把帧时刻变成播放位置          ▼
                          Compose Canvas
```

绘制层同样是三块正交的东西，都不知道彼此的细节：

| | 职责 | 不知道 |
|---|---|---|
| `draw/Layout.kt` | 几何：由 config + metrics 排出所有矩形与圆心 | 颜色、状态、时间 |
| `draw/Palette.kt` | 语义色 → M3 token | 几何、状态 |
| `draw/Painters.kt` | 每个单元一支画笔 | 别的单元、播放进度 |

`draw/DrawWorkflow.kt` 只做两件事：把虚拟画布缩放居中到实际尺寸，然后按 z 序把画笔叫一遍。

关键性质：**时间线是纯函数**。同一个 `Duration` 采出来永远是同一份状态，所以拖进度条、
定格截图、单元测试都能直接用，也不需要在 Canvas 里保存任何动画状态。

## 九种基础可控制单元

| # | 单元 | 出现在 | 可动属性 |
|---|------|--------|----------|
| 1 | `SourceNodeState` | 第一步 | `alpha`、`pulsing` |
| 2 | `LineState` | 第一步的连线 / 第二步末尾的交棒线 | `progress`、`alpha` |
| 3 | `ResultChipState` | 第二步 | `alpha`、`tone`、`scale` |
| 4 | `RippleState` | 第二步选中 / 第三步命中 | `scale`、`alpha`（`target` 决定锚在结果块还是请求行） |
| 5 | `CursorState` | 第二步 | `cell`（浮点，可插值）、`alpha` |
| 6 | `ClockState` | 第一/二步、第三步各一个 | `sweep`、`alpha`、`tone`、`overlayAlpha` |
| 7 | `WindowState` | 第三步 | `tone` |
| 8 | `RequestRowState` | 第三步 | `alpha`、`icon`、`tone`、`iconScale` |
| 9 | `ScrollState` | 第三步 | `rowOffset` |

结果容器边框、mac 三圆点、地址栏是静态装饰，不算可控制单元。
候选圆点与高优先级菱形是**静态标记**（`candidate` / `priority` 两个布尔），不单独成为单元。

高优先级菱形画在源节点里和它每一条结果上，但**只有真开了高优先级等待才画**
（`SelectorWorkflowConfig.showPriorityMarks`）——没开闸时这个源和别的源没有任何区别，
标出来只会让人以为它有特殊待遇。候选圆点不受这个开关影响，它是选源规则的一部分。

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

## 两个计时器：转多久 与 数到几 是两件事

**转多久**是演出参数 `Pacing.clockSweep`，两个表共用；改它才会改变动画长度。

**数到几**是设置项配的秒数（`SelectionSpec.priorityWait` / `ResolveSpec.budget`），
只决定表旁边那个读数从 0 数到几，一位小数。于是「最大等待 20 秒」和「5 秒」放出来一样长，
差别只在读数最后停在 20.0 还是 5.0——读数因此也成了辨认「这个表在数哪个设置项」的标识。

指针停在哪由 `已用 / 一圈` 得出，全是动画时间：

```kotlin
config.interceptStopFraction()                    // 拦截成功时指针停在哪 (0..1)
config.clockSweepForInterceptStop(7.5f / 12f)     // 想停在钟面 7 点半, 一圈该多长
```

读数 = `sweep × budgetSeconds`，所以指针和读数永远是同一个比例的两种说法。

## 三个开关

| 开关 | ViewModel | 影响 |
|------|-----------|------|
| 抢先选源 | `setEagerSelect()` | 第二步：一个全局 cursor ↔ 每个源各起一个 |
| 最大等待高优先级源的时长 | `setPriorityWait()` / `setPriorityWaitSeconds()` | 第一/二步：加一道闸 + 连演「等到了」「等超时」两条路径 |
| 拦截播放链接的特殊动画 | `setResolveDemo()` / `setInterceptBudgetSeconds()` | 第三步：只演成功、**不显示计时器** ↔ 显示计时器并连演成功 / 超时 / 换下一个候选再成功 |

两个 `setXxxSeconds` 只改读数，不改动画长度。

两个计时器都只在自己那个开关打开时才出现（`showInterceptClock` / `pass.gate != null`）——
不演超时就没有"时限在跑"这回事，摆个表反而误导。关掉计时器不会让那一步缩水：
第三步照样请求进场、滚动、命中，只是少了表。

三个开关组合出的八条路径不是八份脚本——它们是同一份剧本在不同配置下编译出的八条时间线。

## 绘制层

### 几何是算出来的

`WorkflowLayout.of(config, metrics)` 是纯函数：结果块尺寸、容器大小、节点位置、窗口、
标题栏三件套、请求行、两个表盘，全部由 `WorkflowMetrics`（一组虚拟单位的比例常量）和
config 推出来。加一个源、多两条结果、换成三列网格，画面自动重排，绘制代码一行不用动。

几个自己会照顾自己的约束：

- 数据源节点以结果容器的竖直中心为轴等距排开；
- 连线终点夹到容器左边的**直边**上，源多到探出容器时也不会落在圆角里；
- 地址栏从 caption button 之后一直占满到内容区右缘；
- 第三步的计时器是浮在内容区右上角的一块 overlay：圆角矩形 + 描边，自带一层底把身下的请求条挡掉，
  离右缘和离顶缘一样远；里面 `[表][读数]`，**宽度按当前这一帧的读数算**——`9.9s` 到 `10.0s` 多一位，
  浮层就跟着往左长一个字符。读数只有数字、小数点和 `s`，宽度基本固定，按字符数估就够准，
  不用把 `TextMeasurer` 塞进布局层。

  所以 `interceptOverlay(readoutSeconds)` 是个函数而不是常量——布局里定的只有它的右上角。

这些都有单测（`WorkflowLayoutTest`）盯着。

### 坐标系

布局算在一个虚拟画布里（默认约 254×86），`drawSelectorWorkflow` 用一次 `scale` + `translate`
把它居中铺到实际尺寸。线宽、圆角跟着一起缩放，所以放到任何尺寸都是同一张矢量图，不会糊。

### 颜色

状态层只给 `ChipTone` / `WindowTone` / `ClockTone` 这类语义枚举，到 `WorkflowPalette` 才落到
`MaterialTheme.colorScheme` 上，深浅色主题自动跟随。M3 基线里没有 success 角，按自定义色角补一个；
数据源颜色按下标在 primary / secondary / tertiary 里循环，源再多也不会没色可用。

不在 Composition 里（预览、截图、测试）用 `workflowPaletteOf(...)` 手搭一份。

#### 语义色是离散的，颜色不是

`Idle → Hit` 是一次跳变，可结果块转绿、表盘转红都不该"啪"地跳一下。所以带语义色的单元一律给三样：
`tone`（要去的色）、`previousTone`（从哪来）、`toneBlend`（0..1 的进度）；画笔把前两个各映射成颜色，
再按进度插值。状态层因此仍旧只谈语义，一个颜色都不碰。

剧本侧对应 `anim/ToneChannel`：`shift(to, at, over)` 排一次过渡，`snap(to, at)` 直接切
（只用于"这会儿它本来就看不见"的复位）。过渡走完 `previousTone` 会被拨回与 `tone` 一致——
有了这个不变式，`blend` 那条 float 轨道可以随便从上一帧插值过来，反正两端同色。

一条容易踩的：**同一个单元的各个部件必须共用一路 tone**。第三步命中行的图标和横条就是这样，
分开写会出现"图标先绿、横条后绿"。转绿还得和涟漪、弹跳同时起步——涟漪那圈绿环正好套在图标上，
晚一步就会被看成图标先变了色。`a_visible_tone_never_snaps_it_always_eases_in` 盯着这两条。

### 光环脉冲

数据源搜索中的光环是个 1.1s 的独立循环，和时间线无关，所以没有为它建轨道——
画笔直接从 `state.time` 取相位。仍然是"同一个时刻画出同一帧"。

## UI 侧接法

```kotlin
val vm = viewModel { SelectorWorkflowViewModel() }
SelectorWorkflowAnimation(vm, Modifier.fillMaxWidth())
```

想自己控制播放（暂停、拖进度、截某一帧）就用另一个重载：

```kotlin
SelectorWorkflowAnimation(
    state = timeline.sampleAt(2.seconds),
    config = config,
)
```

## Playground

`SelectorWorkflowPlayground` 把动画、设计稿上的三个开关、一条播放控制拼在一起，
带两个 `@Preview`：

- `PreviewSelectorWorkflowPlayground` — 整个 playground，预览时会先把播放位置拨到有内容的一帧
  （静态预览不跑 `LaunchedEffect`，停在 0 会是一张空画）；
- `PreviewSelectorWorkflowFrames` — 定格五个关键时刻并排，不用等动画跑到那里。

三个开关和最终要放进设置页的是同一套语义，但这里是只影响这个动画的演示开关；
真接进设置页时会换成读写用户设置的版本。播放控制那一条是 playground 专用，不会带进设置页。
