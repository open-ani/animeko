## Kotlin 多平台

Animeko 客户端基于 Kotlin 多平台技术，目前配置了 Android、桌面 JVM（Windows、macOS、Linux）和 iOS
编译目标。iOS 目标默认不启用，只能在 macOS 上构建；启用方式参见[构建和打包](building.md#打包-ios-app)。

### 什么是 Kotlin 多平台

[KMP]: https://kotlinlang.org/docs/multiplatform-discover-project.html

[Kotlin 多平台][KMP] (Kotlin Multiplatform，简称 KMP、MPP)，是 Kotlin 的多平台代码共享技术.
一个项目可以拥有多个编译目标平台，例如桌面 JVM、 Android、iOS。

每个目标平台由多个源集组成。源集可以理解为存放 `.kt` 代码文件的目录。
名称为 `common` 的源集的代码通常放置在 `src/commonMain/kotlin` (生产代码) 和
`src/commonTest/kotlin` (测试代码)。

`common` 源集为全平台共享的通用代码，多个目标之间能以任意程度共享代码.
例如，`android` 和 `desktop` 可以共同继承于一个 `jvm` 源集，在 `jvm` 源集中的代码就可以共享给安卓和桌面平台;
同时 `ios` 和 `desktop` 还可以共同继承于一个 `skiko` 源集 (因为这两个平台上的 UI 都是用 Skiko
渲染的)。

共享源集内，可以使用各个平台的专有 API。例如安卓和桌面都是 JVM，也就都可以使用 JDK (Java) API.
在 iOS 源集内，则可使用 native API，例如 UIKit。在 iOS 上，Kotlin 还支持 C/Objective-C 交互.

### 多平台编译目标结构

Ani 项目的几乎所有模块都使用 KMP。编译目标结构如下:

图例:

- 蓝色 (android，desktop，iosArm64) 为最终目标。它们将会构建成为 APP；
- 绿色的 (iosSimulatorArm64) 是 iOS 模拟器目标，仅为运行测试；
- `A -> B` 表示 A 依赖 B。实线表示主要路径，虚线表示辅助路径。

```mermaid
flowchart TD
    subgraph "多平台项目统一编译目标结构"
        direction TB
        style common fill: white, color: black
        style android fill: aqua, color: black
        style desktop fill: aqua, color: black
        style iosArm64 fill: aqua, color: black
        style iosSimulatorArm64 fill: lightgreen, color: black
        style skiko fill: #ECECEBA0, stroke-dasharray: 4 4, color: black
        jvm ---> common
        android --> jvm
        desktop --> jvm
        skiko .-> common
        desktop .-> skiko
        ios .-> skiko
        native --> common
        apple --> native
        ios --> apple
        iosArm64 --> ios
        iosSimulatorArm64 --> ios

    end
```

[Skiko]: https://github.com/JetBrains/skiko

Compose Multiplatform 在 `desktop` 和 `ios` 均使用 [Skiko][Skiko] 渲染，因此我们增加一个共享源集命名为
`skiko`。
中间源集 `skiko` 为辅助作用。主要的平台适配代码仍然在 `jvm` 和 `apple` 中。

### 编译时会发生什么

Android TV 文件按功能放在共享模块目录下，由独立的 `tv` KMP Compose 子模块编译：

```text
app/shared/ui-episode/
├── build.gradle.kts           原 KMP 模块
├── src/commonMain/
├── src/androidMain/
├── src/androidTv/kotlin/      TV 生产代码
├── src/androidTvTest/kotlin/  TV 测试
└── tv/build.gradle.kts        ani.kmp-compose 子模块，依赖原 KMP 模块
```

`tv/build.gradle.kts` 使用 `ani.kmp-compose`，
将 `../src/androidTv/kotlin` 加入自己的 `androidMain`，
将 `../src/androidTvTest/kotlin` 加入自己的 `androidHostTest`。
`androidTv` 和 `androidTvTest` 是目录名，不是 KMP target 或 KotlinSourceSet。
TV 子模块沿用统一的 KMP targets，但 TV 文件和专用依赖只加入 Android 源集，desktop/iOS 不接入它们。
Compose、SDK、编译选项与 JUnit 配置由约定插件统一提供；父 KMP 模块不接入 TV 目录或专用依赖。
功能子模块的 Gradle 名称为 `:app:shared:ui-xxx-tv`，目录为 `app/shared/ui-xxx/tv`。
主壳模块 `:app:shared:tv` 位于 `app/shared/shared-tv`，编译 `app/shared/src/androidTv` 中的代码。

TV 子模块通过 Gradle 依赖访问原 KMP 模块的公开 API，不能访问其 `internal` 声明。
原模块的 `androidMain` 继续提供两端需要的 Android `actual` 实现。
TV 主壳子模块 `:app:shared:tv` 装配各功能的 `ui-xxx-tv` 子模块，应用只在 `tv` flavor 中引入它。
手机、desktop、iOS 不引入 TV 代码与专用依赖；手机和 TV 两个 APK 可以在同一次调用中构建，
见[Android TV 构建](building.md#android-tv)。

- `common` 内放置可同时在两个平台跑的代码, `android` 内放置只能在 Android 平台跑的代码，`desktop`
  内放置只能在桌面 JVM 平台跑的代码；
- `android` 内，可以访问 `common` 内的代码，还可以访问 Android SDK 例如 `Activity`；
- 在 `common` 内，不能访问 Android SDK, 也不能使用 Java Swing 等 Android 上不支持的功能；
- 编译 `android` 时，Kotlin 会合并 `common` 与 `android` 的代码一起编译。

### 如何增加多平台功能

在 `common` 内可以使用 `expect fun` 定义一个没有函数体的函数，然后在 `android` 或 `desktop` 内使用
`actual fun` 实现它，这样就可以在 `common` 内间接访问到平台相关的功能。

要开发一个适配各个平台的功能的流程通常是如下所示。

以增加视频播放器为例:

1. 在 `commonMain` 中增加 `expect fun VideoPlayer(state: MediampPlayer)`。该函数没有函数体,
   各个平台分别实现函数体；
2. 考虑该功能应当如何在各个平台实现。对于视频播放器，我们需要在三个平台分别实现；
3. 在 `androidMain`、`desktopMain`、`appleMain` 中分别增加
   `actual fun VideoPlayer(state: MediampPlayer) { ... }`，并完成具体实现。

这样，就可以在 `commonMain` 中调用 `VideoPlayer` 函数，而在编译时 Kotlin 自动选择正确的实现。

> [!TIP]
> 如果有一些功能只有一个平台需要——例如 PC 上的隐藏鼠标指针功能——你仍然需要为所有平台提供实现，
> 将函数体留空即可。
