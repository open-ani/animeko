/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.ProguardFiles.getDefaultProguardFile


plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)
    // 注意! 前几个插件顺序非常重要, 调整后可能导致 compose multiplatform resources 生成错误

    `ani-mpp-lib-targets`

    alias(libs.plugins.kotlin.plugin.serialization)
    // TODO AGP Migration: atomicfu plugin broken see: https://github.com/Kotlin/kotlinx-atomicfu/issues/511
    // alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.sentry.kotlin.multiplatform)
    idea
}

compose.resources {
    packageOfResClass = "me.him188.ani.app"
    generateResClass = always
}

// TODO AGP Migration: atomicfu plugin broken see: https://github.com/Kotlin/kotlinx-atomicfu/issues/511
//atomicfu {
//    transformJvm = false // 这东西很不靠谱, 等 atomicfu 正式版了可能可以考虑下
//}

val enableIosFramework = enableIos && getPropertyOrNull("ani.build.framework") != "false"

kotlin {
    androidLibrary {
        namespace = "me.him188.ani"
        compileSdk = getIntProperty("android.compile.sdk")
        minSdk = getIntProperty("android.min.sdk")
        // TODO AGP Migration: Test package optimization

        optimization {
            minify = false
            keepRules.apply {
                files(
                    getDefaultProguardFile("proguard-android-optimize.txt", layout.buildDirectory),
                    *sharedAndroidProguardRules(),
                )
            }
        }

    }

    sourceSets.commonMain.dependencies {
        api(projects.utils.platform)
        api(projects.utils.intellijAnnotations)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.json)
        api(libs.kotlinx.serialization.json.io)
        api(libs.kotlinx.serialization.protobuf)
        implementation(libs.atomicfu) // room runtime
        api(libs.kotlinx.datetime)
        api(libs.kotlinx.io.core)
        api(libs.kotlinx.collections.immutable)
        api(projects.utils.ktorClient)

        api(projects.app.shared.appPlatform)
        api(projects.app.shared.appData)
        api(projects.app.shared.placeholder)
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.videoPlayer)
        api(projects.app.shared.videoPlayer.torrentSource)
        api(projects.app.shared.uiSettings)
        api(projects.app.shared.uiExploration)
        api(projects.app.shared.uiSubject)
        api(projects.app.shared.uiCache)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiOnboarding)
        api(projects.app.shared.uiEpisode)
        api(projects.app.shared.uiMediaselect)
        api(projects.app.shared.uiExprovider)

        // Compose
        api(libs.compose.lifecycle.viewmodel.compose)
        api(libs.compose.lifecycle.runtime.compose)
        api(libs.compose.navigation.compose)
        api(libs.compose.navigation.runtime)
        api(libs.compose.material3.adaptive.navigation.suite)
        implementation(compose.components.resources)
        implementation(projects.app.shared.reorderable)

        // Data sources
        api(projects.datasource.datasourceApi)
        api(projects.datasource.datasourceCore)
        api(projects.datasource.bangumi)
        api(projects.datasource.mikan)

        api(projects.client)
        api(projects.utils.logging)
        api(projects.utils.coroutines)
        api(projects.utils.io)
        api(projects.app.shared.imageViewer)
        api(projects.utils.xml)
        api(projects.utils.bbcode)
        api(projects.danmaku.danmakuApi)
        api(projects.danmaku.dandanplay)
        api(projects.danmaku.danmakuUi)
        api(projects.utils.ipParser)
        api(projects.torrent.torrentApi)
        api(projects.torrent.anitorrent)

        // Ktor
        api(libs.ktor.client.websockets)
        api(libs.ktor.client.logging)
        api(libs.ktor.client.content.negotiation)
        api(libs.ktor.serialization.kotlinx.json)

        // Others
        api(libs.koin.core)
        api(libs.coil.core)
        api(libs.coil.svg)
        api(libs.coil.compose.core)
        implementation(libs.constraintlayout.compose)
    }

    // shared by android and desktop
    sourceSets.getByName("jvmMain").dependencies {
        // TODO: to be commonized
        api(projects.datasource.dmhy)
        api(projects.datasource.jellyfin)
        api(projects.datasource.ikaros)

        implementation(libs.jna)
        implementation(libs.slf4j.api)
        api(libs.ktor.client.okhttp)
    }

    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.utils.testing)
        implementation(projects.utils.uiTesting)
        implementation(libs.turbine)
    }

    // androidUnitTest is apart from the commonTest tree so we have to do it again
    sourceSets.androidHostTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.utils.testing)
        implementation(projects.utils.uiTesting)
        implementation(libs.turbine)
    }

    sourceSets.androidMain.dependencies {
        api(libs.kotlinx.coroutines.android)
        api(libs.datastore)
        api(libs.datastore.preferences)
        api(libs.androidx.appcompat)
        api(libs.androidx.media)
        api(libs.androidx.core.ktx)
        api(libs.androidx.activity.compose)
        api(libs.androidx.activity.ktx)
        api(libs.koin.android)
        implementation(libs.androidx.browser)

        // Compose
        api(libs.androidx.compose.ui.tooling.preview)

        api(libs.coil)

        api(libs.logback.android)
        api(projects.utils.buildConfig)
    }

    sourceSets.androidHostTest.dependencies {
        implementation(libs.mockito)
        implementation(libs.mockito.kotlin)
        implementation(libs.koin.test)
    }

    sourceSets.nativeMain.dependencies {
        implementation(libs.stately.common) // fixes koin bug
    }

    sourceSets.named("desktopMain").dependencies {
        api(compose.desktop.currentOs) {
            exclude(compose.material) // We use material3
            exclude("org.jetbrains.compose.ui:ui-tooling-preview")
        }
        api("org.jetbrains.compose.ui:ui-graphics-desktop:${libs.versions.compose.multiplatform.get()}")
        api(projects.utils.logging)
        api(libs.kotlinx.coroutines.swing)
        implementation(libs.vlcj)
        implementation(libs.jna) // required and don't change version, otherwise vlcj might crash the VM 

        // This causes duplicated entry when packaging compose binaries (Task `createDistribution`).
        // https://youtrack.jetbrains.com/issue/CMP-7734/packageReleaseDmg-for-macOS-arm64-fails-since-1.8.0-alpha04
//        runtimeOnly(libs.kotlinx.coroutines.debug)

        implementation(libs.log4j.core)
        implementation(libs.log4j.slf4j.impl)

        implementation(libs.ktor.serialization.kotlinx.json)
    }
}

// RESOURCES

idea {
    val generatedResourcesDir = file("build/generated/compose/resourceGenerator/kotlin")
    module {
        generatedSourceDirs.add(generatedResourcesDir.resolve("commonMainResourceAccessors"))
        generatedSourceDirs.add(generatedResourcesDir.resolve("commonResClass"))
    }
}

// AS 问题 since Compose 1.7.0-beta03
afterEvaluate {
    tasks.matching { it.name.contains("generateReleaseLintVitalModel") }.configureEach {
        mustRunAfter("releaseAssetsCopyForAGP")
    }
    tasks.matching { it.name.contains("lintVitalAnalyzeRelease") }.configureEach {
        mustRunAfter("releaseAssetsCopyForAGP")
    }
}


// BUILD CONFIG

//if (bangumiClientDesktopAppId == null || bangumiClientDesktopSecret == null) {
//    logger.warn("bangumi.oauth.client.desktop.appId or bangumi.oauth.client.desktop.secret is not set. Bangumi authorization will not work. Get a token from https://bgm.tv/dev/app and set them in local.properties.")
//}

dependencies {
    androidRuntimeClasspath(libs.androidx.compose.ui.tooling)
}


// 太耗内存了, 只能一次跑一个
// compose bug, 不能用这个 https://youtrack.jetbrains.com/issue/CMP-5835
//tasks.filter { it.name.contains("link") && it.name.contains("Framework") && it.name.contains("Ios") }
//    .sorted()
//    .let { links ->
//        links.forEachIndexed { index, task ->
//            for (index1 in (index + 1)..links.lastIndex) {
//                task.mustRunAfter(links[index1])
//            }
//        }
//    }

if (enableIosFramework) {
// 太耗内存了, 只能一次跑一个
//    tasks.named("linkDebugFrameworkIosArm64") {
//        mustRunAfter("linkReleaseFrameworkIosArm64")
//        mustRunAfter("linkDebugFrameworkIosSimulatorArm64")
//        mustRunAfter("linkReleaseFrameworkIosSimulatorArm64")
//    }
//    tasks.named("linkReleaseFrameworkIosArm64") {
//        mustRunAfter("linkDebugFrameworkIosSimulatorArm64")
//        mustRunAfter("linkReleaseFrameworkIosSimulatorArm64")
//    }
//    tasks.named("linkDebugFrameworkIosSimulatorArm64") {
//        mustRunAfter("linkReleaseFrameworkIosSimulatorArm64")
//    }
}
