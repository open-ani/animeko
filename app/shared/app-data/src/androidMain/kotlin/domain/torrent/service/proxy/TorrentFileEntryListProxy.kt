/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntry
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntryList
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.utils.coroutines.childScope
import kotlin.coroutines.CoroutineContext

class TorrentFileEntryListProxy(
    val delegate: List<TorrentFileEntry>,
    context: CoroutineContext
) : IRemoteTorrentFileEntryList.Stub() {
    private val scope = context.childScope()
    
    override fun get(index: Int): IRemoteTorrentFileEntry {
        return TorrentFileEntryProxy(delegate[index], scope.coroutineContext)
    }

    override fun getSize(): Int {
        return delegate.size
    }
}