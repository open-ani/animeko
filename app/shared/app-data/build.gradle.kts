/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)

    `ani-mpp-lib-targets`
    alias(libs.plugins.kotlin.plugin.serialization)

    // alias(libs.plugins.kotlinx.atomicfu)
    id("kotlin-parcelize")

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
        api(projects.app.shared.videoPlayer.videoPlayerApi)
        api(projects.app.shared.videoPlayer.torrentSource)
        api(mediampLibs.mediamp.api)
        api(mediampLibs.mediamp.source.ktxio)
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
    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.compose.ui.tooling.preview)
        implementation(libs.androidx.compose.ui.tooling)
        implementation(libs.androidx.browser)
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

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    kspDesktop(libs.androidx.room.compiler)
    kspAndroid(libs.androidx.room.compiler)
    if (enableIos) {
        kspIosArm64(libs.androidx.room.compiler)
        kspIosSimulatorArm64(libs.androidx.room.compiler)
    }
    androidRuntimeClasspath(libs.androidx.compose.ui.tooling)
}
