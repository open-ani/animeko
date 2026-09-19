/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.moveTo
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.resolveSibling
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeText
import kotlin.coroutines.cancellation.CancellationException

@Serializable
// Metadata preserves content identity; PieceBitmap owns download progress.
internal data class PikPakTorrentMeta(
    // Missing versions must fail validation rather than inherit CURRENT_VERSION.
    val version: Int = 0,
    val uri: String,
    val sourceKey: String,
    val name: String,
    // Imported episodes alone do not establish the complete torrent listing.
    val indexed: Boolean = true,
    val files: List<PikPakFileMeta>,
) {
    companion object {
        // 5 dropped the per-file transcode variant; a version 4 record names a length that may be
        // a transcode's, which nothing can read any more.
        const val CURRENT_VERSION = 5
        const val FILE_NAME = "meta.json"
    }
}

@Serializable
internal data class PikPakFileMeta(
    val index: Int,
    val pathInTorrent: String,
    val gcid: String = "",
    val length: Long,
)

internal class PikPakResumeData(
    private val saveDirectory: SystemPath,
) : SynchronizedObject() {
    private val metaPath get() = saveDirectory.resolve(PikPakTorrentMeta.FILE_NAME)

    fun read(): PikPakTorrentMeta? = synchronized(this) {
        if (!metaPath.exists()) return null
        return try {
            val meta = json.decodeFromString(PikPakTorrentMeta.serializer(), metaPath.readText())
            if (meta.version != PikPakTorrentMeta.CURRENT_VERSION) null else meta
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            null
        }
    }

    fun write(meta: PikPakTorrentMeta): Unit = synchronized(this) {
        val stamped = meta.copy(version = PikPakTorrentMeta.CURRENT_VERSION)
        val text = json.encodeToString(PikPakTorrentMeta.serializer(), stamped)
        val temp = metaPath.resolveSibling(metaPath.name + ".tmp")
        try {
            temp.writeText(text)
            temp.moveTo(metaPath)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e

            runCatching { if (temp.exists()) temp.delete() }
            metaPath.writeText(text)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    }
}
