/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network.protocol

import kotlinx.serialization.Serializable

@Serializable
data class BangumiLoginRequest(
    val bangumiToken: String,
    val clientVersion: String? = null,

    /**
     * @since 3.0.0-beta27
     */
    val clientOS: String? = null,
    /**
     * @since 3.0.0-beta27
     */
    val clientArch: String? = null,
) {
    companion object {
        val AllowedOSes = listOf(
            "windows", "macos", "android", "ios",
            "linux", "debian", "ubuntu", "redhat",
        )
        val AllowedArchs = listOf(
            "aarch64", "x86", "x86_64",
        )
    }
}

@Serializable
data class BangumiLoginResponse(
    val token: String,
)

