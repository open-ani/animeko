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

    // alias(libs.plugins.kotlinx.atomicfu)
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.app.video.player"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appPlatform)
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.videoPlayer.videoPlayerApi)
        api(libs.mediamp.api)
        api(libs.kotlinx.coroutines.core)
        api(projects.utils.coroutines)
        api(projects.danmaku.danmakuApi)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.compose.material3.adaptive.core)
        implementation(libs.androidx.media3.ui)
        implementation(libs.androidx.media3.exoplayer)
        implementation(libs.androidx.media3.exoplayer.dash)
        implementation(libs.androidx.media3.exoplayer.hls)
        implementation(libs.libass.media)
        api(libs.mediamp.exoplayer)
    }
    sourceSets.desktopMain.dependencies {
        api(compose.desktop.currentOs) {
            exclude("org.jetbrains.compose.material:material") // We use material3
        }

        api(libs.kotlinx.coroutines.swing)
        // desktopMain 是所有桌面平台共用的 source set, 需同时引入两种后端的 API,
        // 运行时按平台加载对应 native library (见 app/desktop). mpv: Windows x64 / macOS arm64,
        // VLC: Linux x64 / macOS x64.
        api(libs.mediamp.mpv)
        api(libs.mediamp.vlc)
    }
    sourceSets.appleMain.dependencies {
        api(libs.mediamp.avkit)
//        api(libs.mediamp.avkit.compose)
    }
}
