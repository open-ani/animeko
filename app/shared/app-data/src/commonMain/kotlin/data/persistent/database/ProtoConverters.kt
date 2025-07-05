/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.protobuf.ProtoBuf
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.datasources.api.EpisodeSort

private val protobuf get() = ProtoBuf.Default

interface ProtoConverter<T> {
    @TypeConverter
    fun fromByteArray(value: ByteArray): T

    @TypeConverter
    fun fromList(list: T): ByteArray
}

object ProtoConverters {
    object StringList : ProtoConverter<List<String>> {
        @TypeConverter
        override fun fromByteArray(value: ByteArray): List<String> {
            return protobuf.decodeFromByteArray(ListSerializer(String.serializer()), value)
        }

        @TypeConverter
        override fun fromList(list: List<String>): ByteArray {
            return protobuf.encodeToByteArray(ListSerializer(String.serializer()), list)
        }
    }

    object IntList : ProtoConverter<List<Int>> {
        @TypeConverter
        override fun fromByteArray(value: ByteArray): List<Int> {
            return protobuf.decodeFromByteArray(ListSerializer(Int.serializer()), value)
        }

        @TypeConverter
        override fun fromList(list: List<Int>): ByteArray {
            return protobuf.encodeToByteArray(ListSerializer(Int.serializer()), list)
        }
    }

    object TagList : ProtoConverter<List<Tag>> {
        @TypeConverter
        override fun fromByteArray(value: ByteArray): List<Tag> {
            return protobuf.decodeFromByteArray(ListSerializer(Tag.serializer()), value)
        }

        @TypeConverter
        override fun fromList(list: List<Tag>): ByteArray {
            return protobuf.encodeToByteArray(ListSerializer(Tag.serializer()), list)
        }
    }

    object IntArrayConverter : ProtoConverter<IntArray> {
        @TypeConverter
        override fun fromByteArray(value: ByteArray): IntArray {
            return protobuf.decodeFromByteArray(IntArraySerializer(), value)
        }

        @TypeConverter
        override fun fromList(list: IntArray): ByteArray {
            return protobuf.encodeToByteArray(IntArraySerializer(), list)
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