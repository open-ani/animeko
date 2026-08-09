/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

// 全仓通用约定, 取代根脚本的 allprojects / subprojects.
// https://docs.gradle.org/current/userguide/implementing_gradle_plugins_convention.html

group = "me.him188.ani"
version = providers.gradleProperty("version.name").get()

val libs = versionCatalogs.named("libs")

// Pin JNA: FileKit and other deps may request newer JNA, which breaks VLC.
// 用 force 而非 constraint, 因为需要的是降级语义.
val jnaVersion = libs.findVersion("jna").get().requiredVersion
configurations.configureEach {
    resolutionStrategy.force(
        "net.java.dev.jna:jna:$jnaVersion",
        "net.java.dev.jna:jna-platform:$jnaVersion",
    )
}

configureEncoding()

// 按类型挂钩, 避免枚举 Kotlin 插件 id; 一个项目可能应用多个, flag 保证只配置一次.
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
