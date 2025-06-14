/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
    kotlin("multiplatform") apply false
    kotlin("android") apply false
    kotlin("jvm") apply false
    kotlin("plugin.serialization") version libs.versions.kotlin apply false
    kotlin("plugin.compose") apply false
    id("org.jetbrains.kotlinx.atomicfu") apply false
    id("org.jetbrains.compose") apply false
    id("com.android.library") apply false
    id("com.android.application") apply false
    id("com.google.devtools.ksp") version libs.versions.ksp apply false
    id("androidx.room") version libs.versions.room apply false
    id("com.strumenta.antlr-kotlin") version libs.versions.antlr.kotlin apply false
    id("de.mannodermaus.android-junit5") version "1.11.2.0" apply false
    id("io.sentry.kotlin.multiplatform.gradle") version libs.versions.sentry.kmp apply false
    alias(libs.plugins.compose.hot.reload) apply false
    id("de.undercouch.download") version "5.6.0" apply false // HTTP download task
    kotlin("native.cocoapods") apply false
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
