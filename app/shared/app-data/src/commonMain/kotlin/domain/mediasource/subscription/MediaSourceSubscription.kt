/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.subscription

import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.ApiFailure
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * 表示一项数据源订阅 (设置). 更新时拉取到的数据是 [SubscriptionUpdateData]
 *
 * @see me.him188.ani.datasources.api.source.MediaSourceConfig
 */
@Serializable
data class MediaSourceSubscription(
    val subscriptionId: String,
    val url: String,
    val updatePeriod: Duration = 1.hours,
    val lastUpdated: LastUpdated? = null,
) {
    @Serializable
    class UpdateError(
        val message: String?,
        val failure: ApiFailure? = null, // serialization compatibility
    )

    @Serializable
    data class LastUpdated(
        /**
         * Epoch timestamp
         */
        val timeMillis: Long,
        /**
         *  null means error
         */
        val mediaSourceCount: Int?,
        val error: UpdateError? = null,
    )
}
