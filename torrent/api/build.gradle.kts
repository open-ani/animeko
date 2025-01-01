/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    kotlin("multiplatform")
    `ani-mpp-lib-targets`
    id("org.jetbrains.kotlinx.atomicfu")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(mediampLibs.mediamp.api)
        implementation(libs.kotlinx.collections.immutable)
        api(projects.utils.io)

        api(projects.datasource.datasourceApi)
    }
}
