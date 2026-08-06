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
}

kotlin {
    android {
        namespace = "me.him188.ani.app.ui.onboarding"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiSettings)
        implementation(projects.utils.ktorClient)
        implementation(libs.compose.components.resources)
        implementation(projects.utils.logging)
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
}


compose.resources {
    packageOfResClass = "me.him188.ani.app.ui.onboarding"
}
