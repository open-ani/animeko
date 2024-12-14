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
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Parcelize
class PEncodedTorrentInfo(
    val dataMem: SharedMemory,
    val length: Int
) : Parcelable {
    fun toEncodedTorrentInfo(): EncodedTorrentInfo {
        val data = dataMem.use { mem ->
            ByteArray(length).apply { mem.mapReadOnly().get(this) }
        }
        return EncodedTorrentInfo.createRaw(data)
    }
}

@RequiresApi(Build.VERSION_CODES.O_MR1)
fun EncodedTorrentInfo.toParceled(): PEncodedTorrentInfo {
    val dataMem = SharedMemory.create("encoded_torrent_info${data.hashCode()}", data.size)
    dataMem.mapReadWrite().apply { put(data, 0, data.size) }
    return PEncodedTorrentInfo(dataMem, data.size)
}

@Suppress("unused")
private fun dummy() {
} // Caused by: java.lang.ClassNotFoundException: Didn't find class "me.him188.ani.app.domain.torrent.parcel.PEncodedTorrentInfoKt" on path: DexPathList[[dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes10.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes11.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes12.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes13.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes14.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes16.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes17.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes3.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes4.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes5.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes7.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes8.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes9.dex", dex file "/data/data/me.him188.ani.debug/code_cache/.overlay/base.apk/classes2.dex", zip file "/data/app/~~IAcM-7-2VQnrJJRBSTosvQ==/me.him188.ani.debug-ahGG5P3csiUI1knk3FoUKw==/base.apk"],nativeLibraryDirectories=[/data/app/~~IAcM-7-2VQnrJJRBSTosvQ==/me.him188.ani.debug-ahGG5P3csiUI1knk3FoUKw==/lib/arm64, /data/app/~~IAcM-7-2VQnrJJRBSTosvQ==/me.him188.ani.debug-ahGG5P3csiUI1knk3FoUKw==/base.apk!/lib/arm64-v8a, /system/lib64, /system_ext/lib64]]
