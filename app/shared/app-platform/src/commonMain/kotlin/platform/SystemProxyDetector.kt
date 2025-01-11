/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import io.ktor.http.Url

data class SystemProxyInfo(
    val url: Url,
)

interface SystemProxyDetector {
    /**
     * Might block thread and throw.
     */
    fun detect(): SystemProxyInfo?

    companion object {
        val instance by lazy { createSystemProxyDetector() }
    }
}

internal object NoOpSystemProxyDetector : SystemProxyDetector {
    override fun detect(): SystemProxyInfo? = null
}

internal expect fun createSystemProxyDetector(): SystemProxyDetector
