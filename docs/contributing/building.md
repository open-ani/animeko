# 构建打包

如果遇到问题，请查看 [常见构建和运行问题](#常见构建和运行问题)。

## 配置秘钥

Ani 依赖一些外部服务，因此你需要有这些服务的秘钥等信息才能正常使用功能。打包之前需要在
`local.properties`
中配置这些信息。如果不配置，打包仍然会成功，但运行时无法使用对应功能。

```properties
ani.dandanplay.app.id=aaaaaaaaa
ani.dandanplay.app.secret=aaaaaaaaaaaaaaa
```

## 打包 Android APP

默认只构建 `arm64-v8a`。如果你需要完整 APK 集合，可在 `local.properties` 中加入
`ani.android.abis=all`。

在 IDE 中双击 Ctrl，可用的命令：

- `./gradlew assembleRelease` - 编译发布版
- `./gradlew assembleDebug` - 编译测试版
- `./gradlew installRelease` - 构建发布版并安装到模拟器
- `./gradlew installDebug` - 构建测试版并安装到模拟器

在 IDE 上也可以选择 `Build -> Build Bundle(s) / APK(s) -> Build APK(s)` 来构建 APK。

## 打包 iOS APP

默认不启用 iOS 构建。打包之前，请先在 `local.properties` 中加入：

```properties
ani.enable.ios=true
ani.build.framework=true
```

然后运行以下命令初始化项目：

1. `./gradlew podInstall`。如果找不到 pod，可以自行 `cd app/ios && pod install`。
2. `./gradlew patchInfoPlist`

在 IDE 中双击 Ctrl，可用的命令：

- `./gradlew buildDebugIpa` - 构建测试版（安装需要自签）
- `./gradlew buildReleaseIpa` - 构建发布版（安装需要自签）

## 打包桌面应用

要构建桌面应用，请参考 [Compose for Desktop]
官方文档，或简单执行 `./gradlew createReleaseDistributable`
，结果保存在 `app/desktop/build/compose/binaries` 中。

一个操作系统只能构建对应的桌面应用，例如 Windows 只能构建 Windows 应用，而不能构建 macOS 应用。

## 运行测试版应用

参考 [testing](testing.md)。

## 运行测试

在 IDE 中双击 Ctrl，执行 `./gradlew check` 可以运行所有测试，包括单元测试和 UI 测试。

默认配置下，macOS 上不会包含 iOS 测试；如果启用了 iOS 目标，测试总数会到 11,000+。Windows 上只能运行安卓和
JVM 平台测试，无法运行 iOS 测试。

> [!TIP]
> **重复运行测试**
>
> 由于启用了 Gradle build cache，如果代码没有修改，test 就不会执行。
>
> 可使用 `./gradlew clean check` 清空缓存并重新运行所有测试。

## 常见构建和运行问题

### 编译报错找不到 `Res.*`

这是 Compose 的 bug，请生成 Compose Multiplatform 资源：

执行 `./gradlew generateComposeResClass` 即可生成一个 `Res` 类，用于在 `:app:shared` 访问资源文件。

### Android 触发断点恢复运行后，APP 无响应

打开 `app.android` 的配置，将 Debugger -> Debug type 改为 Java only。

### 启动 PC 版时报错 `ClassNotDefFoundError`

打开 `Run Desktop` 的配置，复制一份，将 "Use classpath of module" 改为 `ani.app.desktop.test`。
如果又遇到了，则改回来 `ani.app.desktop.main`。
