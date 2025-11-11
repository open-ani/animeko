/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
@file:Suppress("unused")

package me.him188.ani.utils.httpdownloader

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import me.him188.ani.utils.serialization.DatabaseProtoBuf

class DownloadIdConverter {
    @TypeConverter
    fun fromString(value: String): DownloadId {
        return DownloadId(value)
    }

    @TypeConverter
    fun fromDownloadId(value: DownloadId): String {
        return value.value
    }
}

object SegmentInfoListConverter {
    @Serializable
    private class Node(val value: List<SegmentInfo>)

    @TypeConverter
    fun fromByteArray(value: ByteArray): List<SegmentInfo> {
        return DatabaseProtoBuf.decodeFromByteArray(Node.serializer(), value).value
    }

    @TypeConverter
    fun fromList(list: List<SegmentInfo>): ByteArray {
        return DatabaseProtoBuf.encodeToByteArray(Node.serializer(), Node(list))
    }
}

object DownloadStatusConverter {
    @TypeConverter
    fun fromValue(value: Int): DownloadStatus {
        return DownloadStatus.entries[value]
    }

    @TypeConverter
    fun fromStatus(value: DownloadStatus): Int {
        return value.ordinal
    }
}

object StringMapConverter {
    @Serializable
    private class Node(val value: Map<String, String>)

    @TypeConverter
    fun fromByteArray(value: ByteArray): Map<String, String> {
        return DatabaseProtoBuf.decodeFromByteArray(Node.serializer(), value).value
    }

    @TypeConverter
    fun fromList(list: Map<String, String>): ByteArray {
        return DatabaseProtoBuf.encodeToByteArray(Node.serializer(), Node(list))
    }
}