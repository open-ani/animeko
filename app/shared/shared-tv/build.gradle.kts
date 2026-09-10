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

group = "me.him188.ani.leanback"

kotlin {
    android {
        namespace = "me.him188.ani.leanback"
    }
    sourceSets.androidMain {
        kotlin.srcDir("../src/androidTv/kotlin")
        dependencies {
            api(projects.app.shared)
            api(projects.app.shared.uiFoundationTv)
            implementation(projects.app.shared.uiExplorationTv)
            implementation(projects.app.shared.uiSubjectTv)
            implementation(projects.app.shared.uiEpisodeTv)
            implementation(projects.app.shared.uiWatchtogetherTv)
            implementation(projects.app.shared.uiOnboardingTv)
            implementation(projects.app.shared.uiSettingsTv)
        }
    }
    sourceSets.androidHostTest {
        kotlin.srcDir("../src/androidTvTest/kotlin")
        dependencies {
            implementation(libs.konsist)
        }
    }
}
