/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.ProtoBuf
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.datasources.api.EpisodeSort

private val protobuf
    get() = ProtoBuf {
        serializersModule = SerializersModule {
            contextual(IntArray::class, IntArraySerializer())
        }
    }

interface ProtoConverter<T> {
    @TypeConverter
    fun fromByteArray(value: ByteArray): T

    @TypeConverter
    fun fromList(list: T): ByteArray
}

object ProtoConverters {
    class StringList {
        @Serializable
        private class Node(val value: List<String>)

        @TypeConverter
        fun fromByteArray(value: ByteArray): List<String> {
            return protobuf.decodeFromByteArray(Node.serializer(), value).value
        }

        @TypeConverter
        fun fromList(list: List<String>): ByteArray {
            return protobuf.encodeToByteArray(Node.serializer(), Node(list))
        }
    }

    object IntList : ProtoConverter<List<Int>> {
        @Serializable
        private class Node(val value: List<Int>)


        @TypeConverter
        override fun fromByteArray(value: ByteArray): List<Int> {
            return protobuf.decodeFromByteArray(Node.serializer(), value).value
        }

        @TypeConverter
        override fun fromList(list: List<Int>): ByteArray {
            return protobuf.encodeToByteArray(Node.serializer(), Node(list))
        }
    }

    object TagList : ProtoConverter<List<Tag>> {
        @Serializable
        private class Node(val value: List<Tag>)

        @TypeConverter
        override fun fromByteArray(value: ByteArray): List<Tag> {
            return protobuf.decodeFromByteArray(Node.serializer(), value).value
        }

        @TypeConverter
        override fun fromList(list: List<Tag>): ByteArray {
            return protobuf.encodeToByteArray(Node.serializer(), Node(list))
        }
    }

    object IntArrayConverter : ProtoConverter<IntArray> {
        @Serializable
        private class Node(val value: IntArray)

        @TypeConverter
        override fun fromByteArray(value: ByteArray): IntArray {
            return protobuf.decodeFromByteArray(Node.serializer(), value).value
        }

        @TypeConverter
        override fun fromList(list: IntArray): ByteArray {
            return protobuf.encodeToByteArray(Node.serializer(), Node(list))
        }
    }
}

class EpisodeSortConverter {
    @TypeConverter
    fun fromString(value: String): EpisodeSort {
        return EpisodeSort(value)
    }

    @TypeConverter
    fun fromEpisodeSort(sort: EpisodeSort): String {
        return sort.toString()
    }
}