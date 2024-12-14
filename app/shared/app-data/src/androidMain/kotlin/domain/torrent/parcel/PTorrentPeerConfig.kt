/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.parcel

import android.os.Build
import android.os.Parcelable
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.protobuf.ProtoBuf
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Parcelize
class PTorrentPeerFilterSettings(
    val serializedDataMem: SharedMemory,
    val length: Int
) : Parcelable {
    override fun toString(): String {
        return "PTorrentPeerFilterSettings(dataSize=$length)"
    }

    fun toPeerFilterSettings(): PeerFilterSettings {
        val buffer = serializedDataMem.mapReadOnly()
        val data = ByteArray(length).apply { buffer.get(this) }
        serializedDataMem.close()
        return protobuf.decodeFromByteArray(PeerFilterSettings.serializer(), data)
    }

    companion object {
        val protobuf = ProtoBuf { }
    }
}

@RequiresApi(Build.VERSION_CODES.O_MR1)
fun PeerFilterSettings.toParceled(): PTorrentPeerFilterSettings {
    val encoded = PTorrentPeerFilterSettings.protobuf.encodeToByteArray(PeerFilterSettings.serializer(), this)
    val dataMem = SharedMemory.create("peer_filter_config${hashCode()}", encoded.size)
    dataMem.mapReadWrite().apply { put(encoded, 0, encoded.size) }
    return PTorrentPeerFilterSettings(dataMem, encoded.size)
}