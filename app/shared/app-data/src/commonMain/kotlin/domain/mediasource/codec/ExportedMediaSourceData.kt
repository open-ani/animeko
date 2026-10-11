/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.codec

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.him188.ani.datasources.api.source.FactoryId

/**
 * @see MediaSourceCodec.encode
 */
@Serializable
data class ExportedMediaSourceData(
    val factoryId: FactoryId,
    /**
     * 每个 factory 自己定义的 version. 通常的约束是, 高版本导出的数据源无法在低版本导入
     */
    val version: Int,
    /**
     * [MediaSourceArguments] 的序列化结果
     */
    val arguments: JsonElement, // 不使用多态序列化, 为了兼容性
    /**
     * 数据源在订阅中的稳定 ID. 订阅更新时用它对应本地已有的数据源, 这样订阅作者给数据源改名不会丢失用户的设置.
     * 未提供时使用 [MediaSourceArguments.name].
     */
    val id: String? = null,
)

