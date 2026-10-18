# Android 应用语言

Android 的语言设置由 `app-platform` 中的 `AppLocaleController` 管理，`null` 表示跟随系统。
设置页通过 `createAppLocaleController(context)` 读取选择，并调用 `setLanguageTag(activity, tag)` 保存和应用。

- Android 13 及以上直接使用系统 `LocaleManager`。系统负责持久化、更新配置和重建 Activity。
- Android 12 及以下使用应用私有的 `app_locales` SharedPreferences，并在选择变化时重建 Activity。
  未设置偏好时兼容读取已发布版本的 AppCompat 语言存储。升级到 Android 13 时将私有偏好迁移到系统，
  系统已有的选择优先，迁移只执行一次。
- Activity 必须在 `attachBaseContext` 中调用 `wrapBaseContext`，让 Android 12 及以下的资源配置包含所选语言。
  `AniComponentActivity` 提供该集成。自定义宿主也必须显式接入；控制器不要求 AppCompatActivity。
  配置覆盖只包含语言字段，其他配置由 Android 更新。跟随系统时不创建配置覆盖。
- 宿主在 `onCreate` 创建 UI 前及 `onConfigurationChanged` 中调用 `applyDefaultLocale`，
  让 Android 12 及以下的进程默认 Locale 与 Activity 资源一致。Compose Multiplatform 的字符串资源和
  使用默认 Locale 的格式化代码依赖该设置。

`application` 的 `AppLocaleControllerTest` 使用生产 Activity 基类和 Compose `stringResource`，
检查选择、资源配置和实际渲染文本在语言切换、Activity 重建和再次启动后保持一致。
应分别在 API 30 和 API 35 模拟器上运行，以覆盖应用持久化和系统持久化两条路径：

```shell
./gradlew :app:shared:application:connectedAndroidDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.class=me.him188.ani.app.platform.AppLocaleControllerTest
```
