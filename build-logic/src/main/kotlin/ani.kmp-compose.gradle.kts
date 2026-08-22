/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
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

// Sketch exposes stable request/result types to Compose. Keep this file pinned to the Sketch version
// in libs.versions.toml and update both together.
extensions.configure<ComposeCompilerGradlePluginExtension> {
    stabilityConfigurationFiles.add {
        rootProject.layout.projectDirectory.file("gradle/sketch-compose-stability.conf").asFile
    }
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
    // 任务本名为小写开头的 "stabilityCheck" / "stabilityDump", 需要忽略大小写才能匹配到.
    it.name.endsWith("stabilityCheck", ignoreCase = true) || it.name.endsWith("stabilityDump", ignoreCase = true)
}.configureEach {
    stabilityInputTaskNames.forEach { taskName ->
        // 只约束顺序而不强制执行: 这些编译任务如果与 stabilityCheck 同图, 必须先运行
        // (它们会写入 build/stability); 但 stabilityCheck 不应主动触发它们.
        mustRunAfter(tasks.matching { task -> task.name == taskName })
    }
}
