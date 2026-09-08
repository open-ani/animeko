# Android 播放页功能基线与 TV 对照清单

- 记录日期：2026-09-06。
- 基线分支：tv/m0-bootstrap-and-flavor。
- 基线提交：03d8afb436e62c94b66a52467ef1f2c1164ee543。
- 核对范围：Android 手机／平板 default flavor 的播放页，以及它直接打开的菜单、侧栏、弹窗、自动播放行为和相关全局设置。
- 核对方式：依据当前代码的实际入口和调用链盘点。本次未进行新的设备回归；“已接通”表示代码层面已有接入，不保证所有资源、设备和服务端条件下均正常。
- 用途：为 TV 对应功能实现提供基线。下文的功能编号用于后续引用，**不代表 TV 尚未实现或已经通过验收**；TV 当前实现情况需要另行核对。
- 范围边界：从播放页跳转到缓存、角色、人物、数据源设置等独立页面的功能，记录其入口；不继续递归盘点这些页面的全部功能。

## 阅读与使用约定

- “播放页直接入口”：控件、更多菜单、详情区以及它们打开的弹层。
- “设置入口”：在全局设置中调整，运行时影响播放页。
- “条件功能”：取决于资源、播放器能力、系统版本、账号或服务端。
- “未接通／受限”：见第 13 节，不能当作 Android 已完成能力直接制定 TV 对齐要求。
- 方括号内的 S 编号对应文末代码索引；代码行号以本次基线为准。

## 1. 播放控制与页面布局

依据：[S01]、[S02]、[S03]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| PLAY-01 | 播放、暂停、继续 | 播放器控制栏；画面手势和快捷键见第 2 节。 |
| PLAY-02 | 播放时间显示 | 当前时间、总时长；非 1 倍速时显示按倍速计算的剩余时间。 |
| PLAY-03 | 进度定位 | 点击或拖动进度条，结束操作后跳转到目标位置。 |
| PLAY-04 | 缓冲／缓存进度 | 进度条展示可取得的缓冲／缓存区间。 |
| PLAY-05 | 拖动预览 | 展示目标时间；开启帧预览且播放器支持取帧时，展示目标画面缩略图。不能据此宣称支持逐帧步进。 |
| PLAY-06 | 章节区间展示 | 进度条显示视频章节，并合并服务端补充的片头／片尾区间。 |
| PLAY-07 | 手动全屏 | 控制栏和可配置的悬浮按钮切换全屏。 |
| PLAY-08 | 返回键退出全屏 | 全屏时优先退出全屏，再由正常页面返回流程处理后续返回。 |
| PLAY-09 | 横屏自动全屏 | 可在设置中开启。 |
| PLAY-10 | 全屏信息 | 展示番剧名、集数、剧集名和系统时间。 |
| PLAY-11 | 控制栏显隐 | 自动隐藏；菜单、输入框和手势交互时保留必要控件。 |
| PLAY-12 | 手机布局 | 播放器位于上方，下方为详情／评论；全屏时只展示播放器区域。 |
| PLAY-13 | 宽屏／平板布局 | 播放器与详情／评论区域并排；全屏时隐藏侧边内容。 |
| PLAY-14 | 详情／评论切换 | 标签点击和左右滑动切换。 |
| PLAY-15 | 屏幕常亮 | 播放期间保持屏幕常亮。 |
| PLAY-16 | 播放页暗色主题 | 可配置播放页始终使用暗色主题；播放器控件本身使用暗色主题。 |

## 2. 触摸手势、鼠标与键盘

依据：[S04]、[S05]、[S06]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| INPUT-01 | 单击画面 | 触摸模式下显示／隐藏控制栏。 |
| INPUT-02 | 双击画面 | 触摸模式下暂停／继续。 |
| INPUT-03 | 水平滑动定位 | 左右滑动快退／快进，显示目标位置和偏移量。 |
| INPUT-04 | 取消本次定位 | 调整进度时向上移入取消区域可取消；移回来可继续调整，松手时决定是否提交。 |
| INPUT-05 | 长按临时倍速 | 长按使用配置的绝对播放速度，松手恢复原倍速；不是在当前速度上再乘一个倍率。 |
| INPUT-06 | 亮度手势 | 画面左侧上下滑动调整亮度。 |
| INPUT-07 | 音量手势 | 画面右侧上下滑动调整 Android 系统媒体音量。 |
| INPUT-08 | 全屏手势 | 画面中间上滑进入全屏、下滑退出全屏。 |
| INPUT-09 | 手势锁 | 全屏下锁定手势以防误触，点击可唤出解锁入口；不能等同于系统屏幕方向锁。 |
| INPUT-10 | 外接鼠标 | 单击暂停／继续、双击切换全屏、移动显示控件、滚轮调整播放器音量。鼠标滚轮的音量目标与 Android 触摸手势的系统音量不同。 |
| INPUT-11 | 键盘操作 | 播放器取得键盘焦点时生效；输入框和其他控件取得焦点时不会把文字输入当作播放器命令。 |

INPUT-11 的完整快捷键映射：

| 按键 | 行为 |
|---|---|
| ←／→ | 后退／前进 5 秒。 |
| 长按 → | 临时倍速，松开恢复。 |
| ↑／↓ | 调整音量；Shift 配合细调。 |
| Space | 暂停／继续。 |
| F | 切换全屏。 |
| A／D | 降低／提高倍速。 |
| S | 恢复 1 倍速，受当前配置范围限制。 |
| 1／2／3，包括数字小键盘 | 切换到对应倍速，受当前配置范围限制。 |
| B | 开关弹幕。 |
| I | 开关播放统计。 |
| Enter | 当前不是播放快捷键；播放器焦点节点会吞掉该键。 |
| D-pad 中心键 | 交由可点击节点处理激活行为；TV 需要另行设计遥控器焦点与确认键语义。 |

## 3. 倍速、画面、音频与字幕

依据：[S02]、[S03]、[S07]、[S08]、[S09]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| AV-01 | 调整播放倍速 | 全屏／宽屏控制栏；代码默认范围为 0.5～2.5 倍，全局允许在 0.25～4 倍内配置可调范围。 |
| AV-02 | 记忆倍速 | 开启后，播放器内调整写回设置，跨剧集、条目和应用重启沿用。 |
| AV-03 | 默认倍速 | 关闭“记住倍速”后，每次进入播放器使用设置的默认值；本次临时调整不写回默认值。 |
| AV-04 | 页内切集保留倍速 | 同一播放页会话内切集保留当前倍速。 |
| AV-05 | 画面适配模式 | 适应、拉伸、裁切；当前不是固定 4:3／16:9 比例选择器。 |
| AV-06 | 画质增强／超分 | 关闭、性能、质量三档。Android 已接入 ExoPlayer 图像处理；紧凑布局从更多菜单进入，全屏／宽屏有对应入口。 |
| AV-07 | 字幕轨道选择 | 全屏／宽屏控件中选择资源提供的字幕轨道，或关闭字幕；取决于播放器轨道能力和资源内容。 |
| AV-08 | 资源附带的外挂字幕 | 随媒体额外文件加载；Android 接入 libass，支持 ASS 解析与渲染。不能据此宣称播放页存在任意选择本地字幕文件的入口。 |
| AV-09 | 高质量倍速音频 | Android 可开启高质量时间伸缩处理。 |

## 4. 资源查询、筛选与换源

依据：[S03]、[S10]、[S11]、[S12]、[S13]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| SOURCE-01 | 自动查询与选择 | 按当前番剧／剧集查询已配置的数据源，自动选择资源并开始播放。 |
| SOURCE-02 | 多类资源播放 | 已配置的在线资源、BT 资源、本地缓存资源；具体支持取决于解析器、缓存和播放器。 |
| SOURCE-03 | 当前资源摘要 | 展示选中的来源、线路和自动选源状态；详情区摘要可打开换源入口。 |
| SOURCE-04 | 全屏换源 | 全屏／宽屏播放器内打开选源侧栏。 |
| SOURCE-05 | 简洁／详细模式 | 选择器支持切换两种展示方式。 |
| SOURCE-06 | 在线线路选择 | 简洁模式按数据源展示线路，可手动切换。 |
| SOURCE-07 | 资源明细 | 详细模式展示标题、大小、分辨率、字幕语言、来源等可取得的信息。 |
| SOURCE-08 | 资源筛选 | 设置／清除分辨率、字幕语言、字幕组或发布组、数据源偏好。 |
| SOURCE-09 | 偏好记忆 | 保存选源和筛选偏好，影响后续选择。 |
| SOURCE-10 | 被过滤资源 | 展示过滤数量；可展开查看被过滤资源及原因，并手动选择。 |
| SOURCE-11 | 同资源多来源 | 同一资源存在多个来源时，可选择具体来源。 |
| SOURCE-12 | 复制资源链接 | 长按资源复制其链接。 |
| SOURCE-13 | 重新查询全部源 | 从详细模式操作区刷新；会清理当前条目的相关搜索缓存。 |
| SOURCE-14 | 重试单个源 | 对失败等状态的单个源重新查询，并清理对应搜索缓存。 |
| SOURCE-15 | 临时查询禁用源 | 详细模式可触发当前会话中的查询，不等同于全局永久启用数据源。 |
| SOURCE-16 | 查询状态 | 展示查询中、成功、失败、验证码、限流等状态；简洁模式含限流倒计时。 |
| SOURCE-17 | 网站验证 | 有支持的验证流程时进入验证，完成后重新查询；解析器自身的交互内容也挂载在播放页。 |
| SOURCE-18 | 修改查询请求 | 编辑主标题、别名列表、剧集序号、集号；别名支持新增、修改、删除。 |
| SOURCE-19 | 查询帮助 | 打开选源帮助。 |
| SOURCE-20 | 数据源设置入口 | 从选源器跳转数据源设置页。 |

分辨率和字幕语言在这里是“资源筛选条件”；播放器内的字幕轨道选择是 AV-07，两者需要分别实现和验收。

## 5. 播放进度与自动行为

依据：[S03]、[S14]、[S15]、[S16]、[S17]、[S18]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| AUTO-01 | 保存播放进度 | 开始／恢复播放一段时间后、播放期间定期、暂停、换源、切集、结束和退出等时机保存。 |
| AUTO-02 | 恢复播放位置 | 重新打开同一集或换源后，媒体准备好时恢复已保存位置。 |
| AUTO-03 | 清理已完成续播位置 | 接近视频结尾时清理续播记录，避免下次从最后几秒开始。 |
| AUTO-04 | 手动下一集 | 全屏／宽屏有下一集按钮；只有存在已播出的下一集时可用。 |
| AUTO-05 | 自动连播 | 正常播放结束且存在可播放的下一集时切集；可关闭。 |
| AUTO-06 | 自动标记看过 | 可关闭。当前判断采用“90% 进度”和“剩余 100 秒”的位置中较早者，不只是单一 90% 阈值。 |
| AUTO-07 | 手动跳过固定时长 | 播放器顶部按钮向前跳 80／85／90 秒，设置选择，代码默认 85 秒；它不是“跳到当前章节结尾”。 |
| AUTO-08 | 自动跳过片头片尾 | 使用视频章节及服务端补充区间，按时长等规则识别并跳过；提前展示提示，可取消本次自动跳过。 |
| AUTO-09 | 首集自动跳过例外 | 多集条目的首集不自动跳过片头／片尾；并非任何视频都能自动识别可跳区间。 |
| AUTO-10 | 播放失败自动换源 | 可关闭。解析失败或播放器出错时尝试下一在线资源；当前要求偏好类型为在线源，并避开已失败／被切走的资源。不能描述为任意资源类型间自动兜底。 |
| AUTO-11 | 后台自动暂停与恢复 | 普通播放下退到后台暂停，返回前台恢复此前由页面自动暂停的播放；不等于后台音频播放。 |
| AUTO-12 | 一起看协调 | 跟随房主时，进度恢复、自动连播、自动跳过等相关自动行为服从房间同步策略。 |

## 6. 弹幕

依据：[S19]、[S20]、[S21]、[S22]、[S23]、[S24]、[S25]、[S26]。

### 6.1 加载、播放与匹配

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| DANMAKU-01 | 弹幕总开关 | 播放器控制栏和快捷键。 |
| DANMAKU-02 | 自动匹配与加载 | 根据剧集及媒体信息匹配。直接接入 Animeko、弹弹play；列表按实际返回的服务来源展示，不能把所有来源都当作独立直连提供方。 |
| DANMAKU-03 | 播放同步 | 弹幕随时间位置、暂停／恢复、倍速变化同步。 |
| DANMAKU-04 | 状态与数量 | 展示加载、匹配状态，以及来源和弹幕数量。 |
| DANMAKU-05 | 弹幕列表 | 手机点击详情区弹幕信息条打开底部弹窗；宽屏为可展开列表。 |
| DANMAKU-06 | 按来源开关 | 单独启用／停用某个弹幕来源，查看该来源数量。 |
| DANMAKU-07 | 手动重新匹配 | 支持交互匹配的来源可输入关键词、选择作品、选择剧集，再载入对应弹幕。 |
| DANMAKU-08 | 按来源调整时间 | 每个来源可分别设置前后 30 秒偏移。 |
| DANMAKU-09 | 时间微调操作 | 滑杆、±0.1 秒、±0.5 秒、归零、恢复修改前的值、确认或取消。 |
| DANMAKU-10 | 本地弹幕缓存 | 根据全局策略缓存弹幕，并读取本地缓存；不是播放页手动导出弹幕文件。 |

### 6.2 发送弹幕

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| DANMAKU-11 | 弹幕输入与发送 | 手机详情／评论标签旁的入口，以及播放器内输入框；发送位置取当前视频时间。 |
| DANMAKU-12 | 发送后即时显示 | 成功后即时显示本机发送的弹幕，识别自己的弹幕。 |
| DANMAKU-13 | 发送状态 | 展示发送状态，失败时保留／恢复待发送文字。 |
| DANMAKU-14 | 编辑时暂停 | 播放器内输入框遵循“编辑弹幕时暂停”设置；手机竖屏底部输入弹层当前直接执行暂停。结束后按原播放状态恢复，房间同步还会影响相关行为。 |
| DANMAKU-15 | 当前发送样式 | 当前入口固定发送白色滚动弹幕，没有颜色、顶部／底部位置选择器。 |

### 6.3 外观与过滤

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| DANMAKU-16 | 类型开关 | 顶部、滚动、底部、彩色弹幕分别开关。 |
| DANMAKU-17 | 字号 | 可调整弹幕字号。 |
| DANMAKU-18 | 不透明度 | 可调整弹幕透明程度。 |
| DANMAKU-19 | 描边宽度 | 可调整文字描边宽度。 |
| DANMAKU-20 | 字重 | 可调整文字粗细。 |
| DANMAKU-21 | 滚动速度 | 独立调整弹幕滚动速度；与视频播放倍速是不同设置。 |
| DANMAKU-22 | 密度／间距 | 调整弹幕排列密度。 |
| DANMAKU-23 | 显示区域 | 调整弹幕占用画面高度，从关闭到全屏区域。 |
| DANMAKU-24 | 正则过滤总开关 | 启用／停用正则过滤。 |
| DANMAKU-25 | 播放页规则管理 | 新增正则、校验合法性、删除规则、逐条启用／停用。 |
| DANMAKU-26 | 全局规则管理 | 全局设置另外提供规则编辑、从剪贴板导入、导出到剪贴板；这些操作不在当前播放页的规则弹层内。 |

## 7. 选集、收藏与番剧信息

依据：[S20]、[S27]、[S28]、[S29]、[S30]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| DETAIL-01 | 番剧及剧集信息 | 展示番剧名称、当前集数／剧集名和播出状态；不同布局中的位置不同。 |
| DETAIL-02 | 标题复制与展开 | 番剧标题可选择复制，点击打开完整条目详情弹层。 |
| DETAIL-03 | 手机选集 | 横向剧集卡片及“更多剧集”弹层。 |
| DETAIL-04 | 宽屏选集 | 剧集列表可展开／收起，长篇支持分组分页。 |
| DETAIL-05 | 全屏选集 | 播放器内打开选集侧栏。 |
| DETAIL-06 | 当前集定位 | 展示当前播放标识和观看状态；选集器定位当前集。 |
| DETAIL-07 | 页内切集 | 点击剧集切换播放。 |
| DETAIL-08 | 手动观看标记 | 详情区剧集卡片／列表长按快速标记“看过”或取消。 |
| DETAIL-09 | 收藏入口 | 未处于在看／搁置／看过状态时，心形按钮可设为在看；已有对应收藏时打开修改菜单。 |
| DETAIL-10 | 收藏状态修改 | 想看、在看、看过、搁置、抛弃、取消收藏；取消收藏有确认。 |
| DETAIL-11 | 全部剧集标记 | 将条目标记看过时，可进一步将全部剧集标记为已看。 |
| DETAIL-12 | 推荐作品 | 展示推荐作品，点击打开对应条目或外部页面。 |

播放页打开的完整条目详情弹层还包含以下能力；实现 TV 时可复用已有详情页入口，但需要确认入口能够到达：

| 编号 | 弹层内功能 | 说明 |
|---|---|---|
| SUBJECT-01 | 封面查看 | 展示封面，点击放大。 |
| SUBJECT-02 | 条目资料 | 简介、标签、作品信息、播出信息、收藏统计。 |
| SUBJECT-03 | 标签搜索 | 点击标签进入相关搜索。 |
| SUBJECT-04 | 评分与评价 | 查看评分／评价，评分和写评价入口，评价图片／链接及相应评论交互。 |
| SUBJECT-05 | 角色与制作人员 | 浏览角色、制作人员，查看完整列表及进入对应信息页。 |
| SUBJECT-06 | 关联作品 | 浏览关联作品、查看全部并进入对应条目。 |
| SUBJECT-07 | 播放与选集 | 继续播放、选集、修改剧集观看状态。 |
| SUBJECT-08 | 收藏及缓存入口 | 修改条目收藏，打开缓存管理。 |
| SUBJECT-09 | 讨论标签占位 | 当前仍显示“即将推出”，未实现讨论内容。 |

## 8. 剧集评论

依据：[S31]、[S32]、[S33]、[S34]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| COMMENT-01 | 评论列表 | 分页浏览 Animeko 与 Bangumi 剧集评论。 |
| COMMENT-02 | 刷新与加载 | 下拉刷新、继续加载、失败重试、加载失败提示。 |
| COMMENT-03 | 评论展示 | 作者、时间、来源、正文、图片、表情回应和回复摘要。 |
| COMMENT-04 | 富文本阅读 | 显示格式化内容；打开正文链接、放大图片。 |
| COMMENT-05 | 复制评论 | 长按／更多菜单复制正文。 |
| COMMENT-06 | 发表新评论 | 写评论入口发送至 Animeko 评论服务。 |
| COMMENT-07 | 回复 | 对允许回复的 Animeko 评论打开回复编辑器。 |
| COMMENT-08 | 点赞／点踩 | Animeko 评论支持点赞、点踩和取消。 |
| COMMENT-09 | 表情回应 | Animeko 评论支持添加／取消表情回应。 |
| COMMENT-10 | 举报 | Animeko 评论支持举报，附补充说明和提交结果提示。 |
| COMMENT-11 | 举报原因 | 垃圾广告、人身攻击／骚扰、剧透、色情血腥等不适内容、违法违规、其他。 |
| COMMENT-12 | 编辑器布局 | 展开／收起、关闭、输入状态管理。 |
| COMMENT-13 | 富文本编辑 | 粗体、斜体、下划线、删除线、遮罩、图片链接、超链接、Bangumi 表情贴纸。图片按钮插入链接标记，不等于本地图片上传。 |
| COMMENT-14 | 发送前预览 | 预览格式化评论，切回编辑。 |
| COMMENT-15 | 发送反馈 | 发送中状态、成功关闭、失败提示；成功后刷新／定位评论列表。 |
| COMMENT-16 | Bangumi 只读评论 | 可阅读、复制、查看图片／链接，并在 Bangumi 打开原页面；没有站内回复、点赞／点踩、添加回应或举报入口。 |

注意：目前“展开回复”回落到回复编辑入口，不是独立的完整回复楼层浏览；“屏蔽作者”也未在剧集评论中接入。

## 9. 一起看

依据：[S35]、[S36]、[S37]、[S38]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| ROOM-01 | 播放器入口 | 更多菜单打开一起看；关闭状态下点击会启用功能。 |
| ROOM-02 | 登录引导 | 未登录时提示登录并进入登录流程。 |
| ROOM-03 | 加入／创建房间 | 输入房间名和密码；房间不存在时自动创建。 |
| ROOM-04 | 入房表单 | 密码显示／隐藏、加入中状态、失败原因提示。 |
| ROOM-05 | 房间信息 | 房间名、人数、连接状态、当前播放内容和进度。 |
| ROOM-06 | 成员信息 | 房主、自身标记、成员昵称／头像、跟随／自由观看、空闲、正在观看、加载或断线等状态。 |
| ROOM-07 | 同步作品和剧集 | 跟随房主进入对应作品／剧集，必要时切集或导航。 |
| ROOM-08 | 同步播放 | 同步进度、播放／暂停、倍速；自动校正位置偏差。 |
| ROOM-09 | 跟随开关 | 成员可切换跟随房主或自由观看。 |
| ROOM-10 | 跟随约束 | 跟随中限制自行跳到其他剧集，相关播放自动行为服从同步策略。 |
| ROOM-11 | 状态变化提示 | 提示同步导航、校正和连接等变化。 |
| ROOM-12 | 悬浮入口 | 房间面板可收起，保留可拖动入口；全屏时随播放器控件显隐。 |
| ROOM-13 | 退出／解散 | 成员离开房间；房主解散房间有确认。 |
| ROOM-14 | 关闭功能 | 关闭一起看功能，有确认。 |
| ROOM-15 | 连接恢复 | 断线重连及重新加入房间。 |

一起看悬浮入口属于应用内 UI，不是 Android 系统悬浮窗，也不是画中画。

## 10. 截图、外部播放与缓存

依据：[S01]、[S39]、[S40]、[S41]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| OUTPUT-01 | 视频截图 | 全屏／宽屏按钮，捕获视频 Surface，PNG 保存到 Pictures/Animeko；不是整页截图，不包含 Compose 控件和弹幕层。旧版 Android 根据需要申请存储权限。 |
| OUTPUT-02 | 复制资源链接 | 按资源可用信息提供视频流、磁力、种子下载、本地资源等链接。 |
| OUTPUT-03 | 打开资源链接 | 调用 URI 处理器打开对应链接。 |
| OUTPUT-04 | 外部应用播放 | Android 通过“使用其他应用打开”处理可用的视频资源链接；受资源类型和外部应用支持限制。 |
| OUTPUT-05 | 来源网页 | 复制或打开当前资源来源页面。 |
| CACHE-01 | 缓存管理入口 | 更多菜单跳转当前番剧缓存管理页；完整条目详情内也有入口。 |
| CACHE-02 | 本地缓存播放 | 从资源选择链路选择本地缓存资源。 |
| CACHE-03 | BT 边下边播缓存 | 实际使用本地 BT 引擎时自动建立缓存；云离线后端将磁力解析成普通 HTTPS 视频时，不重复触发本地 BT 下载。 |
| CACHE-04 | 弹幕缓存联动 | 见 DANMAKU-10；按缓存策略决定保存时机。 |

## 11. 加载提示与诊断

依据：[S42]、[S43]、[S44]。

| 编号 | Android 当前功能 | 入口、行为与条件 |
|---|---|---|
| DIAG-01 | 加载阶段提示 | 自动选源、解析资源、解码、缓冲等阶段。 |
| DIAG-02 | BT 下载反馈 | 缓冲时展示可取得的下载速度，长时间无速度时给出提示。 |
| DIAG-03 | 播放错误分类 | 播放器错误、网络错误、解析超时、无匹配文件、不支持资源、取消、未知错误。 |
| DIAG-04 | 条目资料失败重试 | 资料加载失败显示错误卡片，可重试。 |
| DIAG-05 | 播放统计开关 | 更多菜单或 I 键开关统计浮层。 |
| DIAG-06 | 基础统计 | 内核、播放状态、媒体标题、进度、倍速。 |
| DIAG-07 | 音视频统计 | 分辨率、帧率、音视频编码和解码器、码率、采样率、声道；仅显示实际可取得的数据。 |
| DIAG-08 | 性能统计 | 带宽估算、解码帧数、丢帧／丢弃音频缓冲数；带宽估算不等于精确实时下载速度。 |
| DIAG-09 | 调试模式工具 | 弹幕调试信息、控制器显示状态诊断，以及详细选源器中的资源匹配信息导出工具；不属于普通用户默认入口。 |

## 12. 相关全局设置索引

以下设置在全局设置中配置，运行时影响播放页。此表是入口索引，与上面的能力有意交叉引用，不应在 TV 任务统计中重复计数。

依据：[S09]、[S26]、[S45]、[S46]。

| 编号 | 设置项 | 对应能力／条件 |
|---|---|---|
| SETTING-01 | 全屏按钮展示方式 | 始终显示悬浮按钮、悬浮按钮自动隐藏、仅在控制栏显示。PLAY-07。 |
| SETTING-02 | 默认画质增强档位 | 关闭、性能、质量。AV-06。 |
| SETTING-03 | 帧预览开关 | PLAY-05；仍要求播放器支持取帧。 |
| SETTING-04 | 倍速可调范围 | AV-01。 |
| SETTING-05 | 记住倍速 | AV-02。 |
| SETTING-06 | 默认倍速 | 关闭记忆时可配置。AV-03。 |
| SETTING-07 | 长按播放速度 | INPUT-05。 |
| SETTING-08 | 编辑弹幕时暂停 | DANMAKU-14；手机竖屏弹层的例外已单独记录。 |
| SETTING-09 | 自动标记看过 | AUTO-06。 |
| SETTING-10 | 选择后隐藏选择器 | 设置项存在，运行时未找到读取，见 LIMIT-04。 |
| SETTING-11 | 横屏自动全屏 | PLAY-09。 |
| SETTING-12 | 自动连播 | AUTO-05。 |
| SETTING-13 | 自动跳过片头／片尾 | AUTO-08、AUTO-09。 |
| SETTING-14 | 片头／片尾跳过长度 | 80／85／90 秒，影响固定时长跳过和补充区间构造。 |
| SETTING-15 | 播放失败自动换源 | AUTO-10。 |
| SETTING-16 | 高质量倍速音频 | Android 专属。AV-09。 |
| SETTING-17 | 实验性 HLS 插播片段过滤 | 已接入播放准备流程；实验性选项，不是所有视频广告的通用过滤。 |
| SETTING-18 | 屏幕刷新率 | Android 11 及以上，从设备支持的显示模式中选择；离开播放页恢复原设置。 |
| SETTING-19 | 提前初始化图像处理管线 | ExoPlayer 画质增强相关兼容性选项。 |
| SETTING-20 | 弹幕正则过滤与规则管理 | DANMAKU-24～26。 |
| SETTING-21 | 弹幕缓存策略 | 不缓存、随媒体缓存、或对在看条目的播放额外缓存等策略。DANMAKU-10。 |
| SETTING-22 | 播放页始终暗色 | PLAY-16，属于主题设置。 |

## 13. 未接通、平台差异和不能推导出的能力

| 编号 | 项目 | 当前代码结论 | 对 TV 对齐的影响 |
|---|---|---|---|
| LIMIT-01 | 播放通知／锁屏媒体控制 | VideoNotifEffect 是空实现；普通播放退后台会暂停。[S47] | 不作为 Android 已完成能力。不能因 Manifest 有通知／前台服务权限就认为已实现媒体控制。 |
| LIMIT-02 | 画中画、内置投屏 | 当前播放页及 Android 接入中未找到实现。 | 如需要，作为额外需求定义。 |
| LIMIT-03 | 手动切换音轨、窗口置顶、单独折叠详情侧栏按钮 | 当前播放控件入口限定桌面端。[S02] | Android 基线不包含这些入口；音频解码和播放不受此结论影响。 |
| LIMIT-04 | 选择后隐藏选择器设置 | 设置项和持久化字段存在，未找到运行时读取；当前选择弹层由点击回调直接关闭。[S45] | 不照搬一个没有控制效果的设置；先确定 TV 所需行为。 |
| LIMIT-05 | 评论屏蔽作者 | 通用组件有回调能力，剧集评论没有接入。[S31]、[S32] | 不计作已有功能。 |
| LIMIT-06 | 完整回复楼层浏览 | 当前展开回复回落到回复入口，不能当作完整楼层浏览。[S31]、[S32] | 对齐回复入口和摘要即可；完整楼层另行定义。 |
| LIMIT-07 | 条目讨论 | 内嵌条目详情“讨论”标签显示即将推出。[S30] | 不计作已有功能。 |
| LIMIT-08 | 自选颜色／位置发送弹幕 | 当前只发送白色滚动弹幕。[S24] | 接收端的类型／颜色开关不等于发送端有样式编辑。 |
| LIMIT-09 | 手动加载任意本地字幕 | 当前确认的是资源附带外挂字幕加载，没有播放页任意选择本地字幕文件入口。[S08] | 不将底层能力当成现成用户入口。 |
| LIMIT-10 | 逐帧步进 | 当前确认的是定位时取帧预览，未确认逐帧前进／后退入口。[S03] | 预览和逐帧步进分开。 |
| LIMIT-11 | 后台音频播放 | AUTO-11 是后台暂停／前台恢复；一起看有独立同步约束。 | 不能将恢复播放或 BT 后台缓存等同于后台音频播放。 |

## 14. 后续 TV 实现与验收记录方式

### 14.1 本会话已明确的架构约束

1. 严格采用 MVI：Model → View，Intent 向上。View 渲染状态并上报操作，业务决策放到 ViewModel。
2. Composable 不直接访问 repository，不通过 GlobalKoin 绕过 ViewModel 取得业务依赖。
3. TV ViewModel 统一在 TvAniAppContent 中通过 tvViewModel 构建，不在 Koin 中提供 TV ViewModel。
4. 对触摸、鼠标和键盘能力，先定义遥控器的可达入口、焦点移动、确认／返回和长按语义，再决定哪些需要等价实现。不要机械复制触摸手势。

前三条来自本会话既定要求；第四条是本清单用于 TV 对照时的实现建议。

### 14.2 建议按功能编号维护进度

可为每个实际纳入范围的编号记录：TV 现状、入口、关联 Intent／Model、实现位置、验证方式、结果及差异说明。建议状态使用“待核对／已有待验收／待实现／已验收／明确不适用”。

| 功能编号 | TV 状态 | TV 入口与交互 | Intent／Model | 代码位置 | 验证证据／差异说明 |
|---|---|---|---|---|---|
| 待选择 | 待核对 |  |  |  |  |

本次只记录 Android 基线，未对 TV 功能重新做覆盖率评估，也未勾选任何 TV 验收项。

## 15. 代码索引

以下链接指向本次工作区的绝对路径，行号以基线提交为准。

[S01]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/EpisodePage.kt:189
[S02]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/EpisodeVideo.kt:202
[S03]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/EpisodeViewModel.kt:305
[S04]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/video-player/src/commonMain/kotlin/ui/gesture/PlayerGestureHost.kt:610
[S05]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/video-player/src/commonMain/kotlin/ui/gesture/PlayerKeyboardShortcuts.kt:38
[S06]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/video-player/src/commonMain/kotlin/ui/gesture/GestureLock.kt:138
[S07]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/video-player/src/androidMain/kotlin/videoenhancement/VideoEnhancementController.android.kt:23
[S08]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/video-player/src/androidMain/kotlin/media/LibassExoPlayerMediampPlayer.kt:55
[S09]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/app-data/src/commonMain/kotlin/data/models/preference/VideoScaffoldConfig.kt:48
[S10]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-mediaselect/src/commonMain/kotlin/ui/mediafetch/MediaSelectorView.kt:111
[S11]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-mediaselect/src/commonMain/kotlin/ui/mediafetch/MediaSourceResultsView.kt:90
[S12]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-mediaselect/src/commonMain/kotlin/ui/mediafetch/MediaSelectorFilters.kt:68
[S13]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-mediaselect/src/commonMain/kotlin/ui/mediafetch/request/MediaFetchRequestEditor.kt:78
[S14]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/app-data/src/commonMain/kotlin/domain/player/extension/RememberPlayProgressExtension.kt:37
[S15]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/app-data/src/commonMain/kotlin/domain/player/extension/MarkAsWatchedExtension.kt:79
[S16]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SwitchNextEpisodeExtension.kt:63
[S17]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/app-data/src/commonMain/kotlin/domain/player/extension/SwitchMediaOnPlayerErrorExtension.kt:160
[S18]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/video/PlayerSkipOpEdState.kt:28
[S19]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/video/settings/EpisodeVideoSettings.kt:139
[S20]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/details/EpisodeDetails.kt:196
[S21]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/details/DanmakuListSection.kt:173
[S22]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/app-data/src/commonMain/kotlin/domain/episode/EpisodeDanmakuLoader.kt:84
[S23]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/app-data/src/commonMain/kotlin/domain/danmaku/DanmakuRepository.kt:74
[S24]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/danmaku/DanmakuEditor.kt:81
[S25]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/video/sidesheet/EditDanmakuRegexFilterSideSheet.kt:84
[S26]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-settings/src/commonMain/kotlin/ui/settings/danmaku/DanmakuRegexFilterGroup.kt:90
[S27]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/details/EpisodeListSection.kt:117
[S28]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/video/sidesheet/EpisodeSelectorSideSheet.kt:134
[S29]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/details/components/FavoriteIconButton.kt:30
[S30]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-subject/src/commonMain/kotlin/ui/subject/details/SubjectDetailsPage.kt:223
[S31]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/comments/EpisodeCommentColumn.kt:57
[S32]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-comment/src/commonMain/kotlin/ui/comment/CommentItem.kt:146
[S33]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-comment/src/commonMain/kotlin/ui/comment/EditComment.kt:69
[S34]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-comment/src/commonMain/kotlin/ui/comment/CommentReportSheet.kt:69
[S35]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-watchtogether/src/commonMain/kotlin/ui/watchtogether/WatchTogetherDialog.kt:148
[S36]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-watchtogether/src/commonMain/kotlin/ui/watchtogether/WatchTogetherViewModel.kt:92
[S37]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-watchtogether/src/commonMain/kotlin/ui/watchtogether/WatchTogetherOverlayHost.kt:94
[S38]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/app-data/src/commonMain/kotlin/domain/player/extension/WatchTogetherPlayerExtension.kt:166
[S39]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/details/components/ShareEpisodeDropdown.kt:48
[S40]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-episode/src/commonMain/kotlin/ui/episode/share/MediaShareData.kt:1
[S41]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/app-data/src/commonMain/kotlin/domain/player/extension/CacheOnBtPlayExtension.kt:40
[S42]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/video/loading/EpisodeVideoLoadingIndicator.kt:67
[S43]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/video-player/src/androidMain/kotlin/ui/PlayerStatsOverlay.android.kt:87
[S44]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/video-player/src/commonMain/kotlin/ui/PlayerStatsOverlay.kt:63
[S45]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-settings/src/commonMain/kotlin/ui/settings/tabs/app/AppSettingsTab.kt:467
[S46]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/ui-settings/src/androidMain/kotlin/ui/settings/tabs/app/AppSettingsTab.android.kt:69
[S47]: C:/Users/StageGuard/Desktop/Projects/ani/app/shared/src/commonMain/kotlin/ui/subject/episode/notif/VideoNotifEffect.kt:16

- [S01] 播放页入口、布局、截图调用、生命周期和弹层接线。
- [S02] 播放器控件、菜单、平台入口条件。
- [S03] 播放页状态、倍速、播放扩展、片头片尾与选源接线。
- [S04] 手势与输入设备分流；[S05] 快捷键；[S06] 手势锁。
- [S07] Android 画质增强；[S08] 字幕管线；[S09] 播放器设置模型。
- [S10] 选源器；[S11] 数据源操作；[S12] 筛选；[S13] 查询请求编辑。
- [S14] 进度记忆；[S15] 自动标记；[S16] 自动连播；[S17] 失败换源；[S18] 自动跳过状态。
- [S19] 弹幕外观；[S20] 播放页详情和弹幕弹层；[S21] 弹幕列表；[S22] 弹幕同步；[S23] 来源与缓存；[S24] 弹幕发送；[S25] 页内正则管理；[S26] 全局正则管理。
- [S27] 详情区选集；[S28] 全屏选集；[S29] 收藏入口；[S30] 内嵌完整条目详情。
- [S31] 剧集评论接线；[S32] 评论交互；[S33] 评论编辑；[S34] 举报。
- [S35] 一起看面板；[S36] 房间操作；[S37] 悬浮入口和导航；[S38] 播放同步。
- [S39] 外部链接；[S40] 资源链接生成；[S41] BT 播放缓存。
- [S42] 加载提示；[S43] Android 统计采集；[S44] 统计展示。
- [S45] 全局播放器设置；[S46] Android 专属设置；[S47] 当前空实现的播放通知入口。
