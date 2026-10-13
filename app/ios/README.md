# iOS APP

构建与运行方式见 [贡献文档](../../docs/contributing/README.md)。

## 视频增强

播放页的增强菜单通过 iOS 控制器与 SwiftBridge 驱动 `EnhancedPlayerView`。
AVPlayer 保留网络读取、解码、声音与 AVPlayerLayer 画中画；AVPlayerItemVideoOutput 提供 BGRA 帧，
MTKView 的 Metal 覆盖层显示处理结果。关闭增强时使用原生画面，并移除取帧输出。
更换媒体会解绑旧输出并清理旧帧，后台期间暂停 Metal 绘制。
档位 Shader 按需在后台编译，准备期间显示原画；已准备的管线可复用。
关闭或切换档位会释放图像缓冲。质量档的缓冲预算不超过 512 MiB；
超过预算、分配失败或 GPU 执行失败时恢复原画，并将菜单状态设为关闭。
没有新视频帧且画面尺寸、比例与档位未变化时，不提交重复 GPU 绘制。

性能档使用 Anime4K Restore CNN S + EWA LanczosSharp；质量档使用 Restore CNN M + Upscale CNN x2 M + EWA LanczosSharp。
Shader 资源来自公共资源模块，固定卷积阶段的 GLSL 在运行时适配为 Metal，保留原始权重与许可证。
播放器的适应、拉伸、裁剪模式同时作用于原生层与 Metal 覆盖层。

独立探针共用 `VideoEnhancementRenderer.swift`，验证结果和运行方式见
[iOS 增强探针](../../tools/ios-enhancement-p0/README.md)。
真机验证覆盖 iPhone 17 / iOS 27.0.1 的 SDR 示例短测，以及 Animeko 播放页中
全屏增强菜单重复打开、关闭 / 最高质量 / 性能优先切换和质量档出画。
播放页验证属于短时功能检查，不代表长时间稳定性或逐像素画质验收。
HDR、受保护媒体、独立字幕轨、长时间发热功耗与真实 PikPak 片源尚未验收。
系统画中画显示原画，不输出增强结果。

## 未捕获异常

Kotlin/Native 的异常钩子在处理异常时获取当前日志 writer，将原始异常与堆栈写入文件并 flush，然后委托给先前的钩子或终止进程。

`pod install` 通过 `scripts/sentry_terminate_patch.rb` 修补 Sentry Cocoa 的 C++ terminate 回调：进入回调时保存前任 handler，在 Sentry 完成崩溃记录后调用它。Sentry 的 fatal-event cleanup 会禁用 monitor 并清空全局 handler，局部快照保证 Kotlin/Native 异常钩子仍能执行。补丁保留 Sentry 的原生崩溃捕获，不改变依赖版本；升级 Sentry 时需要检查此补丁，无法识别的函数结构会使安装失败。

原生回调回归测试与日志写入、钩子委托测试（`IosUnhandledExceptionHookTest`）都在 PR 的 macOS CI 中运行，本地运行方式：

```shell
./gradlew :app:ios:testSentryTerminatePatch
./gradlew :app:shared:application:iosSimulatorArm64Test
```

真实崩溃链需要在未附加调试器的 iOS 进程中验证，因为 Sentry 会根据调试器状态调整原生 monitor。
