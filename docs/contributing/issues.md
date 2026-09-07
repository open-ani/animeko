# 查找待解决的问题

Animeko 使用 [GitHub Issues](https://github.com/open-ani/animeko/issues) 追踪所有问题和新功能计划。
可以根据 issue 的属性来快速筛选待解决的问题。

## 推荐的筛选方式

- 解决 High 优先级的 bug: [field.priority:High type:Bug](https://github.com/open-ani/animeko/issues?q=is%3Aopen%20is%3Aissue%20field.priority%3AHigh%20type%3ABug)
- 解决 High 优先级的新功能: [field.priority:High (type:Feature OR type:"Meta Issue")](https://github.com/open-ani/animeko/issues?q=is%3Aopen%20is%3Aissue%20field.priority%3AHigh%20(type%3AFeature%20OR%20type%3A%22Meta%20Issue%22))
- 解决 High 或 Medium 优先级的新 UI 功能: [field.priority:High,Medium label:"s: ui" (type:Feature OR type:"Meta Issue")](https://github.com/open-ani/animeko/issues?q=is%3Aopen%20is%3Aissue%20field.priority%3AHigh%2CMedium%20label%3A%22s%3A%20ui%22%20(type%3AFeature%20OR%20type%3A%22Meta%20Issue%22))

## 属性列表

### 优先级

使用组织级 issue 的 `Priority` 字段。旧的 P0、P1、P2、P3 labels 已过时，不再用于设置或筛选优先级。

- Urgent：严重问题，需要停止其他工作，立即解决
- High：重要问题，优先考虑
- Medium：一般问题，可以等待
- Low：轻微问题，可以不用解决

推荐你选择 High 或 Medium 优先级的问题。新 issue 会自动分类并填写空缺的 Priority；已经设置的值会保留。

自动分类中的「核心功能」指以下主要使用流程：

- 找到番剧：搜索、浏览条目、进入剧集列表。
- 找到可播放资源：获取数据源、匹配剧集、选择资源。
- 正常观看：启动播放、正常输出音画、暂停和拖动进度。
- 继续追番：管理追番列表、记录和同步观看进度。
- 离线观看：下载缓存、播放已缓存的剧集。

涉及核心功能不代表自动判为 High。需要判断流程是否被阻断或严重影响、涉及哪些用户或平台、是否局限于特定番剧或数据源，以及是否有可行的替代方案。例如：

- 常见受支持平台上普遍无法播放，或升级后稳定出现启动崩溃：High。
- 某个数据源的特定番剧匹配失败，其他资源可用：通常 Medium。
- 播放按钮布局有轻微不便，或截图边缘有小瑕疵，不影响正常使用：通常 Low。

核心功能的新需求通常为 Medium。回归也需要有显著用户影响才能判为 High；轻微视觉回归通常仍为 Low。
影响范围和替代方案必须有报告中的事实支持，不能从单个报告推断所有用户都受影响，也不能臆测存在替代方案或将未提及视为不存在。
Urgent 留给需要停止其他工作的紧急情况；证据不足以支持其他等级时默认 Medium。

当前 Priority 字段仅组织成员可见，需要使用组织成员账号登录才能查看和按该字段筛选。

### 问题分类

- Meta Issue：用于讨论一个整体方向，通常会有一些 sub-issues。例如"本地播放器功能"。
- Feature：一个确定的新功能。
- Bug：错误，即一个不正确的结果。
- Performance：结果正确，但加载速度慢、耗电等。
- Problem：一个开放性问题。

### 子系统标签

用于标记问题所属的子系统，例如 "player"、"ui" 等。

### Milestone

此问题的目标版本。这说明该问题已经由项目组决定在该版本发布之前解决，你可以跳过这些问题。
