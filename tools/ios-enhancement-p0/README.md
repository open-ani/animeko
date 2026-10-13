# iOS 视频增强测试工具

独立的 iOS 测试 App，与 Animeko 共用 `app/ios/Animeko/VideoEnhancementRenderer.swift`。
验证 HLS 视频帧读取、Metal 输出、Anime4K 两档处理和缓冲生命周期。
测试工具不覆盖正式 App 的 Kotlin/Swift 桥接和菜单生命周期。

## 运行

用 Xcode 打开 `P0.xcodeproj`，选择 P0 scheme、模拟器或自己的签名 Team 与 iPhone。

```sh
xcodebuild -project tools/ios-enhancement-p0/P0.xcodeproj -scheme P0 \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/animeko-ios-p0-simulator CODE_SIGNING_ALLOWED=NO build
```

启动参数：

- `--regressions --muted`：静音运行两档检查，再运行性能档测试，覆盖质量档之后重跑性能档的操作顺序。
- `--both-modes`：性能档和质量档的处理、开关、暂停与资源释放检查。
- `--anime4k`：性能档与同尺寸图像变化检查。
- `--metal-output`：HLS 原画 Metal 输出、前后跳转、暂停恢复及原生/Metal 切换。
- `--hls-output`：HLS 解码帧与纹理映射检查。
- 无参数：本地 MP4、公开 HTTPS MP4 与 HLS 的 AVVideoComposition 能力探测。

HLS 示例使用 Apple 的公开测试流；HTTPS MP4 使用 W3C trailer。
AVVideoComposition 不支持 HLS，HLS 增强使用 AVPlayerItemVideoOutput。
增强测试结束后自动暂停；`--muted` 可用于无声自动检查。

## 回归检查

性能档为 Restore CNN S + EWA LanczosSharp；质量档为 Restore CNN M + Upscale CNN x2 M + EWA LanczosSharp。

- 两档均应完成 GPU 工作，关闭后释放增强纹理，再次开启恢复处理。
- 暂停稳定后，已完成的 GPU 显示次数应保持不变。
- 暂停并跳转时临时阻止取得 drawable（可显示的图像缓冲），恢复后应处理并显示已取得的新帧。
- 质量档输出尺寸不同于源图像，同帧比较应拒绝读取。
- 质量档测试之后运行性能档测试，应明确选择性能档并安全完成同尺寸比较。

测试界面、控制台和 App 的 `Documents/p0-report.txt` 均输出结果。
真机报告可用以下命令读取：

```sh
xcrun devicectl device copy from --device '<device-id>' \
  --domain-type appDataContainer --domain-identifier org.animeko.enhancement.p0 \
  --source Documents/p0-report.txt --destination /tmp/animeko-ios-p0-report.txt
```

## 验证边界

iPhone 17 / iOS 27.0.1 已完成 720p SDR 两档短测；模拟器可运行上述自动检查。
帧率与 GPU 耗时是短时采样，不代表真机长期稳定性能。
图像变化检查只确认处理改变像素，不是画质评分。
HDR、受保护媒体、独立字幕轨、长期发热功耗与真实 PikPak 片源未验收。
系统画中画使用原生 AVPlayerLayer，显示原画。
运行报告、截图和录屏不应提交到仓库。
