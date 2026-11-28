# AVKit 流式兼容验证

`AvkitPlaybackPreparerTest` 验证网络 H.264、HEVC、AV1 Matroska 与 `hev1` MP4 只读取有界元数据即可进入流式准备，
以及起播失败时的文件兼容处理。TS、MP4 分片边界和增长文件的 EOF 行为由 commonTest 覆盖。

`AvkitStreamingPlaybackTest` 使用真实 FFmpeg 和本地 HTTP 服务验证认证、Range、分片解码、字幕、已缓冲范围内的跳转与取消清理。
本地 HLS 主播放列表包含普通播放轨道与 I-frame 快进轨道；后者从每个独立分片提取首个视频关键帧，
保留原时间轴，为 AVKit 的高倍速快进提供可解码的轨道。TS 保留节目表与首个视频 PES，
分片 MP4 保留视频首个 sample、解码时间与组合偏移，并重建 `moof` 的数据偏移。
集成测试验证 H.264、HEVC、AV1 的快进分片只解出一个关键帧；设备上的速率切换与声音行为由实机验证。
样片与生成文件位于临时目录，通过环境变量启用集成测试，不写入仓库。

## 准备样片

需要 PATH 中可调用 `ffmpeg`，包含 libx264、libx265 和 libvpx-vp9 编码器。
原始 HEVC 与 AV1 视频来自 [Jellyfin 测试视频](https://repo.jellyfin.org/test-videos/)，
由 Gnattu 提供，遵循页面标明的 CC BY-SA 许可。B 帧、VP9 和 MPEG-4 样片在本地由这些视频生成；
音频使用合成正弦波，字幕由测试生成。

PowerShell：

```powershell
$env:ANI_STREAM_MEDIA_DIR = Join-Path $env:TEMP 'animeko-avkit-samples'
New-Item -ItemType Directory -Path $env:ANI_STREAM_MEDIA_DIR -Force | Out-Null
$samples = @{
    hevc8 = 'SDR/HEVC 8bit/Test Jellyfin 1080p HEVC 8bit 3M.mp4'
    hevc10 = 'SDR/HEVC 10bit/Test Jellyfin 1080p HEVC 10bit 3M.mp4'
    av1 = 'SDR/AV1/Test Jellyfin 1080p AV1 10bit 3M.mp4'
}
foreach ($entry in $samples.GetEnumerator()) {
    $url = 'https://repo.jellyfin.org/test-videos/' + $entry.Value
    Invoke-WebRequest -Uri $url -OutFile "$env:ANI_STREAM_MEDIA_DIR/animeko-sample-$($entry.Key).mp4"
}
$env:ANI_STREAM_TS_FIXTURE = "$env:ANI_STREAM_MEDIA_DIR/fixture.ts"
ffmpeg -v error -f lavfi -i 'testsrc2=size=320x180:rate=25:duration=30' -f lavfi -i 'sine=frequency=400:duration=30' -c:v libx264 -preset ultrafast -g 50 -sc_threshold 0 -c:a aac -mpegts_start_pid 256 -mpegts_flags +pat_pmt_at_frames -flush_packets 1 -f mpegts -y $env:ANI_STREAM_TS_FIXTURE
ffmpeg -v error -i "$env:ANI_STREAM_MEDIA_DIR/animeko-sample-hevc10.mp4" -t 12 -vf scale=320:180 -c:v libx265 -pix_fmt yuv420p10le -preset ultrafast -g 10 -bf 3 -x265-params 'log-level=error:pools=2:open-gop=0:scenecut=0' -an -y "$env:ANI_STREAM_MEDIA_DIR/animeko-sample-hevc-bframes.mp4"
ffmpeg -v error -i "$env:ANI_STREAM_MEDIA_DIR/animeko-sample-hevc8.mp4" -t 4 -vf scale=320:180 -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -g 10 -an -y "$env:ANI_STREAM_MEDIA_DIR/animeko-sample-vp9.mkv"
ffmpeg -v error -i "$env:ANI_STREAM_MEDIA_DIR/animeko-sample-hevc8.mp4" -t 4 -vf scale=320:180 -c:v mpeg4 -q:v 5 -an -y "$env:ANI_STREAM_MEDIA_DIR/animeko-sample-mpeg4.mkv"
./gradlew :app:shared:app-data:desktopTest --tests '*Avkit*Test' --tests '*Matroska*Test' --tests '*Mp4*Test'
```

## 验证边界

桌面 FFmpeg 测试验证封装、时间戳和 HTTP 行为。iOS 使用项目的原生 FFmpeg runtime，
HEVC、AV1 分片在交给播放器前还会由设备的 AVFoundation 解码首片，解码失败则进入文件兼容处理。
实际设备的 HLS 播放、系统解码能力和后台行为仍需要安装对应 IPA 验证。

HLS EVENT 的可跳转范围随转换进度增长；续播需要等待对应位置的分片，准备超时使用文件兼容处理。
其他视频编码不自动套用 HEVC 的码流复制路径：[mediamp 0.5.3 的 iOS FFmpeg 配置](https://github.com/open-ani/mediamp/blob/v0.5.3/buildSrc/src/main/kotlin/ffmpeg/FfmpegTargets.kt)
只提供 MPEG-4 Part 2 视频编码器，VP9、MPEG-4 Part 2 等不符合 [Apple HLS 视频格式要求](https://developer.apple.com/documentation/http-live-streaming/hls-authoring-specification-for-apple-devices/)，
使用兼容 MP4 文件路径。
