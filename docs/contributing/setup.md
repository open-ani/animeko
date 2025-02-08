# 开发工具和环境

> [!IMPORTANT]
> 这些步骤只需要几分钟即可完成，请不要跳过。跳过可能会导致花费更多时间解决问题。

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

## 准备 JDK (JetBrains Runtime with JCEF)

由于 PC 端使用 [JCEF](https://github.com/jetbrains/jcef) (内置浏览器)，JDK 必须使用 JetBrains
Runtime (JCEF)，版本 17 或 21，下文简称 JBR。

需要自行安装 JBR (必须是带有 JCEF 的版本, 见图)。在 Android Studio 或 IntelliJ IDEA 中，如下图所示，
可打开设置
`Build, Execution, Deployment -> Build Tools -> Gradle`，修改 Gradle JDK 配置为 JBR (JCEF) 17 或 21。
如果使用 21，则需要在 `local.properties` 中添加一行 `jvm.toolchain.version=21`。如果使用 17 则无需修改配置。

<img src="images/idea-settings-download-jdk.png" alt="download jbr" width="400"/>
<img src="images/idea-settings-download-jdk-version.png" alt="choose version" width="200"/>

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

## 优化编译速度

*编译整个项目是对你的电脑的一个考验 :P*

在高性能个人机器上 (Apple M2 Max / AMD Ryzen 7 5800X / Intel i9-12900H + 64 GB 内存) 编译和测试整个项目仍然可能需要
10 分钟以上。

### 通用优化

**对于所有操作系统**，都建议禁用你不需要的 Android 架构。
例如你的手机大概率是 arm64-v8a，那么可以设置只构建这个架构，将大幅提升编译速度。

> [!TIP]
> **只启用 Android arm64-v8a 架构**
>
> 在项目根目录的 `local.properties`（如果没有就创建一个）中增加以下内容:
>
> ```properties
> ani.android.abis=arm64-v8a
> ```

### macOS 优化

由于 macOS 上支持构建 iOS (也默认开启)，对内存的需求会大幅上升。如果你无需运行 iOS APP，可以禁用
Framework 构建。这可以帮你节约 20 分钟以上的编译时间。

> [!TIP]
> **禁用 iOS Framework**
>
> 在项目根目录的 `local.properties`（如果没有就创建一个）中增加以下内容:
>
> ```properties
> ani.build.framework=false
> ```
