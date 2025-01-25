/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")

    `ani-mpp-lib-targets`
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiComment)
        implementation(compose.components.resources)
        implementation(projects.app.shared.placeholder)
        api(libs.kmpalette.core)
    }
    sourceSets.commonTest.dependencies {
    }
    sourceSets.named("jvmTest").dependencies {
        implementation(kotlin("reflect"))
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
}

android {
    namespace = "me.him188.ani.app.ui.subject"
}

compose.resources {
    packageOfResClass = "me.him188.ani.app.ui.subject"
}
