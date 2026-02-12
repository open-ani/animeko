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
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)

    `ani-mpp-lib-targets`
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlinx.atomicfu)
    idea
    `build-config`
}

val aniAuthServerUrlDebug =
    getPropertyOrNull("ani.auth.server.url.debug") ?: "https://auth.myani.org"
val aniAuthServerUrlRelease = getPropertyOrNull("ani.auth.server.url.release") ?: "https://auth.myani.org"
val dandanplayAppId = getPropertyOrNull("ani.dandanplay.app.id") ?: ""
val dandanplayAppSecret = getPropertyOrNull("ani.dandanplay.app.secret") ?: ""
val sentryDsn = getPropertyOrNull("ani.sentry.dsn") ?: ""
val analyticsKey = getPropertyOrNull("ani.analytics.key") ?: ""
val overrideAniApiServer = getPropertyOrNull("ani.api.server")?.takeIf { it.isNotBlank() }

val distroChannel = getPropertyOrNull("ani.distro.channel") ?: "default"

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.app.platform"
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

//if (bangumiClientDesktopAppId == null || bangumiClientDesktopSecret == null) {
//    logger.warn("bangumi.oauth.client.desktop.appId or bangumi.oauth.client.desktop.secret is not set. Bangumi authorization will not work. Get a token from https://bgm.tv/dev/app and set them in local.properties.")
//}

/// BUILD CONFIG

buildConfig {
    packageName.set("me.him188.ani.app.platform")
    className.set("AniBuildConfig")
    outputDir.set(layout.buildDirectory.dir("generated/buildconfig"))

    // Desktop platform configuration
    fun Build_config_gradle.BuildConfigPlatform.firebaseFields() {
        fun getProp(name: String): String {
            return if (enableFirebase) {
                getProperty(name).also {
                    check(it.isNotBlank()) { "Local property '$name' is not set. You must either set it or disable `ani.enable.firebase`." }
                }
            } else {
                ""
            }
        }
        stringField("firebaseGAAppId", getProp("firebase.ga.app.id"), isOverride = false)
//        stringField("firebaseApiKey", getProp("firebase.api.key"), isOverride = false)
        //            stringField("firebaseStorageBucket", getProperty("firebase.storage.bucket"), isOverride = false)
        //            stringField("firebaseProjectId", getProperty("firebase.project.id"), isOverride = false)
        //            stringField("firebaseGATrackingId", getProperty("firebase.ga.tracking.id"), isOverride = false)
        //            
        //            stringField("firebaseGAMeasurementId", getProperty("firebase.ga.measurement.id"), isOverride = false)
        stringField("firebaseGAApiSecret", getProp("firebase.ga.api.secret"), isOverride = false)

        booleanField("analyticsEnabled", enableFirebase)
    }

    platform("desktop") {
        stringField("versionName", project.version.toString())
        expressionField(
            "isDebug",
            "System.getenv(\"ANI_DEBUG\") == \"true\" || System.getProperty(\"ani.debug\") == \"true\"",
        )
        stringField("dandanplayAppId", dandanplayAppId)
        stringField("dandanplayAppSecret", dandanplayAppSecret)
        stringField("sentryDsn", sentryDsn)
        stringField("overrideAniApiServer", overrideAniApiServer ?: "")
        stringField("distroChannel", distroChannel)

        firebaseFields()
    }

    // Android platform configuration
    platform("android") {
        stringField("versionName", project.version.toString())
        expressionField("isDebug", "BuildConfig.DEBUG")
        stringField("dandanplayAppId", dandanplayAppId)
        stringField("dandanplayAppSecret", dandanplayAppSecret)
        stringField("sentryDsn", sentryDsn)
        stringField("overrideAniApiServer", overrideAniApiServer ?: "")
        stringField("distroChannel", distroChannel)

        booleanField("analyticsEnabled", enableFirebase)
    }

    // iOS platform configuration (only if enabled)
    if (enableIos) {
        platform("ios") {
            stringField("versionName", project.version.toString())
            booleanField("isDebug", false)
            stringField("dandanplayAppId", dandanplayAppId)
            stringField("dandanplayAppSecret", dandanplayAppSecret)
            stringField("sentryDsn", sentryDsn)

            val sentryEnabled = (getPropertyOrNull("ani.sentry.ios") ?: "true").toBooleanStrict()
            booleanField("sentryEnabled", sentryEnabled)
            stringField("overrideAniApiServer", overrideAniApiServer ?: "")
            stringField("distroChannel", distroChannel)

            firebaseFields()
        }
    }
}
