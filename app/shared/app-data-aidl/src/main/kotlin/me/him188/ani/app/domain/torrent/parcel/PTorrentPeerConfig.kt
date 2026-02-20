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
import android.os.Parcelable
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import kotlinx.parcelize.Parcelize

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Parcelize
class PTorrentPeerFilterSettings(
    val serializedDataMem: SharedMemory,
    val length: Int
) : Parcelable {
    override fun toString(): String {
        return "PTorrentPeerFilterSettings(dataSize=$length)"
    }
}
