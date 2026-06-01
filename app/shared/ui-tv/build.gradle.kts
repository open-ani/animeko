/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    // 全仓通用约定 (opt-ins / jvm target / 测试源集配置). 上游把根脚本的 allprojects/subprojects
    // 解散成了这个约定插件, 不显式应用就会**静默**丢掉那些配置; 顺带它还把 build-logic 带上
    // 本脚本的 classpath, 下面的 getIntProperty 才解析得到 (included build 不像 buildSrc 那样泄漏 classpath).
    id("ani.base")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)
}

apply(plugin = "de.mannodermaus.android-junit5")

/*
 * Android TV (遥控器) 专属界面.
 *
 * 与其它 ui-* 模块不同, 本模块**只有 android 一个 target**, 因此故意不套用
 * `ani.kmp-compose` (那个约定会同时配出 desktop 与 ios).
 * 桌面端和 iOS 既不需要遥控器界面, 也不该为它付编译代价 —— 这是把 TV 适配做成独立
 * target 的全部意义.
 *
 * 代价是 android target 的那部分配置 (compileSdk / 测试源集) 只能在这里重复一遍:
 * `ani.base` 的测试配置对所有含 android target 的模块都要求 androidHostTest 与
 * androidDeviceTest 存在.
 *
 * 依赖方向: ui-tv -> 各共享界面模块, 共享模块不得反向依赖本模块. 因为本模块只有 android
 * target, 多 target 模块的 commonMain 根本无法依赖它 —— 这正是需要的约束: TV 页面只能由
 * android 应用入口装配, 控件级差异只能靠 AniUiBehavior 这类数据开关表达.
 */
kotlin {
    android {
        namespace = "me.him188.ani.app.tv"
        compileSdk = getIntProperty("android.compile.sdk")
        minSdk = getIntProperty("android.min.sdk")
        androidResources.enable = true

        withHostTestBuilder {
            sourceSetTreeName = KotlinSourceSetTree.test.name
        }

        withDeviceTestBuilder {
            sourceSetTreeName = KotlinSourceSetTree.test.name
        }.configure {
            targetSdk {
                release(getIntProperty("android.min.sdk"))
            }
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            instrumentationRunnerArguments["runnerBuilder"] = "de.mannodermaus.junit5.AndroidJUnit5Builder"
            instrumentationRunnerArguments["package"] = "me.him188"
            execution = "HOST"
        }

        packaging {
            resources {
                pickFirsts.add("META-INF/LICENSE.md")
                pickFirsts.add("META-INF/LICENSE-notice.md")
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets.commonMain.dependencies {
        implementation(projects.utils.platform)
        // app:shared 是顶层界面聚合模块 (播放页 ViewModel / 主页导航), TV 页面直接复用它的状态层.
        // 方向是 ui-tv -> app:shared, 反向由"本模块只有 android target"从物理上阻止.
        api(projects.app.shared)
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiSubject)
        api(projects.app.shared.uiExploration)
        api(projects.app.shared.uiMediaselect)
        api(projects.app.shared.uiSettings)
        api(projects.app.shared.uiCache)
        api(projects.app.shared.uiComment)
        api(projects.app.shared.appData)
        api(projects.app.shared.videoPlayer)
        api(projects.danmaku.danmakuUi)
        implementation(libs.koin.core)
        // 进度条缩略图取帧 (见 TvFramePreviewSource): media3 的 ExperimentalFrameExtractor,
        // 用来替代 mediamp 那份打不开 HLS 的 MediaMetadataRetriever 实现
        implementation(libs.androidx.media3.transformer)
        implementation(libs.androidx.media3.exoplayer)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.testing)
    }
}
