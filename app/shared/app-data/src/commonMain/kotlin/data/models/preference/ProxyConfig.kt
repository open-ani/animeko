/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.utils.platform.annotations.SerializationOnly

/**
 * All proxy preferences
 */
@Serializable
data class ProxySettings(
    /**
     * Default settings to use if [MediaSourceProxySettings] is not set for a media source.
     */
    val default: MediaSourceProxySettings = MediaSourceProxySettings.Default,
    @Suppress("PropertyName") @Transient val _placeHolder: Int = 0,
) {
    companion object {
        val Default = ProxySettings()

        val Disabled = ProxySettings(default = MediaSourceProxySettings(mode = ProxyMode.DISABLED))
    }
}

@Serializable
data class MediaSourceProxySettings @SerializationOnly constructor(
    /**
     * v4.4 以前的设置. 新版本不再使用, 请使用 [mode] 替代. 构造时可固定传入 false
     */
    @Deprecated(message = "For compatibility", level = DeprecationLevel.ERROR)
    val enabled: Boolean = false, // 在 4.4 以前也是 false.
    /**
     * @since 4.4
     */
    @Suppress("DEPRECATION_ERROR")
    val mode: ProxyMode = if (enabled) ProxyMode.CUSTOM else ProxyMode.SYSTEM, // 旧用户可能已经设置了 enabled = true, 我们需要保持他们的设置.
    /**
     * 如果 [mode] 为 [ProxyMode.CUSTOM], 则使用此配置
     */
    @SerialName("config")
    val customConfig: ProxyConfig = ProxyConfig.Default,
) {
    @OptIn(SerializationOnly::class)
    constructor (
        mode: ProxyMode = ProxyMode.SYSTEM,
        /**
         * 如果 [mode] 为 [ProxyMode.CUSTOM], 则使用此配置
         */
        customConfig: ProxyConfig = ProxyConfig.Default,
    ) : this(enabled = mode != ProxyMode.DISABLED, mode = mode, customConfig = customConfig)

    companion object {
        val Default = MediaSourceProxySettings()
    }
}

/**
 * @since 4.4
 */
@Serializable
enum class ProxyMode {
    DISABLED,
    SYSTEM,
    CUSTOM
}

// data class is required for stable equals
@Serializable
data class ProxyConfig(
    val url: String = "http://127.0.0.1:7890",
    val authorization: ProxyAuthorization? = null,
) {
    companion object {
        val Default = ProxyConfig()
    }
}

@Serializable
data class ProxyAuthorization(
    val username: String,
    val password: String,
)
