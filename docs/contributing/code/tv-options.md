# TV options 与覆盖层

公共 API 位于 `ui-foundation/src/androidTv/kotlin/ui/foundation`，由 `ui-foundation-tv` 提供。
播放页与详情页都依赖它；foundation 不依赖条目、播放页或页面私有的 panel 枚举。

| API | 职责 |
| --- | --- |
| `TvOptionsRow` | 可换行的操作行，可通过 slot 在当前按钮上方放置面板 |
| `TvOptionChip` / `TvOptionsRowDefaults` | 播放页抽出的描边胶囊按钮和测量尺寸，区分 active 与焦点状态 |
| `TvOptionStepper` | 播放倍速和评分共用的整组调节控件；左右调节，装饰箭头不聚焦，可选确认操作及忙碌输入阻挡 |
| `TvOptionPanel` / `tvOptionPanelSurface` | 公共面板外观，标题、图标和内容通过参数及 slot 传入 |
| `TvOptionModal` | 播放页与条目评论全文共用的居中弹窗，可通过 `footer` 固定底部操作区，保留后方页面；调用方管理返回和焦点恢复 |
| `TvOptionPanelDefaults` | 248dp 默认宽度、276dp 最大高度、20dp 圆角及锚定间距 |
| `TvOptionAnchors` / `tvOptionAnchor` | 以稳定业务 key 记录按钮的窗口坐标与尺寸 |
| `TvAnchoredOptionLayout` | 在覆盖层中按原坐标、原尺寸重绘按钮，把面板放到按钮上方，并约束到可用视口 |
| `TvModalOverlay` / `tvModalUnderlay` | 当前窗口内的模态覆盖层、焦点隔离与背景输入阻挡，不使用 `Dialog` / `Popup` |
| `tvBackKey` | 在松开 Back/Escape 时关闭当前层，消费完整按键周期 |

按钮原始布局负责位置登记，重绘的按钮不再登记同一个 key。登记实际绘制区域，
避免把 Material 最小触摸区域的额外留白算入按钮尺寸。位置采用窗口坐标，
覆盖层测量完成后转换为自身局部坐标，适用于非零页面偏移和嵌套布局。
尚未获得覆盖层位置时不绘制锚定内容，焦点请求等待目标附着，避免首帧出现在错误位置。
不要用 dp 常量猜测按钮位置，也不要用固定延时等待布局。

`panelAnchorFraction` 指定按钮左边缘对齐到面板宽度的比例，默认 0（左侧对齐）；
评分面板使用 `TvSubjectDetailsDefaults.RatingPanelAnchorFraction`。最终位置仍受屏幕安全边距约束。

背景通过 slot 提供。详情页传入全屏模糊的条目背景，收藏、评分及其确认层共用原操作入口；
简介、评论和人物资料也在页面内覆盖显示。背景页面保留组合以保存滚动和布局，
激活覆盖层时隐藏背景语义并阻止背景焦点与输入，关闭后按业务 key 恢复入口。
保留在子层下方的覆盖层设置 `active = false`，暂时解除自身的焦点退出限制与返回处理，
并应用 `tvModalUnderlay(true)` 阻挡底层输入；仅顶部覆盖层持有模态焦点。
覆盖层整体以 180ms 淡入，背景、面板和重绘按钮一起过渡；覆盖层背景图关闭独立 crossfade。
覆盖层优先复用当前详情页已解码的背景图，避免入场期间重复加载造成黑底闪现；缓存仅随页面存活。
共享 `AsyncImage(crossfade = false)` 使用明确的无过渡 factory，避免 Sketch 将 null 过渡重新合并为全局 crossfade。

页面负责 `TvFocusScope`、初始焦点、面板内导航、按钮回到面板的路径和多层返回。
`TvOptionPanel` 与 `TvOptionModal` 同时提供 Material3 和 TV Material 的内容颜色，避免混用文本组件时继承深色文字；次要文字仍显式使用 muted 颜色。
异步业务结果仍由页面按请求身份处理，公共容器不提交收藏或评分。
收藏业务的选项及二次确认由 `ui-subject-tv/collection/TvCollectionOptions` 共用，
两个页面都通过 `TvOptionRow(enabled = !busy)` 表达等待，不另行插入 circular loading。
`tvBackKey` 保留按下时的回调直至松开；页面关闭动作按该次按下时的面板代数校验。
Android 可能先分发系统 Back 回调，再将同次 key-up 送到 Compose，已关闭的层不得再次关闭其父层。

回归入口：`TvSubjectDetailsUiTest` 验证原位置重绘、上方锚定、窄视口/大字体、焦点隔离和分层返回；
`TvPlayerI18nUiTest` 验证共享 options 行、缩窄后的面板位置与焦点。
