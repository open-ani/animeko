/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    id("ani.kmp-compose")
}

kotlin {
    android {
        namespace = "me.him188.ani.utils.selectorworkflow"
    }
    sourceSets.commonMain.dependencies {
        api(libs.compose.lifecycle.viewmodel)
        implementation(libs.kotlinx.collections.immutable)
    }
    // 渲染冒烟测试要真的落到 Skia 上, 需要当前平台的 skiko native
    sourceSets.getByName("desktopTest").dependencies {
        implementation(compose.desktop.currentOs)
    }
}
