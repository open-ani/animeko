# 下载管理

页面位于 `app/shared/ui-download`，业务与存储代码位于
`app/shared/app-data/src/commonMain/kotlin/domain/media`，分工如下：

| 位置 | 职责 |
| --- | --- |
| `ui-download` 模块 | 展示下载状态，管理页面选择，转发用户操作。 |
| `domain/media/download` 包 | 下载实体与管理器，添加下载的选源会话，批量暂停、继续和删除。 |
| `domain/media/cache` 包 | 持久化下载记录，通过下载引擎执行传输和文件操作。 |

## 实体与管理器

[MediaDownloadManager][manager] 与应用同生命周期，聚合所有 `MediaCacheStorage` 的记录。
它为每条底层 `MediaCache` 记录维持一个稳定的 [MediaDownload][download] 实例：存储的记录实例不变时，
对应的 `MediaDownload` 实例也不变；记录被删除或被存储替换后，对应实例被关闭并释放其共享协程。
多个存储持有相同 id 的记录时，只保留注册顺序靠前的一个。

`snapshots(subjectId?)` 与 `downloadsForSubject` 在每个存储都给出首个记录列表后才输出；
`downloads` 是供同步读取的 `StateFlow`，在此之前为空列表。存储在应用启动时异步恢复持久化记录，
恢复完成前它们给出的列表可能为空，因此启动阶段的空列表不代表没有下载。
`findCaches` 直接读取各存储的当前记录，与刚完成的创建或删除保持一致，供弹幕缓存策略等同步判断使用。

`MediaDownload` 提供两样东西：

- `snapshot`：共享的热流，包含状态、进度、大小、平均速度、可播放性和正在执行的操作。
  进度与速度每秒最多更新一次；状态、可播放性与操作的变化立即反映。
  最后一个订阅者离开 5 秒后停止计算，再次订阅时立即重放最近的快照。
  列表增删导致观察者重新订阅时，上游的速度统计不会重置。
  底层统计流抛出异常时，快照变为失败状态并保持列表可用；下一次从无订阅者到有订阅者时重新收集上游。
- `operation`：已排队或正在执行的操作，由 `DownloadOperations` 维护，供页面显示忙碌。
  `pause` 只暂停进行中的下载，`resume` 只继续已暂停的下载，其他状态下不做任何事。

管理器的其他入口：`downloadStatusForEpisode` 直接由记录的状态与文件统计汇总某一集的状态，不计算速度，
供剧集列表和收藏进度使用；`overallStats` 汇总各存储的传输统计；
`createDownload` 在指定存储中持久化记录：任一存储已有同一资源同一剧集的记录时直接返回该记录，
默认存储由 `defaultStorageFor` 决定：按注册顺序取第一个支持该资源的存储；
BT 资源由 `selectTorrentStorage` 决定，一个种子只归一个引擎：已有该种子任一集记录的存储优先，
其次是正在播放它的引擎，最后是偏好（PikPak 能提供该种子时选它，否则 Anitorrent）。
种子归属以持久化记录为准；所属引擎不可用时，添加该种子的下载失败，已有记录保留。
PikPak 恢复可用时，存储补回尚未恢复的记录，已有下载保留其文件句柄和统计订阅。
播放 BT 资源时自动保存的下载（`CacheOnBtPlayExtension`）走同一选择，并把实际播放的引擎作为提示；
同集切源产生的自动记录在切集或退出播放时统一检查，未传输任何字节的记录被删除。
已有进度或已由用户转为持久下载的记录保留。PikPak 跟随播放的记录不展示在下载页，显式添加下载会将其转为完整下载。

## 添加下载

[DownloadRequestSession][session] 为一个条目的若干剧集添加下载，按传入顺序处理，一集持久化完成后才处理下一集。
每集依次经过以下状态：

1. `Preparing`：从条目收藏信息中取得条目与剧集信息，并检查本条目已有的下载。
   若已有 `episodeRange` 覆盖本集的合集资源（包括本会话刚创建的），直接复用它，不再选源。
2. `AwaitingSelection`：创建查询会话与选源器，等待用户通过 `select` 选定资源。
   查询在此期间持续进行，与选源弹窗是否可见无关。
3. `Creating`：选中资源并保存选源偏好（含弹窗内对字幕组、分辨率等的修改），
   然后通过 [AddDownloadUseCase][add] 持久化。持久化在应用作用域执行，
   会话在此期间被取消时这一集仍会完成，只是不再处理后续剧集。

任何一步失败都以 `Finished(error)` 结束会话，已经创建的下载保留。`cancel` 取消查询、停止等待并跳过剩余剧集。
`select` 只接受当前正在等待选源的那一集。`select` 与 `cancel` 可在任意线程调用。
会话由 `DownloadRequestSessionFactory` 创建并随传入的父作用域取消；页面 ViewModel 用自己的作用域创建会话，
离开页面即取消尚未选源的请求。

`AddDownloadUseCase` 是用户发起添加的提交步骤：持久化成功即视为添加成功，弹幕缓存与统计事件在后台进行，
其失败不影响结果。

## 已有下载：观察与操作

页面通过 `MediaDownloadManager.snapshots` 观察下载，把 `DownloadSnapshot` 映射为列表项，
`operation` 非空的项显示为忙碌并禁用相关按钮。

[DownloadOperations][operations] 在应用作用域串行执行暂停、继续与删除：同一时刻只有一个操作在执行，
批次按提交顺序排队。`submit(ids, operation)` 在提交时固定目标集合并把它们标记为忙碌，
已不存在的 id 被忽略，已在排队或执行中的下载被跳过（页面已为忙碌的项禁用按钮）。
返回的 `Deferred` 给出执行时抛出异常的下载：页面关闭或停止等待都不会中断已提交的操作。
删除经由 `DeleteCacheUseCase`，它在删除记录后清理不再需要的弹幕缓存。

页面通过 `DownloadOperationRunner` 提交操作并累计失败数，失败提示由页面单独收集这个计数显示，
用户关闭提示后归零；它独立于页面状态流，页面关闭后不再统计。

## 页面接入

一个条目的下载页状态与操作封装在 [SubjectDownloadsPresenter][presenter] 中，它不是 ViewModel：
由持有页面的 ViewModel 创建，生命周期由 `close` 显式结束。独立的条目下载页的 `SubjectDownloadsViewModel`
随导航条目存活，在存续期间持有一个实例；全局页的 `DownloadManagementViewModel` 通过 `selectSubject`
随详情栏所选条目切换实例，切换时关闭上一个实例并取消其进行中的选源会话；新实例在下一帧才就绪，
详情栏在此期间显示与新实例首个状态一致的加载态（`SubjectDownloadsDetailPane(presenter = null)`），不会闪现旧条目或空占位。
全局页把分组里已知的条目名作为初始标题传给实例，标题不经历加载状态；条目信息加载完成后换成正式名称。两个页面的 ViewModel 都随导航条目存活，
旋转屏幕、切换底部标签或前进到详情页再返回，进行中的选源请求都保留。

条目下载页和全局页的详情栏共用 `SubjectDownloadsHost(presenter)`，统一挂接状态、选源弹窗、权限提示和错误反馈。
presenter 持有当前会话并把会话状态映射为页面状态：

- `Preparing` 与 `Creating` 显示为忙碌，此时其他剧集的下载按钮不可用；`AwaitingSelection` 不算忙碌，
  行内仍显示下载按钮，点击可重新展示被隐藏的弹窗。
- `AwaitingSelection` 对应选源弹窗；`Finished(error)` 对应添加失败的提示；取消或完成后不显示任何弹窗。
- 正在等待其他剧集选源时请求新的剧集，会取消当前会话并为新剧集开启会话；正在准备或持久化时忽略新的请求。
  只有真正开启了新会话时才申请通知权限。
- 条目信息与下载列表在页面重新订阅时保留上一次的值，只有用户主动重新加载时才回到加载中。

`DownloadManagementViewModel` 观察全部下载，按条目分组并结合本地收藏信息展示。
多选范围由各自页面持有：条目页限定本条目，全局页可以跨条目选择。

资源查询与筛选的细节见[选源](media-selector.md)，底层存储见[媒体缓存](media-cache.md)。

[manager]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/MediaDownloadManager.kt
[download]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/MediaDownload.kt
[session]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/DownloadRequestSession.kt
[add]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/AddDownloadUseCase.kt
[operations]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/media/download/DownloadOperations.kt
[presenter]: ../../../../app/shared/ui-download/src/commonMain/kotlin/ui/download/subject/SubjectDownloadsPresenter.kt
