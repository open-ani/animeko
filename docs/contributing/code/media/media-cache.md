# 缓存 `Media`

用户可管理的视频下载见 [下载管理](media-downloads.md)。本文中的“缓存”指底层媒体存储机制。

Ani 目前支持缓存许多类型。主要可以分为三类：

- BT（磁力链接，种子文件）；
- HTTP 协议的视频文件（如 MP4）；
- HLS 流式传输资源（如 M3U8）。

BT 资源由 BT 引擎处理（Anitorrent），HTTP 和 HLS 资源由下载器 `HttpDownloader` 处理。

## BT 缓存的持久化

`TorrentMediaCacheEngine` 用两张表记录 BT 缓存：`torrent_cache` 以 `mediaId` 为主键保存种子数据与下载目录，
一个合集只有一行；`torrent_cache_episode` 以 `(mediaId, episodeId)` 为主键保存每集的完成状态、
种子内的文件路径与流量统计。同一合集的多集共用一行 `torrent_cache` 与一个下载会话，各自有一行 `torrent_cache_episode`；
删除某集只删除它的剧集行，合集没有剩余剧集时才删除种子行与文件。
`torrent_cache` 上的完成状态与文件路径列只用于恢复没有剧集行的旧记录（数据库版本 24 之前创建的缓存）：
恢复时按记录的剧集判断旧行的文件是否属于该集，是则沿用，否则重新从种子中选择文件，并补建剧集行。

[//]: # (TODO： 缓存)
