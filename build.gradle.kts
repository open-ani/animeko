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
    // alias(libs.plugins.stability.analyzer) apply false
    idea
}

allprojects {
    group = "me.him188.ani"
    version = properties["version.name"].toString()

    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
        maven("https://jogamp.org/deployment/maven")
    }
}


extensions.findByName("buildScan")?.withGroovyBuilder {
    setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
    setProperty("termsOfServiceAgree", "yes")
}

subprojects {
    // configureComposeStabilityAnalyzer()
    afterEvaluate {
        configureKotlinOptIns()
        configureKotlinTestSettings()
        configureEncoding()
        configureJvmTarget()
//        kotlin.runCatching {
//            extensions.findByType(ComposeExtension::class)?.apply {
//                this.kotlinCompilerPlugin.set(libs.versions.compose.multiplatform.compiler.get())
//            }
//        }
    }
}

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
