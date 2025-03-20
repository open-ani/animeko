/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.codec

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceCodec
import me.him188.ani.app.domain.mediasource.subscription.SubscriptionUpdateData
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceCodec
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * @see MediaSourceArguments
 */
class MediaSourceCodecManager(
    private val codecs: ImmutableList<MediaSourceCodec<*>> = persistentListOf(
        // Register your codec here
        RssMediaSourceCodec,
        SelectorMediaSourceCodec,
    )
) {

    internal val context = object : MediaSourceCodecContext {
        override val json: Json = Companion.json
    }

    private fun <T : MediaSourceArguments> getByArgumentType(argument: T): MediaSourceCodec<T> {
        val reg = codecs.find { it.forClass.isInstance(argument) }
            ?: throw IllegalStateException("Could not find registry for argument of class ${argument::class.qualifiedName}: $argument")
        @Suppress("UNCHECKED_CAST")
        return reg as MediaSourceCodec<T>
    }

    private fun findByFactoryId(factoryId: FactoryId): MediaSourceCodec<MediaSourceArguments>? {
        val reg = codecs.find { it.factoryId == factoryId }
            ?: return null
        @Suppress("UNCHECKED_CAST")
        return reg as MediaSourceCodec<MediaSourceArguments>
    }

    fun <T : MediaSourceArguments> encode(arguments: T): ExportedMediaSourceData {
        val codec = getByArgumentType(arguments)
        return with(codec) {
            context.encode(arguments).also {
                check(it.factoryId == codec.factoryId) { "Codec $codec returned different factory id $it than ${codec.factoryId}" }
            }
        }
    }

    fun serialize(
        factoryId: FactoryId,
        arguments: JsonElement
    ): ExportedMediaSourceData {
        val codec = findByFactoryId(factoryId)
            ?: throw FactoryNotFoundException(factoryId)
        return with(codec) {
            context.serialize(arguments).also {
                check(it.factoryId == codec.factoryId) { "Codec $codec returned different factory id $it than ${codec.factoryId}" }
            }
        }
    }

    /**
     * 序列化单个数据源 [ExportedMediaSourceData]. 此序列化结果不能用于导入. 仅用于内部测试使用.
     */
    fun <T : MediaSourceArguments> serializeSingleToString(arguments: T): String {
        return context.json.encodeToString(ExportedMediaSourceData.serializer(), encode(arguments))
    }

    /**
     * 反序列化该数据为其对应的 [MediaSourceArguments].
     *
     * `null` means it's not supported by this client
     */
    @Throws(MediaSourceDecodeException::class)
    fun decode(data: ExportedMediaSourceData): MediaSourceArguments {
        val codec = findByFactoryId(data.factoryId)
            ?: throw FactoryNotFoundException(data.factoryId)
        check(codec.factoryId == data.factoryId)
        return with(codec) {
            context.decode(data)
        }
    }

    /**
     * 将 [me.him188.ani.datasources.api.source.MediaSourceConfig.serializedArguments]
     * 转换为 [factoryId] 对应的 [MediaSourceArguments].
     */
    fun deserializeArgument(
        factoryId: FactoryId,
        jsonElement: JsonElement
    ): MediaSourceArguments {
        val codec = findByFactoryId(factoryId)
            ?: throw FactoryNotFoundException(factoryId)
        return with(codec) {
            context.deserialize(jsonElement)
        }
    }

    /**
     * 序列化订阅数据. 序列化之后的数据, 在 HTTP 服务器 host 之后, 可以作为订阅添加.
     */
    fun serializeSubscriptionToString(
        data: SubscriptionUpdateData,
    ): String = context.json.encodeToString(SubscriptionUpdateData.serializer(), data)

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

fun MediaSourceCodecManager.serializeSubscriptionToString(
    saves: List<MediaSourceSave>
): String = serializeSubscriptionToString(
    SubscriptionUpdateData(
        ExportedMediaSourceDataList(
            saves.mapNotNull {
                val args = it.config.serializedArguments ?: return@mapNotNull null
                serialize(it.factoryId, args)
            },
        ),
    ),
)

class FactoryNotFoundException(factoryId: FactoryId) : MediaSourceDecodeException("Factory not found: $factoryId")

// region serialization with ExportedMediaSourceDataList

fun MediaSourceCodecManager.serializeToString(
    list: ExportedMediaSourceDataList
): String {
    // should not throw
    return context.json.encodeToString(ExportedMediaSourceDataList.serializer(), list)
}

fun MediaSourceCodecManager.decodeFromStringOrNull(string: String): ExportedMediaSourceDataList? {
    return kotlin.runCatching {
        // Decode might throw when encountering invalid data
        context.json.decodeFromString(ExportedMediaSourceDataList.serializer(), string)
    }.getOrNull()
}

// endregion

// region serialization with List<MediaSourceArgument>

fun MediaSourceCodecManager.serializeToString(
    arguments: List<MediaSourceArguments>
): String = serializeToString(ExportedMediaSourceDataList(arguments.map { encode(it) }))

//fun MediaSourceCodecManager.deserializeListFromStringOrNull(string: String): List<MediaSourceArgument>? {
//    return decodeFromStringOrNull(string)?.let { list ->
//        list.list.mapNotNull {
//            runCatching {
//                decode(it)
//            }
//        }
//    }
//}

// endregion


@TestOnly
fun createTestMediaSourceCodecManager(): MediaSourceCodecManager = MediaSourceCodecManager()

fun MediaSourceSave.getArgumentOrNull(codecManager: MediaSourceCodecManager) =
    config.serializedArguments?.let { codecManager.deserializeArgument(factoryId, it) }
