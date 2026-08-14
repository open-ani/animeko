/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import me.him188.ani.datasources.api.EpisodeSort

/**
 * Web 源搜索结果的播放 session 级缓存. 一行为一个条目页面上的一个剧集.
 *
 * 生命周期由播放页控制: 进入/退出播放页与手动重新查询时全部清空,
 * 因此表中数据总是当前播放 session 内的新鲜搜索结果.
 *
 * 一行的完整身份是 (mediaSourceId, subjectName, subjectUrl, channel, episodeName):
 * 同一查询 ([subjectName]) 的多个搜索结果页面以 [subjectUrl] 区分, 互不覆盖.
 */
@Entity(
    tableName = "web_search_session_cache",
    indices = [
        Index(
            value = ["mediaSourceId", "subjectName", "subjectUrl", "channel", "episodeName"],
            unique = true,
        ),
        Index(value = ["mediaSourceId", "subjectName"]),
    ],
)
data class WebSearchSessionCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * 创建此缓存的 subject 的 id, 在重启
     */
    val requesterSubjectId: Int?,
    val mediaSourceId: String,
    /**
     * 搜索时使用的条目名 (查询 key), 来自 MediaFetchRequest.subjectNames.
     */
    val subjectName: String,
    /**
     * 条目页面上显示的条目名.
     */
    val subjectPageName: String,
    val subjectInternalId: String,
    /**
     * 条目页面完整 URL.
     */
    val subjectUrl: String,
    val subjectPartialUrl: String,
    /**
     * 线路名. 无线路时为空字符串, 不使用 `null` 以保证唯一索引生效
     * (SQLite 的 UNIQUE 索引把 NULL 视为互不相等).
     */
    val channel: String,
    /**
     * "第x集" 等剧集原名.
     */
    val episodeName: String,
    val episodeSortOrEp: EpisodeSort?,
    val playUrl: String,
    /**
     * 缓存写入时间 (epoch millis).
     */
    val cachedAt: Long,
    /**
     * 过期时间 (epoch millis), 即 [cachedAt] + 有效 TTL.
     * TTL 在写入时取数据源配置与用户设置中的较小者, 之后的读取与清理只依据本字段.
     */
    val expiresAt: Long,
)

@Dao
interface WebSearchSessionCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WebSearchSessionCacheEntity>)

    @Query(
        """
        DELETE FROM web_search_session_cache
        WHERE mediaSourceId = :mediaSourceId AND subjectName = :subjectName AND subjectUrl = :subjectUrl
        """,
    )
    suspend fun deletePage(mediaSourceId: String, subjectName: String, subjectUrl: String)

    /**
     * 以条目页面为单位整体替换: 先删除该页面的旧行再插入, 避免残留页面上已不存在的剧集.
     */
    @Transaction
    suspend fun replacePage(
        mediaSourceId: String,
        subjectName: String,
        subjectUrl: String,
        items: List<WebSearchSessionCacheEntity>,
    ) {
        deletePage(mediaSourceId, subjectName, subjectUrl)
        insertAll(items)
    }

    /**
     * 返回未过期的行. 按 `id` 升序, 保持写入 (页面上的剧集) 顺序.
     */
    @Query(
        """
        SELECT * FROM web_search_session_cache
        WHERE mediaSourceId = :mediaSourceId AND subjectName = :subjectName AND expiresAt > :now
        ORDER BY id
        """,
    )
    suspend fun filterBySubjectName(
        mediaSourceId: String,
        subjectName: String,
        now: Long,
    ): List<WebSearchSessionCacheEntity>

    @Query("DELETE FROM web_search_session_cache WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query(
        """
        DELETE FROM web_search_session_cache
        WHERE subjectName IN (:subjectNames) AND expiresAt <= :now
        """,
    )
    suspend fun deleteExpiredBySubjectNames(subjectNames: List<String>, now: Long)

    @Query(
        """
        DELETE FROM web_search_session_cache
        WHERE requesterSubjectId = :requesterSubjectId
    """,
    )
    suspend fun deleteByRequestedSubject(requesterSubjectId: Int)
}
