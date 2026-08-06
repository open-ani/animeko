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
}

group = "org.burnoutcrew.composereorderable"
version = "0.9.7"

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.app.reorderable"
    }
    sourceSets {
        val commonMain by getting {
            dependencies {

                implementation(libs.compose.foundation)
                implementation(libs.compose.animation)
                implementation(libs.compose.ui.util)
            }
        }
    }
}