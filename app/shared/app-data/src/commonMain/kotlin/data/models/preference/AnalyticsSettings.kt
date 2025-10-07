/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class AnalyticsSettings(
    val isBugReportEnabled: Boolean = true,
    val isAnalyticsEnabled: Boolean = true,
    /**
     * 这其实是本地的唯一标识, 而不是 User.id
     */
    val deviceId: String = Uuid.random().toString(),
) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun default() = AnalyticsSettings()

    }
}
