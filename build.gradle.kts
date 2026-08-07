/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// 这串 `apply false` 不是历史包袱, 不要"因为都搬进 build-logic 了"而删掉.
//
// Gradle 按 buildscript classpath 给每个 project 建 ClassLoader, 子项目的 scope 是根的子级, 类加载 parent-first.
// 子项目各自的 `plugins {}` 组合不同 (serialization / sentry / ksp / room ...), classpath 就不同,
// 于是每个组合都会拿到一个独立 ClassLoader —— 里面各带一份 KGP. 而 BuildService 是全局按名字注册的:
// 先注册的项目存进去的是它那份 KGP 里的类, 另一个项目取出来往自己那份类上 cast 就炸,
// 例如 macOS 上配置 :app:shared 时的 SwiftPMLockTaskAggregationBuildService ClassCastException.
//
// 在根这里声明 (apply false 表示只进 classpath 不应用), 这些 jar 就落在根 scope,
// 子项目 parent-first 命中同一份类, 全仓才只有一份 KGP. 版本与 catalog 一致, 子项目照旧可以带版本请求.
// 新增会被多个模块共享的插件时, 这里也要补一行.
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
    alias(libs.plugins.mannodermaus.android.junit5) apply false
    alias(libs.plugins.sentry.kotlin.multiplatform) apply false
    alias(libs.plugins.undercouch.download) apply false
    alias(libs.plugins.compose.stability.analyzer) apply false
    idea
}

// 仓库搬到 settings 的 dependencyResolutionManagement, 其余约定搬到 build-logic 的 `ani.base`.
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
