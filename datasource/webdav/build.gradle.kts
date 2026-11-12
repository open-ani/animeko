/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    id("ani.kmp-library")
}

kotlin {
    android {
        namespace = "me.him188.ani.datasources.webdav"
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.ktor.client.mock)
    }
}

dependencies {
    commonMainApi(projects.datasource.datasourceApi)
    commonMainImplementation(projects.utils.xml)
}
