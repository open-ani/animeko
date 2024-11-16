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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileHandle
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.utils.coroutines.IO_

@RequiresApi(Build.VERSION_CODES.O_MR1)
class RemoteTorrentFileHandle(
    private val fetchRemoteScope: CoroutineScope,
    private val remote: RemoteCall<IRemoteTorrentFileHandle>,
    private val connectivityAware: ConnectivityAware
) : TorrentFileHandle {
    override val entry: TorrentFileEntry
        get() = RemoteTorrentFileEntry(
            fetchRemoteScope,
            RetryRemoteCall(fetchRemoteScope) { remote.call { torrentFileEntry } },
            connectivityAware,
        )
    
    override fun resume(priority: FilePriority) {
        remote.call { resume(priority.ordinal) }
    }

    override fun pause() {
        remote.call { pause() }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO_) {
            remote.call { close() }
        }
    }

    override suspend fun closeAndDelete() {
        withContext(Dispatchers.IO_) {
            remote.call { closeAndDelete() }
        }
    }
}