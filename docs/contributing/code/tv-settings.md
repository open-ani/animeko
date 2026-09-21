# TV 设置页

TV 设置位于 `ui-settings/src/androidTv`，使用共享设置仓库、弹幕规则格式、调色板和关于元数据。
界面采用全屏左侧分区、右侧详情，面向遥控器提供独立的编辑器和焦点路径。
设置由 `NavRoutes.Settings` 独立承载；主页侧栏的设置入口负责导航，退出后恢复原主页内容与入口焦点。

## 设置范围

| 分区 | 内容 |
|---|---|
| 界面 | 应用语言、NSFW 内容、搜索隐藏看过与抛弃条目 |
| 主题与色彩 | 共享调色板；TV 使用深色主题 |
| 播放器和弹幕过滤 | 播放行为、画质与倍速、弹幕与过滤、高级设置四个子页，覆盖共享 Android 播放设置与全部规则操作 |
| 数据源管理 | 订阅与数据源的启用开关；订阅开关联动所属数据源，不提供配置编辑 |
| 观看偏好 | 字幕与分辨率优先级、字幕组；高级设置包含选源流程动画、在线源选择、超时、缓存、验证码及非 BT 筛选项 |
| 关于 | 版本、发布说明、网站、反馈、源码、开发者、致谢、开源许可与交流群 |

## 职责与入口

- `TvSettingsViewModel` 合并设置、弹幕规则和数据源快照，写入共享仓库并提供失败反馈。设置更新函数作用于最新持久化值，保留其他字段。
- `TvSettingsRoute` 收集状态，处理剪贴板、显示模式、浏览器和结果提示。
- `TvSettingsScreen` 持有当前分区、活动面板、关于层级和编辑器，负责遥控器与焦点协调。
- `TvSettingsLayout` 通过 slot 组合 list、detail、extra。设置标题位于 list，详情直接铺在页面背景上，焦点切换不改变面板位置与宽度。播放器子菜单、观看高级设置和订阅列表使用 detail/extra，保留父菜单并隐藏顶级 list，符合共享端的 `ExtraPaneForNestedDetails` 布局规则。
  extra 打开时，左侧父菜单使用标题与当前值，省略说明段落，使当前入口在窄栏和大字体下保持可见。
- `TvSettingsSections`、`TvSettingsAbout` 将共享数据与文案映射为设置项；`TvSettingsEditor` 组合选项、输入、优先级排序、倍速范围、规则编辑与只读详情。复杂编辑器按职责拆分为相邻文件。
- `TvApplicationTheme` 在 TV Activity 根部应用持久化颜色和语言，更新 Compose 资源及原生输入上下文。
- `TvSearchViewModel` 随搜索设置变化重新生成结果，应用 NSFW 和收藏状态筛选；模糊模式使用共享图片模糊效果。

VM 由 `TvAniAppContent` 构造，依赖从 `TvAppDependencies` 传入。数据源开关使用 `MediaSourceManager.setEnabled`；订阅状态写入 `MediaSourceSubscriptionRepository`，同时批量更新所属数据源。单个数据源开关独立于订阅状态。禁用的订阅暂停更新；后台订阅差异应用与订阅开关通过订阅仓库事务串行，防止进行中的请求在禁用后添加数据源。
开源库 JSON 由应用层传入，许可合并逻辑和开发者元数据由共享端提供，许可列表按需组合。
语言选项使用 `app-lang` 的 `SupportedLocales` 与 `renderLocale`，手机、桌面和 TV 共用语言名称。设置、保存、高级设置与主页导航文案引用已有共享资源；只为缺少对应语义的文案定义资源。
调色板直接复用 `ThemePalette` 与 `ColorButton`，TV 只提供焦点锚点与持久化回调。`ColorButton` 用描边表示焦点，保留共享色盘与选中标记。
观看高级设置复用 `MediaSelectorWorkflowDemoState` 和 `MediaSelectorWorkflowPreview`，动画以 extra pane 内容区一半的宽度居中，保持在滚动设置项上方。快速选源、最长等待时间、解析超时及搜索缓存的操作驱动相应流程演示。

## 遥控器与焦点

1. 首次进入聚焦左侧分区；上下键预览对应详情。
2. 确认或右键进入详情，恢复该分区上次浏览的设置项，首次落在第一项；调色板落在当前配色。焦点反馈使用填充色、描边与文字对比，缩放固定为 `1f`。
3. 左键和返回键从 extra 回到 detail 的原入口，再从 detail 返回分区。调色板行内左右键在颜色间移动，各行最左侧颜色的左键返回分区。
4. 编辑器关闭后恢复原设置项；删除需二次确认，默认聚焦取消，确认删除后聚焦添加规则。播放器与关于子页逐层返回各自入口。
5. 优先级编辑器用复选框选择参与选源的项目。“调整优先级”进入排序列表，确认抓取项目，上下键移动，确认放置。移动中返回撤销本次移动，排序列表返回保留草稿并回到复选框；表单的保存提交选中项目及顺序，取消或返回丢弃草稿。
6. 许可证详情上下键滚动；到达边缘后可进入主页按钮。弹层阻止底层设置响应输入。
7. 关于外链展示可在手机扫描的二维码，默认聚焦“在电视上打开”；二维码保留静区并按整数像素绘制。

单选项和开关确认后立即应用，文本、规则、倍速范围与优先级表单通过“保存／取消”提交或丢弃草稿。
设置行区分主标题、说明、当前值、开关和详情入口箭头。分类使用焦点及背景状态，单选值使用单选按钮；对勾用于调色板的当前配色。重试与加载状态没有详情箭头。弹窗提交和取消使用按钮。
数据源和订阅行直接展示开关，确认即应用，不打开详情弹窗。

页面使用 `TvFocusScope`、稳定设置 ID 和焦点记忆。焦点请求由目标附着事件执行，用户后续导航会取消旧请求；不以动画时长推测送焦时机。
分区和关于层级的浏览位置保存在 `SaveableStateHolder` 中。

## 验证

`TvSettingsViewModelTest` 验证持久化、连续更新字段保留、倍速约束、正则校验、共享导入导出格式和加载失败重试，并覆盖订阅批量开关、独立数据源开关、写入失败以及下载中禁用订阅的竞争情况。
`TvSettingsUiTest` 使用 `runAniComposeUiTest` 与合成遥控器输入，从分类入口完整走通固定面板导航、extra 与父菜单、焦点记忆、语言切换、共享调色板、订阅与数据源开关、选源流程动画、规则保存与删除确认、排序及撤销、倍速草稿、关于层级、异步许可列表与文本滚动。导航不直接调用语义焦点请求。英文大字体截图检查布局，二维码截图通过解码检查链接内容。
`TvWatchingProgressThemeUiTest` 在同一组合实例内切换主题种子色，检查观看进度条重新着色且进度几何保持不变。
测试生成设置页 PNG；Android 的 `assertScreenshot` 当前没有基准比对实现，因此视觉验收需检查这些截图。
`TvSettingsNavigationUiTest` 在 Navigation 3 中验证主页设置入口、全屏设置、逐级返回及原主页内容与入口焦点恢复。
截图与二维码解码验收需要关闭模拟器的布局边界辅助线（`debug.layout=false`），避免调试叠层覆盖实际画面；验收后恢复原设置。

```shell
./gradlew :app:shared:ui-settings-tv:testAndroidHostTest
./gradlew :app:shared:ui-settings-tv:connectedAndroidDeviceTest
./gradlew :app:shared:tv:testAndroidHostTest
./gradlew :app:android:assembleDefaultDebug :app:android:assembleTvDebug
```

视觉与导航参考 [Android TV 列表规范](https://developer.android.com/design/ui/tv/guides/components/lists)、
[焦点规范](https://developer.android.com/design/ui/tv/guides/styles/focus-system)，并沿用项目的 TV options 与覆盖层。
排序的移动状态参考 [Android TV 行排序](https://support.google.com/androidtv/answer/6121336?hl=en) 与
[Google TV 应用排序](https://support.google.com/chromecast/answer/12390692?hl=en)：先选择移动、按内容排列方向调整、确认位置。
