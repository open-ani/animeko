/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import java.util.Properties

plugins {
    `kotlin-dsl`
}

// included build 不继承主构建的 gradle.properties, jvm.toolchain.* 必须显式从仓库根读,
// 否则 toolchain 会悄悄回落到默认 JDK. (settingsDirectory 是 build-logic/ 自己, ".." 才是仓库根)
fun rootProperties(fileName: String): Provider<Properties> =
    providers.fileContents(layout.settingsDirectory.dir("..").file(fileName)).asText
        .map { text -> Properties().apply { text.reader().use { load(it) } } }

val rootLocalProperties = rootProperties("local.properties")
val rootGradleProperties = rootProperties("gradle.properties")

fun toolchainProperty(name: String): Provider<String> =
    rootLocalProperties.map { it.getProperty(name) }
        .orElse(rootGradleProperties.map { it.getProperty(name) })
        .orElse(providers.gradleProperty(name))

kotlin {
    jvmToolchain {
        toolchainProperty("jvm.toolchain.vendor").orNull?.let { vendor.set(JvmVendorSpec.matching(it)) }
        toolchainProperty("jvm.toolchain.version").orNull?.let { languageVersion.set(JavaLanguageVersion.of(it)) }
    }
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi")
    }
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.awssdk.s3)
}

dependencies {
    api(gradleApi())
    api(gradleKotlinDsl())

    api(libs.kotlin.gradle.plugin) {
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-reflect")
    }

    api(libs.android.gradle.plugin)
    api(libs.atomicfu.gradle.plugin)
    api(libs.android.application.gradle.plugin)
    api(libs.android.kotlin.multiplatform.library.gradle.plugin)
    api(libs.compose.multiplatfrom.gradle.plugin)
    api(libs.kotlin.compose.compiler.gradle.plugin)
    api(libs.kotlin.native.cocoapods.gradle.plugin)
    api(libs.mannodermaus.android.junit5.gradle.plugin)
    api(libs.compose.stability.analyzer.gradle.plugin)
    implementation(kotlin("script-runtime"))
    implementation(libs.snakeyaml)
}




