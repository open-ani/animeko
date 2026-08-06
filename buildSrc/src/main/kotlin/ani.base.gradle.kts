/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

/*
 * 全仓通用约定. 取代原先根 build.gradle.kts 里的 `allprojects {}` 和
 * `subprojects { afterEvaluate { ... } }`.
 *
 * 官方指引:
 *   "Convention plugins are preferred over `allprojects {}` / `subprojects {}` blocks."
 *   https://docs.gradle.org/current/userguide/implementing_gradle_plugins_convention.html
 *
 * 这个插件本身不依赖任何 Kotlin / Android 插件, 因此可以被所有子项目无条件应用
 * (包括 `:app:ios` 这种纯任务编排项目和 `:ci-helper:sqlite-woa64` 这种 java-library).
 * 与 Kotlin 相关的约定通过 `pluginManager.withPlugin(...)` 挂钩 —— 无论 Kotlin 插件
 * 是在本插件之前还是之后应用, 回调都会触发, 因此不再有插件顺序依赖.
 */

group = "me.him188.ani"
version = providers.gradleProperty("version.name").get()

val libs = versionCatalogs.named("libs")

// Pin JNA: FileKit and other deps may request newer JNA, which breaks VLC.
//
// 这里必须用 `force` 而不是 dependency constraint: 需要的是"把更高版本压回去"的降级语义,
// 而 constraint 只能抬高下界, 遇到更高版本不会降级.
val jnaVersion = libs.findVersion("jna").get().requiredVersion
configurations.configureEach {
    resolutionStrategy.force(
        "net.java.dev.jna:jna:$jnaVersion",
        "net.java.dev.jna:jna-platform:$jnaVersion",
    )
}

// 原先这些都在 root 的 `subprojects { afterEvaluate { ... } }` 里.
// 现在由每个项目显式应用本插件触发, 并且内部实现全部换成了 configureEach / matching,
// 不再 eager realize 任务.
configureEncoding()

// 按【类型】而不是插件 id 挂钩: KotlinBasePlugin 是所有 Kotlin Gradle 插件
// (multiplatform / jvm / android / ...) 的公共父类型, 因此不用枚举 id, 也不会漏掉
// 某个项目实际用的是哪个 flavor.
// 一个项目可能应用多个 KotlinBasePlugin, 用 flag 保证约定只配置一次.
var kotlinConventionsConfigured = false
plugins.withType(KotlinBasePlugin::class.java) {
    if (kotlinConventionsConfigured) return@withType
    kotlinConventionsConfigured = true

    configureKotlinOptIns()
    configureKotlinTestSettings()
    configureJvmTarget()
}

// Compose + Android KMP Library 才需要 ui-tooling.
pluginManager.withPlugin("org.jetbrains.compose") {
    pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
        configureComposePreviewToolingDependency()
    }
}
