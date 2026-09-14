/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
    id("ani.android-application")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.google.gms.google.services)
    idea
}

val archs = getPropertyOrNull("ani.android.abis")
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.takeIf { it.isNotEmpty() }
    ?.let { abis ->
        if (abis.size == 1 && abis.first() == "all") {
            listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        } else {
            abis
        }
    }
    ?: listOf("arm64-v8a")

android {
    namespace = "me.him188.ani.android"
    compileSdk = getIntProperty("android.compile.sdk")
    defaultConfig {
        applicationId = "me.him188.ani"
        minSdk = getIntProperty("android.min.sdk")
        targetSdk = getIntProperty("android.compile.sdk")
        versionCode = getIntProperty("android.version.code")
        versionName = project.version.toString()
        ndk {
            // Specifies the ABI configurations of your native
            // libraries Gradle should build and package with your app.
            abiFilters.clear()
            //noinspection ChromeOsAbiSupport
            abiFilters += archs
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            //noinspection ChromeOsAbiSupport
            include(*archs.toTypedArray())
            isUniversalApk = true // 额外构建一个
        }
    }
    signingConfigs {
        kotlin.runCatching { getProperty("signing_release_storeFileFromRoot") }.getOrNull()?.let {
            create("release") {
                storeFile = rootProject.file(it)
                storePassword = getProperty("signing_release_storePassword")
                keyAlias = getProperty("signing_release_keyAlias")
                keyPassword = getProperty("signing_release_keyPassword")
            }
        }
        kotlin.runCatching { getProperty("signing_release_storeFile") }.getOrNull()?.let {
            create("release") {
                storeFile = file(it)
                storePassword = getProperty("signing_release_storePassword")
                keyAlias = getProperty("signing_release_keyAlias")
                keyPassword = getProperty("signing_release_keyPassword")
            }
        }
    }
    packaging {
        jniLibs {
            // FFmpeg is launched as a process, so the native binary must be extracted to a filesystem path.
            useLegacyPackaging = true
        }
        resources {
            merges.add("META-INF/DEPENDENCIES") // log4j
            pickFirsts.add("META-INF/LICENSE.md")
            pickFirsts.add("META-INF/LICENSE-notice.md")

        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                *sharedAndroidProguardRules(),
            )
        }
        debug {
            applicationIdSuffix = getLocalProperty("ani.android.debug.applicationIdSuffix") ?: ".debug2"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("default") {
            dimension = "distribution"
        }
        create("tv") {
            // Android TV 形态 (atv-architecture.md D1): 与 default 平级、单维度,
            // 保证手机任务名 assembleDefaultRelease 与产物路径零变化.
            dimension = "distribution"
            applicationIdSuffix = ".leanback"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // 两个 flavor 共用共享库；TV 对手机页面的访问边界靠约定 + Konsist 维护。
    implementation(projects.app.shared)
    implementation(projects.app.shared.application)
    "tvImplementation"(projects.app.shared.tv)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)

//    implementation(libs.log4j.core)
//    implementation(libs.log4j.slf4j.impl)

    implementation(libs.ktor.client.core)
    implementation(libs.mediamp.ffmpeg)
}

idea {
    module {
        excludeDirs.add(file(".cxx"))
    }
}

googleServices {
    missingGoogleServicesStrategy = (getLocalProperty("ani.enable.firebase") ?: "false").toBooleanStrict()
        .let {
            if (it) MissingGoogleServicesStrategy.ERROR else MissingGoogleServicesStrategy.IGNORE
        }
}

// tv flavor 不接入 Firebase: google-services.json 只含手机包名, 禁用 tv variant 的
// GoogleServices 任务以避免 "No matching client found" 失败 (atv-architecture.md §10.1).
tasks.configureEach {
    if (name.startsWith("processTv") && name.endsWith("GoogleServices")) {
        enabled = false
    }
}

// 同时从 tv variant 的依赖闭包剔除 Firebase/GMS (经 :utils:analytics api 传递进来):
// TV 端 Analytics 永不初始化, 剔除后 manifest 不再混入 AD_ID/AdServices 权限与 measurement 服务.
configurations.configureEach {
    if (name.startsWith("tv") && name.endsWith("Classpath")) {
        exclude(group = "dev.gitlive", module = "firebase-analytics")
        exclude(group = "dev.gitlive", module = "firebase-analytics-android")
        exclude(group = "dev.gitlive", module = "firebase-app")
        exclude(group = "dev.gitlive", module = "firebase-app-android")
        exclude(group = "com.google.firebase")
        exclude(group = "com.google.android.gms")
    }
}
