# 下载管理

用户主动保存、暂停、继续和删除的视频称为“下载”。`ui-download` 提供下载管理页面；
`app-data/domain/media/download` 提供添加下载、观察状态和执行操作的业务入口。

`MediaDownloadManager` 位于 `domain/media/download`，管理跨存储的持久化视频下载，
包括手动下载与播放时自动保存的记录。调用方统一使用 `downloadManager`，通过
`downloadsForSubject`、`downloadStatusForEpisode`、`findFirstDownload` 和 `deleteDownload` 等方法访问下载。
它是直接注册到依赖注入容器的具体类，生命周期与应用一致。

`MediaCacheStorage`、`MediaCache` 和 engine 是实际存储与传输的底层实现。
下载管理器不负责播放引擎的临时缓冲；既有存储记录和页面使用的下载 ID 保持兼容。
引擎清理未引用的播放文件时，应保留下载记录引用的文件。

## 模型与职责

- `EpisodeDownloadItem`：剧集的展示信息，没有请求器或协程。
- `DownloadSnapshot`：domain 提供的一份下载状态快照。`id` 对应底层 `MediaCache.cacheId`。
- `DownloadItem`：下载行的不可变展示数据。同一剧集可以对应多个下载项。
- `SubjectDownloadListItem`：未下载的剧集行或已有下载行，列表由纯函数 `buildSubjectDownloadItems` 组装。
- `SubjectDownloadsViewModel`：持续组合条目元数据、下载、播放历史和操作状态。
- `AddDownloadsSession`：管理一个条目的多集查询、选择和确认。
- `DownloadPlan` / `EpisodeDownloadSpec`：提交时冻结的逐集清单，不持有 selector、Flow 或协程。
- `SubmitDownloadsUseCase`：在应用作用域接收清单、去重并返回逐集结果。

## 添加下载

`AddDownloadsSessionFactory` 负责加载条目与剧集信息、创建逐集查询和选择器、保存偏好，
并根据已有季度资源尝试复用下载资源。

`AddDownloadsSession` 的阶段为 `Editing`、`Submitting`、`Completed`；没有请求时为 `Idle`。
编辑状态以剧集 ID 为键，分别保存各集的 `Preparing`、`ChoosingMedia`、`SelectingMedia`、`Ready` 或 `Failed` 状态。

- `start(episodeIds)` 开始一个请求；`setEpisodes(requestId, episodeIds)` 修改选集，保留仍被选中剧集的查询和资源选择，只释放移除的剧集。
- 每个剧集有独立查询作用域；同时最多准备三个剧集上下文。请求 ID 和作用域身份共同拦截过期回调，包括移除后重新添加同一剧集的情况。
- `selectMedia(requestId, episodeId, media)` 只更新指定剧集，先保存偏好，再冻结该集的资源和元数据。普通 WEB 资源需要逐集选源；合集可以为不同剧集提供同一个 `Media`。
- 全部剧集就绪后，`submit(requestId)` 把 `DownloadPlan` 同步交给应用作用域，再释放查询。每个 `EpisodeDownloadSpec` 包含条目、剧集、资源和元数据，执行时读取清单中的快照。
- `Completed` 保留逐集 `Created`、`AlreadyExists` 或 `Failed` 结果。重试仅提交失败项，并与之前成功项合并；准备阶段重试仅重建失败剧集的上下文。
- 状态转换在短同步锁中完成，查询和持久化在锁外运行。提交过程中拒绝重复确认、替换和取消。

单集页面启用 `submitWhenReady`，选源后直接提交只有一项的清单。
领域会话已经支持多集编辑和一次确认，批量选集 UI 尚未接入。

`SubmitDownloadsUseCase.submit` 同步接收不可变清单，由应用作用域中的单个消费者按顺序创建。
执行每一项前按条目、剧集和资源身份检查已有下载，重叠批次不会重复创建同一目标；一项失败不影响后续项。
关闭页面只取消查询和结果观察，已接收的整份清单继续执行。应用退出会取消在途、排队和后续提交的结果。

`CreateEpisodeDownloadUseCase` 是逐项持久化操作，通过 `MediaDownloadManager.defaultStorageFor` 使用默认存储：
按注册顺序使用第一个支持资源的存储；启用且支持该资源的 PikPak HTTP 引擎优先处理 BT 资源。
创建调用返回代表配置已持久化，并不代表视频已下载完成；弹幕准备和埋点不改变持久化成功的结果。

HTTP 新任务按资源 ID、条目 ID 和剧集 ID 生成稳定的 `http-v2-` 标识，合集的不同剧集有独立任务及输出文件。
恢复时优先查找新标识，找不到则沿用旧的资源 ID 标识，既有下载文件无需改名。

## 观察与操作

`ObserveDownloadsUseCase` 为每个下载维护独立收集任务，增加其他下载不会重置已有项的速度统计。
删除下载或结束观察时释放相应任务。首次列表及其下载快照就绪后才发出结果，空列表表示已加载且没有下载。

`DownloadOperations.submit` 在事件回调中同步接收 ID 快照，把暂停、继续和删除命令放入同一个队列。
应用作用域中的单个消费者按提交顺序执行，前一个操作挂起时，后续操作等待它完成。
ViewModel 的协程只等待结果以显示错误；离开页面不会取消已接受的命令。
应用作用域结束时，正在执行及尚未执行的命令结果都会取消，后续提交也会立即返回已取消的结果。

批量操作提交完整选择范围，不根据可能滞后的 UI 状态预先筛选。
执行时重新查找下载并检查实际状态，跳过已经删除的记录，完成的下载不会被暂停或恢复。
批量操作返回逐项失败信息，其余项继续执行；创建、暂停、继续和删除均由存储系统提供实际状态。

## UI 入口与选择范围

独立的 `SubjectDownloadsScreen` 和全局页的 `SubjectDownloadsDetailPane` 共用
`SubjectDownloadsFeature`、`SubjectDownloadsContent` 和 `SubjectDownloadRequestDialogs`。
Feature 负责 ViewModel 组装、资源弹窗、通知权限及错误反馈；Content 只消费展示数据和操作回调。

独立页面持有本条目的多选状态。全局页面持有跨条目的多选状态并传给详情栏。
删除确认固定操作目标，列表实际移除成功项后再清理选择，失败项保持选中。
剧集元数据加载失败时仍展示已有下载，并提供重试入口。

## 验证

```shell
./gradlew :app:shared:app-data:desktopTest --tests '*domain.media.download.*' :app:shared:ui-download:desktopTest :app:shared:compileKotlinDesktop
```

测试覆盖多集编辑、查询保留和释放、过期回调、清单快照、重复提交、重叠批次去重、部分失败重试、
默认存储、HTTP 合集任务隔离及旧下载恢复，以及暂停恢复顺序、应用退出和页面多选交互。
UI 测试使用合成输入，不需要真实窗口或系统鼠标。
