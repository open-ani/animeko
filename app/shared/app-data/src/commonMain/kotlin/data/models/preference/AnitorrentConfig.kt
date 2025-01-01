/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.platform.Platform

@Serializable
data class AnitorrentConfig(
    /**
     * 设置为 [FileSize.Unspecified] 表示无限
     */
    val downloadRateLimit: FileSize = FileSize.Unspecified,
    /**
     * 设置为 [FileSize.Unspecified] 表示无限, [FileSize.Zero] 表示不允许上传
     */
    val uploadRateLimit: FileSize = DEFAULT_UPLOAD_RATE_LIMIT,
    /**
     * 种子分享率限制.
     */
    val shareRatioLimit: Float = 2.0f, // libtorrent default. See https://libtorrent.org/reference-Settings.html#share-ratio-limit
    /**
     * 在计费网络限制上传速度为 10 KB/s
     * * Android 移动流量
     * * Windows 计费 Wi-Fi
     */
    val limitUploadOnMeteredNetwork: Boolean = true,
    @Transient private val _placeholder: Int = 0,
) {
    companion object {
        const val SHARE_RATIO_LIMIT_INFINITE = 10f
        val DEFAULT_UPLOAD_RATE_LIMIT = 2.megaBytes

        val Default = AnitorrentConfig()
    }
}

fun Platform.supportsLimitUploadOnMeteredNetwork(): Boolean {
    return this is Platform.Android || this is Platform.Windows
}
