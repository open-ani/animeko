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
}

// TODO AGP Migration: Test new build config behavior
android {
    val idGroup = "me.him188.ani"
    namespace = "$idGroup.buildconfig"
    defaultConfig {
        compileSdk = getIntProperty("android.compile.sdk")
        minSdk = getIntProperty("android.min.sdk")
    }

    buildTypes {
        release {
            buildConfigField("String", "APP_APPLICATION_ID", "\"${idGroup}\"")
        }

        debug {
            buildConfigField("String", "APP_APPLICATION_ID", "\"${idGroup}.debug2\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}