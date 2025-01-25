/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import io.ktor.client.HttpClientConfig
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameter
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.utils.ktor.ClientProxyConfig
import me.him188.ani.utils.ktor.WrapperHttpClient
import me.him188.ani.utils.ktor.proxy
import me.him188.ani.utils.ktor.userAgent
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class FactoryId(
    // 强制类型检查, 防止跟 mediaSourceId 弄混 (历史遗留问题, 有太多的 mediaSourceId 了)
    val value: String,
) {
    override fun toString(): String = value
}

/**
 * 表示一个数据源类型, 可以构造多个该类型的实例. 例如 RSS 类型可以构造出适配不同站点的实例.
 *
 * 负责定义 [MediaSource] 的可配置参数列表, 并使用这些参数创建一个示例.
 *
 * @see MediaSource
 */
interface MediaSourceFactory { // SPI service load
    /**
     * 不一定和 [MediaSource.mediaSourceId] 相同, 因为一个数据源类型可能有多个实例.
     */
    val factoryId: FactoryId

    /**
     * 是否允许用户使用此模板创建多个实例. 不同的实例可以用不同的配置.
     *
     * 如果设置为 `true`, 构造的 [MediaSource] 就必须采用 [create] 时传入的 `mediaSourceId` 参数.
     */
    val allowMultipleInstances: Boolean get() = false

    /**
     * 数据源的可配置参数列表. 例如 API key, 用户名密码等.
     */
    val parameters: MediaSourceParameters get() = MediaSourceParameters.Empty

    /**
     * 初始的配置信息, 仅在创建数据源时有用. 之后将使用 [MediaSource.info]
     */
    val info: MediaSourceInfo

    /**
     * 必须返回新实例.
     *
     * @param mediaSourceId 系统为数据源分配的唯一 ID. 注意, 历史遗留的, 不支持多个实例的旧数据源实现会忽略这个参数.
     */
    fun create(
        mediaSourceId: String,
        config: MediaSourceConfig,
        client: WrapperHttpClient,
    ): MediaSource
}

/**
 * 本地配置. 导出时不会导出 [MediaSourceConfig], 而是导出 [arguments].
 */
@Serializable
data class MediaSourceConfig(
    val proxy: ClientProxyConfig? = null,
    val userAgent: String? = null,
    /**
     * 用户为数据源配置的参数列表. 一定包含 [MediaSourceFactory.parameters] 中的所有参数, 但如果数据源更新了更多参数, 则可能不会包含.
     *
     * @suppress 此属性已弃用. 只有 Jellyfin, Emby 和 Ikaros 才会使用. 新版本查看 [serializedArguments].
     */
    val arguments: Map<String, String?> = mapOf(), // TODO: completely remove this
    /**
     * 新版本的参数列表, 各个数据源可以自己决定该数据的格式. 要使用此类型参数, 数据源必须拥有单独的配置 UI 界面.
     *
     * 用户为数据源配置的参数列表.
     *
     * 如需转换为 `MediaSourceArguments`, 使用 `MediaSourceCodecManager.deserializeArgument`.
     */
    val serializedArguments: JsonElement? = null,
    /**
     * 所属订阅的 ID. `null` 表示是本地自己添加的
     */
    val subscriptionId: String? = null,
) {
    companion object {
        val Default = MediaSourceConfig()
    }
}

operator fun <T> MediaSourceConfig.get(parameter: MediaSourceParameter<T>): T =
    arguments[parameter.name]?.let { parameter.parseFromString(it) } ?: parameter.default()

fun HttpClientConfig<*>.applyMediaSourceConfig(
    config: MediaSourceConfig,
) {
    config.proxy?.let { proxy(it) }
    config.userAgent?.let { userAgent(it) }
}


private val parametersJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun <T> MediaSourceConfig.deserializeArgumentsOrNull(
    deserializationStrategy: DeserializationStrategy<T>,
    json: Json = parametersJson,
): T? = serializedArguments?.let { json.decodeFromJsonElement(deserializationStrategy, it) }

fun <T> MediaSourceConfig.Companion.serializeArguments(
    serializationStrategy: SerializationStrategy<T>,
    value: T,
    json: Json = parametersJson,
): JsonElement = json.encodeToJsonElement(serializationStrategy, value)

fun <T> MediaSourceConfig.Companion.deserializeArgumentsFromString(
    deserializationStrategy: DeserializationStrategy<T>,
    value: String,
    json: Json = parametersJson,
): T = json.decodeFromString(deserializationStrategy, value)

fun <T> MediaSourceConfig.Companion.serializeArgumentsToString(
    serializationStrategy: SerializationStrategy<T>,
    value: T,
    json: Json = parametersJson,
): String = json.encodeToString(serializationStrategy, value)

