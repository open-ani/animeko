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
