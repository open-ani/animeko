/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

// = ani.kmp-library + Compose. 插件顺序在此固定, 顺序错会导致 compose resources 生成错误.

plugins {
    id("ani.kmp-library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.github.skydoves.compose.stability.analyzer")
}

val libs = versionCatalogs.named("libs")

configure<KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
        api(libs.getLibrary("compose-foundation"))
        api(libs.getLibrary("compose-runtime"))
        api(libs.getLibrary("compose-ui"))
        api(libs.getLibrary("compose-animation"))
        api(libs.getLibrary("compose-material3"))
        api(libs.getLibrary("compose-material-icons-extended"))
        api(libs.getLibrary("compose-window-core"))
    }
    sourceSets.commonTest.dependencies {
        // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
        implementation(libs.getLibrary("compose-ui-test"))
    }
    sourceSets.getByName("desktopMain").dependencies {
        implementation(libs.getLibrary("compose-ui-test-junit4"))
    }
}

dependencies {
    "androidRuntimeClasspath"(libs.getLibrary("androidx-compose-ui-test-manifest"))
}

// Compose 资源生成的任务顺序 workaround. 不要"顺手清理" —— 去掉会导致
// generateComposeResClass / generateResourceAccessorsFor* 与 Kotlin 编译任务竞争.
tasks.named("generateComposeResClass") {
    mustRunAfter("generateResourceAccessorsForAndroidHostTest")
}
tasks.withType(KotlinCompilationTask::class).configureEach {
    mustRunAfter(tasks.matching { it.name == "generateComposeResClass" })
    mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidMain" })
    mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidHostTest" })
    mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidDeviceTest" })
}

// stability analyzer 的输入任务
val stabilityInputTaskNames = listOf(
    "compileAndroidMain",
    "compileAndroidHostTest",
    "compileAndroidDeviceTest",
    "compileKotlinDesktop",
    "compileTestKotlinDesktop",
)
tasks.matching {
    it.name.endsWith("StabilityCheck") || it.name.endsWith("StabilityDump")
}.configureEach {
    stabilityInputTaskNames.forEach { taskName ->
        dependsOn(tasks.matching { task -> task.name == taskName })
    }
}
