/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

/** Kotlin/Native 不会把 commonTest resources 带进测试 bundle: https://developer.squareup.com/blog/kotlin-multiplatform-shared-test-resources/ */
fun Project.configureAniIosTestResources() {
    if (!enableIos) return

    val copyTestResources = tasks.register<Copy>("copyiOSTestResources") {
        from("src/commonTest/resources")
        into(layout.buildDirectory.dir("bin/iosSimulatorArm64/debugTest/resources"))
    }
    tasks.named("iosSimulatorArm64Test") {
        dependsOn(copyTestResources)
    }
}

/** CocoaPods 集成, 只在 macOS 上有意义 (需要本机 pod 工具链). */
fun Project.configureAniCocoapods() {
    if (!enableIos || getOs() != Os.MacOS) return

    apply(plugin = "org.jetbrains.kotlin.native.cocoapods")

    configure<KotlinMultiplatformExtension> {
        configure<CocoapodsExtension> {
            version = project.version.toString()
            summary = project.name
            homepage = "https://github.com/open-ani/animeko"
            name = project.name

            ios.deploymentTarget = "16.0"

            // Maps custom Xcode configuration to NativeBuildType
            xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
            xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
        }
    }
}
