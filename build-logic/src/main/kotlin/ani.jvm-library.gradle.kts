/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// 纯 JVM Kotlin 模块. 由约定插件应用 kotlin.jvm: 同块内若有别的 KGP 子插件先解析,
// 模块自己带版本请求会报 "already on the classpath with an unknown version".

plugins {
    id("ani.base")
    id("org.jetbrains.kotlin.jvm")
}
