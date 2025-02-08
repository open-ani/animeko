# 测试

## 测试源集结构

基于[多平台架构](kmp.md)，Ani 也拥有多平台测试。测试源集结构如下：

- `commonTest`
    - `jvmTest`
        - `desktopTest`
        - `androidInstrumentedTest`
    - `nativeTest`
        - `appleTest`
            - `iosTest`
                - `iosSimulatorArm64Test`
    - `skikoTest` (由 `desktopTest` 和 `iosTest` 共享)
- `androidUnitTest` (独立于其他所有测试)

提示：

- 绝大部分测试可写在 `commonTest` 里，它们会被所有平台共享，也就是所有平台都会执行这些测试；
- 对于桌面端专用的测试，应当放置在 `jvmTest/desktopTest`，对于 iOS 专用的测试，应当放置在
  `nativeTest/appleTest/iosTest`，以此类推；
- 如果是桌面端和安卓都可以使用的测试，则放置在 `jvmTest` 中。

## Android Instrumented Test

[Android Instrumented Test]: https://developer.android.com/training/testing/unit-testing/instrumented-unit-tests

项目拥有 [Android Instrumented Test]。安卓平台测试有以下两种：

- `androidUnitTest`：使用本地 JDK 运行的单元测试，无法调用 Android SDK API
- `androidInstrumentedTest`：连接到安卓模拟器或真机运行

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

### 如何在本地运行 instrumented test

1. 在 local.properties 增加 `android.min.sdk=30`
   > 因为 SDK 30 才支持函数名写空格 (我们已经有一万个 case 了，没办法回头改每个 case 的名字了)。
2. ADB 连接手机或者启动模拟器
3. `./gradlew connectedCheck`

说明：

- `./gradlew check` 不会执行 `androidInstrumentedTest` (但会执行 `androidUnitTest` 和其他)。需要使用
  `./gradlew connectedCheck` 才能执行 instrumented test。默认会连接到 ADB 连接的一个设备，
  也就是需要提前插上手机或启动模拟器；
- IDE 内不支持从一个函数运行，只能用 `./gradlew connectedCheck` 运行全部；
- 这可能需要 5-10 分钟。

### 我需要在日常提交代码前运行 instrumented test 吗？

不需要。绝大部分情况下不会有代码通过了 `commonTest` (即所有平台的 unit 测试)，但不能在安卓真机上运行。
PR 的 CI 总是会运行 instrumented test，如果 CI 报错才需要本地运行 debug。

简单来说，日常仍然只需要测试 `./gradlew check` 通过后，即可 push commit 和提交 PR。

----

## 运行调试版本 APP

以下各个小节分别说明如何运行各个平台的调试 APP (支持断点)。

### 什么是 Run Configuration (运行配置)

项目自带一些运行配置，方便你运行测试版 APP，可以在 Android Studio 顶部找到：

![](images/run-configuration.png)

`app.android` 就是一个运行配置，使用它即可运行 Android APP (下面有说明)。

> [!WARNING]
> **如何编辑一个运行配置**
>
> ![](images/edit-run-configuration.png)
>
> 打开后，将配置复制一份，然后修改复制的配置。因为默认配置是由 Git 管理的，除非有很强的理由，
> 否则不要修改默认配置。

### 运行调试版本 Android APP

在 Android Studio 或 IntelliJ IDEA 中，选择运行配置 `app.android`，点击按钮运行或调试即可。

> [!TIP]
> **Android 调试版本 (Debug) 的性能远低于发布版本 (Release)**
>
> 由于调试版本禁用了一切优化，而且包含 Compose 额外的调试信息，性能会比发布版本低很多。
> 所有手机都会非常卡。如果你要测试性能，请切换到发布版本。

### 运行 PC APP

仅支持 macOS 和 Windows。

在 Android Studio 或 IntelliJ IDEA 中，选择运行配置 `Run Desktop`，点击按钮运行或调试即可。

### 运行 iOS APP

只有 macOS 才能运行 iOS APP。需要先在 App Store 安装 Xcode 并打开一次同意 Xcode 的协议。

如果提示找不到模拟器，请安装一个 iPhone 15 模拟器。

在 Android Studio 中，选择运行配置 `Run iOS Debug`，点击按钮运行即可。


----

## UI Testing

在 `commonTest` 中可以编写 UI 测试。UI 测试使用 Compose Multiplatform 的测试框架，可以在所有平台运行。API
非常类似 Jetpack Compose 的测试框架。

示例：[me.him188.ani.app.ui.foundation.layout.CarouselAutoAdvanceEffectTest](https://github.com/open-ani/animeko/blob/e87c190fbe7078cfe461ae4176017174608e64bf/app/shared/ui-foundation/src/commonTest/kotlin/ui/foundation/layout/CarouselAutoAdvanceEffectTest.kt#L45)
