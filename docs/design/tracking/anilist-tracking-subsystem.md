# 可扩展追踪子系统与 AniList 首个实现

> 状态：实现中  
> 日期：2026-09-23  
> 关联议题：[open-ani/animeko#3427](https://github.com/open-ani/animeko/issues/3427)  
> 实现 PR：[azusachino/animeko#4](https://github.com/azusachino/animeko/pull/4)

## 目标

Animeko 需要的是一个完整的追踪子系统，而不是只够调用 AniList 的 GraphQL 客户端。AniList 是第一个实现；后续追踪服务应当能够复用账号、搜索、绑定、刷新、编辑和同步流程，只提供服务自身的认证、状态、评分和远端 API 映射。

Android 已完成设备登录验证。macOS Desktop 使用独立的追踪凭据 DataStore 并注册 AniList 追踪入口；旧 Keychain 实现曾完成真实账号登录，新存储仍需重新授权及重启验证；远端写入仍需验证。iOS 尚未注册 AniList 登录入口。

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
| `TrackerManager` | `TrackingRegistry` | Track sheet 与观看同步从同一注册表发现 source；账号中心仍保留 AniList 专属登录控件。 |

## 有意不照搬 Mihon 的部分

### 绑定已有条目时保留远端状态

Mihon 的 `Anilist.bind` 会把远端个人状态复制到待绑定对象，再立即更新远端。Animeko 在用户选中标题后读取远端条目；已有条目原样保留，无第二次确认或写入。只有尚无远端条目时才建立计划观看条目。

`prepareBinding` 负责读取已有状态，source 的 `bind` 负责匹配记录及必要的新条目创建。后续用户主动编辑才覆盖状态、进度和评分。

### 追踪凭据独立于 Animeko 会话与备份

Mihon 当前通过 tracker preferences 保存凭据。Animeko 的约束更严格：token 不进入 Room、日志、截图、普通设置导出或备份。

Android 首个实现使用：

- Android Keystore 中不可导出的 AES-256 key；
- AES-GCM authenticated encryption；
- `noBackupFilesDir` 中的原子 ciphertext 文件；
- provider-scoped `TrackingCredentialStore`；
- logout 删除 ciphertext。

`utils/io` 的 `obscure` 明确只是带硬编码 key 的防窥混淆，不能用于追踪 token。

`TrackingCredentialStore` 是 provider-scoped 的读、写、删除边界。稳定的 `TrackingProviderId` 是存储键；新增追踪服务使用同一个桌面存储，不扩展 Animeko/Bangumi 的 `TokenSave`。macOS Desktop 将追踪 token 保存于独立的 DataStore 文件，并将其目录限制为当前用户访问；文件内容没有 Keychain 静态加密保护。该 DataStore 不进入设置快照、追踪匹配备份、日志或 UI。非机密的账号与标题匹配 ID 仍在 Java Preferences。

桌面适配器只读取独立 DataStore。已有 Keychain 凭据不自动导入，用户需重新授权 AniList；旧 Keychain 项不会被此适配器读取或删除。Android 继续使用 Keystore 加密文件；其它桌面平台未注册 AniList 入口。未来 iOS 适配器须选择本地存储实现并遵守相同的 provider 隔离和备份边界。

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

Android 与 macOS Desktop 的条目 UI 通过 `TrackingRegistry`、`TrackingCoordinator` 与 `TrackingSource` 协作；观看事件由 domain 层的 `TrackingEpisodeSynchronizer` 分发。Bangumi 和 AniList 各自实现 source，单栏与多栏页面只调用 `TrackingSection(subjectId)`。同一张能力驱动卡片绘制状态、进度和评分。账号中心的 AniList OAuth 控件仍是专属实现；持久化同步失败重试尚未实现，因此这还不是完整的跨平台追踪系统。

设置备份会导出 AniList 的非机密标题匹配关系（服务 ID、账号 ID、Animeko 条目 ID、AniList 媒体 ID），导入时按这些键合并到现有匹配，旧备份缺少该字段仍可导入。备份不包含 AniList token；离线或未登录时也可导入；之后连接与备份记录相同的 AniList 账号，匹配关系才会显示。Bangumi 使用相同 subject ID，不需要单独导出匹配关系。Android 原有 `anilist-bindings` preferences 保持兼容。

人工匹配搜索沿用图标按钮、清除输入按钮和键盘 Search 动作；输入变更清除过期结果。通用 `TrackingSection` 保持这些交互，而不为 AniList 单独构造搜索表单。

搜索结果使用 AniList 返回的封面和标题。搜索初始词遵循 Animeko 的“显示原名”设置：关闭时先用当前显示的本地化标题；若该查询没有结果，再尝试原名。用户手动改写查询后只执行输入的词，避免意外的第二次搜索。

备份选择框提供“应用设置”和“追踪匹配关系”两个选项，允许只导出其中一类；可保存文件或复制到剪贴板，恢复时可选择文件或剪贴板。文件名包含本地时间，格式为 `animeko_YYYY-MM-DD_HH-mm.animekobk`。`.animekobk` 文件使用 gzip 压缩，解压后的 JSON 包含 `format: animeko-backup`、`version: 1`、`settings`、`tracking` 字段。恢复时先验证格式与版本，再写入设置；未知版本被拒绝。恢复也接受先前导出的普通 JSON 文件及旧版设置剪贴板 JSON。`TrackingRegistry` 汇总所有已注册 source 的绑定记录，并按 `providerId` 将导入记录交回各 source；无本地匹配的 Bangumi source 返回空列表。新增追踪平台实现 `TrackingSource.exportBindings`、`validateBindings` 和 `applyValidatedBindings`，以自己的存储格式保存绑定，在导出边界转换为 `TrackingBindingRecord`；无需修改备份 UI 或文件结构。未知 `providerId` 在恢复前被拒绝，避免静默丢失。文件选择及内容选项参照 Mihon 的 `BackupCreator`；Mihon 的 `.tachibk` 文件使用 gzip 压缩的 protobuf，Animeko 的格式与之不兼容。备份不包含离线视频或完整观看数据库。应用设置类别包含现有 Animeko 会话数据，因此备份文件需要私密保存；追踪账号 token 不在其中。

| 能力 | 当前实现 | 验证边界 |
|---|---|---|
| AniList 账号 | Android OAuth 与 Keystore 凭据；macOS Desktop OAuth 回调与独立 DataStore 凭据 | Android 已在连接的手机上完成登录。macOS Desktop 的 DataStore 持久化需完成回归验证；真实 OAuth 回调与已连接账号行已验证，换存储后需重新授权；远端写入尚未验证。iOS 不注册 AniList 登录入口。 |
| AniList 条目 | 手动搜索、绑定、状态、进度、1–10 分、起止日期、隐私、远端删除与本地解绑 | Provider MockEngine tests 覆盖部分 API 映射与更新；手机上完成搜索与卡片烟测。没有条目 UI 自动回归测试。 |
| 自动观看同步 | 单集完成与“全部标记看过”会触发 AniList hook；远端进度较新时不回退 | 曾用连接账号完成手机试验；本轮新增末集状态转换回归测试。hook 本身没有自动测试，也没有持久化失败重试。 |
| Bangumi | `BangumiTrackingSource` 复用现有收藏、剧集和评分 repository；Track sheet 显示并编辑三项字段 | 当前 Animeko 会话有效时可用；会话未连接 Bangumi 时显示为 Animeko 收藏。新卡片仍需手机交互回归。 |
| 扩展能力 | `TrackingSource`、账号连接器和图标渲染器经注册表统一条目 UI、账号 UI 和观看事件入口 | AniList 沿用账号范围内的 `anilist-bindings`，没有迁移为通用绑定存储。 |

绑定已存在的 AniList 条目会保留远端状态；没有第二次确认。AniList source 继续读取原 `anilist-bindings`，避免丢失已有匹配。观看同步在一个 source 失败后仍尝试其他 source，但失败只记录不含 token 的错误类型并结束本次任务，不是 durable retry。上述缺口不得在 PR 描述或测试报告中算作完成。

## 当前模块边界

`tracking/api/TrackingSource.kt` 定义一个账号范围内的追踪源：连接状态、展示信息、能力、状态与评分选项，以及 `observe/search/bind/edit/unlink/episodeWatched`。`TrackingSnapshot` 区分已匹配媒体和远端列表条目；远端条目删除后仍可保留本地匹配。`TrackingEdit` 是类型化命令，UI 不传 Bangumi 或 AniList 的 API 字段。

平台 DI 构造 `DefaultTrackingRegistry`。Bangumi source 使用当前 Animeko 会话和原有收藏 repository；AniList source 包装 `TrackingProvider`，以账号 ID 与 subject ID 查找匹配。Android 沿用 Keystore 账号和原 `anilist-bindings`；macOS Desktop 使用独立 DataStore 凭据和 Java Preferences 匹配记录。两者的存储机制留在 adapter 内，不进入通用卡片。

`TrackingCoordinator` 只通过注册表查找 source，提供观察、搜索、绑定、编辑和解绑入口。`TrackingSection(subjectId, modifier)` 是两种详情页布局的唯一入口。卡片按 capability 绘制状态、进度、评分、日期、私密和删除操作；品牌图标由各平台注册的 `TrackingIconRenderer` 按 `TrackingProviderId` 提供。Bangumi 的离散正片列表与 AniList 的累计数字列表使用同一个选择弹层。完成状态下若 Bangumi 仍有未看剧集，另行询问是否全部标记看过。

`TrackingProviderId` 是用于持久化和注册表查找的开放类型，不使用封闭 enum。每个平台只在自己的实现中声明一次稳定 ID 常量。账号中心遍历已注册的 `TrackingAccountConnector`，由共同的 `TrackingAccountItem` 展示身份、连接状态及断开确认；连接器提供登录动作和可选详情页。Android manifest 明确声明每个 OAuth redirect host；`MainActivity` 通过 `TrackingAuthRedirectRouter` 将 host 交给平台注册的 handler，不包含 AniList 分支。新增平台需声明自己的 host，不能依赖通配 `ani://` 接收规则。

观看 hook 在本地操作成功后调用 domain 层的 `TrackingEpisodeSynchronizer`。AniList source 对正片整数集数执行单调远端进度更新，较新的远端进度不会回退；Bangumi source 不重复写入已由本地操作保存的剧集状态。一个 source 失败时 synchronizer 继续执行其余 source，并在结束后抛出错误供 hook 记录。当前没有持久化失败队列，离线事件不会在重启后自动重试。

### 接入新的同步平台

1. 在 `tracking/api` 的通用类型上实现一个 `TrackingSource`，在 adapter 内处理凭据、远端 API、状态与评分转换、匹配存储和能力声明。直接 ID 的服务可以像 Bangumi 一样不提供搜索；需要人工匹配的服务复用通用搜索与绑定界面。
2. 在平台 DI 中注册 source、账号连接器和图标渲染器。Track sheet 与观看 hook 从 `TrackingRegistry` 发现 source；账号中心与卡片分别从连接器和图标注册表发现平台。通用 UI 不增加服务名分支。需要 OAuth 回调的 Android 平台还须在 manifest 声明自己的 host，并注册 `TrackingAuthRedirectHandler`。
3. 测试账号恢复、搜索、已有远端条目保护、字段能力、编辑、删除、取消传播、观看进度单调性与失败隔离。用连接设备验证登录、绑定、编辑和重启恢复。若服务需要离线自动重试，应先补齐持久化失败队列及其测试，不得依赖当前一次性 hook。

### 扩展与恢复约束

- 恢复备份时，在写入任何设置或会话数据之前检查文件格式、schema 版本、追踪服务 ID 与绑定字段。每个 `TrackingSource` 负责验证自己的绑定记录格式；注册表先验证全部记录，再分发写入。导入不访问网络，也不要求追踪账号已经连接。绑定按账号 ID 隔离，只有重新连接对应账号后才进入追踪 UI。存储写入失败仍可能留下部分更改，完整事务恢复尚未实现。
- 搜索结果只属于发起它的查询版本；用户更改搜索词、清除输入或切换追踪服务后，旧请求结果不得重新显示。
- 观看事件向多个服务分发属于 domain 层；UI coordinator 只处理卡片观察与用户操作。一个服务失败后继续通知其他服务，最后向 hook 报告失败。
- 账号登录入口由已注册的 `TrackingAccountConnector` 提供。添加服务时注册自己的 provider、source、账号连接器和图标渲染器；通用卡片、账号行、绑定备份格式和观看分发不添加服务名分支。能力关闭时，provider 无需实现日期或隐私编辑。
- Animeko 未登录时，详情页保留登录入口，同时提供追踪账号设置入口。macOS Desktop 远端写入、iOS 登录和失败持久化重试仍未覆盖。

### 验证范围

- `:tracking:api:allTests :tracking:anilist:allTests :app:shared:app-data:testAndroidHostTest :app:shared:ui-subject:testAndroidHostTest :app:shared:ui-settings:testAndroidHostTest :app:android:assembleDefaultDebug -Pani.android.abis=arm64-v8a` 通过；测试覆盖第三个 source 的路由、跨 source 观看事件失败隔离，以及无效绑定在写入前被拒绝、离线账号的绑定可恢复。
- Android debug 构建已安装在连接的手机。应用正常启动；Tracking accounts 同时显示 Bangumi 与 AniList 图标、账号名和连接状态，两行均打开共同的账号操作对话框。此前 Slam Dunk 的 Track sheet 显示两个服务的状态、`7 / 101` 进度和评分字段，Bangumi 正片选择弹层可以打开。此轮只读检查没有证明新版本的远端写入。
- macOS Desktop 的 `:app:desktop:test` 验证独立 DataStore 的 provider 隔离、删除与私有目录；`createDistributable` 和打包应用启动通过，账号页显示未连接状态，目录权限为当前用户可访问。旧 Keychain 登录曾在桌面验证；新存储的真实 OAuth、重启恢复、远端写入回归、iOS 登录和失败持久化重试仍待完成；不能把构建与只读烟测当作这些能力的验证。
