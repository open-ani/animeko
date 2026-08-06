/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    id("ani.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

    // alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.kotlin.parcelize)

    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.androidx.room)
    idea
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.app.data"
    }
    sourceSets.commonMain.dependencies {
        implementation(projects.app.shared.appPlatform)
        implementation(projects.app.shared.appLang)
        implementation(projects.utils.intellijAnnotations)
        implementation(libs.compose.components.resources)
        api(projects.app.shared.videoPlayer.videoPlayerApi)
        api(projects.app.shared.videoPlayer.torrentSource)
        api(libs.mediamp.api)
        api(libs.mediamp.test)
        api(libs.mediamp.source.ktxio)
        implementation(libs.kotlinx.serialization.json.io)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.core)
        api(libs.kotlinx.collections.immutable)
        implementation(libs.kotlinx.serialization.json)
        implementation(projects.utils.io)
        implementation(projects.utils.coroutines)
        api(projects.danmaku.danmakuUiConfig)
        api(projects.utils.xml)
        api(projects.utils.coroutines)
        api(projects.client)
        api(projects.utils.ipParser)
        api(projects.utils.jsonpath)
        api(projects.utils.httpDownloader)
        api(projects.utils.serialization)

        api(projects.torrent.torrentApi)
        api(projects.torrent.anitorrent)
        api(projects.torrent.pikpak)

        api(libs.datastore.core) // Data Persistence
        api(libs.datastore.preferences.core) // Preferences
        api(libs.androidx.room.runtime)
        api(libs.androidx.room.paging)
        api(libs.sqlite.bundled)

        api(projects.datasource.datasourceApi)
        api(projects.datasource.datasourceCore)
        api(projects.datasource.bangumi)
        api(projects.datasource.mikan)
        api(projects.datasource.jellyfin)
        api(projects.datasource.ikaros)
        api(projects.danmaku.danmakuApi)
        api(projects.danmaku.dandanplay)

        api(libs.paging.common)

        implementation(libs.koin.core)
        implementation(libs.atomicfu)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
        implementation(projects.utils.androidxLifecycleRuntimeTesting)
        implementation(libs.ktor.client.mock)
        implementation(libs.turbine)
        implementation(kotlin("reflect"))
    }
    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.slf4j.simple)
    }
    sourceSets.desktopMain {
        dependencies {
            implementation(libs.onnxruntime)
            // 判断的是构建主机, 所以 Windows ARM64 包必须在 ARM64 机器上原生构建, 无法从 x64 交叉打包.
            if (getOs() == Os.Windows && getArch() == Arch.AARCH64) {
                // AndroidX sqlite-bundled-jvm 没有 Windows ARM64 native 库, 这里补上本机编译的 sqliteJni.dll.
                // 详见 ci-helper/sqlite-woa64/build.gradle.kts 的头注释
                runtimeOnly(projects.ciHelper.sqliteWoa64)
            }
        }
    }
    sourceSets.desktopTest {
        resources.srcDir("src/androidDeviceTest/assets")
        dependencies {
            implementation("androidx.room:room-testing:${libs.versions.room.get()}")
        }
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.browser)
        implementation(libs.onnxruntime.android)
        api(libs.androidx.lifecycle.runtime.ktx)
        api(libs.androidx.lifecycle.service)
        api(libs.androidx.lifecycle.process)
        api(projects.app.shared.appDataAidl)
    }
    sourceSets.nativeMain.dependencies {
        implementation(libs.stately.common) // fixes koin bug
        implementation(libs.kotlinx.io.okio)
    }
}

compose.resources {
    packageOfResClass = "me.him188.ani.app.data"
    generateResClass = always
}

room {
    schemaDirectory("$projectDir/schemas")
}

// 不要再加 ksp { arg("room.schemaLocation", ...) }: Room 插件禁止与上面的 room {} 同时使用.

dependencies {
    kspDesktop(libs.androidx.room.compiler)
    kspAndroid(libs.androidx.room.compiler)
    if (enableIos) {
        add("kspIosArm64", libs.androidx.room.compiler)
        add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    }
}
