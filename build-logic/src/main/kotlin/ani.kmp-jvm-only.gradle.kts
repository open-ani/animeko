/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// KMP 但无 Android target. 用 jvm() 而非 jvm("desktop"), 故源集是 jvmMain / jvmTest.

plugins {
    id("ani.base")
    id("org.jetbrains.kotlin.multiplatform")
}

configure<KotlinMultiplatformExtension> {
    if (project.enableIos) {
        iosArm64()
        iosSimulatorArm64() // to run tests
    }

    jvm()

    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets.commonMain.dependencies {
        implementation(project(":utils:platform"))
    }
    sourceSets.commonTest.dependencies {
        implementation(project(":utils:testing"))
    }
}

configureAniIosTestResources()
configureAniCocoapods()
