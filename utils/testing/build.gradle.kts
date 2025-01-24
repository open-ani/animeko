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
}

android {
    namespace = "me.him188.ani.utils.testing"
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            api(kotlin("test-annotations-common", libs.versions.kotlin.get()))
            api(libs.kotlinx.coroutines.test)
            api(projects.utils.coroutines)
        }
    }

    sourceSets.getByName("jvmMain") {
        dependencies {
            implementation(kotlin("test-junit5", libs.versions.kotlin.get()))
        }
    }
}
