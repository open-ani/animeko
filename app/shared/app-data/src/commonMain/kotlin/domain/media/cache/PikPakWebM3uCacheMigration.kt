/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import androidx.datastore.core.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import me.him188.ani.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheInfoDao
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheInfoEntity
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.toSafeDownloadId
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.httpdownloader.DownloadStatus
import me.him188.ani.torrent.pikpak.PikPakSavedFiles
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

// Completed HTTP downloads are imported as local files. Partial downloads restart because
// the old representation may differ from the variant selected by the new engine.
//
// Season packs are dropped rather than migrated. The legacy download id is derived from mediaId alone,
// so every episode of a pack shared one HTTP task and one output file; nothing records which episode
// that file holds, and handing it to all of them is exactly the per-episode confusion this layout
// replaces. Only media with a single record can be carried over.
class PikPakWebM3uCacheMigration(
    private val metadataStore: DataStore<List<MediaCacheSave>>,
    private val httpDao: HttpCacheDownloadStateDao,
    private val torrentDao: TorrentCacheInfoDao,
    private val baseSaveDirProvider: MediaSaveDirProvider,
    private val pikpakSaveDir: SystemPath,
) {
    private val logger = logger<PikPakWebM3uCacheMigration>()

    suspend fun migrate() {
        val all = metadataStore.data.first()
        val candidates = all.filter { it.isLegacyPikPakCache() }
        if (candidates.isEmpty()) return

        // The torrent row is keyed by mediaId and shared by both torrent engines, so migrating a media
        // anitorrent already owns would hand anitorrent PikPak's torrent data and save directory. One
        // torrent belongs to one engine, and anitorrent's record is the live one, so the legacy record
        // goes. Skipping it instead would leave a record no engine accepts: HttpMediaCacheEngine no
        // longer supports magnets, so it could never be restored, played, or deleted from the UI.
        val ownedByAnotherEngine = all.asSequence()
            .filter { it.engine != MediaCacheEngineKey.WebM3u }
            .mapTo(mutableSetOf()) { it.origin.mediaId }

        val byMedia = candidates.groupBy { it.origin.mediaId }
        // A season pack (several records on one mediaId) is discarded, not migrated. The legacy engine
        // derived the download id from the mediaId alone, so every episode of the pack shared one task
        // and one output file, and nothing recorded which episode those bytes belong to. Migrating would
        // point each episode at the same file and mark them all complete.
        val discardedIds = byMedia
            .filterValues { it.size > 1 || it.first().origin.mediaId in ownedByAnotherEngine }
            .keys
        val singles = byMedia.filterKeys { it !in discardedIds }.values.map { it.single() }

        // Unlike a migrated record, a discarded one keeps nothing, so its files are cleaned before the
        // commit rather than after. Cleaning after would strand them: once the records are gone, later
        // runs have no candidate from which to find the files. Re-running before the commit is harmless
        // because a second pass finds the HTTP row already gone and does nothing.
        for (mediaId in discardedIds) {
            try {
                deleteLegacyDownload(byMedia.getValue(mediaId).first().origin)
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to clean up discarded legacy PikPak cache $mediaId, dropping it anyway." }
            }
        }

        val migrated = mutableMapOf<String, MigratedFile>()
        for (save in singles) {
            try {
                migrated[save.origin.mediaId] = migrateOne(save.origin)
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to migrate legacy PikPak cache ${save.origin.mediaId}, leaving it as is." }
            }
        }
        if (migrated.isEmpty() && discardedIds.isEmpty()) return

        metadataStore.updateData { list ->
            list.mapNotNull { save ->
                if (!save.isLegacyPikPakCache()) return@mapNotNull save
                if (save.origin.mediaId in discardedIds) return@mapNotNull null
                val result = migrated[save.origin.mediaId] ?: return@mapNotNull save

                save.copy(
                    engine = MediaCacheEngineKey.PikPak,
                    metadata = if (result.complete) {
                        save.metadata.copy(completed = true, pathInTorrent = result.pathInTorrent)
                    } else {
                        save.metadata.copy(completed = false)
                    },
                )
            }
        }

        // Metadata is the migration commit point; retain HTTP rows until it is persisted for crash recovery.
        for (result in migrated.values) {
            result.legacyDownloadId?.let {
                runCatching { httpDao.deleteById(it) }
                    .onFailure { e -> logger.warn(e) { "Failed to drop legacy HTTP cache row $it" } }
            }
        }
        // A discarded record has a torrent row either because a previous build already migrated it, or
        // because another engine owns the media. Which case it is only becomes knowable after the commit,
        // so the row is checked here rather than alongside the file cleanup.
        for (mediaId in discardedIds) {
            runCatching { deleteTorrentRowIfUnreferenced(mediaId) }
                .onFailure { e -> logger.warn(e) { "Failed to drop the torrent row of discarded pack $mediaId" } }
        }
        logger.info {
            "Migrated ${migrated.size} legacy PikPak cache(s) to the torrent engine layout, " +
                    "discarded ${discardedIds.size} media that could not be carried over."
        }
    }

    private suspend fun deleteLegacyDownload(origin: Media) {
        val state = httpDao.getById(origin.toSafeDownloadId()) ?: return
        deleteLegacyFiles(state)
        httpDao.deleteById(state.downloadId)
    }

    private suspend fun deleteLegacyFiles(state: DownloadState) {
        val webM3uDir = Path(baseSaveDirProvider.saveDir, HttpMediaCacheEngine.MEDIA_CACHE_DIR)
        withContext(Dispatchers.IO_) {
            Path(webM3uDir, state.relativeOutputPath).inSystem.takeIf { it.exists() }?.deleteRecursively()
            Path(webM3uDir, state.relativeSegmentCacheDir).inSystem.takeIf { it.exists() }?.deleteRecursively()
        }
    }

    private suspend fun deleteTorrentRowIfUnreferenced(mediaId: String) {
        if (metadataStore.data.first().any { it.origin.mediaId == mediaId }) return
        torrentDao.deleteByMediaId(mediaId)
    }

    private class MigratedFile(
        val complete: Boolean,
        val pathInTorrent: String,
        val legacyDownloadId: DownloadId?,
    )

    private suspend fun migrateOne(origin: Media): MigratedFile {
        val downloadId = origin.toSafeDownloadId()
        val state = httpDao.getById(downloadId)
        val webM3uDir = Path(baseSaveDirProvider.saveDir, HttpMediaCacheEngine.MEDIA_CACHE_DIR)

        val uri = origin.download.uri

        val torrentData = PikPakSavedFiles.encodedTorrentInfoFor(uri)
        val targetDir = PikPakSavedFiles.saveDirectoryFor(pikpakSaveDir, uri)

        val sourceFile = state?.relativeOutputPath
            ?.let { Path(webM3uDir, it).inSystem }
            ?.takeIf { withContext(Dispatchers.IO_) { it.exists() } }

        val importedName = state?.relativeOutputPath?.substringAfterLast('/')
        // A previous attempt may have moved the file but stopped before committing metadata. Only a
        // finished download was ever moved, so requiring COMPLETED keeps an unfinished record from
        // claiming a same-named file the PikPak engine downloaded on its own. The HTTP row still says
        // COMPLETED at this point: it is dropped only after the commit.
        val finishedBefore = state?.status == DownloadStatus.COMPLETED
        val alreadyImported = finishedBefore && sourceFile == null && importedName != null &&
                withContext(Dispatchers.IO_) {
                    targetDir.resolve(importedName).run { exists() && length() > 0 }
                }

        val complete = alreadyImported || (finishedBefore && sourceFile != null)

        val pathInTorrent = if (alreadyImported) {
            checkNotNull(importedName)
        } else if (complete) {
            val fileName = checkNotNull(state).relativeOutputPath.substringAfterLast('/')

            PikPakSavedFiles.importCompletedFile(
                rootDataDirectory = pikpakSaveDir,
                uri = uri,
                source = checkNotNull(sourceFile),
                pathInTorrent = fileName,
            )
            fileName
        } else {
            state?.let { deleteLegacyFiles(it) }
            ""
        }

        val relativeDir = targetDir.absolutePath.substringAfter(baseSaveDirProvider.saveDir).also {
            check(it != targetDir.absolutePath) {
                "Failed to strip ${targetDir.absolutePath} of base ${baseSaveDirProvider.saveDir}"
            }
        }
        torrentDao.upsert(
            TorrentCacheInfoEntity(
                mediaId = origin.mediaId,
                torrentData = torrentData.data,
                relativeDir = relativeDir,
                completed = complete,
                pathInTorrent = pathInTorrent,
            ),
        )

        return MigratedFile(complete = complete, pathInTorrent = pathInTorrent, legacyDownloadId = state?.downloadId)
    }

    private fun MediaCacheSave.isLegacyPikPakCache(): Boolean =
        engine == MediaCacheEngineKey.WebM3u && origin.kind == MediaSourceKind.BitTorrent

}
