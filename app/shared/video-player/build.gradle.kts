/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appPlatform)
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.videoPlayer.videoPlayerApi)
        api(mediampLibs.mediamp.api)
        api(libs.kotlinx.coroutines.core)
        api(projects.utils.coroutines)
        api(projects.danmaku.danmakuApi)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.compose.ui.tooling.preview)
        implementation(libs.androidx.compose.ui.tooling)
        implementation(libs.compose.material3.adaptive.core)
        implementation(libs.androidx.media3.ui)
        implementation(libs.androidx.media3.exoplayer)
        implementation(libs.androidx.media3.exoplayer.dash)
        implementation(libs.androidx.media3.exoplayer.hls)
        api(mediampLibs.mediamp.exoplayer)
    }
    sourceSets.desktopMain.dependencies {
        api(compose.desktop.currentOs) {
            exclude(compose.material) // We use material3
        }

        api(libs.kotlinx.coroutines.swing)
        implementation(libs.vlcj)
        api(mediampLibs.mediamp.vlc)
        api("org.openani.mediamp:mediamp-mpv:${mediampLibs.versions.mediamp.get()}")
    }
    sourceSets.appleMain.dependencies {
        api(mediampLibs.mediamp.avkit)
//        api(mediampLibs.mediamp.avkit.compose)
    }
}

android {
    namespace = "me.him188.ani.app.video.player"
}
