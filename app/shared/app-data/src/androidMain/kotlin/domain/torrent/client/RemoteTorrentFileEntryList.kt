/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.client

import android.os.Build
import androidx.annotation.RequiresApi
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntry
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntryList
import me.him188.ani.app.torrent.api.files.TorrentFileEntry

@RequiresApi(Build.VERSION_CODES.O_MR1)
class RemoteTorrentFileEntryList(
    private val remote: IRemoteTorrentFileEntryList
): AbstractList<TorrentFileEntry>() {
    private val cachedMap: MutableMap<Int, IRemoteTorrentFileEntry> = mutableMapOf()

    override val size: Int by lazy { remote.size }

    override fun get(index: Int): TorrentFileEntry {
        val cached = cachedMap[index]
        return if (cached != null) {
            RemoteTorrentFileEntry(cached)
        } else {
            val fetched = remote.get(index)
            cachedMap[index] = fetched

            RemoteTorrentFileEntry(fetched)
        }
    }
}