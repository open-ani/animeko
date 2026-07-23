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

    /**
     * Channel 级别的 tier 覆盖, key 为 channel 名称.
     * 支持 channel 概念的数据源 (如 web selector 源) 可以覆盖此属性, 为不同 channel 指定不同 tier.
     * 未在此表中的 channel 使用 [tier].
     *
     * @since 4.9
     */
    val channelTiers: Map<String, MediaSourceTier> get() = emptyMap()
}

@RequiresOptIn("实现新的 MediaSourceArgument 时, 还需要在 MediaSourceCodecManager 注册此 Argument 类型的 codec")
annotation class DontForgetToRegisterCodec

typealias MediaSourceTier = me.him188.ani.datasources.api.source.MediaSourceTier
