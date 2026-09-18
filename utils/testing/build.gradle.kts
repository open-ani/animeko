/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    id("ani.kmp-library")
}

kotlin {
    android {
        namespace = "me.him188.ani.utils.testing"
    }
    sourceSets.commonMain {
        dependencies {
            // commonMain 用到 kotlin.test.Test. gradle.properties 关闭了 kotlin.test.infer.jvm.variant,
            // KGP 不会自动补上 kotlin-test, 因此显式声明; JVM 上的 JUnit 5 集成由 jvmMain 的 kotlin-test-junit5 提供.
            api(kotlin("test", libs.versions.kotlin.get()))
            api(libs.kotlinx.coroutines.test)
            api(projects.utils.coroutines)
        }
    }

    sourceSets.getByName("jvmMain") {
        dependencies {
            implementation(kotlin("test-junit5", libs.versions.kotlin.get()))
        }
    }
}
