# 开发工具和环境

> [!IMPORTANT]
> 这些步骤只需要几分钟即可完成，请不要跳过。跳过可能会导致花费更多时间解决问题。

## 主流程

第一次参与开发时，只需要完成下面几步：

1. [准备 IDE](#准备-ide)
2. [配置 Android SDK](#配置-android-sdk)
3. [Clone 仓库](#clone-仓库)
4. 导入项目并等待第一次同步完成

> [!TIP]
> **默认会使用以下配置**
>
> - JDK 会由 Gradle 自动下载并使用正确的 JBR 21
> - Android 默认只构建 `arm64-v8a`
> - iOS 默认关闭
>
> 只有在你确实遇到问题，或者要开发 iOS / 改 ABI 时，再看文档后半部分的 [按需配置](#按需配置默认不用先读)

## 准备 IDE

强烈建议使用最新的正式版 Android Studio (AS) 或者 IntelliJ IDEA。

必须安装如下 IDE 插件:

- Jetpack Compose (AS 已内置)
- Android Design Tools (AS 已内置)
- Compose Multiplatform IDE Support

建议也安装:

- Compose colors preview (用于预览颜色)
- Kotlin Multiplatform (如果你需要运行 iOS APP)
- JSONPath (用于高亮 JSONPath 语法)
- ANTLR v4 (如果你要修改 BBCode 解析模块)

## 配置 Android SDK

1. 打开 SDK Manager
    - Android Studio 中为 Tools -> SDK Manager
    - IntelliJ 中 Tools -> Android -> SDK Manager
2. 安装 SDK 版本 35

## Clone 仓库

建议使用 IDE clone 功能. 如果你要自己使用命令行 clone, 必须添加 `--recursive`:

```shell
git clone --recursive git@github.com:open-ani/animeko.git
# or 
git clone --recursive https://github.com/open-ani/animeko.git
```

> [!WARNING]
> **Windows 特别提示**
>
> 建议在 clone 项目后立即设置 Git 使用 LF 并忽略文件权限。
>
>   ```shell
>   git config core.autocrlf false
>   git config core.eol lf
>   git config core.filemode false
>   ```

Clone 后第一次导入项目可能需要 1 小时。导入项目后别着急编译，先阅读 [优化编译速度](#优化编译速度)。

## 按需配置

下面这些内容只在你有特定需求时才需要。

### JDK 自动配置与手动配置

由于 PC 端使用 [JCEF](https://github.com/jetbrains/jcef) (内置浏览器)，JDK 必须使用 JetBrains
Runtime (JCEF)，版本必须为 21，下文简称 JBR。

> [!TIP]
> 本仓库已配置 [gradle-daemon-jvm.properties](../../gradle/gradle-daemon-jvm.properties)。在执行
> `./gradlew` 命令时，Gradle 会自动下载并使用正确的 JBR 21。

> - 下载位置：默认存于 `$GRADLE_USER_HOME/jdks/`
> - 配置生成：`./gradlew updateDaemonJvm --jvm-version=21 --jvm-vendor=jbr`
> - 优势：自动下载的版本（来自 api.foojay.io）已确认包含 JCEF，可避免手动配置错误
>
> 如果你只是正常导入和构建项目，到这里为止通常不需要再做任何事。

如果你怀疑 JDK 不对，可以这样检查：

```shell
# 确认下载的 JDK 版本包含 JCEF。
find ~/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2/ -name jcef.jmod
# Output: ~/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2/jmods/jcef.jmod

# 也可用 SDKMAN!。
find ~/.sdkman/candidates/java/21.0.10-jbr/ -name jcef.jmod
# Output: ~/.sdkman/candidates/java/21.0.10-jbr/jmods/jcef.jmod
```

如果自动下载在你的环境里不可用，也可以自行安装 JBR。在 Android Studio 或 IntelliJ IDEA 中，可打开设置
`Build, Execution, Deployment -> Build Tools -> Gradle`，将 Gradle JDK 改为 JBR (JCEF) 21。

<img src="images/idea-settings-download-jdk.png" alt="download jbr" width="400"/>
<img src="images/idea-settings-download-jdk-version.png" alt="choose version" width="200"/>

### 准备 iOS 构建（仅 macOS）

默认情况下，仓库不会启用 iOS 编译目标，也不会构建 framework。只有在你确实要运行或打包 iOS APP 时，才需要做这一节。

先在项目根目录的 `local.properties`（如果没有就创建一个）中加入：

```properties
ani.enable.ios=true
ani.build.framework=true
```

然后再安装 iOS 依赖：

1. 在 App Store 中安装 Xcode 并打开，安装默认勾选的必要的组件。
2. 安装 Cocoapods。有多种安装方式，参考 Kotlin
   官方文档 [CocoaPods](https://kotlinlang.org/docs/native-cocoapods.html#set-up-an-environment-to-work-with-cocoapods)。

### 优化编译速度

*编译整个项目是对你的电脑的一个考验 :P*

在高性能个人机器上 (Apple M2 Max / AMD Ryzen 7 5800X / Intel i9-12900H + 64 GB 内存) 编译和测试整个项目仍然可能需要
10 分钟以上。

#### 切换安卓 ABI

默认已经只构建 `arm64-v8a`，这是大多数真机开发最省时的配置。

> [!TIP]
> **切换 Android ABI**
>
> ```properties
> # 默认值，不写也一样
> ani.android.abis=arm64-v8a
>
> # 如果你要给 x86_64 模拟器跑
> ani.android.abis=x86_64
>
> # 如果你需要完整产物
> ani.android.abis=all
> ```

#### macOS 优化

默认已经关闭 iOS 目标和 framework 构建，所以大多数 macOS 用户不需要额外优化。
只有在你要开发 iOS 时，再按上面的步骤显式开启即可。
