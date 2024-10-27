[Compose for Desktop]: https://www.jetbrains.com/lp/compose-desktop/

[动漫花园]: https://www.dmhy.org/

[KMP]: https://kotlinlang.org/docs/multiplatform.html

# 参与开发

欢迎你提交 PR 参与开发。本文将介绍项目结构等可能帮助你的内容。

## 获取帮助

开发文档一直都是一个进行中的工作. 如你对项目结构有任何疑问, 欢迎通过以下途径寻求帮助:

- TG
  开发者群 [![Group](https://img.shields.io/badge/Telegram-2CA5E0?style=flat-squeare&logo=telegram&logoColor=white)](https://t.me/openani_dev)
- [GitHub Discussions](https://github.com/open-ani/ani/discussions)

## 目录

1. 开发工具: IDE, JDK, 推荐插件
2. 代码风格与代码规范
3. 项目总体架构: 整个项目范围的模块划分, 模块间依赖
4. 构建和打包: 如何编译, 如何打包 APK
5. 运行调试版本 APP
6. App 架构: 最主要的客户端模块的层级划分, 以及各层的职责
7. 开发与调试: 源集结构, 预览 Compose UI, Navigation, 问答, 以及一些坑

## 1. 开发工具和环境

> [!IMPORTANT]
> 这些步骤只需要几分钟即可完成, 请不要跳过. 跳过可能会导致花费更多时间解决问题.

### 准备 IDE

强烈建议使用最新的正式版 Android Studio (AS).

必须安装如下 IDE 插件:

- Jetpack Compose (AS 已内置)
- Android Design Tools (AS 已内置)
- Compose Multiplatform IDE Support

建议也安装:

- Compose colors preview (用于预览颜色)
- Kotlin Multiplatform (如果你需要运行 iOS APP)
- JSONPath (用于高亮 JSONPath 语法)
- ANTLR v4 (如果你要修改 BBCode 解析模块)

### Clone 仓库

建议使用 IDE clone 功能. 如果你要自己使用命令行 clone, 必须添加 `--recursive`:

```shell
git clone --recursive git@github.com:open-ani/ani.git
# or 
git clone --recursive https://github.com/open-ani/ani.git
```

> [!WARNING]
> **Windows 特别提示**
>
> 建议在 clone 项目后立即设置 Git 使用 LF 并忽略文件权限.
>
>   ```shell
>   git config core.autocrlf false
>   git config core.eol lf
>   git config core.filemode false
>   ```

### 准备 JDK (JetBrains Runtime with JCEF)

由于 PC 端使用 [JCEF](https://github.com/jetbrains/jcef) (内置浏览器), JDK 必须使用 JetBrains
Runtime (JCEF), 版本 17 或 21, 下文简称 JBR.

需要自行安装 JBR. 在 Android Studio 或 IntelliJ IDEA 中, 如下图所示, 可打开设置
`Build, Execution, Deployment -> Build Tools -> Gradle`, 修改 Gradle JDK 配置为 JBR (JCEF) 17 或 21.
如果使用 21, 则需要在 `local.properties` 中添加一行 `jvm.toolchain.version=21`. 如果使用 17 则无需修改配置.

<img src=".readme/images/contributing/idea-settings-download-jdk.png" alt="download jbr" width="400"/>
<img src=".readme/images/contributing/idea-settings-download-jdk-version.png" alt="choose version" width="200"/>

### 配置 Android SDK & NDK

项目包含使用 Android NDK 的 C++ 代码. 必须配置 NDK 才能编译:

1. 打开 SDK Manager (Android Studio 中 Tools -> SDK Manager)
2. 安装 SDK 至少 34
3. 安装 Android SDK Build-Tools, NDK 和 Cmake 并更新到最新版本

## 2. 代码风格与代码规范

### 格式化

建议在 IDE 的 Commit 页面, 点击右下角设置, 勾选 "Reformat code" 和 "Optimize imports".
在提交时将会自动格式化代码.

<img src=".readme/images/contributing/formatting.png" alt="commit-settings" width="300"/>

### 代码规范

我们力求打造一个高质量的 Compose Multiplatform 项目.

为了达成这一点:

- 请遵循 [Kotlin 官方代码风格指南](https://kotlinlang.org/docs/coding-conventions.html)
- 请为新功能增加单元测试
- 请为新功能增加 UI 测试 (如何编写测试将在下面说明)
- PR 审核将会比较严格. 在保证项目代码质量的同时, 我们也希望尽可能地帮助你提高技术水平.
  审核者技术有限, 如有意见不统一的情况, 请务必提出, 相互学习 :)

## 3. 项目总体架构

Ani 现在已经是一个不小的项目, 本章节将给你一个全局的了解.

Ani 基于 Kotlin 多平台代码技术, 目前正式支持 Android, macOS, Windows 三个平台. 代码库中有正在开发中的
iOS 代码, 但还没有配置构建 iOS APP.

> [!TIP]
> [Kotlin 多平台][KMP] (Kotlin Multiplatform, 简称 KMP、MPP), 是 Kotlin 的多平台代码共享技术.
> 一个项目可以拥有多个编译目标平台, 例如桌面 JVM、 Android、iOS.
>
> 每个目标平台由多个源集组成. `common` 源集为全平台共享的通用代码, 多个目标之间能以任意程度共享代码.
> 例如, `android` 和 `desktop` 可以共同继承于一个 `jvm` 源集, 在 `jvm` 源集中的代码就可以共享给安卓和桌面平台;
> 同时 `ios` 和 `desktop` 还可以共同继承于一个 `skiko` 源集 (因为这两个平台上的 UI 都是用 Skiko
> 渲染的).
>
> 共享源集内, 可以使用各个平台的专有 API. 例如安卓和桌面都是 JVM, 也就都可以使用 JDK (Java) API.
> 在 iOS 源集内, 则可使用 native API, 例如 UIKit. 在 iOS 上, Kotlin 还支持 C/Objective-C 交互.

### 多平台源集结构

Ani 项目的几乎所有模块都使用 KMP. 源集结构如下:

```mermaid
flowchart TD
    subgraph "多平台项目统一源集结构"
        direction BT
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

蓝色 (android, desktop, iosArm64) 为最终目标. 它们将会构建成为 APP.
绿色的 (iosSimulatorArm64) 是 iOS 模拟器目标, 仅为运行测试.

[Skiko]: https://github.com/JetBrains/skiko

Compose Multiplatform 在 `desktop` 和 `ios` 均使用 [Skiko][Skiko] 渲染, 因此共享的 `skiko` 源集.
中间源集 `skiko` 为辅助作用. 主要的平台适配代码在 `jvm` 和 `apple` 中.

> [!NOTE]
> 实际上有些模块的源集结构是不完整的 (更简单一些). 例如工具模块 `:utils:io` 不区分 `android`
> 和 `desktop`, 它只有 `jvm` 作为最终目标, 同时支持 Android 和桌面端.

在这种架构下, 要开发一个适配各个平台的功能的流程通常是如下所示. 以增加视频播放器为例:

1. 在 `commonMain` 中增加 `expect fun VideoPlayer(state: PlayerState)`. 该函数没有函数体,
   各个平台分别实现函数体.
2. 考虑该功能应当如何在各个平台实现. 对于视频播放器, 我们需要在三个平台分别实现.
3. 在 `androidMain`, `desktopMain`, `iosMain` 中分别增加
   `actual fun VideoPlayer(state: PlayerState) { ... }`

这样, 就可以在 `commonMain` 中调用 `VideoPlayer` 函数, 而在编译时 Kotlin 自动选择正确的实现.

> [!TIP]
> 如果有一些功能只有一个平台需要, 例如 PC 上的隐藏鼠标指针功能, 你仍然需要为所有平台提供实现,
> 将函数体留空即可.

### 项目架构

模块结构也对应源码目录结构. 对于具体的模块说明, 请查看 [6. App 项目架构](#6-app-项目架构).

```mermaid
flowchart TD
    classDef omitted fill: transparent, stroke: transparent

    subgraph "基础工具"
        :utils:platform
        :utils:io
        :utils:serialization
        :utils:logging
        :utils:coroutines
        :utils:testing
        :utils:bbcode
        :utils:ip-parser
        :utils:...(...)
        class :utils:... omitted
    end

    subgraph "数据源"
        direction LR

        subgraph "BT"
            direction TB
            dmhy(dmhy)
            mikan(mikan)
        end

        BT --> datasource:api
        Ikaros(Ikaros) --> datasource:api
    end

    数据源 --> 基础工具
    APP --> 基础工具
    APP --> 数据源

    subgraph "弹幕"
        danmaku:api[:danmaku:api <br/> 多弹幕源接口]
        danmaku:dandanplay[:danmaku:dandanplay<br/> 弹弹 play] --> danmaku:api
        :client[:client<br/> Ani 弹幕服务] --> danmaku:api
        danmaku:ui[:danmaku:ui<br/> 视频播放器 UI 的弹幕层] --> danmaku:api
    end

    弹幕 --> 基础工具
    APP --> 弹幕

    subgraph "BitTorrent"
        torrent:api[:torrent:api<br/>多 BT 引擎接口]
        torrent:anitorrent[:torrent:anitorrent<br/>Anitorrent 自身, C++ 编写]
        torrent:impl:anitorrent[:torrent:impl:anitorrent<br/>用 Anitorrent 实现 BT 引擎] --> torrent:api
        torrent:impl:anitorrent .-> torrent:anitorrent
    end

    BitTorrent --> 基础工具
    APP --> BitTorrent

    subgraph "APP"
        direction LR
        android[":app:android <br/> Android 入口"] --> shared:application
        desktop[":app:desktop <br/> 桌面端入口"] --> shared:application
        ios[":app:ios <br/> 计划"] --> shared:application
        shared:application[":app:shared:application<br/>APP 启动入口"] --> app_business
    %% shared:app-data --> client
    %% client[":client<br/>Ani 云服务客户端 (弹幕+登录)"]
        style android fill: cyan, color: black
        style desktop fill: cyan, color: black
        style ios fill: cyan, stroke-dasharray: 4 4, color: black

        subgraph app_business ["业务层"]
            direction LR
            shared[":app:shared<br/>绝大部分 UI<br/>(各页面)"]
            shared:ui-settings[":app:shared:ui-settings<br/>UI 设置页面部分"]
            image-viewer[:app:shared:image-viewer<br/>图片查看器]
            shared --> image-viewer
            video-player[:app:shared:video-player<br/>视频播放器]
            shared --> video-player
            reorderable[:app:shared:reorderable<br/>长按排序组件]
            shared --> reorderable
            shared --> shared:ui-settings
        end

        app_business --> app_foundation

        subgraph app_foundation ["ui-foundation"]
            direction LR
            animation(animation)
            layout(layout)
            richtext(RichText)
            theme(theme)
        end

        app_foundation --> app_data

        subgraph app_data [app-data 数据层]
            direction LR
        %% app_data:spacer(" ")
        %% class app_data:spacer omitted
            MediaSourceManager(MediaSourceManager)
            MediaCaching(Media Caching)
            room[(Room Database)]
            datastore[(DataStore)]
        end

        app_data --> app_platform
        subgraph app_platform ["`app-platform 平台层`"]
            direction LR
        %% app_platform:spacer2(" ")
        %% class app_platform:spacer2 omitted
            DateFormatter(DateFormatter)
            permission(PermissionManager)
            audio(AudioManager)
        end
    end
```

蓝色的模块为 APP 的入口点. 它们里面只有微量的代码用来启动 `:app:shared` 中的 UI 等.
绝大部分客户端代码都在共享模块 `:app:shared` 中.

> 这样做的一个原因是各个平台的构建限制:
>
> - Android 方面是因为 Android Library 无法在 manifest 定义 Activity.
> - Compose for Desktop 在多平台项目里面构建很难配置, 因此用单独的模块只用于打包.
> - iOS 需要使用 Xcode 项目才能启动

## 4. 构建打包

如果遇到问题, 请查看 [常见构建和运行问题](#常见构建和运行问题)

### 考虑禁用一些编译目标

编译整个项目是对你的电脑的一个考验 :P

在高性能个人机器上 (Apple M2 Max / AMD Ryzen 7 5800X / Intel i9-12900H + 64 GB 内存) 编译整个项目仍然可能需要
10 分钟以上.

**对于 macOS**: 由于 macOS 上支持构建 iOS (也默认开启), 对内存的需求会大幅上升. 如果你的电脑的内存为
16
GB 左右, 即使是 M3 Pro CPU, 编译和测试仍然需要 30 分钟. 如果你的电脑小于 32 GB, 建议禁用 iOS
目标编译来缩短时间.

> [!TIP]
> **禁用 iOS 目标**
>
> 在项目根目录的 `local.properties` 中增加以下内容:
>
> ```properties
> ani.enable.ios=false
> ani.build.framework=false
> ```

**对于所有操作系统**, 都建议禁用你不需要的 Android 架构. 例如你的手机是 arm64-v8a (
绝大部分手机都是
), 那么可以设置只构建这个架构, 将大幅提升编译速度.

> [!TIP]
> **只启用 Android arm64-v8a 架构**
>
> 在项目根目录的 `local.properties` 中增加以下内容:
>
> ```properties
> ani.android.abis=arm64-v8a
> ```

### 打包 Android APP

在 IDE 中双击 Ctrl, 执行 `./gradlew assembleRelease` 或 `./gradlew assembleDebug`
，分别编译发布版或测试版。使用 `./gradlew installRelease` 或 `./gradlew installDebug` 还可以构建应用并安装到模拟器。

在 IDE 上也可以选择 `Build -> Build Bundle(s) / APK(s) -> Build APK(s)` 来构建 APK.

### 打包桌面应用

要构建桌面应用，请参考 [Compose for Desktop]
官方文档，或简单执行 `./gradlew createReleaseDistributable`
，结果保存在 `app/desktop/build/compose/binaries` 中。

一个操作系统只能构建对应的桌面应用，例如 Windows 只能构建 Windows 应用，而不能构建 macOS 应用。

### 运行测试

在 IDE 中双击 Ctrl, 执行 `./gradlew check` 可以运行所有测试，包括单元测试和 UI 测试。

在 macOS 上, 这将会运行全部测试, 总共约 8000 个 (如果未启用 iOS 目标, 会少一些). 在 Windows 上只能运行安卓和
JVM 平台测试, 无法运行
iOS 测试.

> [!TIP]
> **重复运行测试**
>
> 由于启用了 Gradle build cache, 如果代码没有修改, test 就不会执行.
>
> 可使用 `./gradlew clean check` 清空缓存并重新运行所有测试.

### 常见构建和运行问题

#### 提示找不到 `reorderable`

未找到 `app/shared/reorderable`, 这是因为没有正确 clone 导致的. 可尝试下列任一方法解决:

1. `git submodule update --init --recursive`
2. 使用 Android Studio 的 New Project from Version Control, 而不要使用命令行
3. 使用命令行时确保带上 recursive 选项: `git clone --recursive git@github.com:open-ani/ani.git`

#### 编译报错找不到 `Res.*`

这是 Compose 的 bug, 请生成 Compose Multiplatform 资源:

执行 `./gradlew generateComposeResClass` 即可生成一个 `Res` 类, 用于在 `:app:shared` 访问资源文件.

#### Android 触发断点恢复运行后, APP 无响应

打开 `app.android` 的配置, 将 Debugger -> Debug type 改为 Java only.

#### 启动 PC 版时报错 `ClassNotDefFoundError`

打开 `Run Desktop` 的配置, 复制一份, 将 "Use classpath of module" 改为 `ani.app.desktop.test`.
如果又遇到了, 则改回来 `ani.app.desktop.main`.

## 5. 运行调试版本 APP

以下各个小节分别说明如何运行各个平台的调试 APP (支持断点).

### 什么是 Run Configuration (运行配置)

项目自带一些运行配置, 方便你运行测试版 APP, 可以在 Android Studio 顶部找到:

![](.readme/images/contributing/run-configuration.png)

`app.android` 就是一个运行配置, 使用它即可运行 Android APP (下面有说明).

> [!WARNING]
> **如何编辑一个运行配置**
>
> ![](.readme/images/contributing/edit-run-configuration.png)
>
> 打开后, 将配置复制一份, 然后修改复制的配置. 因为默认配置是由 Git 管理的, 除非有很强的理由,
> 否则不要修改默认配置.

### 运行调试版本 Android APP

在 Android Studio 中, 选择运行配置 `app.android`, 点击按钮运行或调试即可.
Android Studio 的调试器同时支持调试 Kotlin 和 C++ 代码 (torrent 部分由 C++ 编写). 因此你可能会看到两个调试器窗口,
请注意切换.

> [!TIP]
> **Android 调试版本 (Debug) 的性能远低于发布版本 (Release)**
>
> 由于调试版本禁用了一切优化, 且包含了 Compose 额外的调试信息, 性能会比发布版本低很多.
> 所有手机都会非常卡. 如果你要测试性能, 请切换到发布版本.

### 运行 PC APP

仅支持 macOS 和 Windows.

在 Android Studio 中, 选择运行配置 `Run Desktop`, 点击按钮运行或调试即可.

### 运行 iOS APP

只有 macOS 才能运行 iOS APP. 需要先在 App Store 安装 Xcode 并打开一次同意 Xcode 的协议.

如果提示找不到模拟器, 请安装一个 iPhone 15 模拟器.

在 Android Studio 中, 选择运行配置 `Run iOS Debug`, 点击按钮运行即可.

## 6. App 项目架构

> [!IMPORTANT]
> TODO: 此文档可能有些内容已经过时. 如有疑问欢迎在开发者群询问.

本节将介绍客户端共享模块 (`:app:shared`) 的架构设计, 也就是对应目录 `app/shared` 里的内容.
建议搭配上面的架构图看.

### `app-data`: 数据层

包含对 UI 需要使用到的所有数据的管理. 后面介绍的 UI 层不会进行 HTTP 请求或是文件访问, 而是调用这里的接口.

数据层包含许多模块：

* `models`: App UI 或其他组件使用的数据结构，外部数据源的数据结构将会转换到此包中数据结构.
* `persistent`: 数据持久化包，例如轻量数据 preference 和大量数据 database.
* `repository`: 数据仓库，通常是外部数据源与 App 交互的中间仓库.
* `bangumi`: Bangumi 索引数据源的相关实现. 例如搜索条目
* `source`: 数据源.

App 主要通过 `repository` 和 `source` 与外部数据交互.

### `foundation`: 基础组件

提供 i18n 支持, 通用的 UI 组件, 对平台交互的封装等.

### `placeholder`

来自 Google Accompanist 的 `Placeholders` 基础库. 由于 Google 已经停止维护, 这里就复制了一份.

它提供 `Modifier.placeholder`, 可在数据还在加载中时, 在 Composable 之上覆盖一个特效表示正在加载.

### `video-player`

提供一个视频播放器 Composable: `VideoPlayer`, 对应地它的逻辑封装在 `PlayerState`.
由于它依赖了一些 `foundation` 的组件, 所以没有放在项目顶层.

### `pages`: 按 UI 页面分类的功能模块

UI 设计采用 MVVM 模式.

`ui/pages` 目录中, 按照在 APP 实际运行时会展示的页面区分子目录. 相关联的 View (`@Composable`) 和
ViewModel 放置在同一目录下.

- `main`: 整个 APP UI 的入口点, 实现各个页面之间的跳转等
- `home`: 首页
- `subject-collection`: "我的收藏"页面
- `subject-details`: 番剧详情页面 (可看声优列表等)
- `episode-play`: 剧集详情以及播放页面
- ...

每个页面目录, 有 `common`, `android`, `desktop` 三个子目录.
它们分别存放共享代码, Android 端代码, 桌面端代码. (Kotlin MPP 特性)

若你不了解 Kotlin MPP, 简单来说:

- `common` 内放置可同时在两个平台跑的代码, `android` 内放置只能在 Android 平台跑的代码, `desktop`
  内放置只能在桌面 JVM 平台跑的代码.
- 编译 `android` 时, Kotlin 会合并 `common` 与 `android` 的代码一起编译.
- `android` 内, 可以访问 `common` 内的代码, 还可以访问 Android SDK 例如 `Activity`.
- 在 `common` 内, 不能访问 Android SDK, 也不能使用 Java Swing 等 Android 上不支持的功能.
- 在 `common` 内可以使用 `expect fun` 定义一个没有函数体的函数, 然后在 `android` 或 `desktop` 内使用
  `actual fun` 实现它, 这样就可以在 common 内间接访问到平台相关的功能.
- 详细参考 [Kotlin 官方文档](https://kotlinlang.org/docs/multiplatform-discover-project.html)

#### UI 层组织方式

Ani 目前只有一个 `MainActivity`. `MainActivity` 使用 `AniAppContent`.

`AniAppContent` 位于 `pages/main` 目录中, 它是 UI 的入口点.
它引用了所有的页面并实现了页面之间的跳转.

每一个页面通常分别有一个 `Screen` 和 `Page`.
`XXXScreen` 用于实现页面之间的跳转, `XXXPage` 用于实现页面的具体内容.
`Screen` 会访问当前 navigation BackStackEntry, 获取该页面的参数并根据参数决定该如何展示 Page.

有的页面可以拥有多个 Page, 例如 `HomeScreen` 也就是进入 APP 后看到的页面, 它可以通过底部导航栏切换不同的
Page (首页/我的收藏/个人中心).

> 这样的设计让 Page 不依赖 navigation, 就可以在开发时实时预览.

### Ani 的数据层

数据层主要包含两个部分: 单独的数据源 (`:datasource`) 与放在 `app/shared/data`
目录的数据仓库 (`Repository`).

各数据源位于 app 模块之外的项目根目录的 `datasource` 目录下. 数据源列表:

- `datasource/bangumi`: Bangumi 索引数据源, 提供番剧索引, 观看记录等.
    - API 客户端使用其官方 OpenAPI 文档自动生成
- `datasource/api`: 下载数据源的抽象, 定义了数据源的接口以供接入多个下载数据源.
- `datasource/dmhy`: 动漫花园下载数据源, 只提供番剧下载链接.
- `datasource/acg.rip`: acg.rip 下载数据源, 只提供番剧下载链接.
- `datasource/mikan`: mikanani.me 下载数据源, 只提供番剧下载链接.
- ... 欢迎 PR 更多支持例如 SMB

## 7. 开发与调试

### 源集 (source set) 结构

为了减少文件树层级, 项目使用了一些特殊的源集结构.

在 `commonMain` 或 `common`, `androidMain` 或 `android` 目录直接存放 `.kt` 源码,
而不需要 `src/kotlin` 目录.
资源放置于 `commonResources`, `androidResources` 等目录下.

若你不是很清楚 Gradle 这方面的功能, 只需模仿已有的源代码存放新文件就行.

### 预览 Compose UI

因为项目支持 Android 和桌面两个平台, 预览也就分两个平台. 绝大部分 UI 代码在 Android 的桌面的效果是一模一样的,
因此使用一个平台的预览即可.

在 common 中使用 `@Preview` 将进行桌面平台的预览, 但桌面预览不支持可交互式预览 (Interactive Mode),
也不支持即时刷新, 不推荐.

在开发时, 通常建议在 android 中编写预览代码. 你可以通过 IDE 分屏功能将 android 预览放到一边.

#### 查找已有页面的预览

用 IDE 查找想要预览的 Composable 的实现就能找到 (按住 Ctrl 点击函数名). 几乎所有的页面都有 Android
的预览.

<img width="400" src=".readme/images/contributing/find-existing-previews.png" alt="previewing-compose-ui"/>

#### 示例: 视频播放器的 Android 预览

使用视频播放器作为示例, 视频播放器的入口点是 `VideoScaffold`, 它提供播放器的框架.
调用方在该框架上通过参数传递 `topBar`, `video`, `danmakuHost` 等组件来组装一个视频播放器 UI.

[
`VideoScaffold`](https://github.com/Him188/ani/tree/master/app/shared/video-player/common/ui/VideoScaffold.kt#39)
是通用的, 全部代码都位于 common 中:

`app/shared/video-player/common/ui/VideoScaffold.kt`

```kotlin
@Composable
fun VideoScaffold() {
}
```

[
`PreviewVideoScaffold`](https://github.com/Him188/ani/tree/master/app/shared/video-player/android/ui/VideoScaffold.android.kt#L47)
位于 `app/shared/video-player/android/ui/VideoScaffold.android.kt`:

```kotlin
@Preview
@Composable
private fun PreviewVideoScaffold() {
    VideoScaffold()
}
```

<img width="600" src=".readme/images/contributing/previewing-compose-ui.png" alt="previewing-compose-ui"/>

### Navigation

Navigation 使用了 [PreCompose](https://github.com/Tlaster/PreCompose). 它与 Jetpack NavHost 用法类似.
*也有很多坑, 但我已经踩完了.*

跳转功能已经封装为了 `AniNavigator`, 在任意 Composable 中可以使用 `LocalAniNavigator.current` 获取当前
navigator,
然后使用
`navigateXxx` 进行跳转.

### 如何找到我想修改的页面/功能?

从 UI 入手, 全局搜索页面上会显示的文字的字符串最简单.

你也可以用遍历的方式:

1. 所有 UI 页面都在 `app/shared/pages` 目录下.
2. 进入每个目录看它们的 `XXXPage` 或 `XXXScreen` 文件.

### 如何增加一个新页面

1. 在 `app/shared/pages` 目录下创建一个新目录, 例如 `pages/person-details`
2. 在 `app/shread/build.gradle.kts` 中仿照现有代码,
   添加一行 `submodule("app/shared/pages/person-details")`
3. 创建 `app/shared/pages/person-details/common`, `.../android`, `.../desktop`
4. 在三个目录中分别创建 `package.kt`, 内容为 `package me.him188.ani.app.ui.person.details` (
   根据你的页面名称修改)
   > 这是为了设置改目录下的默认包名.
5. 然后就可以在 `common` 里添加 `PersonDetailsPage`, `PersonDetailsViewModel` 等了. 可以参考
   已有的类似的页面的实现. 例如对于实现人员详情页面, 可以参考 `SubjectDetailsPage`.

