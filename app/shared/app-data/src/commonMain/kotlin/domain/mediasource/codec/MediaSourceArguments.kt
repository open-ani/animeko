/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalSubclassOptIn::class)

package me.him188.ani.app.domain.mediasource.codec

import kotlinx.serialization.Serializable
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import kotlin.jvm.JvmInline

/**
 * Marker interface. 表示一个 [me.him188.ani.datasources.api.source.MediaSource] 的可导出配置.
 *
 * @see me.him188.ani.datasources.api.source.MediaSourceConfig.serializedArguments
 * @see MediaSourceCodecManager.deserializeArgument
 *
 * @see MediaSourceCodec
 * @see MediaSourceCodecManager
 */
@SubclassOptInRequired(DontForgetToRegisterCodec::class)
interface MediaSourceArguments {
    val name: String // used as id

    /**
     * @since 4.7
     */
    val tier: MediaSourceTier
}

/**
 * 数据源的等级.
 *
 * 等级主要用来排序, 影响自动选择数据源. 等级的值越低, 越高优先使用.
 *
 * 具体算法参考 [DefaultMediaSelector].
 *
 * @since 4.7
 */
@JvmInline
@Serializable // serialized as Int
value class MediaSourceTier(val value: UInt) : Comparable<MediaSourceTier> {
    override fun compareTo(other: MediaSourceTier): Int = this.value.compareTo(other.value)

    companion object {
        /**
         * 当数据源订阅没有指定 tier, 并且用户没有手动设置 tier 时的 fallback 值.
         *
         * 默认在范围内 [me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelect.InstantSelectTierThreshold].
         */
        val Fallback = MediaSourceTier(2u)

        val MaximumValue = MediaSourceTier(UInt.MAX_VALUE)
    }
}

@RequiresOptIn("实现新的 MediaSourceArgument 时, 还需要在 MediaSourceCodecManager 注册此 Argument 类型的 codec")
annotation class DontForgetToRegisterCodec