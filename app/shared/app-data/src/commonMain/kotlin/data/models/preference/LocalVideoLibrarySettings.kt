/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 本地视频库设置.
 *
 * 当前仅用于「使用本地视频文件」选择器的默认目录, 并为后续扫描本地视频库预留.
 */
@Serializable
data class LocalVideoLibrarySettings(
    /**
     * 本地视频库目录.
     *
     * - Android: SAF tree URI, 例如 `content://com.android.externalstorage.documents/tree/primary%3ADownload`
     * - 桌面端: 绝对路径
     * - `null`: 未设置
     */
    val directory: String? = null,

    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        val Default = LocalVideoLibrarySettings()
    }
}
