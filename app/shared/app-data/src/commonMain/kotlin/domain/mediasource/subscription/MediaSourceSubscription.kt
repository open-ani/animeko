/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
    val enabled: Boolean = true,
    /**
     * 最近一次成功更新时从订阅获取的 [SubscriptionMetadata]. 从未成功更新时为 `null`.
     */
    val metadata: SubscriptionMetadata? = null,
) {
    @Serializable
    class UpdateError(
        val message: String?,
        val failure: ApiFailure? = null, // serialization compatibility
        /**
         * 订阅使用了这个客户端不支持的格式, 见 [SubscriptionUpdateData.schemaVersion].
         */
        val requiresNewerApp: Boolean = false,
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
        /**
         * 订阅中这个客户端无法解析的数据源数量. 这些数据源保留更新前的配置.
         */
        val unsupportedMediaSourceCount: Int = 0,
    )
}

/**
 * 用于展示的名称: 订阅作者提供的名称, 没有时为去掉协议的订阅链接.
 */
val MediaSourceSubscription.displayName: String
    get() = metadata?.name?.takeIf { it.isNotBlank() }
        ?: url.removePrefix("https://").removePrefix("http://")
