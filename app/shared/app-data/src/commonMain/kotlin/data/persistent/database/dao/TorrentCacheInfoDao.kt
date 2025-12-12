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
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 存储 BitTorrent 引擎缓存的媒体的信息.
 * 
 * 种子文件的最终目录应该是 [MediaSaveDirProvider.saveDir] + [relativeDir] + [pathInTorrent]
 */
@Entity(
    tableName = "torrent_cache",
    primaryKeys = ["mediaId"],
    indices = [Index(value = ["mediaId"], unique = true)],
)
data class TorrentCacheInfoEntity(
    /**
     * 媒体 ID, 对应 [MediaCacheSave.origin] 中的 [Media.mediaId]
     */
    val mediaId: String,
    /**
     * 种子信息
     */
    val torrentData: ByteArray,
    /**
     * 种子的缓存目录, 相对于 [MediaSaveDirProvider.saveDir] 的相对路径.
     *
     * 注意, 一个 MediaCache 可能只对应该种子资源的其中一个文件.
     */
    val relativeDir: String,
    /**
     * torrent 是否已经完成, 意味着已经下载完并达到分享率
     */
    val completed: Boolean = false,
    /**
     * @see TorrentFileEntry.pathInTorrent
     */
    val pathInTorrent: String = "",
    /**
     * 该种子已下载的大小, 字节
     */
    val downloadSize: Long = 0,
    /**
     * 该种子已上传的大小, 字节
     */
    val uploadSize: Long = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TorrentCacheInfoEntity

        if (completed != other.completed) return false
        if (downloadSize != other.downloadSize) return false
        if (uploadSize != other.uploadSize) return false
        if (mediaId != other.mediaId) return false
        if (!torrentData.contentEquals(other.torrentData)) return false
        if (relativeDir != other.relativeDir) return false
        if (pathInTorrent != other.pathInTorrent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = completed.hashCode()
        result = 31 * result + downloadSize.hashCode()
        result = 31 * result + uploadSize.hashCode()
        result = 31 * result + mediaId.hashCode()
        result = 31 * result + torrentData.contentHashCode()
        result = 31 * result + relativeDir.hashCode()
        result = 31 * result + pathInTorrent.hashCode()
        return result
    }
}

@Dao
interface TorrentCacheInfoDao {
    @Query("""SELECT * FROM torrent_cache""")
    fun getAll(): Flow<List<TorrentCacheInfoEntity>>

    @Query("""SELECT * FROM torrent_cache WHERE mediaId = :mediaId LIMIT 1""")
    suspend fun get(mediaId: String): TorrentCacheInfoEntity?

    @Query("""SELECT * FROM torrent_cache WHERE mediaId in (:mediaIds)""")
    suspend fun batchGet(mediaIds: List<String>): List<TorrentCacheInfoEntity>

    @Upsert
    suspend fun upsert(item: TorrentCacheInfoEntity)

    @Query("""DELETE FROM torrent_cache WHERE mediaId = :mediaId""")
    suspend fun deleteByMediaId(mediaId: String)
}

@TestOnly
fun createMemoryTorrentCacheInfoDao(): TorrentCacheInfoDao {
    return object : TorrentCacheInfoDao {
        private val store = MemoryDataStore(listOf<TorrentCacheInfoEntity>())

        override fun getAll(): Flow<List<TorrentCacheInfoEntity>> {
            return store.data
        }

        override suspend fun get(mediaId: String): TorrentCacheInfoEntity? {
            return store.data.firstOrNull()?.find { it.mediaId == mediaId }
        }

        override suspend fun batchGet(mediaIds: List<String>): List<TorrentCacheInfoEntity> {
            return store.data.firstOrNull()?.filter { it.mediaId in mediaIds } ?: emptyList()
        }

        override suspend fun upsert(item: TorrentCacheInfoEntity) {
            store.updateData {
                val existing = it.indexOfFirst { e -> e.mediaId == item.mediaId }
                if (existing >= 0) {
                    it.toMutableList().apply { this[existing] = item }
                } else {
                    it + item
                }
            }
        }

        override suspend fun deleteByMediaId(mediaId: String) {
            store.updateData {
                it.filter { e -> e.mediaId != mediaId }
            }
        }
    }
}