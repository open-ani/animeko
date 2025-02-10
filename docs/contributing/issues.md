# 查找待解决的问题

Animeko 使用 [GitHub Issues](https://github.com/open-ani/animeko/issues) 追踪所有问题和新功能计划。
可以根据 issue 的属性来快速筛选待解决的问题。

## 推荐的筛选方式

- 解决 P1
  bug: [(label:P1) (type:bug)](https://github.com/open-ani/animeko/issues?q=is%3Aopen%20is%3Aissue%20label%3AP1%20type%3ABug)
- 解决 P1
  新功能: [(label:P1) (type:Feature OR type:"Meta Issue")](https://github.com/open-ani/animeko/issues?q=is%3Aopen%20is%3Aissue%20%20(label%3AP1)%20%20%20(type%3AFeature%20OR%20type%3A%22Meta%20Issue%22)%20%20)
- 解决 P1 或 P2 的新 UI
  功能: [(label:P1 OR label:P2) (type:Feature OR type:"Meta Issue")](https://github.com/open-ani/animeko/issues?q=is%3Aopen%20is%3Aissue%20label%3A%22s%3A%20ui%22%20%20(label%3AP1%20OR%20label%3AP2)%20%20%20(type%3AFeature%20OR%20type%3A%22Meta%20Issue%22))

## 属性列表

### 优先级

P0 - P3，各等级意义如下。

- P0：严重问题，需要停止其他工作，立即解决
- P1：重要问题，优先考虑
- P2：一般问题，可以等待
- P3：轻微问题，可以不用解决

推荐你选择 P1-P2 优先级的问题。

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
