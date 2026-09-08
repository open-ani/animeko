# 下载管理

用户主动保存、暂停、继续和删除的视频称为“下载”。`ui-download` 提供下载管理页面；
`app-data/domain/media/download` 提供添加下载、观察状态和执行操作的业务入口。

`MediaDownloadManager` 位于 `domain/media/download`，管理跨存储的持久化视频下载，
包括手动下载与播放时自动保存的记录。调用方统一使用 `downloadManager`，通过
`downloadsForSubject`、`downloadStatusForEpisode`、`findFirstDownload` 和 `deleteDownload` 等方法访问下载。
它是直接注册到依赖注入容器的具体类，生命周期与应用一致。

`MediaCacheStorage`、`MediaCache` 和 engine 是实际存储与传输的底层实现。
下载管理器不负责播放引擎的临时缓冲；底层存储格式、下载 ID 与恢复流程保持兼容。
引擎清理未引用的播放文件时，应保留下载记录引用的文件。

## 模型与职责

- `EpisodeDownloadItem`：剧集的展示信息，没有请求器或协程。
- `DownloadSnapshot`：domain 提供的一份下载状态快照。`id` 对应底层 `MediaCache.cacheId`。
- `DownloadItem`：下载行的不可变展示数据。同一剧集可以对应多个下载项。
- `SubjectDownloadListItem`：未下载的剧集行或已有下载行，列表由纯函数 `buildSubjectDownloadItems` 组装。
- `SubjectDownloadsViewModel`：持续组合条目元数据、下载、播放历史和操作状态。
- `AddEpisodeDownloadSession`：管理当前添加下载会话。

## 添加下载

`EpisodeDownloadSessionFactory` 负责加载条目与剧集信息、创建资源查询和选择器、保存偏好，
并根据已有季度资源及存储引擎兼容性尝试复用下载资源。

会话阶段为 `Preparing`、`ChoosingMedia`、`ChoosingStorage`、`Submitting`、`Failed`；
没有活动请求时为 `Idle`。状态只描述阶段，操作通过会话方法进行。

- 同一功能实例只有一个活动会话。切换剧集会取消旧请求的作用域，包含元数据加载阶段。
- 每个请求有 `requestId`，资源选择、返回、取消和重试都携带该 ID，过期回调会被忽略。
- 关闭资源弹窗仅隐藏 UI，会话继续保留查询结果；取消请求会释放查询资源。
- 从存储选择返回资源选择时复用原查询。
- 手动选择资源时在提交下载前保存其偏好。过滤偏好变化也由会话监听。
- 提交过程中拒绝重复提交和请求替换；失败后可以重试同一个目标。

`CreateEpisodeDownloadUseCase` 在应用作用域提交下载。创建调用返回代表存储配置已经持久化，
并不代表视频已下载完成。弹幕准备和埋点独立执行，不改变持久化成功的结果。
页面关闭会释放其查询和观察任务，已提交的创建操作及下载继续由应用管理。

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

测试覆盖请求替换、过期回调、重复提交、失败重试、作用域释放、资源复用、下载状态变化、
命令顺序、操作挂起、调用方取消、应用退出、批量操作、列表映射和页面多选交互。
UI 测试使用合成输入，不需要真实窗口或系统鼠标。
