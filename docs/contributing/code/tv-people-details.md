# TV 角色、声优与制作人员详情

人物页位于 `ui-subject/src/androidTv/kotlin/ui/subject/person`，实现遵循
[TV 开发规范](../tv-development.md) 和 [人物页设计](../../dev/tv/people-design.md)。

## 导航与数据

角色使用 `NavRoutes.CharacterDetail`。人物使用 `NavRoutes.PersonDetail(personId, role)`，
`role` 明确区分 `VoiceActor` 与 `Staff`；同一个人物的两个入口拥有不同的导航身份、VM 和保存状态。
默认值保留原有手机人物导航的行为。职业数据加载完成后不会更改页面类型。

条目页角色卡进入角色页，制作人员卡进入 Staff。角色页声优进入 VoiceActor；
出演角色卡进入角色页，作品卡进入条目页。
`TvAniAppContent` 在导航条目中创建 `TvPeopleDetailsViewModel`，依赖统一来自 `TvAppDependencies`。
VM 组合 `PersonDetailsRepository`、`PersonCommentRepository` 和共享 `CommentState` / `CommentReportState`。
关联列表保留完整的 `CharacterSubjectInfo`、`PersonCastInfo`、`PersonWorkInfo`，不丢弃演员或职位。

## 共享布局

三个入口均使用 `TvPeopleDetailsLayout`，提供 identity、portrait、introduction、discussion、actions、sections slots。
页面滚动、海报背景和遮罩来自条目页的 `TvSubjectDetailsPageLayout`；
首屏高度比例可配置，首屏图片随整页滚走，下方列表使用整个页面宽度。
末尾统一由该公共布局追加 192dp 的独立留白 Box。聚焦实际显示的最后一个区块时，
纵向 BringIntoView 将留白底边对齐视口底边，行内左右导航保持该锚点；作品列表为空而隐藏时，
自动按剩余区块判断末节。留白不属于卡片或区块的焦点范围，也不接收焦点。

区块顺序：

| 页面 | 区块 |
| --- | --- |
| 角色 | 声优 → 出演作品 |
| 声优 | 出演角色 → 参与作品 |
| 制作人员 | 参与作品 |

圆形头像复用 `TvDetailsPersonCard`，作品与条目详情页一致，复用 16:9 的 `TvLandscapeCard`
及 `RelatedCardWidth`（198dp）；定位或职位放在卡内标题上方，现有封面裁切为横版。
标题动画复用 `TvDetailsBrowseRowLayout`（16sp → 26sp）。卡片不缩放。
“浏览参与作品 / 浏览出演作品”按钮已按实施要求移除；首屏只保留查看大图。
只有加载完成的空列表隐藏区块，加载与失败继续提供本节入口，追加失败保留已有卡片。

## 阅读与讨论

介绍入口与条目页直接共用 `components/TvDetailsDescriptionCard`，仅传入对应标题、摘要和点击回调；
图标、字号、留白、描边、焦点颜色及“显示更多”排版均由同一组件控制。
介绍展开使用条目页全屏模糊覆盖层。`TvDetailsReadingArea` 由条目简介与人物介绍共用，
提供单个阅读焦点、上下键平滑滚动、边缘渐隐与滚动条。
人物介绍正文后接基础信息 key/value 表，两部分在同一个 scroll column 中；不生成标签。

讨论只有一个 `TvOptionModal`，列表、全文、举报与图片预览在同一表面切换。
原页面保持组合，由 `tvModalUnderlay` 阻止后台焦点和按键，模态容器截获触摸。
评论预览复用 `TvReviewCard(showRating = false)`；富文本复用 `RichText`，
隐藏内容在视觉和无障碍语义中均被遮罩，展开操作才使用原始元素。
来源能力控制投票和 Bangumi 原文入口；举报使用共享状态，过期提交结果不能关闭新评论。
来源合并分页结束前显示已加载数量加 `+`；缺失值显示 `—`；部分来源不可用时提示并继续保留下界。

## 焦点与状态

`TvPeoplePresentationState` 保存页面焦点、每行业务 key、当前覆盖层、评论身份和返回代数。
滚动状态留在 View 中，由 Navigation 3 的保存状态按条目隔离。
`TvPeopleScrollMemory` 在跳转前另存纵向位置和各行偏移，避免返回时 Paging 的临时 loading 项
缩短内容并截断 `ScrollState`。恢复等待关联内容完成布局；恢复期间不重新应用 TV 的焦点滚动策略，
下一次主动导航立即恢复正常滚动并取消未完成的恢复请求。
角色出演记录的 key 包含角色 ID 和作品 ID，声优与作品分别按人物/条目 ID。
跨行恢复先准备 Lazy 项，再等待布局、窗口获焦和生命周期 RESUMED；用户导航能取消旧请求。
后台数据完成不重复执行默认送焦；删除时按原索引回到相邻业务 ID。

| 操作 | 返回位置 |
| --- | --- |
| 首次进入 | 介绍卡片（加载期间保持可聚焦） |
| 关闭介绍 | 介绍卡片 |
| 关闭大图 | 查看大图 |
| 评论全文 Back | 原评论和列表位置 |
| 评论列表 Back | 讨论卡片 |
| 下方区块 Back | 首屏介绍卡片 |
| 首屏介绍 Back | 交给导航栈 |

## 验证

`TvPeoplePresentationTest` 覆盖独立导航身份、两级返回、重复 key-up、过期举报结果和分页计数。
`TvPeopleDetailsUiTest` 使用 `runAniComposeUiTest` 与合成遥控器输入覆盖三个页面、
介绍与信息表滚动、列表与全文切换、无额外窗口、遮罩、来源能力、分页失败重试、
删除/重排、导航返回、大图、加载完成不抢焦点以及大字体布局。
另验证三类页面末节底部锚定、同一行左右换焦点保持纵向位置，以及作品区块隐藏后的末节判断。

Android 的 `assertScreenshot` 目前不执行像素比较；测试另行导出 `tv-people-*.png`，
需从测试 APK 的 external files 目录取出并人工核对。
测试肖像沿用测试包中已注明来源的海报，仅用于稳定测量，不代表真实人物数据。
