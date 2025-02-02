/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.test

actual fun Any.readTestResourceAsString(path: String): String {
    return this::class.java
        .getResource(path)!!
        .readText()
}

actual fun Any.readTestResourceAsByteArray(path: String): ByteArray {
    return this::class.java
        .getResource(path)
        ?.readBytes()
        ?: kotlin.run {
            println("Warning: Resource $path not found")
            byteArrayOf()
        }
}
