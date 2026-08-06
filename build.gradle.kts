/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlinx.atomicfu) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.kotlin.native.cocoapods) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false

    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.antlr.kotlin) apply false
    // mannodermaus.android.junit5 与 compose.stability.analyzer 已改由 buildSrc 的
    // 约定插件 (ani.kmp-library / ani.kmp-compose) 在自己的 plugins {} 里声明,
    // 它们的 marker artifact 在 buildSrc 的 classpath 上. 这里再写 `apply false`
    // 会因为"已在 classpath 上但版本未知"而冲突.
    alias(libs.plugins.sentry.kotlin.multiplatform) apply false
    alias(libs.plugins.undercouch.download) apply false
    idea
}

// group / version / 仓库 / JNA pin / Kotlin 约定原先都在这里的 `allprojects {}` 和
// `subprojects { afterEvaluate { ... } }` 里. 现已分别搬到:
//   - 仓库          -> settings.gradle.kts 的 dependencyResolutionManagement
//   - 其余全部      -> buildSrc 的 `ani.base` 约定插件, 由各子项目显式应用
// 参见 gradle-build-rework.md 的 F1.
group = "me.him188.ani"
version = providers.gradleProperty("version.name").get()

idea {
    module {
        excludeDirs.add(file(".kotlin"))
    }
}

// Note: this task does not support configuration cache
tasks.register("downloadAllDependencies") {
    notCompatibleWithConfigurationCache("Filters configurations at execution time")
    description = "Resolves every resolvable configuration in every project"
    group = "help"

    doLast {
        rootProject.allprojects.forEach { p ->
            p.configurations
                .filter { it.isCanBeResolved }
                .forEach {
                    runCatching { it.resolve() }
                }
        }
    }
}
