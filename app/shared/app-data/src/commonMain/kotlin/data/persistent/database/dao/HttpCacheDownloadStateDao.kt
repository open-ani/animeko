/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.httpdownloader.DownloadStatus

@Dao
interface HttpCacheDownloadStateDao {
    @Query("""SELECT * FROM http_cache_download_state""")
    fun getAll(): Flow<List<DownloadState>>

    @Upsert
    suspend fun upsert(state: DownloadState)

    @Query("""UPDATE http_cache_download_state SET status = :status WHERE downloadId = :id""")
    suspend fun updateStatus(id: DownloadId, status: DownloadStatus)

    @Query("""DELETE FROM http_cache_download_state""")
    suspend fun deleteAll()

    @Query("""SELECT * FROM http_cache_download_state WHERE downloadId = :id LIMIT 1""")
    suspend fun getById(id: DownloadId): DownloadState?
}