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
    // TODO AGP Migration: atomicfu plugin broken see: https://github.com/Kotlin/kotlinx-atomicfu/issues/511
    // alias(libs.plugins.kotlinx.atomicfu)
}

kotlin {
    androidLibrary {
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

        api(libs.coil.compose.core)
        api(libs.coil.svg)
        api(libs.coil.network.ktor3)

        implementation(compose.components.resources)
        api(libs.compose.lifecycle.viewmodel.compose)
        api(libs.compose.lifecycle.runtime.compose)
        api(libs.compose.navigation.compose)
        api(libs.compose.navigation.runtime)
        api(libs.compose.material3.adaptive.core.get().toString())
        api(libs.compose.material3.adaptive.layout.get().toString())
        api(libs.compose.material3.adaptive.navigation0.get().toString())

        implementation(projects.utils.bbcode)
        implementation(libs.constraintlayout.compose)
        api(projects.app.shared.pagingCompose)

        api(libs.koin.core)

        api(libs.materialkolor)
    }
    sourceSets.commonTest.dependencies {
        api(projects.utils.uiTesting)
        api(projects.utils.androidxLifecycleRuntimeTesting)
    }
    sourceSets.androidMain.dependencies {
        api(libs.androidx.compose.ui.tooling.preview)
        api(libs.androidx.compose.ui.tooling)
        api(libs.compose.material3.adaptive.core)
        // Preview only
    }
    sourceSets.desktopMain.dependencies {
        implementation(libs.jna)
        implementation(libs.jna.platform)
        api(libs.directories)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "me.him188.ani.app.ui.foundation"
}
