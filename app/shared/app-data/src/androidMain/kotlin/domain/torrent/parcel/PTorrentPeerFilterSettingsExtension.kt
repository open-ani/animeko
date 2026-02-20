/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.parcel

import android.os.Build
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import kotlinx.serialization.protobuf.ProtoBuf
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings

private val protobuf = ProtoBuf { }

fun PTorrentPeerFilterSettings.toPeerFilterSettings(): PeerFilterSettings {
    val data = serializedDataMem.use { mem ->
        ByteArray(length).apply { mem.mapReadOnly().get(this) }
    }
    return protobuf.decodeFromByteArray(PeerFilterSettings.serializer(), data)
}

@RequiresApi(Build.VERSION_CODES.O_MR1)
fun PeerFilterSettings.toParceled(): PTorrentPeerFilterSettings {
    val encoded = protobuf.encodeToByteArray(PeerFilterSettings.serializer(), this)
    val dataMem = SharedMemory.create("peer_filter_config${hashCode()}", encoded.size)
    dataMem.mapReadWrite().apply { put(encoded, 0, encoded.size) }
    return PTorrentPeerFilterSettings(dataMem, encoded.size)
}