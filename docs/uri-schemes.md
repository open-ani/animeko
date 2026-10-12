# 外部链接与 URI schemes

Animeko 使用 `ani://` 接收外部应用或网页链接。条目 ID 和剧集 ID 均为 Bangumi ID，必须为正整数。

## Android 手机和平板

| URI | 行为 |
| --- | --- |
| `ani://subjects/{subjectId}` | 打开条目详情页。 |
| `ani://subjects/{subjectId}?ep={ep}` | 按 Bangumi 条目内序号 `ep` 查找正片，打开对应剧集播放页。 |
| `ani://episodes/{episodeId}` | 通过服务端 `GET /v2/episodes/{episodeId}` 查询所属条目，打开对应剧集播放页。支持正片、SP、OP 等剧集类型。 |
| `ani://qr-login?requestId={requestId}` | 打开扫码登录确认页。`requestId` 来自 Animeko 扫码登录会话，需要 URL 编码。用户在确认页决定是否授权。 |

`ep` 是 Bangumi 的 **条目内序号**，不是系列序号 `sort`，也不是剧集 ID。例如分割放送的后半条目中，第一集可能为 `ep=1`、`sort=13`；应传 `ep=1`。支持正整数和 `.5` 序号，例如 `ep=1.5`。同类型剧集编号独立，`ep` 只匹配正片；精确指定 SP 等其他类型时使用 `ani://episodes/{episodeId}`。

`ep` 缺失、非法、无匹配或匹配不唯一时，打开条目详情页。查询剧集列表失败时也回退条目详情页。单独剧集 ID 不存在或查询失败时，保留当前页面并提示重试。非法 ID 和不支持的链接不触发导航。打开播放页后，资源选择与播放遵循应用已有设置。

以上链接支持冷启动和应用已运行时接收。需要查询服务端的链接受网络可用性影响。

调试版本可用 ADB 验证（默认调试包名为 `me.him188.ani.debug2`，发布版本为 `me.him188.ani`）：

```sh
adb shell am start -a android.intent.action.VIEW -d 'ani://subjects/12' me.him188.ani.debug2
adb shell am start -a android.intent.action.VIEW -d 'ani://subjects/12?ep=1' me.him188.ani.debug2
adb shell am start -a android.intent.action.VIEW -d 'ani://episodes/1027' me.him188.ani.debug2
```

Android 手机 manifest 还声明了 `ani://bangumi-oauth-callback`，用于历史 OAuth 回调兼容；当前 Activity 不将它作为公开导航入口处理。

## 其他平台

- iOS：支持 `ani://qr-login?requestId={requestId}`。条目与剧集链接没有接入 iOS 的 URL 处理入口。
- Android TV：manifest 声明了 `ani://subjects/{subjectId}`，但 Activity 没有链接导航处理，不能依赖它跳转到条目。
- 桌面端：没有注册上述 `ani://` 导航入口。

`anime-play://` 不在支持范围内。
