# Fork 维护指南 (Android TV)

本仓库是 [open-ani/animeko](https://github.com/open-ani/animeko) 的 Android TV fork。
本文档记录 fork 与上游的全部差异及其原因,供 rebase 上游时逐条核对。**每次 rebase 或新增改动后请更新本文档。**

## 一、架构总览

TV 适配已经做成**独立于其他平台的构建目标**,三层隔离:

| 层 | 载体 | 效果 |
|---|---|---|
| 构建变体 | `formFactor` flavor 维度 (`phone` / `tv`) | 两个独立 APK, TV 清单/入口/代码不进手机包 |
| 界面模块 | `:app:shared:ui-tv` (只有 android target) | 桌面端与 iOS 完全不编译 TV 界面 |
| 共享代码 | `AniUiBehavior` + 变体插槽 | 共享层**零处**设备判断 |

`Platform.isTv()` / `LocalIsAndroidTV` 已删除。共享代码里现在搜不到任何 TV 判定,
唯一例外见文末「已知遗留」。

隔离效果实测 (解析 APK 的 dex `class_defs` 表, 只数**已定义**的类):

| | 类总数 | TV 相关已定义类 |
|---|---|---|
| phone universal APK | 74580 | 2 (只有 no-op 接缝) |
| tv universal APK | 75445 | 814 |

phone 变体的合并清单里 `LEANBACK_LAUNCHER` / `android:banner` / `AniDreamService` /
`WRITE_EPG_DATA` / `software.leanback` 出现次数均为 0。

## 二、表达设备差异的三种机制

写新的 TV 适配时**按顺序**选择机制,越靠前越好:

### 1. 行为开关 `AniUiBehavior` (首选)

`app-platform .../ui/foundation/AniUiBehavior.kt`,12 个字段的不可变数据类,
经 `LocalAniUiBehavior` 下发。共享代码读的是**能力**而不是**产品**:
`focusDrivenNavigation`(方向键移动焦点/焦点恢复/弹窗初始焦点)、`showBackNavigationButton`、
`immersiveShell`、`panelsAsCenteredDialogs`、`supportsWindowedPlayback` 等。

**禁止**新增 `isTv` 之类的产品判断字段 —— 字段名必须描述交互能力,这样上游看到
`showBackNavigationButton = false` 就知道是「这个形态没有返回按钮」,而不是「这是电视」。

取值只有两处:`app/android/src/phone/kotlin/FormFactorSetup.kt` 给 `AniUiBehavior.Default`,
`src/tv` 给 `ui-tv` 里的 `TvAniUiBehavior`。

### 2. 通用命名的共享组件

很多「TV 组件」其实不含遥控器逻辑(居中半透明面板、卡片网格弹窗、10 英尺布局参数),
它们属于共享设计系统,应当用中性名字放在共享模块里,由行为开关驱动:

| 原 TV 名字 | 现在 |
|---|---|
| `TvMediaSelectorDialog` | `ui-foundation .../widgets/AniCenteredPanelDialog.kt` |
| `TvDetailsDialogs.kt` | `ui-subject .../sections/CenteredDetailsDialogs.kt` |
| `TvInputModifiers.kt` | `ui-foundation .../AniClickable.kt` (`aniClickable` 等) |
| `TvFocusHelpers.kt` / `TvFocusResolver.kt` / `TvGridFocus.kt` | `ui-foundation .../focus/` (`FocusHelpers` / `FocusResolver` / `GridFocus`) |
| `TvMediaSelector.kt` | `ui-mediaselect .../FocusMediaSelector.kt` |
| `TvEpisodesSection.kt` | `ui-subject .../sections/FocusEpisodesSection.kt` |
| `TvSortMediaSourceList.kt` | `ui-settings .../source/FocusSortMediaSourceList.kt` |
| `SubjectDetailsLayoutParams.Tv` | `.TenFoot` (10-foot UI 是行业术语) |
| `AniThemeDefaults.tvPageBackgroundColor` | `shellBackgroundColor` |
| `NavigationMotionScheme.calculateTv` | `calculateCrossfade` |

`Focus` 前缀 = 焦点驱动的变体,不等于电视专属(桌面键盘导航同样受益)。

### 3. 变体插槽 (整页替换才用)

整页布局差异太大时,共享页面暴露一个 `fun interface XxxVariant` + `staticCompositionLocalOf<XxxVariant?>`,
`null`(默认)走原布局。目前 6 个:

| 插槽 | 位置 |
|---|---|
| `MainScreenShellVariant` | `app:shared /ui/main/` |
| `EpisodeScreenVariant` | `app:shared /ui/subject/episode/` |
| `ExplorationPageVariant` | `ui-exploration /ui/exploration/` |
| `SearchPageVariant` | `ui-exploration /ui/exploration/search/` |
| `CollectionPageVariant` | `ui-subject /ui/subject/collection/` |
| `SubjectDetailsPageVariant` | `ui-subject /ui/subject/details/` |

注入点唯一:`app/android/src/tv/kotlin/tv/TvPageVariants.kt`。
注意 `@Composable` 函数不能用 `::foo` 函数引用,必须写成显式 SAM lambda。

**依赖方向由构建系统物理保证**:`ui-tv` 只有 android target,任何多 target 模块的
`commonMain` 都无法依赖它,所以共享代码不可能反向引用 TV 页面。

## 三、焦点动线的铁律

以下每条都对应一个真机上出现过、且**静态审查看不出来**的 bug。写新的焦点逻辑前先过一遍。

### 1. 进页/返回恢复焦点不能挂 `LaunchedEffect(Unit)`

主页的三个 tab (探索/追番/播放历史) 从详情页快速返回时,整棵子树可能一直没被销毁
(TV 导航用 crossfade 过渡),`LaunchedEffect(Unit)` 不会重跑 —— **没有任何人发起恢复**。
而网格项在离开期间被销毁,焦点随之悬空,表现为返回后看不到焦点圈、按方向键才落到首项。

用 `lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED)`:生命周期信号与组合是否存活无关。
见 `TvCollectionPage` 里 `resumedBefore` 那段。独立导航目的地 (搜索页/详情页) 返回时会真的
重建,暂时不受影响,但不要依赖这一点。

### 2. 方向键在边界必须消费

按键处理器返回 `false` 会把事件交给默认方向搜索。在焦点组边界上、该方向没有候选时,搜索会
退出并重进焦点组,被外层 `focusProperties { onEnter }` 送到**首个可聚焦元素**。追番页末标签
按右因此绕回第一个标签,按住不放就无限循环。

规则:边界上返回 `true` (消费掉),组内还有候选才返回 `false`。反例是「行首按左」—— 那里
**要**不消费,让焦点落到侧边栏。

### 3. 跟随焦点的状态变更只认用户输入

「聚焦即选中」这类状态变更必须能区分「用户按键移来的焦点」与「焦点系统自行分配的焦点」——
二者在 `onFocusChanged` 里完全一样。焦点悬空时系统会把默认焦点塞给第一个可聚焦元素,
于是从卡片进详情页再返回会把选中 tab 改成第一个。

做法:左右键分支置一个 armed 标志,`onFocusChanged` 只在标志置位时才改状态并清除
(`TvCollectionPage.selectByFocusArmed`)。**靠时序门挡不住** —— `gridFocus.pending == null`
`!restorePending` 这类条件在快速返回时早已放开。

### 4. 落点轮询要有用户介入出口

`resolveFocusRepeatedly` / `GridFocusController.resolve` 在目标始终不到位时会烧满 attempts 轮,
**每轮发一遍 `requestFocus`**(最长 1~2 秒),用户此刻按遥控器会被下一轮抢回去。

出口:`GridFocusController.onUserNavigation()` 由按键 modifier 递增计数,解析发现计数变了立即
放弃;`resolveFocusRepeatedly` 的 `abandon` 参数同理。判据**只认按键**,不认「焦点落在别处」
—— 后者与系统分配的默认焦点无从区分,误判会让进页恢复半途放弃。

轮询本身的 `attempts` / `delayMillis` 不要调:那些数字是真机调出来的,调小会换回焦点卡死。

### 5. 整行/整列要给显式落点

全宽的行 (播放器进度条) 上下键交给空间搜索会落到**行中间**的元素。必须用
`focusProperties { up = ...; down = ... }` 指向目标行的首个元素;指向顺序常量的首项
(`TV_PILL_VISUAL_ORDER.first()`) 而不是写死符号,以后调顺序不会漏改。

### 6. 条件 modifier 尽量避开焦点节点之前

`.then(if (cond) Modifier.focusRequester(x) else Modifier)` 这类条件元素会让链上元素个数随
状态翻转。Compose 的节点链差异比对通常会复用后续节点,所以**多数情况无害**(实测不是上述任何
一个 bug 的成因),但它会让「谁挂着请求器」随时变化,排查时极难推理。能改成挂固定请求器
(`.focusRequester(if (cond) a else b)`,链结构恒定) 就改。

### 7. 按焦点分层的按键逻辑要记忆层级

「焦点不在页面任何地方」是一个**独立状态**,不能和「焦点在别的层」合并。凡页面上有 `Dialog` /
`Popup`(独立窗口,开着期间宿主窗口没有任何元素持有焦点,关闭后归还还要好几帧),现读
`hasFocus` 判层级必然在这段空档里判错——详情页返回键三级分层曾把「关弹窗那一瞬」判成「焦点在
选集页下方」,于是把用户往下送回卡片,白吃一次按键;紧接着的一次返回又撞上滚动动画未归零,
在「海报页 ⇄ 卡片页」之间来回。

同一类缺陷也出现在**布尔化的"可用性"标志**上:播放器选集条原来只有
`episodeStripAvailable: Boolean`,把「还在加载」和「确认没有分集」合成了一个 false,于是图标行
下键在数据到达前直接跳去详情页(用户按下键的意思明明是看选集)。改成三态
`TvEpisodeStripState`,加载中记下意图、就绪后自动兑现。**凡"没有 X"与"X 还没到"会导致不同
行为的地方,都不能用布尔。**

做法:各区块 `onFocused` 上报,页面存一个 `backLevel` 状态(`SubjectDetailsTvPage`),
**只在焦点确实落在某区块时更新**,空档沿用旧值。同时 `BackHandler(enabled = ...)` 的判据
**不要读动画中的量**(`scrollState.value`)——它在按下后仍会保持几百毫秒的旧值,而焦点已经
走了,两个量凑出的组合现实中不存在。顺带:读 `scrollState.value` 还会让整个作用域每帧重组。

### 8. 一个区块「能不能用」不要挂在增量数据源上

播放器选集条原先从详情页那套 `SubjectDetailsState` 读分集列表,于是「能不能选集」被绑在了
整套详情状态的组装上。真机分段计时:那套东西首次就绪耗时波动在 **88ms ~ 2.7 秒**,且**与条目
是否已在本地缓存无关**(同一条目冷 516ms / 热 806ms)——不是取数据慢,是这些协程在起播这一刻
跟种子引擎、解码器一起挤 `Dispatchers.Default`。表现就是唤出控制层后立刻按下键,选集条迟迟
不出来。

改成读播放器自己那条 `EpisodeViewModel.episodeListUiStateFlow`(播放会话的 info bundle,
**起播的必经之路**,没它连播哪一集都不知道),实测进屏后 **1~13ms** 就到,选集条在第一次组合
就报可用。TMDB 剧照/时长/简介仍走详情状态——它们没到只是卡片暂时无图,**不该反过来卡住整个
区块的可用性**。选数据源时先问一句:这个区块的可用性判据,是否落在了某条「可有可无」的流上。

同一次排查里的另一个坑:`stateIn(scope, WhileSubscribed(5000), ...)` 的惰性流,**如果唯一读它
的组件平时不组合(藏在 `AnimatedVisibility` 里),上游就一直没启动**。实测控制层隔 6.4 秒才
唤出的那次,数据早在 +0.8s 就齐了,这条流却一直等到 +6.49s 才发出第一个真值,白等 5.7 秒。
需要它跟着页面一起预热时,在进屏处挂一个空收集者(`collect { }`)即可。

## 四、构建

```
./gradlew :app:android:assembleDefaultTvDebug        # TV 调试包
./gradlew :app:android:assembleDefaultPhoneDebug     # 手机调试包
./gradlew :app:android:compileDefaultTvDebugKotlin
./gradlew :app:android:compileDefaultPhoneDebugKotlin   # 上游合并面, 必须一起编过
```

产物:`app/android/build/outputs/apk/defaultTv/debug/android-default-tv-<abi>-debug.apk`。

包名:`applicationId` 保持上游的 `me.him188.ani`,tv flavor 靠 `applicationIdSuffix = ".tv"`
得到 `me.him188.ani.tv`(debug 为 `me.him188.ani.tv.debug2`),与拆分前的 fork 版本一致,可原地升级。

**`assembleDefaultDebug` / `compileDefaultDebugKotlin` 这些名字已经不存在**(Gradle 会报
ambiguous),CI 与文档都必须写全变体名。

## 五、fork 新增文件 (rebase 零冲突)

### TV 专属 (上游合并时整体保留)

| 文件 | 用途 |
|---|---|
| `app/shared/ui-tv/build.gradle.kts` | 只有 android target 的界面模块; 故意不套 `ani-mpp-lib-targets` |
| `ui-tv .../TvAniUiBehavior.kt` | TV 的 `AniUiBehavior` 取值 |
| `ui-tv .../ui/main/TvMainScreenLayout.kt` | TV 主页外壳 (侧边栏 + 内容区 focusGroup) |
| `ui-tv .../ui/exploration/TvExplorationPage.kt` | 沉浸式探索页 (hero 背景 + 轮播) |
| `ui-tv .../ui/exploration/search/TvSearchPage.kt` | TV 搜索页 |
| `ui-tv .../ui/subject/collection/TvCollectionPage.kt` | TV 追番页 |
| `ui-tv .../ui/subject/details/layout/SubjectDetailsTvPage.kt` | TV 详情页 (全屏背景 + 选集轮播); 含播放器内嵌变体 |
| `ui-tv .../ui/subject/episode/tv/` (TvEpisodeScreen / TvPlayerOverlayState / TvPlayerControls / TvPlayerPanels / TvPlayerEpisodeStrip / TvPlayerDetailsOverlay / TvPlayerSideSheets / TvPlayerFrameCapture) | TV 播放器 (Prime 风格): 单一状态机 + 根部唯一按键路由, 胶囊按钮浮出面板, 图标行下键唤出选集条, 详情页覆盖层 (视频作背景), 暂停帧捕获 |
| `ui-tv .../ui/foundation/session/TvNavigationSideRail.kt` | 可展开侧边导航栏 (主页+详情页共用) |
| `ui-tv .../ui/foundation/tv/TvCards.kt` | TV 卡片 |
| `app/android/src/tv/AndroidManifest.xml` | leanback 入口 / banner / EPG 权限 / 屏保 service |
| `app/android/src/tv/kotlin/FormFactorSetup.kt` | tv 变体接缝: 行为开关 + 装插槽 + 主屏频道初始化 |
| `app/android/src/phone/kotlin/FormFactorSetup.kt` | phone 变体接缝 (全部 no-op) |
| `app/android/src/tv/kotlin/tv/TvPageVariants.kt` | 把 TV 页面塞进 6 个变体插槽 |
| `app/android/src/tv/kotlin/tv/AniDreamService.kt` | TV 屏保 (Daydream) |
| `app/android/src/tv/kotlin/tv/TvHomeChannels.kt` | 主屏预览频道 / 继续观看 |
| `app/android/src/tv/res/drawable/tv_banner.xml` | 主屏图标 banner |

### 共享层新增 (通用能力, 手机/桌面同样使用)

| 文件 | 用途 |
|---|---|
| `app-platform .../ui/foundation/AniUiBehavior.kt` | 界面行为开关 |
| `app-platform .../navigation/MainPageRequest.kt` | `requestMainPage`: 弹回主页时经 SavedStateHandle 切 tab |
| `ui-foundation .../AniClickable.kt` | `aniClickable` / `aniCombinedClickable` (长按时长常量) |
| `ui-foundation .../FocusConstants.kt` | 焦点相关共享常量 |
| `ui-foundation .../focus/FocusHelpers.kt` `FocusResolver.kt` `GridFocus.kt` | 默认焦点容器、焦点解析重试循环、海报网格落点协调器 |
| `ui-foundation .../PlayerFrameHolder.kt` | 播放器暂停帧跨导航传递 (一次性消费) |
| `ui-foundation .../widgets/AniCenteredPanelDialog.kt` | 居中半透明大面板 |
| `ui-foundation .../widgets/AniScrollableTextDialog.kt` | 纯文字滚动弹窗 (详情页简介「显示更多」) |
| `ui-foundation .../widgets/AniBottomSheetDefaults.kt` | bottom sheet 统一样式 |
| `ui-mediaselect .../FocusMediaSelector.kt` | 焦点驱动的数据源选择列表 |
| `ui-settings .../source/FocusSortMediaSourceList.kt` | 焦点驱动的数据源排序 |
| `ui-subject .../sections/CenteredDetailsDialogs.kt` | 卡片网格弹窗 / 评论卡片弹窗 / 聚焦高亮卡 |
| `ui-subject .../sections/FocusEpisodesSection.kt` | 选集轮播/网格卡片 (从 `EpisodesSection` 拆出) |
| `ui-cache .../ForcedDarkTheme.kt` | 播放器内强制深色 |
| 6 个 `*Variant.kt` | 见上表 |

### 数据层新增

| 文件 | 用途 |
|---|---|
| `app-data .../network/TmdbImageService.kt` `TmdbEpisodeMatcher.kt` `StaleRefreshGate.kt` | TMDB 横版背景图/剧集缩略图 + DataStore 缓存 |
| `app-data .../network/BangumiSummaryService.kt` | bgm.tv 直连简介兜底 (Ani 服务器无数据时) |
| `app-data .../torrent/service/TorrentDiagnosticsServer.kt` | debug 构建 localhost 种子诊断端口 |
| `app-data .../mediasource/ChineseConverter.kt` (+各平台 actual) | 简繁转换 (中文条目匹配修复的一部分) |
| `app-data schemas/.../22.json` | 数据库迁移 (种子按集存文件选择) |

### CI

| 文件 | 用途 |
|---|---|
| `.github/workflows/fork-release.yml` | fork 专属 Android release 流程 (与上游 `release.yml` 隔离) |
| `.github/workflows/notify-mirror.yml` | release 发布后通知镜像仓库 |

## 六、修改的上游文件及原因

### 构建 / CI

| 文件 | 原因 |
|---|---|
| `settings.gradle.kts` | include `:app:shared:ui-tv` |
| `gradle/libs.versions.toml` | `androidx-tvprovider` 等依赖 |
| `app/android/build.gradle.kts` | `formFactor` flavor 维度 + `tvImplementation`(约 +21 行) |
| `app/shared/app-platform/build.gradle.kts` | `ani.tmdb.api.token` BuildConfig 字段 |
| `.github/workflows/build.yml` | fork 上禁掉部分上游 job (`if: false`)、删掉 YAML 一致性检查、编译任务改全变体名 |
| `ci-helper/build.gradle.kts` / `buildSrc .../ciHelperTasks.kt` | 上传目录改 `defaultTv`; 剥掉文件名里的 `tv-` 段以保持 release 资产名不变 |
| `ci-helper/release-template.md` / `README.md` | fork 下载链接与 TV 说明 |

`.github/workflows/release.yml` 与上游保持一致不动(它的 `create-release` 有仓库判断,
fork 上整条流水线是 skipped)。

### 主壳 / 导航 / 主题

| 文件 | 原因 |
|---|---|
| `activity/MainActivity.kt` | 仅 12 行接缝: `formFactorUiBehavior` / `InstallFormFactorUi` / `onFormFactorActivityCreated` |
| `ui/main/MainScreen.kt` | 外壳变体插槽; 双击导航滚顶; 顶栏显隐读行为开关 |
| `ui/main/AniAppContent.kt` | 焦点兜底循环 (`focusDrivenNavigation`); 观察 `MAIN_REQUESTED_PAGE_KEY` 切 tab |
| `ui/main/AniApp.kt` | `uiBehavior` 参数; 拦截未消费 BACK 键 (框架会映射成 `FocusDirection.Exit` 抢焦点) |
| `navigation/AniNavigator.jvm.kt` / `.ios.kt` | `popBackOrNavigateToMain` 弹回前调 `requestMainPage` |
| `ui/foundation/theme/AppTheme.kt` | `shellBackgroundColor`; 焦点指示 (`LocalIndication`) |
| `animation/AniMotionScheme.kt` / `NavigationMotionScheme.kt` | 交叉淡入动效 (`calculateCrossfade`) |
| `platform/AniBuildConfig.kt` | `tmdbApiToken` 配置项 |
| `platform/CommonKoinModule.kt` | 注册 TmdbImageService / BangumiSummaryService; 数据库迁移 |
| `data/persistent/SettingsStore.kt` | tmdbImageCacheStore |
| `data/models/preference/ThemeSettings.kt`、`ui/settings/tabs/theme/ThemePreferences.kt` | 沉浸式探索页开关 |

### 焦点/按键散点适配 (改动小、模式统一)

`ExplorationScreen` `ScheduleScreen` `SearchFilter` `SearchPage` `SearchPageResultColumn`
`SubjectPreviewItem` `SuggestionSearchBarState` `TrendingSubjectsCarousel` `CollectionPage`
`SubjectCollectionsColumn` `EditCollectionTypeDropDown` `EditableSubjectCollectionTypeButton`
`SubjectCollectionTypeButton` `SubjectProgressButton` `EpisodeListItem` `PeopleDetailsPage`
`PeoplePreview` `RelatedSubjectsRow` `SubjectDetailsSections` `SubjectPeopleSections`
`EpisodesSection` `CommentColumn` `SliderItem` `SorterState` `AppSettingsTab` `ProfilePopup`
`ProfilePopupLayout` `SettingsScreen` `WelcomeScreen` `OnboardingScreen` `TopAppBarGoBackButton`
`MediaSelectorFilters` `MediaSelectorItem` `MediaSelectorView` `MediaSourceResultsView`
`CacheManagementScreen` `CacheGroupDetailsPage` `EpisodeGrid` `PaginatedEpisodeList`
`EpisodeListSection` `EpisodeDetails`
— 均为: 可聚焦化、D-pad 键处理、读行为开关调整顶栏/形状/背景、焦点恢复。逐个 hook 很小;
冲突时按语义重加。**收敛已有内联逻辑时禁止「顺手统一」细微语义**(`onPreviewKeyEvent` vs
`onKeyEvent`、KeyDown vs KeyUp 等差异都是调试出来的),改完必须真机验证对应页面。

### 行内语义性修改 (rebase 冲突时需重点理解)

| 文件 | 原因 |
|---|---|
| `ui/subject/episode/EpisodePage.kt` | 仅一处 `EpisodeScreenVariant` 插槽 (TV 播放器全在 `ui-tv`) |
| `ui/subject/episode/EpisodeViewModel.kt` | 新增 `episodeListUiStateFlow` (播放会话 info bundle → `EpisodeListUiState`), 供 TV 选集条脱离详情状态取分集列表, 见铁律 8 |
| `video-player .../PlayerControllerBar.kt` | `OptionsSwitcher` 下拉的焦点支持 (popup focusable + 首项自动聚焦 + 返回键关闭 + 聚焦高亮) |
| `video-player .../SubtitleSwitcher.kt` | 功能性: 用户字幕选择记忆+重放; 另 `onExpandedChanged` 透传 + popup focusable |
| `video-player .../AudioSwitcher.kt` `VideoSideSheets.kt` | popup focusable 一行; `close()` 关闭方法 (TV 根路由返回键用) |
| `EpisodeVideoSideSheet` `EpisodeVideoSettings` `EpisodeSelectorSideSheet` | 侧边抽屉焦点导航; `EpisodeSelectorState` 加 `selectPrev`/`hasPrevEpisode` (选集抽屉本身已不用, 但状态类在用) |
| `episode/details/DanmakuListSection.kt` `EpisodeDetails.kt` `DanmakuMatchInfoGrid.kt` | `DanmakuSourceChips`/`DanmakuTimeShiftDialog`/`renderDanmakuServiceId` 改 public 供 TV 弹幕面板复用; 时移对话框按键适配 |
| `ui-cache .../SubjectCacheScene.kt` | 从播放器进入时用暂停帧 + 半透明遮罩作背景 (`PlayerFrameHolder` 一次性消费) |
| `ui-cache .../CacheListGroup.kt` | `EpisodeCacheActionIcon` 重写: 节点稳定 + 事件驱动焦点夺回; `panelsAsCenteredDialogs` 时底部抽屉改居中大弹窗 |
| `sections/ViewAllSheet.kt` `SubjectReviewsSection.kt` `person/PeopleDetailsSections.kt` | `panelsAsCenteredDialogs` 时 ModalBottomSheet 改居中卡片网格弹窗 (手机/桌面路径原样) |
| `person/PeoplePreview.kt` `PeopleDetailsPage.kt` | 预览由贴边窗改居中大弹窗、初始焦点给「打开完整页面」; 顶部内容聚焦时滚动归零 |
| `ui-settings .../source/MediaSourceGroup.kt` | item/三点按钮完全接管确认键 (KeyUp 派发, 长按进排序); 排序模式切换 |
| `ui-subject .../details/*` (Page/State/StateFactory/StateLoader/MultiColumnPage/LayoutParams) | TMDB/bgm 简介兜底链、背景图、`MultiColumnScaffold` 等改 public 供 TV 页复用; `videoBackground`/`onClickCacheOverride` 透传 + `containerColor` 参数化 |
| `ui-settings .../update/UpdateChecker.kt` `UpdateNotifierHost.kt` `AppUpdateViewModel.kt` `NewVersionDialog.kt` | 更新源指向本 fork release; `autoInstallUpdates` 行为 |
| `tools/update/AndroidUpdateInstaller.kt` | FileProvider 安装 APK |
| `app-data .../captcha/CaptchaBrowser.kt` `InteractiveSolveDialog.kt` `WebViewCaptchaBrowser.kt` (+desktop/test 签名同步) | 验证码虚拟光标: `View(onExitRequest, onConfirmRequest)` 默认参数扩展 |
| `app-lang` / `app/shared` `strings.xml` | 新增文案 |

### 平台无关修复 (上游 PR 候选 — 合并后从本表删除)

| 文件 | 修复 |
|---|---|
| `TorrentCacheInfoDao.kt` (+Dao 测试) `AniDatabase.kt` `TorrentMediaCacheEngine.kt` `TorrentMediaResolver.kt` (+SelectVideoFile 测试) | 种子按集存储文件选择,修多集合集选错文件/S00 特别篇撞集号 |
| `AbstractMediaCacheEngine.kt` `TorrentMediaCacheStorage.kt` | 缓存列表: 删除被并发刷新悄悄撤销、启动时已完成缓存不显示 |
| `GetSubjectRecommendationFlowUseCase.kt` | 过滤服务器注入的广告条目 |
| `SubjectCollectionRepository.kt` `MediaListFilters.kt` `LabelFirstRawTitleParser.kt` (+测试) + ChineseConverter | 中文条目名/别名匹配、标题解析 |
| `BaseJellyfinMediaSource.kt` | Jellyfin 外挂字幕流 |
| `AniTorrentService.kt` `TorrentServiceConnectionManager.kt` | 种子服务修复/诊断 |

## 七、Rebase 工作流

`main` 的历史前缀**就是** `android-tv` 分支(上游 PR 分支),`git log android-tv..main`
即 fork 专属提交。因此 rebase 上游分两步:

```
git rebase --onto <新上游> <旧上游> android-tv     # 先把纯 TV 适配挪上去
git rebase --onto android-tv <android-tv 旧 tip> main   # 再replay fork 专属提交
```

- `git rerere` 已启用 (`rerere.enabled` + `rerere.autoupdate`),重复冲突自动重放上次解法。
- fork 提交按功能归组;新改动用 `git commit --fixup <hash>` +
  `GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash <hash>^`。
- 上游整文件重构(如 captcha 重写、页面 redesign)时,不要硬合文本: 先接受上游版本,
  再按本文档记录的「改动原因」在新架构上重新实现。
- rebase 后验证: `compileDefaultTvDebugKotlin` **和** `compileDefaultPhoneDebugKotlin` 都要过
  (phone 编不过通常意味着 TV 代码漏进了共享层),再按下节分区在 TV 真机抽测。
- **绝不提交** `release.keystore`、`*.jks`、录屏 mp4、截图 png。这些已写进 `.git/info/exclude`,
  但 `git add -A` 前仍应看一眼 `git status`。

## 八、TV 真机验证分区 (rebase 后抽测)

1. 主页: 侧边栏展开/收起、切 tab、返回键回探索页、头像动作
1b. 追番页焦点 (回归面最大): 各标签选**靠后需要滚动**的卡进详情页再返回 (分「正常速度」
   与「按下确认立刻按返回」两种), 应回到原卡且不切标签; 按住左右键到边界应停住不循环;
   标签上下键进出网格; 数据还没加载出来时快速按方向键 (焦点应归用户, 不被拽回)
2. 探索页: hero 轮播、卡片导航、按返回退出
3. 详情页: 选集轮播/网格弹层、简介兜底 (找一个 Ani 服务器无简介的条目)、侧边栏遮罩
4. 播放器 (Prime 风格): 确认键播放暂停并唤出控制层、上下键仅唤出、左右键快进退;
   胶囊按钮浮出弹幕列表/推荐/评论面板 (条目吸底, 下键回按钮, 返回回进度条);
   图标行下键进详情页覆盖层 (视频背景, 选集切换当前集, 返回回纯视频);
   弹幕发送展开框、倍速/比例/字幕下拉、选集/弹幕设置/数据源侧边抽屉、OP/ED 跳过、
   三个点→缓存页 (暂停帧背景)、5 秒自动隐藏 (暂停时常驻)
5. 缓存页: 列表操作按钮焦点不丢、多选工具栏、删除后不复活
6. 设置: 滑条上下键离开、数据源长按排序、验证码 WebView 虚拟光标
7. 更新: 检查更新指向 fork release、下载安装
8. 系统级 (改过 flavor 清单后必测): 主屏预览频道、屏保、leanback 启动器图标

## 九、已知遗留

- `app-data .../captcha/WebViewCaptchaBrowser.kt` 仍直接问系统 `UiModeManager` 判断遥控器:
  对话框宿主挂在 `AniApp` 的 overlay 槽位,拿不到 `LocalAniUiBehavior`。Android-only 且自洽,
  改动前需要能复现验证码流程。
- TMDB 背景图依赖 `ani.tmdb.api.token`(local.properties / CI secret),上游若合并需要自备
  token,否则详情页无横版背景与剧集缩略图。
- `.github/workflows/build.yml` 是上游 `src.main.kts` 生成的,fork 直接改的是生成产物
  (上游一致性检查 job 已删)。改 CI 时注意两边会漂。
