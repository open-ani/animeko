/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import com.android.build.api.dsl.CompileSdkSpec
import org.gradle.api.Project

/**
 * 所有 Android 模块共用的 compileSdk, 由 `gradle.properties` 的 `android.compile.sdk` 和
 * `android.compile.sdk.minor` 决定. 用法: `compileSdk(aniCompileSdk)`.
 *
 * Android 平台从 API 37 起带 minor 版本, AAR 可以在元数据里用 `minCompileMinorSdk` 要求最低的 minor 版本.
 * 只给整数的 `compileSdk = 37` 表示 37.0, 所以 minor 版本要单独指定.
 */
val Project.aniCompileSdk: CompileSdkSpec.() -> Unit
    get() {
        val major = getIntProperty("android.compile.sdk")
        val minor = getIntProperty("android.compile.sdk.minor")
        return {
            version = release(major) {
                minorApiLevel = minor
            }
        }
    }
