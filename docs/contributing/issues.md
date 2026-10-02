# 查找待解决的问题

Animeko 使用 [GitHub Issues](https://github.com/open-ani/animeko/issues) 追踪所有问题和新功能计划。
可以根据 issue 的属性来快速筛选待解决的问题。

## 推荐的筛选方式

- 解决 High 优先级的 bug: [field.priority:High type:Bug](https://github.com/open-ani/animeko/issues?q=is%3Aopen%20is%3Aissue%20field.priority%3AHigh%20type%3ABug)
- 解决 High 优先级的新功能: [field.priority:High (type:Feature OR type:"Meta Issue")](https://github.com/open-ani/animeko/issues?q=is%3Aopen%20is%3Aissue%20field.priority%3AHigh%20(type%3AFeature%20OR%20type%3A%22Meta%20Issue%22))
- 解决 High 或 Medium 优先级的新 UI 功能: [field.priority:High,Medium label:"s: ui" (type:Feature OR type:"Meta Issue")](https://github.com/open-ani/animeko/issues?q=is%3Aopen%20is%3Aissue%20field.priority%3AHigh%2CMedium%20label%3A%22s%3A%20ui%22%20(type%3AFeature%20OR%20type%3A%22Meta%20Issue%22))

各属性（优先级、问题分类、子系统标签、Milestone）的含义见 [Issue 属性列表](issue-labels.md)。
