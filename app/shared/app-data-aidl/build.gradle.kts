/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    alias(libs.plugins.android.library)
    id("kotlin-parcelize")
}

android {
    val idGroup = "me.him188.ani"
    namespace = "me.him188.ani.app.data.aidl"
    defaultConfig {
        compileSdk = getIntProperty("android.compile.sdk")
        minSdk = getIntProperty("android.min.sdk")
    }
    buildFeatures {
        aidl = true
    }
}

dependencies {
    api(projects.torrent.torrentApi)
    api(projects.datasource.datasourceApi)
    api(projects.utils.coroutines)
}