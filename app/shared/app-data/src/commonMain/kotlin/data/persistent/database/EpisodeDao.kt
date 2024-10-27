/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

//
//@Dao
//interface EpisodeDao {
//    @Query("""SELECT * FROM episode WHERE id = :id""")
//    fun findByEpisodeId(id: Int): Flow<EpisodeEntity?>
//
//    @Upsert
//    suspend fun upsert(item: EpisodeEntity)
//
//    @Upsert
//    suspend fun upsert(item: List<EpisodeEntity>)
//
//    @Query("""select * from episode""")
//    fun all(): PagingSource<Int, EpisodeEntity>
//
//    @Query("""SELECT * FROM episode WHERE subjectId = :subjectId ORDER BY sort""")
//    fun filterBySubjectId(subjectId: Int): Flow<List<EpisodeEntity>>
//
//    @Query("""SELECT * FROM episode WHERE subjectId = :subjectId ORDER BY sort""")
//    fun filterBySubjectIdPaging(subjectId: Int): PagingSource<Int, EpisodeEntity>
//}
//
//
///**
// * @see EpisodeInfo
// */
//@Entity(
//    tableName = "episode",
//    foreignKeys = [
//        ForeignKey(
//            entity = SubjectEntity::class,
//            parentColumns = ["id"],
//            childColumns = ["subjectId"],
//        ),
//    ],
//)
//data class EpisodeEntity(
//    val subjectId: Int,
//    @PrimaryKey val id: Int,
//    val type: EpisodeType?,
//    val name: String,
//    val nameCn: String,
//    val airDate: PackedDate,
//    val comment: Int,
//    val desc: String,
//    val sort: EpisodeSort,
//    val ep: EpisodeSort? = null,
//)