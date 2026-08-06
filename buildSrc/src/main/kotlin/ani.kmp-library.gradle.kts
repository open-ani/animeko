/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

/*
 * 「KMP 库」模块入口: common + desktop(JVM) + android (+ 可选 iOS).
 *
 * 与被它取代的 `ani-mpp-lib-targets` 的关键区别:
 * 这里【显式声明】需要哪些插件, 而不是用 `extensions.findByType(...) != null` 去探测
 * 别人有没有先应用. 模块脚本因此不再需要维护 "前几个插件顺序非常重要" 这种隐式契约.
 *
 * 需要 Compose 的模块请改用 `ani.kmp-compose`, 它组合了本插件.
 */

plugins {
    id("ani.base")
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("de.mannodermaus.android-junit5")
}

configure<KotlinMultiplatformExtension> {
    /**
     * 平台架构:
     * ```
     * common
     *   - jvm (可访问 JDK, 但不能使用 Android SDK 没有的 API)
     *     - android (可访问 Android SDK)
     *     - desktop (可访问 JDK)
     *   - native
     *     - apple
     *       - ios
     *         - iosArm64
     *         - iosSimulatorArm64
     * ```
     *
     * `native - apple - ios` 的架构是为了契合 Kotlin 官方推荐的默认架构.
     */
    if (project.enableIos) {
        iosArm64()
        iosSimulatorArm64() // to run tests
        // no x86
    }

    jvm("desktop")

    androidLibrary {
        compileSdk = getIntProperty("android.compile.sdk")
        minSdk = getIntProperty("android.min.sdk")
        androidResources.enable = true

        withHostTestBuilder {
            sourceSetTreeName = KotlinSourceSetTree.test.name
        }

        withDeviceTestBuilder {
            sourceSetTreeName = KotlinSourceSetTree.test.name
        }.configure {
            targetSdk {
                release(getIntProperty("android.min.sdk"))
            }
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            instrumentationRunnerArguments["runnerBuilder"] = "de.mannodermaus.junit5.AndroidJUnit5Builder"
            instrumentationRunnerArguments["package"] = "me.him188"
            execution = "HOST"
        }

        packaging {
            resources {
                pickFirsts.add("META-INF/LICENSE.md")
                pickFirsts.add("META-INF/LICENSE-notice.md")
            }
        }
    }

    applyDefaultHierarchyTemplate {
        common {
            group("jvm") {
                withJvm()
                group("android")
            }
            group("skiko") {
                withJvm()
                withNative()
            }
            group("mobile") {
                group("android")
                withIos()
            }

            group("android") {
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets.commonMain.dependencies {
        if (project.path != ":utils:platform") {
            implementation(project(":utils:platform"))
        }
    }
    sourceSets.commonTest.dependencies {
        implementation(project(":utils:testing"))
    }

    sourceSets {
        // Workaround for MPP compose bug, don't change
        removeIf { it.name == "androidAndroidTestRelease" }
        removeIf { it.name == "androidTestFixtures" }
        removeIf { it.name == "androidTestFixturesDebug" }
        removeIf { it.name == "androidTestFixturesRelease" }
    }
}

configureAniIosTestResources()
configureAniCocoapods()
