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
    kotlin("plugin.compose")
    id("org.jetbrains.compose")

    `ani-mpp-lib-targets`
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    idea
    `build-config`
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.utils.platform)
        api(projects.app.shared.appLang)
        api(libs.kotlinx.coroutines.core)
        api(projects.danmaku.danmakuApi)
        api(libs.kotlinx.collections.immutable)
        api(projects.app.shared.imageViewer)

        api(libs.coil.compose.core)
        api(libs.coil.svg)
        api(libs.coil.network.ktor3)

        api(libs.compose.lifecycle.viewmodel.compose)
        api(libs.compose.lifecycle.runtime.compose)
        api(libs.compose.navigation.compose)
        api(libs.compose.navigation.runtime)
        api(libs.compose.material3.adaptive.core)
        api(libs.compose.material3.adaptive.layout)
        api(libs.compose.material3.adaptive.navigation0)

        api(libs.koin.core)
        api(projects.utils.analytics)
    }
    sourceSets.commonTest.dependencies {
        api(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
        api(libs.androidx.compose.ui.tooling.preview)
        api(libs.androidx.compose.ui.tooling)
    }
    sourceSets.desktopMain.dependencies {
        api(libs.jna)
        api(libs.jna.platform)
    }
    sourceSets.iosMain.dependencies {
        // Workaround for CMP bug since 1.8.0. Removing this will cause IDE sync failure and may break ios build.
        api("androidx.performance:performance-annotation:1.0.0-alpha01")
    }
}

android {
    namespace = "me.him188.ani.app.platform"
}


val aniAuthServerUrlDebug =
    getPropertyOrNull("ani.auth.server.url.debug") ?: "https://auth.myani.org"
val aniAuthServerUrlRelease = getPropertyOrNull("ani.auth.server.url.release") ?: "https://auth.myani.org"
val dandanplayAppId = getPropertyOrNull("ani.dandanplay.app.id") ?: ""
val dandanplayAppSecret = getPropertyOrNull("ani.dandanplay.app.secret") ?: ""
val sentryDsn = getPropertyOrNull("ani.sentry.dsn") ?: ""
val analyticsServer = getPropertyOrNull("ani.analytics.server") ?: ""
val analyticsKey = getPropertyOrNull("ani.analytics.key") ?: ""
val overrideAniApiServer = getPropertyOrNull("ani.api.server")?.takeIf { it.isNotBlank() }

//if (bangumiClientDesktopAppId == null || bangumiClientDesktopSecret == null) {
//    logger.warn("bangumi.oauth.client.desktop.appId or bangumi.oauth.client.desktop.secret is not set. Bangumi authorization will not work. Get a token from https://bgm.tv/dev/app and set them in local.properties.")
//}

android {
    buildTypes.getByName("release") {
        isMinifyEnabled = false
        isShrinkResources = false
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            *sharedAndroidProguardRules(),
        )
        buildConfigField("String", "APP_APPLICATION_ID", "\"me.him188.ani\"")
    }
    buildTypes.getByName("debug") {
        buildConfigField("String", "APP_APPLICATION_ID", "\"me.him188.ani.debug2\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

/// BUILD CONFIG

buildConfig {
    packageName.set("me.him188.ani.app.platform")
    className.set("AniBuildConfig")
    outputDir.set(layout.buildDirectory.dir("generated/buildconfig"))

    // Desktop platform configuration
    platform("desktop") {
        stringField("versionName", project.version.toString())
        expressionField(
            "isDebug",
            "System.getenv(\"ANI_DEBUG\") == \"true\" || System.getProperty(\"ani.debug\") == \"true\"",
        )
        stringField("dandanplayAppId", dandanplayAppId)
        stringField("dandanplayAppSecret", dandanplayAppSecret)
        stringField("sentryDsn", sentryDsn)
        stringField("analyticsKey", analyticsKey)
        stringField("analyticsServer", analyticsServer)
        stringField("overrideAniApiServer", overrideAniApiServer ?: "")
    }

    // Android platform configuration
    platform("android") {
        stringField("versionName", project.version.toString())
        expressionField("isDebug", "BuildConfig.DEBUG")
        stringField("dandanplayAppId", dandanplayAppId)
        stringField("dandanplayAppSecret", dandanplayAppSecret)
        stringField("sentryDsn", sentryDsn)
        stringField("analyticsKey", analyticsKey)
        stringField("analyticsServer", analyticsServer)
        stringField("overrideAniApiServer", overrideAniApiServer ?: "")
    }

    // iOS platform configuration (only if enabled)
    if (enableIos) {
        platform("ios") {
            stringField("versionName", project.version.toString())
            booleanField("isDebug", false)
            stringField("dandanplayAppId", dandanplayAppId)
            stringField("dandanplayAppSecret", dandanplayAppSecret)
            stringField("sentryDsn", sentryDsn)
            stringField("analyticsKey", analyticsKey)
            stringField("analyticsServer", analyticsServer)

            val sentryEnabled = (getPropertyOrNull("ani.sentry.ios") ?: "true").toBooleanStrict()
            val analyticsEnabled = (getPropertyOrNull("ani.analytics.ios") ?: "true").toBooleanStrict()

            booleanField("sentryEnabled", sentryEnabled)
            booleanField("analyticsEnabled", analyticsEnabled)
            stringField("overrideAniApiServer", overrideAniApiServer ?: "")
        }
    }
}
