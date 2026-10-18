# iOS APP

构建与运行方式见 [贡献文档](../../docs/contributing/README.md)。

## 未捕获异常

Kotlin/Native 的异常钩子在处理异常时获取当前日志 writer，将原始异常与堆栈写入文件并 flush，然后委托给先前的钩子或终止进程。

`pod install` 通过 `scripts/sentry_terminate_patch.rb` 修补 Sentry Cocoa 的 C++ terminate 回调：进入回调时保存前任 handler，在 Sentry 完成崩溃记录后调用它。Sentry 的 fatal-event cleanup 会禁用 monitor 并清空全局 handler，局部快照保证 Kotlin/Native 异常钩子仍能执行。补丁保留 Sentry 的原生崩溃捕获，不改变依赖版本；升级 Sentry 时需要检查此补丁，无法识别的函数结构会使安装失败。

原生回调回归测试与日志写入、钩子委托测试（`IosUnhandledExceptionHookTest`）都在 PR 的 macOS CI 中运行，本地运行方式：

```shell
./gradlew :app:ios:testSentryTerminatePatch
./gradlew :app:shared:application:iosSimulatorArm64Test
```

真实崩溃链需要在未附加调试器的 iOS 进程中验证，因为 Sentry 会根据调试器状态调整原生 monitor。
