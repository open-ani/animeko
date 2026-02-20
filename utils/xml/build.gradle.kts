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
    `ani-mpp-lib-targets`
    alias(libs.plugins.kotlin.plugin.serialization)
}


kotlin {
    androidLibrary {
        namespace = "me.him188.ani.utils.xml"
    }
    sourceSets.commonMain.dependencies {
        api(projects.utils.io)
    }

    sourceSets.getByName("jvmMain").dependencies {
        api(libs.jsoup)
    }

    sourceSets.nativeMain.dependencies {
        api(libs.ksoup)
        api(libs.ksoup.kotlinx)
    }
}
