# 下载管理

页面位于 `app/shared/ui-download`，业务与存储代码位于
`app/shared/app-data/src/commonMain/kotlin/domain/media`，分工如下：

| 位置 | 职责 |
| --- | --- |
| `ui-download` 模块 | 展示下载状态，管理页面选择，转发用户操作。 |
| `domain/media/download` 包 | 管理选源过程、创建下载、汇总进度，以及执行暂停、继续和删除。 |
| `domain/media/cache` 包 | 持久化下载记录，通过下载引擎执行传输和文件操作。 |

[MediaDownloadManager][manager] 是业务层访问底层存储的统一入口，负责跨存储查询和默认存储选择。
它与应用同生命周期，供下载页面和播放时自动保存视频的逻辑共同使用。

## 添加下载

[AddDownloadsSession][session] 按传入的剧集顺序执行：查询资源、等待用户选源、保存选择偏好、创建下载。
一集创建成功后才处理下一集。可复用的合集资源直接用于创建对应剧集的下载。
任何一步失败都会结束流程并显示错误，已经创建的下载保留。

会话只维护当前剧集、待选源数据和错误。每次添加创建独立实例，UI 回调绑定该实例。
ViewModel 启动协程调用 `session.run()`，并在同一串行上下文中转发选源操作。取消该协程会结束会话。
每集的资源查询作用域在该集处理结束时释放。

`AddDownloadsSessionFactory` 负责组装依赖。Session 直接查询仓库、使用选源器，
在创建下载前捕获条目、剧集、资源和元数据，并在应用作用域中调用 `MediaDownloadManager.createDownload`，等待该集持久化完成。
关闭会话会取消查询、停止等待和后续剧集的处理；已经交给应用作用域的那次创建继续执行。

manager 选择默认存储，由存储在锁内查重和持久化，成功后在后台保存弹幕并记录统计事件。
创建成功包括复用已有记录；后续传输进度由下载引擎提供。
当前页面传入单集，批量选集 UI 尚未接入。

## 已有下载：观察与操作

[ObserveDownloadsUseCase][observe] 将底层下载整理为 `Flow<List<DownloadSnapshot>>`，
供 ViewModel 组合剧集信息和观看记录。每个下载独立收集进度，列表增删不会重置其他下载的速度统计。
首次收到全部下载项的状态后才输出列表，因此空列表表示已加载且没有下载。

[DownloadOperations][operations] 在应用作用域执行暂停、继续和删除，按下载 ID 防止重复操作。
应用组装层提供与应用同生命周期的串行作用域，用于登记和清理 `busyIds`，实际操作在后台执行。
同一下载忙时拒绝新的操作，不同下载可以并行。
`submit` 返回逐项错误和被拒绝的 ID；页面关闭或停止等待结果，不会取消已开始的操作。

页面订阅共享的 `busyIds`，将对应下载标记为忙碌，并禁用单项和相关批量操作按钮。
重新进入页面也使用这份忙碌状态。批量操作提交选中 ID，由执行者根据实际状态判断能否操作；
单项失败会释放其忙碌状态，其余项继续执行。

## 页面复用与存储规则

条目下载页和全局页的详情栏共用 `SubjectDownloadsFeature`，统一组装 ViewModel、选源弹窗、权限提示和错误反馈。
[SubjectDownloadsViewModel][view-model] 负责组合展示数据，`SubjectDownloadsContent` 负责渲染和上报事件。
多选范围由各自页面持有：条目页限定本条目，全局页可以跨条目选择。

下载统一使用默认存储：按注册顺序选择支持该资源的存储；BT 资源优先使用已启用且能够解析资源的 PikPak HTTP 引擎。
HTTP 任务标识包含资源、条目和剧集 ID，保证合集中的各集有独立任务和输出文件。
已有记录的标识兼容处理封装在 `HttpMediaCacheEngine` 内。

可从 ViewModel 的操作入口开始阅读，再沿调用进入选源会话或下载操作。
资源查询与筛选的细节见[选源](media-selector.md)，底层存储见[媒体缓存](media-cache.md)。

[view-model]: ../../../../app/shared/ui-download/src/commonMain/kotlin/ui/download/subject/SubjectDownloadsViewModel.kt
[session]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/AddDownloadsSession.kt
[manager]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/MediaDownloadManager.kt
[observe]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/ObserveDownloadsUseCase.kt
[operations]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/DownloadOperations.kt
