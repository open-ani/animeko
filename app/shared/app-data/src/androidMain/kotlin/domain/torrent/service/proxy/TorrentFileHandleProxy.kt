/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntry
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileHandle
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.utils.coroutines.childScope
import kotlin.coroutines.CoroutineContext

class TorrentFileHandleProxy(
    private val delegate: TorrentFileHandle,
    context: CoroutineContext
) : IRemoteTorrentFileHandle.Stub(), CoroutineScope by context.childScope() {
    override fun getTorrentFileEntry(): IRemoteTorrentFileEntry? {
        return TorrentFileEntryProxy(delegate.entry, coroutineContext)
    }

    override fun resume(priorityEnum: Int) {
        delegate.resume(FilePriority.entries[priorityEnum])
    }

    override fun pause() {
        delegate.pause()
    }

    override fun close() {
        runBlocking { delegate.close() }
    }

    override fun closeAndDelete() {
        runBlocking { delegate.closeAndDelete() }
    }
}