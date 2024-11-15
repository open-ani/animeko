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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileHandle
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.utils.coroutines.IO_

@RequiresApi(Build.VERSION_CODES.O_MR1)
class RemoteTorrentFileHandle(
    connectivityAware: ConnectivityAware,
    getRemote: () -> IRemoteTorrentFileHandle
) : TorrentFileHandle,
    RemoteCall<IRemoteTorrentFileHandle> by RetryRemoteCall(getRemote),
    ConnectivityAware by connectivityAware {
    override val entry: TorrentFileEntry
        get() = RemoteTorrentFileEntry(this) { call { torrentFileEntry } }
    
    override fun resume(priority: FilePriority) {
        call { resume(priority.ordinal) }
    }

    override fun pause() {
        call { pause() }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO_) {
            call { close() }
        }
    }

    override suspend fun closeAndDelete() {
        withContext(Dispatchers.IO_) {
            call { closeAndDelete() }
        }
    }
}