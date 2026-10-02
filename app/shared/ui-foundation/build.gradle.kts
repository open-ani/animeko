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
}

kotlin {
    android {
        namespace = "me.him188.ani.app.foundation"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appData)
        api(projects.app.shared.appPlatform)
        api(projects.utils.uiPreview)
        api(projects.utils.platform)
        api(projects.app.shared.appLang)
        api(libs.kotlinx.coroutines.core)
        implementation(projects.danmaku.danmakuApi)
        api(libs.kotlinx.collections.immutable)
        implementation(libs.kotlinx.serialization.protobuf)
        implementation(projects.app.shared.placeholder)

        api(libs.sketch.compose.core)
        implementation(libs.sketch.http.core)
        implementation(libs.sketch.svg)
        api(libs.zoomimage.compose.sketch4.core)
        implementation(libs.filekit.dialogs)
        implementation(libs.filekit.dialogs.compose)

        implementation(libs.compose.components.resources)
        api(libs.compose.lifecycle.viewmodel.compose)
        api(libs.compose.lifecycle.runtime.compose)
        api(libs.compose.navigation.compose)
        api(libs.compose.navigation.runtime)
        api(libs.compose.navigation3.runtime)
        api(libs.compose.navigation3.ui)
        api(libs.compose.lifecycle.viewmodel.navigation3)
        api(libs.compose.material3.adaptive.core.get().toString())
        api(libs.compose.material3.adaptive.layout.get().toString())
        api(libs.compose.material3.adaptive.navigation0.get().toString())

        implementation(projects.utils.bbcode)
        implementation(libs.constraintlayout.compose)
        api(projects.app.shared.pagingCompose)

        api(libs.koin.core)
        api(libs.atomicfu)

        api(libs.materialkolor)
        api(libs.kmpalette.core)
        api(libs.haze)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
        implementation(projects.utils.androidxLifecycleRuntimeTesting)
        implementation(libs.ktor.client.mock)
    }
    sourceSets.androidMain.dependencies {
        api(libs.compose.material3.adaptive.core)
        // Preview only
    }
    sourceSets.desktopMain.dependencies {
        implementation(libs.jna)
        implementation(libs.jna.platform)
        implementation(libs.dbus.java.core)
        implementation(libs.dbus.java.transport.native.unixsocket)
        api(libs.directories)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "me.him188.ani.app.ui.foundation"
}
