/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.subscription

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceData

/**
 * 数据源订阅的文件格式. 订阅链接返回这个格式, 导出数据源也生成这个格式.
 *
 * 这个格式会被第三方托管, 已经发布的客户端无法跟着升级, 所以只做兼容的改动: 只新增可选字段, 不改名, 不改类型.
 * 客户端忽略不认识的字段. 无法兼容的改动必须增加 [schemaVersion], 让旧客户端提示升级, 而不是解析出错.
 *
 * 读取时使用 [SubscriptionUpdateDataReader], 它同时接受旧格式.
 *
 * @see MediaSourceSubscription
 */
@Serializable
data class SubscriptionUpdateData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val name: String? = null,
    val description: String? = null,
    /**
     * 订阅的主页, 例如维护者的网站. 不是订阅链接本身.
     */
    val websiteUrl: String? = null,
    val iconUrl: String? = null,
    val mediaSources: List<ExportedMediaSourceData>,
) {
    val metadata: SubscriptionMetadata
        get() = SubscriptionMetadata(name, description, websiteUrl, iconUrl)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

/**
 * 订阅作者提供的订阅信息, 仅用于展示.
 */
@Serializable
data class SubscriptionMetadata(
    val name: String? = null,
    val description: String? = null,
    val websiteUrl: String? = null,
    val iconUrl: String? = null,
)

class UnsupportedSubscriptionSchemaException(
    val schemaVersion: Int,
) : Exception("Unsupported subscription schema version: $schemaVersion")

/**
 * 读取 [SubscriptionUpdateData], 同时接受以下旧格式:
 * - 订阅文件 `{"exportedMediaSourceDataList": {"mediaSources": [...]}}`
 * - 导出的数据源 `{"mediaSources": [...]}`
 */
object SubscriptionUpdateDataReader : DeserializationStrategy<SubscriptionUpdateData> {
    override val descriptor: SerialDescriptor get() = AnyVersion.serializer().descriptor

    @Throws(SerializationException::class, UnsupportedSubscriptionSchemaException::class)
    override fun deserialize(decoder: Decoder): SubscriptionUpdateData {
        val data = decoder.decodeSerializableValue(AnyVersion.serializer())
        if (data.schemaVersion > SubscriptionUpdateData.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedSubscriptionSchemaException(data.schemaVersion)
        }
        val mediaSources = data.mediaSources
            ?: data.exportedMediaSourceDataList?.mediaSources
            ?: throw SerializationException("Subscription has no mediaSources")
        return SubscriptionUpdateData(
            name = data.name,
            description = data.description,
            websiteUrl = data.websiteUrl,
            iconUrl = data.iconUrl,
            mediaSources = mediaSources,
        )
    }

    @Serializable
    private class AnyVersion(
        val schemaVersion: Int = 1,
        val name: String? = null,
        val description: String? = null,
        val websiteUrl: String? = null,
        val iconUrl: String? = null,
        val mediaSources: List<ExportedMediaSourceData>? = null,
        val exportedMediaSourceDataList: LegacyMediaSourceList? = null,
    )

    @Serializable
    private class LegacyMediaSourceList(
        val mediaSources: List<ExportedMediaSourceData>,
    )
}
