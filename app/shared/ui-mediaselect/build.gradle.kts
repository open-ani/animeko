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
    // org.jetbrains.kotlinx.atomicfu
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.app.ui.mediaselect"
    }

    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiSettings)
        implementation(libs.atomicfu)
        implementation(projects.utils.ktorClient)
        implementation(libs.compose.components.resources)
        implementation(projects.utils.logging)
    }
    sourceSets.commonTest.dependencies {
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
}
