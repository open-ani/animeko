# 缓存 `Media`

Ani 目前支持缓存许多类型。主要可以分为三类：

- BT（磁力链接，种子文件）；
- HTTP 协议的视频文件（如 MP4）；
- HLS 流式传输资源（如 M3U8）。

BT 资源由 BT 引擎处理（Anitorrent），HTTP 和 HLS 资源由下载器 `HttpDownloader` 处理。

[//]: # (TODO： 缓存)
