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
        namespace = "me.him188.ani.utils.jsonpath"
    }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.serialization.json)
        api(libs.jsonpathkt.kotlinx) // 不 api "Run Desktop" 时会 NoSuchMethodError
//        runtimeOnly(libs.jsonpathkt.kotlinx)
        implementation(projects.utils.intellijAnnotations)
    }
}
