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

## 添加下载：选源与执行的边界

[AddDownloadsSession][session] 管理一个条目的选源过程，由页面 ViewModel 持有。
它按剧集维护查询、选择和错误状态；`AddDownloadsSessionFactory` 负责接入剧集信息、资源查询、选源器和偏好存储。
修改选集范围时，保留仍被选中剧集的查询和选择，取消被移除剧集的查询。
异步查询返回时会校验请求 ID 和查询任务身份，避免过期结果写入当前状态。

选源结果通过 [DownloadPlan][plan] 交给创建流程。清单中的每个 `EpisodeDownloadSpec`
包含一集的条目、剧集、资源和下载元数据，是创建任务所需的不可变快照。
**清单不持有选源器或查询任务，因此提交后可以释放选源资源，创建过程也不依赖页面继续存活。**

[SubmitDownloadsUseCase][submit] 在调用时将清单入队，由应用作用域按顺序处理。
它负责去重和汇总结果，实际创建每一项时调用 `CreateEpisodeDownloadUseCase` 选择默认存储并持久化下载。
这里的成功表示下载项创建成功；传输进度由下载引擎提供。

创建流程有以下约束：

- 全部选中剧集就绪后才能提交；提交期间禁止修改清单或重复确认。
- 每项创建前，按条目 ID、剧集 ID 和资源 ID 检查已有下载，重叠清单可复用同一记录。
- 结果按项返回；一项失败不影响其他项，重试仅提交失败项。
- 关闭页面只取消查询和结果观察，已接收的创建任务继续执行。
- 清单按顺序创建任务；视频传输的并发由下载引擎控制。

会话和清单支持多集。当前页面启用 `submitWhenReady`，单集选源完成后自动提交；批量选集 UI 尚未接入。

## 已有下载：观察与操作

[ObserveDownloadsUseCase][observe] 将底层下载整理为 `Flow<List<DownloadSnapshot>>`，
供 ViewModel 组合剧集信息和观看记录。每个下载独立收集进度，列表增删不会重置其他下载的速度统计。
首次收到全部下载项的状态后才输出列表，因此空列表表示已加载且没有下载。

[DownloadOperations][operations] 为暂停、继续和删除提供一个有序队列。
**调用方应在事件回调中直接调用 `submit`，再启动协程等待结果。**
入队发生在协程调度之前，连续的暂停、继续请求才能保持用户提交的顺序。
已入队的操作由应用执行，页面关闭不取消这些操作。

批量操作提交完整的选中 ID 集合，由执行者检查实际下载状态。
不能依据页面快照过滤目标，否则在暂停已执行、UI 尚未刷新时，随后的继续请求可能被过滤掉。
普通操作错误按项返回，其余项继续处理。

## 页面复用与存储规则

条目下载页和全局页的详情栏共用 `SubjectDownloadsFeature`，统一组装 ViewModel、选源弹窗、权限提示和错误反馈。
[SubjectDownloadsViewModel][view-model] 负责组合展示数据，`SubjectDownloadsContent` 负责渲染和上报事件。
多选范围由各自页面持有：条目页限定本条目，全局页可以跨条目选择。

下载统一使用默认存储：按注册顺序选择支持该资源的存储；BT 资源优先使用已启用且能够解析资源的 PikPak HTTP 引擎。
HTTP 任务标识包含资源、条目和剧集 ID，保证合集中的各集有独立任务和输出文件。
已有记录的标识兼容处理封装在 `HttpMediaCacheEngine` 内。

可从 ViewModel 的操作入口开始阅读，再沿调用进入会话或操作队列。
资源查询与筛选的细节见[选源](media-selector.md)，底层存储见[媒体缓存](media-cache.md)。

[view-model]: ../../../../app/shared/ui-download/src/commonMain/kotlin/ui/download/subject/SubjectDownloadsViewModel.kt
[session]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/AddDownloadsSession.kt
[plan]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/DownloadPlan.kt
[submit]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/SubmitDownloadsUseCase.kt
[manager]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/MediaDownloadManager.kt
[observe]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/ObserveDownloadsUseCase.kt
[operations]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/DownloadOperations.kt
