# TV 播放页

跨页面适用的开发顺序、MVI、slotting、焦点、视觉和验收要求见 [TV 页面开发范式](../tv-development.md)。
本文只说明播放页的功能组织与实现入口。

TV 播放页位于 `app/shared/ui-episode/src/androidTv/kotlin/ui/episode`，
由 `:app:shared:ui-episode-tv` 编译。完整包名前缀为 `me.him188.ani.leanback.ui.episode`。
TV 源集和模块的接线方式见 [KMP 文档](../kmp.md#编译时会发生什么)。

## 包职责

| 包 | 内容 |
|---|---|
| 根包 | `TvEpisodeRoute`、`TvEpisodeScreen`、`TvEpisodeViewModel`，页面聚合状态、Intent、事件及通用消息文案 |
| `controls` | 标题栏、底部控制器、选集条、倍速与字幕控件、播放信息格式化 |
| `comments` | 评论分页列表、BBCode 预览与详情、评论骨架 |
| `danmaku` | 弹幕渲染、列表、设置、匹配、对应状态与文案、弹幕骨架 |
| `source` | 选源界面、浏览状态、资源分组、来源图标与文案 |
| `recommendation` | 条目推荐行、推荐导航目标及卡片骨架 |
| `settings` | 收藏状态面板及确认状态、画质增强设置 |
| `presentation` | 覆盖层状态机、面板与弹窗类型、页面级焦点锚点和焦点切换效应 |
| `playback` | 预览进度与临时倍速的交互状态、自动跳过及章节提示状态 |
| `components` | 页面布局、面板容器、滚动列表、骨架基础块、通用按键修饰符及底部切换动画 |

同一功能的模型、文案映射、组件和骨架放在同一功能包。
跨功能使用的页面聚合模型保留在根包，视图容器通过 slot 接收内容。
例如，评论和弹幕共用 `TvPlayerPanelList`，控制器与推荐条目共用 `TvBottomControllerLayout`。

Options 行的 `TvOptionsRow`、`TvOptionChip`、尺寸定义、`TvOptionAnchors` 和 `TvOptionPanel`
已抽到 `ui-foundation-tv`，详情页也使用这些 API。播放页在行上方显示面板，
根据按钮的窗口坐标计算位置；页面继续负责业务选项、缩窄后的文字折叠和焦点出口。
这些面板不创建 `Dialog` / `Popup` 窗口，见 [TV options 与覆盖层](tv-options.md)。
倍速调节通过 foundation 的 `TvOptionStepper` 实现，与条目评分共用整组焦点、左右调节及箭头按键反馈；播放页仅负责倍速文案、边界和 Intent。
居中弹窗使用 foundation 的 `TvOptionModal`，与条目 Review 的评论全文共用外观；调用方继续负责内容与返回处理。
收藏状态列表、删除确认与 Watched 二次确认统一使用 `ui-subject-tv` 的 `collection/TvCollectionOptions`。
播放页只接入自己的列表、焦点入口和 Intent；所有选项在收藏或全部标记请求期间置灰禁用，保留布局，完成后恢复。

## 状态与焦点

`TvEpisodeRoute` 负责收集 ViewModel 状态和页面生命周期；`TvEpisodeScreen` 负责组合各功能组件并接线。
功能组件接收状态、回调和焦点锚点，通过 Intent 上报业务操作；repository、服务及播放器业务决策由 ViewModel 负责。

`presentation` 管理视图显隐和页面之间的焦点交接，组件内部的列表锚点及局部浏览状态由对应功能包维护。
程序化送焦继续使用共享 `TvFocusScope`，不直接使用裸 `FocusRequester`。
`playback` 中的交互状态可在 ViewModel 与视图间共享；ViewModel 不依赖 `presentation` 的覆盖层状态机或焦点对象。

一起看功能继续由 `ui-watchtogether` 的 TV 模块提供，播放页仅接线，不在此重复实现。

## 验证

状态机测试随实现放在 `src/androidTvTest/kotlin/ui/episode/presentation`；
跨功能的状态测试和基于完整 `TvEpisodeScreen` 的 UI 测试保留在测试根包。
模块边界及焦点约定由 `:app:shared:tv` 的 `TvArchitectureTest` 检查。
