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
    `ani-mpp-lib-targets`
    kotlin("plugin.serialization")
}

android {
    namespace = "me.him188.ani.utils.jsonpath"
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.serialization.json)
        api(libs.jsonpathkt.kotlinx) // 不 api "Run Desktop" 时会 NoSuchMethodError
//        runtimeOnly(libs.jsonpathkt.kotlinx)
        implementation(projects.utils.intellijAnnotations)
    }
}
