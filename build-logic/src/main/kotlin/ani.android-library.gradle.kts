/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// 纯 AGP library 模块 (非 KMP). 不应用 kotlin.android: AGP 9 起 Kotlin 支持内建, 显式应用会被 KGP 拒绝.

plugins {
    id("ani.base")
    id("com.android.library")
    // 在这里声明, KGP 的 marker 才会进消费方 classpath; AGP 内建的 Kotlin 不注册可解析的 plugin id.
    id("org.jetbrains.kotlin.plugin.parcelize")
}
