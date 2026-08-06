/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// Android 应用模块. 同 ani.android-library, Kotlin 支持由 AGP 9 内建提供.

plugins {
    id("ani.base")
    id("com.android.application")
    // 在这里声明, KGP 的 marker 才会进消费方 classpath; AGP 内建的 Kotlin 不注册可解析的 plugin id.
    id("org.jetbrains.kotlin.plugin.parcelize")
}
