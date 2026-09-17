# 缓存 `Media`

用户可管理的视频下载见 [下载管理](media-downloads.md)。本文中的“缓存”指底层媒体存储机制。

Ani 目前支持缓存许多类型。主要可以分为三类：

- BT（磁力链接，种子文件）；
- HTTP 协议的视频文件（如 MP4）；
- HLS 流式传输资源（如 M3U8）。

BT 资源由 BT 引擎处理（Anitorrent），HTTP 和 HLS 资源由下载器 `HttpDownloader` 处理。

启用 PikPak 且云端引擎具有可用凭据时，BT 资源可由 `HttpMediaCacheEngine` 通过云端直链下载。
HTTP 缓存使用独立的 `OfflineDownloadMediaResolver`，解析失败时保留云端错误原因。
播放解析器可以回退到本地 BT；HTTP 下载器仅接受 URL，不能消费本地 BT 的 `SeekableInputMediaData`。

云端引擎不可用时，下载管理器为 BT 资源选择本地种子存储。已持久化的 HTTP 下载通过保存的 URL 恢复，
恢复过程不要求云端账号当前可用。
