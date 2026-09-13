/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.FileStat
import io.github.nihildigit.pikpak.FolderNotFoundException
import io.github.nihildigit.pikpak.PikPakClient
import io.github.nihildigit.pikpak.PikPakException
import io.github.nihildigit.pikpak.batchDelete
import io.github.nihildigit.pikpak.getOrCreateDeepFolderId
import io.github.nihildigit.pikpak.getPathFolderId
import io.github.nihildigit.pikpak.listFiles
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

internal class PikPakDriveIndex(
    private val clientProvider: suspend () -> PikPakClient,
    private val tempFolderName: String = TEMP_FOLDER,
    private val legacyFolderName: String = LEGACY_FOLDER,
) {
    private val logger = logger<PikPakDriveIndex>()

    private val lock = Mutex()

    private var cachedTempFolderId: String? = null

    suspend fun tempFolderId(): String = lock.withLock {
        cachedTempFolderId ?: clientProvider()
            .getOrCreateDeepFolderId(parentId = "", path = tempFolderName)
            .also { cachedTempFolderId = it }
    }

    // Creating a file is where a folder deleted on another device first shows up, and the cached id
    // would otherwise fail every read until the app restarts. Resolving again recreates the folder.
    suspend fun <T> withTempFolder(block: suspend (parentId: String) -> T): T = try {
        block(tempFolderId())
    } catch (e: PikPakException) {
        if (!isFolderGone(e)) throw e
        logger.info { "[pikpak] temp folder no longer exists; creating it again" }
        invalidate()
        block(tempFolderId())
    }

    suspend fun listTemp(): List<FileStat> = try {
        listFolder(existingTempFolderId())
    } catch (e: PikPakException) {
        if (!isFolderGone(e)) throw e
        logger.info { "[pikpak] temp folder no longer exists; resolving it again" }
        invalidate()
        listFolder(existingTempFolderId())
    }

    suspend fun listLegacy(): List<FileStat> = listFolder(folderIdOrNull(legacyFolderName))

    suspend fun delete(fileIds: List<String>) {
        if (fileIds.isEmpty()) return
        clientProvider().batchDelete(fileIds)
    }

    private suspend fun invalidate() {
        lock.withLock { cachedTempFolderId = null }
        clientProvider().clearFolderIdCache()
    }

    private suspend fun existingTempFolderId(): String? = lock.withLock {
        cachedTempFolderId ?: folderIdOrNull(tempFolderName)?.also { cachedTempFolderId = it }
    }

    private suspend fun folderIdOrNull(name: String): String? = try {
        clientProvider().getPathFolderId(name)
    } catch (e: FolderNotFoundException) {
        null
    }

    private suspend fun listFolder(id: String?): List<FileStat> =
        id?.let { clientProvider().listFiles(parentId = it) } ?: emptyList()

    companion object {
        const val TEMP_FOLDER = "Animeko-Temp"

        // Legacy downloads require an explicit user decision; automatic cleanup only touches TEMP_FOLDER.
        const val LEGACY_FOLDER = "Animeko-Playing"
    }
}

internal fun isFolderGone(e: PikPakException): Boolean =
    e.httpStatus == 404 || e.errorMessage.contains("not_found")
