/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.compose.runtime.Stable
import me.him188.ani.utils.platform.currentPlatform


@Stable
interface AniBuildConfig {
    /**
     * `3.0.0-rc04`
     */
    val versionName: String
    val isDebug: Boolean
    val aniAuthServerUrl: String
    val dandanplayAppId: String
    val dandanplayAppSecret: String
    val sentryDsn: String
    val analyticsServer: String
    val analyticsKey: String

    companion object {
        @Stable
        fun current(): AniBuildConfig = currentAniBuildConfig
    }
}

/**
 * E.g. `3000` for `3.0.0`, `3012` for `3.1.2`
 */
val AniBuildConfig.fourDigitVersionCode: String
    get() = buildString {
        val split = versionName.substringBefore("-").split(".")
        if (split.size == 3) {
            split[0].toIntOrNull()?.let {
                append(it.toString())
            }
            split[1].toIntOrNull()?.let {
                append(it.toString().padStart(2, '0'))
            }
            split[2].toIntOrNull()?.let {
                append(it.toString())
            }
        } else {
            for (section in split) {
                section.toIntOrNull()?.let {
                    append(it.toString())
                }
            }
        }
    }

@Stable
@PublishedApi
internal expect val currentAniBuildConfigImpl: AniBuildConfig

@Stable
inline val currentAniBuildConfig: AniBuildConfig get() = currentAniBuildConfigImpl

/**
 * 满足各个数据源建议格式的 User-Agent, 所有 HTTP 请求都应该带此 UA.
 */
fun getAniUserAgent(
    version: String = currentAniBuildConfig.versionName,
    platform: String = currentPlatform().nameAndArch,
): String = "open-ani/ani/$version ($platform) (https://github.com/open-ani/ani)"
