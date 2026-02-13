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
        namespace = "me.him188.ani.app.ui.settings"
        packaging {
            resources {
                excludes.add("win32-x86-64/attach_hotspot_windows.dll")
                excludes.add("win32-x86/attach_hotspot_windows.dll")
                pickFirsts.add("META-INF/AL2.0")
                pickFirsts.add("META-INF/LGPL2.1")
                excludes.add("META-INF/DEPENDENCIES")
                excludes.add("META-INF/licenses/ASM")
            }
        }
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        implementation(compose.components.resources)
        implementation(projects.app.shared.reorderable)
        implementation(projects.app.shared.placeholder)
        implementation(libs.filekit.dialogs)
        implementation(libs.filekit.dialogs.compose)
        implementation(libs.atomicfu)
    }
    sourceSets.commonTest.dependencies {
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.slf4j.simple)
        implementation(libs.ktor.server.core)
        implementation(libs.ktor.server.test.host)
    }
}

compose.resources {
    packageOfResClass = "me.him188.ani.app.ui.settings"
}
