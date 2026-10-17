# 外部链接与 URI schemes

Animeko 使用 `ani://` 接收外部应用或网页链接。条目 ID 和剧集 ID 均为 Bangumi ID，必须为正整数。

## Android 手机和平板

| URI | 行为 |
| --- | --- |
| `ani://subjects/{subjectId}` | 打开条目详情页。 |
| `ani://subjects/{subjectId}?ep={ep}` | 按 Bangumi 条目内序号 `ep` 查找正片，打开对应剧集播放页。 |
| `ani://episodes/{episodeId}` | 通过服务端 `GET /v2/episodes/{episodeId}` 查询所属条目，打开对应剧集播放页。支持正片、SP、OP 等剧集类型。 |

`ep` 是 Bangumi 的 **条目内序号**，不是系列序号 `sort`，也不是剧集 ID。例如分割放送的后半条目中，第一集可能为 `ep=1`、`sort=13`；应传 `ep=1`。支持正整数和 `.5` 序号，例如 `ep=1.5`。同类型剧集编号独立，`ep` 只匹配正片；精确指定 SP 等其他类型时使用 `ani://episodes/{episodeId}`。

`ep` 缺失、非法、无匹配或匹配不唯一时，打开条目详情页。查询剧集列表失败时也回退条目详情页，并通过提示说明错误原因。单独剧集 ID 不存在或查询失败时，保留当前页面并提示原因。非法 ID 和不支持的链接不触发导航。打开播放页后，资源选择与播放遵循应用已有设置。

## 其他平台

- iOS：不支持条目与剧集链接。
- Android TV：不支持。
- 桌面端：没有注册上述 `ani://` 导航入口。
