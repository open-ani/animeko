/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/*
 * 「KMP 库, 但没有 Android target」模块入口.
 *
 * 只有两个模块用它: :datasource:datasource-core 和 :datasource:mikan
 * (mikan 的 commonTest 需要 android 不支持的 resources).
 *
 * 注意它用的是 `jvm()` 而不是 `ani.kmp-library` 的 `jvm("desktop")`,
 * 所以源集叫 jvmMain / jvmTest 而不是 desktopMain / desktopTest.
 *
 * 这是原 `ani-mpp-lib-targets` 里 `if (androidLibraryExtension != null) { ... } else { ... }`
 * 的 else 分支 —— 现在它是一个显式的模块类型, 而不是"探测不到 Android 插件"的副作用.
 */

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
