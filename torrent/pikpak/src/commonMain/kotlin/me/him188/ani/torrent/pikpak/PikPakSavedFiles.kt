/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.copyTo
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.moveTo
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.cancellation.CancellationException

// Migration uses this boundary so paths, metadata and piece bitmaps stay owned by the engine.
object PikPakSavedFiles {
    private val logger = logger<PikPakSavedFiles>()

    fun saveDirectoryFor(rootDataDirectory: SystemPath, uri: String): SystemPath =
        rootDataDirectory.resolve(sourceKeyFor(uri))

    fun encodedTorrentInfoFor(uri: String): EncodedTorrentInfo =
        EncodedTorrentInfo.createRaw(encodeUri(uri))

    suspend fun importCompletedFile(
        rootDataDirectory: SystemPath,
        uri: String,
        source: SystemPath,
        pathInTorrent: String,
    ): SystemPath {
        require(pathInTorrent.isNotEmpty()) { "pathInTorrent must not be empty" }
        val sourceKey = sourceKeyFor(uri)
        return withContext(Dispatchers.IO_) {
            val saveDirectory = rootDataDirectory.resolve(sourceKey)
            saveDirectory.createDirectories()
            val target = saveDirectory.resolve(pathInTorrent)

            target.path.parent?.inSystem?.createDirectories()
            if (source.absolutePath != target.absolutePath) {
                try {
                    source.moveTo(target)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e

                    logger.info { "[pikpak] atomic move failed (${e.message}), copying instead" }
                    source.copyTo(target)
                    source.delete()
                }
            }

            // Imports are complete local representations and need no cloud identity.
            val length = target.length()
            markFullyDownloaded(target, length)
            val entry = PikPakFileMeta(
                index = 0,
                pathInTorrent = pathInTorrent,
                gcid = "",
                length = length,
            )
            val resumeData = PikPakResumeData(saveDirectory)

            val existing = resumeData.read()?.takeIf { it.uri == uri && it.sourceKey == sourceKey }
            val meta = if (existing == null) {
                PikPakTorrentMeta(
                    uri = uri,
                    sourceKey = sourceKey,
                    name = pathInTorrent,
                    indexed = false,
                    files = listOf(entry),
                )
            } else if (existing.files.any { it.pathInTorrent == pathInTorrent }) {
                existing.copy(
                    files = existing.files.map {
                        if (it.pathInTorrent == pathInTorrent) entry.copy(index = it.index) else it
                    },
                )
            } else {
                existing.copy(
                    files = existing.files + entry.copy(index = (existing.files.maxOfOrNull { it.index } ?: -1) + 1),
                )
            }
            resumeData.write(meta)
            logger.info { "[pikpak] imported $pathInTorrent ($length bytes) into $sourceKey" }
            target
        }
    }

    suspend fun markFullyDownloaded(target: SystemPath, length: Long) {
        val pieceCount = ((length + PikPakFileEntry.PIECE_SIZE - 1) / PikPakFileEntry.PIECE_SIZE).toInt()
        val bitmap = PieceBitmap(PieceBitmap.pathFor(target), pieceCount)
        for (i in 0 until pieceCount) bitmap.set(i)
        bitmap.flush()
    }
}
