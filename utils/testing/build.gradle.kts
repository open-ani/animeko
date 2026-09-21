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
            api(kotlin("test-annotations-common", libs.versions.kotlin.get()))
            api(libs.kotlinx.coroutines.test)
            api(projects.utils.coroutines)
        }
    }

    sourceSets.getByName("jvmMain") {
        dependencies {
            // JVM 上 kotlin.test.Test 是 junit5 jar 元数据里的 typealias (org.junit.jupiter.api.Test),
            // 平台编译 commonMain 源码时需要它. 但不能直接声明 kotlin("test-junit5"):
            // KGP 会为 commonMain 的 kotlin-test-annotations-common 注入一个 kotlin-test 根依赖,
            // IDE 导入时该根依赖被硬编码重写为 junit capability, 与 junit5 冲突 (kotlin-test-framework-impl).
            // 这里改为给根依赖显式加 junit5 capability: IDE 解析时图上出现两个根依赖,
            // KGP 的重写 (singleOrNull) 被跳过, 解析走与 androidHostTest 相同的干净路径.
            implementation(kotlin("test", libs.versions.kotlin.get())) {
                capabilities {
                    requireCapability("org.jetbrains.kotlin:kotlin-test-framework-junit5")
                }
            }
            implementation(libs.junit5.jupiter.api)
        }
    }
}
