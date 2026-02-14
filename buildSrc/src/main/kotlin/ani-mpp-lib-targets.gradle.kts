/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/*
 * 配置 JVM + Android 的 compose 项目. 默认不会配置 resources. 
 * 
 * 该插件必须在 kotlin, compose, android 之后引入.
 * 
 * 如果开了 android, 就会配置 desktop + android, 否则只配置 jvm.
 */

val androidLibraryExtension = extensions.findByType(KotlinMultiplatformExtension::class)
        ?.extensions?.findByType(KotlinMultiplatformAndroidLibraryExtension::class) 
val composeExtension = extensions.findByType(ComposeExtension::class)
val composeCompilerExtension =
    extensions.findByType(org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension::class)
val enableHotReload = getLocalProperty("ani.compose.hot.reload")?.toBooleanStrict() != false

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
     *         - iosSimulatorArm64 TODO
     * ```
     *
     * `native - apple - ios` 的架构是为了契合 Kotlin 官方推荐的默认架构. 以后如果万一要添加其他平台, 可方便添加.
     */
    if (project.enableIos) {
        iosArm64()
        iosSimulatorArm64() // to run tests
        // no x86
    }
    if (androidLibraryExtension != null) {
        jvm("desktop")
        /*androidTarget {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
            unitTestVariant.sourceSetTree.set(KotlinSourceSetTree.unitTest)
        }*/
        androidLibrary {
            compileSdk = getIntProperty("android.compile.sdk")
            minSdk = getIntProperty("android.min.sdk")
            androidResources.enable = true

            withHostTestBuilder {
                sourceSetTreeName = KotlinSourceSetTree.unitTest.name
            }

            withDeviceTestBuilder {
                sourceSetTreeName = KotlinSourceSetTree.test.name
            }.configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                instrumentationRunnerArguments["runnerBuilder"] = "de.mannodermaus.junit5.AndroidJUnit5Builder"
                instrumentationRunnerArguments["package"] = "me.him188"
                execution = "HOST"
            }

            packaging {
                resources {
                    /*pickFirsts.add("META-INF/LICENSE.md")
                    pickFirsts.add("META-INF/LICENSE-notice.md")*/
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

        // This won't work (KT 2.1.0)
//        sourceSets {
//            val commonAndroidTest = create("commonAndroidTest") {
//                dependsOn(getByName("jvmTest"))
//            }
//            getByName("androidInstrumentedTest").dependsOn(commonAndroidTest)
//            getByName("androidUnitTest").dependsOn(commonAndroidTest)
//        }
    } else {
        jvm()

        applyDefaultHierarchyTemplate()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    val libs = versionCatalogs.named("libs")
    val composeMultiplatformVersion = libs.findVersion("compose-multiplatform").get()
    sourceSets.commonMain.dependencies {
        // 添加常用依赖
        if (composeExtension != null) {
            // Compose
            api("org.jetbrains.compose.foundation:foundation:${composeMultiplatformVersion}")
            api("org.jetbrains.compose.animation:animation:${composeMultiplatformVersion}")
            api("org.jetbrains.compose.ui:ui:${composeMultiplatformVersion}")
            
            api("org.jetbrains.compose.material3:material3:${libs.findVersion("compose-material3").get()}")
            api("org.jetbrains.androidx.window:window-core:${libs.findVersion("compose-window-core").get()}")

            api("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            api("org.jetbrains.compose.runtime:runtime:${composeMultiplatformVersion}")
        }

        if (project.path != ":utils:platform") {
            implementation(project(":utils:platform"))
        }
    }
    sourceSets.commonTest.dependencies {
        // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html#writing-and-running-tests-with-compose-multiplatform
        if (composeExtension != null) {
            implementation("org.jetbrains.compose.ui:ui-test:${composeMultiplatformVersion}")
        }
        implementation(project(":utils:testing"))
    }

    if (composeExtension != null) {
        sourceSets.getByName("desktopMain").dependencies {
            val compose = ComposePlugin.Dependencies(project)
            implementation("org.jetbrains.compose.ui:ui-test-junit4:${composeMultiplatformVersion}")
        }
    }

    if (androidLibraryExtension != null) {
        val androidMainSourceSetDir = projectDir.resolve("androidMain")
        /*val androidExtension = extensions.findByType(CommonExtension::class)
        if (androidExtension != null) {
            androidExtension.sourceSets["main"].aidl.srcDirs(androidMainSourceSetDir.resolve("aidl"))
            // add more sourceSet dirs if necessary.
        }*/

        sourceSets {
            // Workaround for MPP compose bug, don't change
            removeIf { it.name == "androidAndroidTestRelease" }
            removeIf { it.name == "androidTestFixtures" }
            removeIf { it.name == "androidTestFixturesDebug" }
            removeIf { it.name == "androidTestFixturesRelease" }
        }

        if (composeExtension != null) {
            tasks.named("generateComposeResClass") {
                mustRunAfter("generateResourceAccessorsForAndroidHostTest")
            }
            tasks.withType(KotlinCompilationTask::class) {
                mustRunAfter(tasks.matching { it.name == "generateComposeResClass" })
                mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidRelease" })
                mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidHostTest" })
                mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidHostTestRelease" })
                mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidHostTestDebug" })
                mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidDebug" })
            }

            val composeVersion = versionCatalogs.named("libs").findVersion("jetpack-compose").get()
            listOf(
                sourceSets.getByName("androidDeviceTest"),
                sourceSets.getByName("androidHostTest"),
            ).forEach { sourceSet ->
                sourceSet.dependencies {
                    // https://developer.android.com/develop/ui/compose/testing#setup
//                implementation("androidx.compose.ui:ui-test-junit4-android:${composeVersion}")
//                implementation("androidx.compose.ui:ui-test-manifest:${composeVersion}")
                    // TODO: this may cause dependency rejection when importing the project in IntelliJ.
                }
            }

            project.dependencies {
                "androidRuntimeClasspath"("androidx.compose.ui:ui-test-manifest:${composeVersion}")
            }
        }
    }
}

if (enableIos) {
    // ios testing workaround
    // https://developer.squareup.com/blog/kotlin-multiplatform-shared-test-resources/
    tasks.register<Copy>("copyiOSTestResources") {
        from("src/commonTest/resources")
        into("build/bin/iosSimulatorArm64/debugTest/resources")
    }
    tasks.named("iosSimulatorArm64Test") {
        dependsOn("copyiOSTestResources")
    }
}

if (androidLibraryExtension != null) {
    apply(plugin = "de.mannodermaus.android-junit5")
}

if (enableIos) {
    if (getOs() == Os.MacOS) {
        apply(plugin = "org.jetbrains.kotlin.native.cocoapods")

        configure<KotlinMultiplatformExtension> {
            this.configure<CocoapodsExtension> {
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
}
