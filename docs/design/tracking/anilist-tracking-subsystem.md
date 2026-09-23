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
| `TrackerManager` | 尚待实现的 provider registry | Accounts、绑定和同步最终应只依赖注册表及统一门面；当前 Android 路径仍直接创建 AniList provider。 |

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

## 2026-09-23 实现状态

**尚未完成通用追踪子系统。** Android 上的 AniList 路径可运行，但账号 UI、条目 UI、绑定存储和观看同步分别直接引用 AniList 实现。Bangumi 在同一个 Track sheet 中复用 Animeko 原有收藏控件，没有实现 `TrackingProvider`。当前文件名 `AniListTrackingSection` 同时包含 Bangumi 卡片，不能把它视为已完成的 provider registry 或插件边界。

| 能力 | 当前实现 | 验证边界 |
|---|---|---|
| AniList 账号 | Android OAuth、Keystore 凭据、Tracking accounts 中显示账号 | 已在连接的 Android 手机上完成登录；本轮 provider tests 与 Android 构建通过。Desktop/iOS 只提供占位实现。 |
| AniList 条目 | 手动搜索、绑定、状态、进度、1–10 分、起止日期、隐私、远端删除与本地解绑 | Provider MockEngine tests 覆盖部分 API 映射与更新；手机上完成搜索与卡片烟测。没有条目 UI 自动回归测试。 |
| 自动观看同步 | 单集完成与“全部标记看过”会触发 AniList hook；远端进度较新时不回退 | 曾用连接账号完成手机试验；本轮新增末集状态转换回归测试。hook 本身没有自动测试，也没有持久化失败重试。 |
| Bangumi | 已连接时显示原有收藏状态与操作；Bangumi 的剧集收藏、评分能力存在于 Animeko 其他界面 | Track sheet **没有** Bangumi 剧集进度和评分编辑。Bangumi 没有 tracking provider adapter。 |
| 扩展能力 | `TrackingProvider` 约束单个远端服务的数据操作 | 没有 provider registry、通用匹配存储、统一 Track sheet 状态机或多 provider 同步协调器。MyAnimeList 不能只靠注册一个 adapter 接入。 |

当前绑定 UX 对已存在的 AniList 条目保留远端状态；没有第二次确认。`prepareBinding` 与本地绑定记录由 AniList UI 直接协调，尚无通用 reconciliation 工作流。观看同步失败只记录不含 token 的错误类型并结束本次任务，不是 durable retry。上述缺口不得在 PR 描述或测试报告中算作完成。

### 本轮回归证据

- `:tracking:api:allTests :tracking:anilist:allTests`：通过。覆盖 provider 基础契约、AniList API 映射、现有条目保护、日期/隐私更新、删除，以及新增的末集 `CURRENT → COMPLETED` 回归。
- `:app:android:assembleDefaultDebug -Pani.android.abis=arm64-v8a`：通过；随后 `:app:android:installDefaultDebug` 安装到连接手机。
- 手机只读烟测：重装当前分支后，CITY THE ANIMATION 显示 `2 trackers`；Track sheet 显示 Bangumi 收藏按钮与 AniList `Completed · 13 / 13`。这不证明本轮发生了远端写入。
- 未覆盖：Bangumi Track sheet 进度/评分、账号中心的通用注册、绑定持久化迁移、同步失败重试、跨平台登录、UI 自动回归。不能把一次手机烟测替代这些测试。

## 为什么下一步需要统一工作流

新增 MyAnimeList 时，理想的改动集中在其 adapter、认证接线、品牌资源和注册清单。条目页、账号中心、同步 hook 不应再增加按 provider ID 分支。要达到这个目标，需要把三种变化放在各自的边界内：

1. **Provider** 负责远端账号和条目语义：搜索、状态与评分映射、读取、写入、能力声明。Bangumi 的账号依附 Animeko 会话；AniList 和未来 MyAnimeList 使用独立凭据。现有 `TrackingProvider.login/logout` 强制所有服务采用独立登录，接入 Bangumi 前须先分离账号连接策略与条目操作接口。
2. **匹配与绑定** 负责 Animeko `subjectId` 到 provider media ID 的关系。Bangumi 使用相同 ID；AniList/MyAnimeList 使用用户确认的匹配。通用存储必须以 provider、账号和 subject 为键，并兼容已有 `anilist-bindings` 数据；移除本地匹配和删除远端条目必须是不同操作。
3. **协调器** 从注册表加载已连接 provider，向 UI 暴露统一的账号、匹配、条目和错误状态，并处理观看事件。写入本地 Bangumi 记录先成功；外部同步按各 provider 执行，保持进度单调，失败进入可观察、持久化的重试队列。一个 provider 失败不能让另一个 provider 的成功被回滚或隐藏。
4. **Track sheet** 只渲染注册表返回的卡片和 capability 驱动的字段。所有 provider 使用同一状态、进度、评分编辑布局；日期、隐私等可选能力由声明控制。状态始终显示文字，可用少量语义色辅助区分，但不能只靠颜色表达含义。未连接账号不出现可操作卡片。Bangumi 的原有收藏/剧集/评分写入由 adapter 调用现有 repository，UI 不直接知道这些 repository。

这是接入第三个服务的验收约束，不是当前代码已经达到的状态。不要仅添加一个 `TrackingSection` 名称或未被调用的 registry，就宣称插件架构完成。

## 接入新的同步平台

在上述统一工作流落地后，接入 MyAnimeList 等平台按以下顺序进行：

1. 实现 provider 的账号连接、API、状态/评分转换和 capability；不要把 token 放进普通 preferences 或日志。
2. 选择直接 ID 或需人工搜索的匹配策略；复用通用绑定存储和现有匹配确认 UI。
3. 将 provider、匹配策略和品牌资源登记在唯一的注册清单；账号中心、Track sheet 与观看同步从该清单发现它。
4. 用 provider fixture 测试账号、搜索、已存在远端条目、更新、删除、限流和取消传播；用第三个 fake provider 验证注册后无需改动通用 UI/同步代码。
5. 用连接设备验证登录、搜索、绑定、状态/进度/评分、重启恢复、单集与全部标记看过、断网重试和断开连接。逐平台记录支持范围，不由 common 编译推断 Android、Desktop 或 iOS 的运行能力。

若第 3 步仍要求修改条目页或同步 hook 的 provider 分支，扩展边界尚未完成，不能把新平台作为纯 adapter 合入。
