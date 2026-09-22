# 测试

本文说明如何编写和运行自动化测试。如需运行和调试 APP，请参考[运行和调试 APP](running.md)。

## 单元测试

Animeko 拥有多平台测试。启用 iOS 目标后，在 macOS 上会运行 11,000+ 测试。其他平台上会略少一些。
我们建议你为所有新功能编写测试，不仅是为了验证功能的正确性，也是为了防止未来出现回溯问题。

### 测试源集结构

基于[多平台架构](kmp.md)，Ani 也拥有多平台测试。测试源集结构如下：

- `commonTest`
    - `jvmTest`
        - `desktopTest`
        - `androidDeviceTest`
    - `nativeTest`
        - `appleTest`
            - `iosTest`
                - `iosSimulatorArm64Test`
    - `skikoTest` (由 `desktopTest` 和 `iosTest` 共享)
- `androidHostTest` (独立于其他所有测试)

提示：

- 绝大部分测试可写在 `commonTest` 里，它们会被所有平台共享，也就是所有平台都会执行这些测试；
- 对于桌面端专用的测试，应当放置在 `jvmTest` 或 `desktopTest`。
- 对于 iOS 专用的测试，应当放置在
  `nativeTest` 或 `appleTest` 或 `iosTest`。对于不依赖 Apple API 的部分，建议放置于 `nativeTest`；对于依赖
  macOS 和 iOS 都存在的 Apple API 的部分，建议放置于 `appleTest`；对于只能在 iOS 运行的部分，放置于
  `iosTest`。
- 如果是桌面端和安卓都可以使用的测试，则放置在 `jvmTest` 中。

### Android Instrumented Test

[Android Instrumented Test]: https://developer.android.com/training/testing/unit-testing/instrumented-unit-tests

项目拥有 [Android Instrumented Test]。安卓平台测试有以下两种：

- `androidHostTest`：使用本地 JDK 运行的单元测试，无法调用 Android SDK API
- `androidDeviceTest`：连接到安卓模拟器或真机运行

> [!TIP]
> **为什么要有两种测试?**
>
> 因为安卓 SDK 和 JDK 有些许区别。例如:
>
> - 安卓的 Regex 需要比 JDK 更多的转义。当不成对时，`\]` 在 JDK 可以去除前面的 `\`，而在安卓不可以。
    IDE 会提示去除 `\`，导致在安卓真机运行时才能发现问题 (而现在开发者更倾向于方便地用 PC
    缩小窗口大小来"模拟"安卓，很可能会漏掉 bug)
> - 部分 API 在安卓上没有，但在 `jvmMain` 内可以访问，导致运行时 `NoSuchMethodError`。例如
    `List.removeFirst()`

#### 如何在本地运行 instrumented test

1. 在 local.properties 增加 `android.min.sdk=30`
   > 因为 SDK 30 才支持函数名写空格 (我们已经有一万个 case 了，没办法回头改每个 case 的名字了)。
2. ADB 连接手机或者启动模拟器
3. `./gradlew connectedCheck`

说明：

- `./gradlew check` 不会执行 `androidDeviceTest` (但会执行 `androidHostTest` 和其他)。需要使用
  `./gradlew connectedCheck` 才能执行 instrumented test。默认会连接到 ADB 连接的一个设备，
  也就是需要提前插上手机或启动模拟器；
- IDE 内不支持从一个函数运行，只能用 `./gradlew connectedCheck` 运行全部；
- 这可能需要 5-10 分钟。

#### 我需要在日常提交代码前运行 instrumented test 吗？

不需要。绝大部分情况下不会有代码通过了 `commonTest` (即所有平台的 unit 测试)，但不能在安卓真机上运行。
PR 的 CI 总是会运行 instrumented test，如果 CI 报错才需要本地运行 debug。

简单来说，日常仍然只需要测试 `./gradlew check` 通过后，即可 push commit 和提交 PR。

## UI 单元测试

在 `commonTest` 中可以编写 UI 测试。UI 测试使用 Compose Multiplatform 的测试框架，可以在所有平台运行。API
非常类似 Jetpack Compose 的测试框架。

示例：[me.him188.ani.app.ui.foundation.layout.CarouselAutoAdvanceEffectTest](https://github.com/open-ani/animeko/blob/e87c190fbe7078cfe461ae4176017174608e64bf/app/shared/ui-foundation/src/commonTest/kotlin/ui/foundation/layout/CarouselAutoAdvanceEffectTest.kt#L45)
