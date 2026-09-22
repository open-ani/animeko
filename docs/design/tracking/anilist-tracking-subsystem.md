# 可扩展追踪子系统与 AniList 首个实现

> 状态：实现中  
> 日期：2026-09-23  
> 关联议题：[open-ani/animeko#3427](https://github.com/open-ani/animeko/issues/3427)  
> 实现 PR：[azusachino/animeko#4](https://github.com/azusachino/animeko/pull/4)

## 目标

Animeko 需要的是一个完整的追踪子系统，而不是只够调用 AniList 的 GraphQL 客户端。AniList 是第一个实现；后续追踪服务应当能够复用账号、搜索、绑定、刷新、编辑和同步流程，只提供服务自身的认证、状态、评分和远端 API 映射。

首个可运行版本以 Android 为目标。Desktop 和 iOS 在拥有等价的安全凭据存储并完成端到端验证前，不宣称支持登录。

## 主要参考：Mihon

设计以 Mihon 的追踪实现为行为参考，而不是从空白接口开始推测。研究基准固定为 Mihon revision [`f52d890`](https://github.com/mihonapp/mihon/tree/f52d890)。重点参考：

- [`Tracker.kt`](https://github.com/mihonapp/mihon/blob/f52d890/app/src/main/java/eu/kanade/tachiyomi/data/track/Tracker.kt)：服务能力、账号状态、状态/评分、搜索、绑定、刷新和更新的统一门面。
- [`BaseTracker.kt`](https://github.com/mihonapp/mihon/blob/f52d890/app/src/main/java/eu/kanade/tachiyomi/data/track/BaseTracker.kt)：登录状态、用户资料刷新和阅读进度触发的状态转换。
- [`TrackerManager.kt`](https://github.com/mihonapp/mihon/blob/f52d890/app/src/main/java/eu/kanade/tachiyomi/data/track/TrackerManager.kt)：追踪服务注册表及已登录服务筛选。
- [`Anilist.kt`](https://github.com/mihonapp/mihon/blob/f52d890/app/src/main/java/eu/kanade/tachiyomi/data/track/anilist/Anilist.kt)：AniList 状态、五种评分格式、登录、绑定和更新规则。
- [`AnilistApi.kt`](https://github.com/mihonapp/mihon/blob/f52d890/app/src/main/java/eu/kanade/tachiyomi/data/track/anilist/AnilistApi.kt)：GraphQL 查询、更新、删除及 `POINT_100` 内部评分。
- [`AddTracks.kt`](https://github.com/mihonapp/mihon/blob/f52d890/app/src/main/java/eu/kanade/domain/track/interactor/AddTracks.kt)：本地绑定写入边界。
- [`RefreshTracks.kt`](https://github.com/mihonapp/mihon/blob/f52d890/app/src/main/java/eu/kanade/domain/track/interactor/RefreshTracks.kt)：刷新远端状态后更新本地记录。
- [`SyncChapterProgressWithTrack.kt`](https://github.com/mihonapp/mihon/blob/f52d890/app/src/main/java/eu/kanade/domain/track/interactor/SyncChapterProgressWithTrack.kt)：阅读进度触发同步时的单调性保护。

AniList 协议细节同时以[官方 API 文档](https://docs.anilist.co/)为准，尤其是[认证](https://docs.anilist.co/guide/auth/)和[限流](https://docs.anilist.co/guide/rate-limiting)。Mihon 是产品工作流参考，AniList 官方文档是协议事实来源。

## 从 Mihon 继承的结构

| Mihon 概念 | Animeko 对应 | 说明 |
|---|---|---|
| `Tracker` | `TrackingProvider` | 一个服务的完整门面，不拆成互不关联的 CRUD 与账号接口。 |
| `id`, `name`, logo | `TrackingProviderInfo` + UI 资源映射 | common code 不暴露 Android drawable ID。 |
| `supportsReadingDates`, `supportsPrivateTracking` | `TrackingCapabilities` | UI 只展示服务实际支持的字段。 |
| `isLoggedInFlow`, 用户刷新 | `TrackingAccountState` | Accounts UI 观察登录、刷新及用户资料。 |
| status list | `TrackingStatusOption` | common code 保留统一状态，provider 提供显示选项和远端映射。 |
| score list/conversion | `TrackingScoreOption` + 0–100 `TrackingScore` | 沿用 Mihon AniList 的五种显示格式和 0–100 内部表示。 |
| `search` | `search` | 返回远端条目及绑定选择所需信息。 |
| `bind` | `prepareBinding` + 明确 reconciliation + `bind` | 先刷新远端状态，再由用户选择合并策略。 |
| `update(..., didReadChapter)` | `update(..., didWatchEpisode)` | 复用 watching/completed/rewatching 状态转换。 |
| `refresh` | `refresh` | 远端状态是刷新来源，不以旧本地缓存冒充成功。 |
| `DeletableTracker.delete` | `delete` | 远端删除与本地解除绑定是两个操作。 |
| `TrackerManager` | 尚待实现的 provider registry | Accounts、绑定和同步只依赖注册表及统一门面。 |

## 有意不照搬 Mihon 的部分

### 绑定必须先确认 reconciliation

Mihon 的 `Anilist.bind` 会把远端个人状态复制到待绑定对象，再立即更新远端。Animeko 必须先展示本地与远端状态，并要求用户选择保留远端、采用本地或明确覆盖。默认路径不得降低远端进度。

因此 `prepareBinding` 与 `bind` 分开：前者只读取，后者只接受已经确认的结果。该差异来自安全要求，不是另造一套 provider 工作流。

### 凭据不进入普通 preferences 或 Room

Mihon 当前通过 tracker preferences 保存凭据。Animeko 的约束更严格：token 不进入 Room、日志、截图、普通设置导出或备份。

Android 首个实现使用：

- Android Keystore 中不可导出的 AES-256 key；
- AES-GCM authenticated encryption；
- `noBackupFilesDir` 中的原子 ciphertext 文件；
- provider-scoped `TrackingCredentialStore`；
- logout 删除 ciphertext。

`utils/io` 的 `obscure` 明确只是带硬编码 key 的防窥混淆，不能用于追踪 token。

### KMP common code 不依赖 Android 类型

Mihon 的 `Tracker` 直接返回 drawable 与 string resource。Animeko common contract 使用稳定 ID 和显示模型，品牌图标及本地化文本由 presentation layer 映射。

### 不复制 Mihon 的 service locator

网络客户端、凭据存储和本地 repository 都通过 Animeko 的依赖注入提供。Provider DTO 不引用 Room entity、Compose state 或平台 Context。

## AniList 行为基线

AniList provider 必须具备：

1. 使用 `Viewer` 验证 token，并读取账号 ID、显示名、头像和 `scoreFormat`。
2. 支持 `CURRENT`、`COMPLETED`、`PAUSED`、`DROPPED`、`PLANNING`、`REPEATING`。
3. 支持 `POINT_100`、`POINT_10`、`POINT_10_DECIMAL`、`POINT_5`、`POINT_3`；内部统一为 0–100。
4. 搜索 `ANIME`，返回 AniList ID、标题、封面、总集数和 canonical URL。
5. 绑定前读取已有 `mediaListEntry`。
6. 保存后以服务返回值刷新本地状态。
7. 删除前解析 list-entry ID；没有远端 entry 时删除是幂等成功。
8. 传播 coroutine cancellation；区分未授权、限流和其他远端失败。
9. 自动同步不得改变本地 Animeko 操作的成功结果，并且只能推进到最新期望状态。

## 当前实现与剩余工作

PR #4 当前包含：

- `tracking/api` 的统一 provider contract；
- `tracking/anilist` 的 Ktor GraphQL adapter；
- AniList 账号、状态和评分映射；
- common/JVM MockEngine tests；
- Android Keystore credential-store 初版。

尚未完成：

- 在具备 Android SDK 的主机上编译并测试 Keystore adapter；
- 确认 AniList OAuth application/client ID 所有权及 redirect 配置；
- Android OAuth 与 Accounts UI；
- original-title-first 搜索及手动绑定 UI；
- reconciliation、手动编辑和错误/重试状态；
- 可持久化、可合并、可重试的 opt-in 自动同步；
- Android 端到端验证。

## 新增另一个追踪服务时

一个新 provider 至少要提供：

1. 稳定 provider ID、名称、网站及品牌 UI 映射；
2. Android 安全凭据 adapter 或明确禁用登录；
3. 登录验证、账号刷新与 logout；
4. capability、状态和评分映射；
5. search、prepare binding、bind、update、refresh、delete；
6. provider API fixture tests；
7. reconciliation 和进度不回退测试；
8. Accounts、绑定、更新、重启恢复和断开连接的 Android 验证证据。

不得先增加 provider-specific Room/Compose 类型到 common contract，也不得仅凭 common compilation 声称平台支持。
