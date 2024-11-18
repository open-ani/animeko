/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.format

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * @see SelectorChannelFormat
 * @see SelectorSubjectFormat
 */
interface SelectorFormat {
    /**
     * 永久唯一的命名. 用于序列化和反序列化.
     */
    val id: SelectorFormatId
}

@Serializable
@Immutable
@JvmInline
value class SelectorFormatId(
    val value: String,
)

interface SelectorFormatConfig {
    /**
     * 注意, 这只做最简单的检测, 例如是否为空. 不会检查是否合法.
     */
    fun isValid(): Boolean
}

fun Regex.Companion.parseOrNull(regex: String): Regex? {
    return try {
        regex.toRegex()
    } catch (e: Exception) {
        null
    }
}

object SelectorHelpers {
    fun computeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        @Suppress("NAME_SHADOWING")
        var baseUrl = baseUrl
        if (baseUrl.endsWith('/')) {
            baseUrl = baseUrl.dropLast(1)
        }
        return when {
            relativeUrl.startsWith("http") -> relativeUrl
            relativeUrl.startsWith('/') -> baseUrl + relativeUrl
            else -> "$baseUrl/$relativeUrl"
        }
    }
}