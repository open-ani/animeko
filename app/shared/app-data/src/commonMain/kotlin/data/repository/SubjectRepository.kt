/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.persistent.database.SubjectDao
import me.him188.ani.app.data.persistent.database.SubjectEntity
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.apis.DefaultApi

/**
 * 条目本身的信息
 */
@Immutable
data class SubjectInfo(
    val subjectId: Int,
    val subjectType: SubjectType,
    val name: String,
    val nameCn: String,
    val summary: String,
    val nsfw: Boolean,
    val imageLarge: String,
    val totalEpisodes: Int,
    val airDateString: String?,

    val tags: List<Tag>,
    val aliases: List<String>,
    val ratingInfo: RatingInfo,
    val collectionStats: SubjectCollectionStats,

    // 以下为来自 infoxbox 的信息
    val completeDate: PackedDate,
) {
    /**
     * 放送开始
     * @sample me.him188.ani.app.ui.subject.renderSubjectSeason
     */
    val airDate: PackedDate =
        if (airDateString == null) PackedDate.Invalid else PackedDate.parseFromDate(airDateString)

    /**
     * 主要显示名称
     */
    val displayName: String get() = nameCn.takeIf { it.isNotBlank() } ?: name

    /**
     * 主中文名, 主日文名, 以及所有别名
     */
    val allNames by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildList {
            // name2 千万不能改名叫 name, 否则 Kotlin 会错误地编译这份代码. `name` 他不会使用 local variable, 而是总是使用 [SubjectInfo.name]
            fun addIfNotBlank(name2: String) {
                if (name2.isNotBlank()) add(name2)
            }
            addIfNotBlank(nameCn)
            addIfNotBlank(name)
            aliases.forEach { addIfNotBlank(it) }
        }
    }

    companion object {
        @Stable
        val Empty = SubjectInfo(
            subjectId = 0,
            subjectType = SubjectType.ANIME,
            name = "",
            nameCn = "",
            summary = "",
            nsfw = false,
            imageLarge = "",
            totalEpisodes = 0,
            airDateString = null,
            tags = emptyList(),
            aliases = emptyList(),
            ratingInfo = RatingInfo.Empty,
            collectionStats = SubjectCollectionStats.Zero,
            completeDate = PackedDate.Invalid,
        )
    }
}

class SubjectRepository(
    private val client: BangumiClient,
    private val subjectApi: DefaultApi,
    private val subjectDao: SubjectDao,
) {
    class BatchSubjectDetails(
        val subjectInfo: me.him188.ani.app.data.repository.SubjectInfo,
    )

    fun subjectInfoFlow(subjectId: Int): Flow<me.him188.ani.app.data.repository.SubjectInfo> {
        return subjectDao.find(subjectId).map {
            if (it != null) {
                it.toSubjectInfo()
            } else {
                val newInfo = getSubjectDetails(subjectId).subjectInfo
                subjectDao.upsert(newInfo)
                newInfo
            }
        }
    }

    suspend fun getSubjectDetails(ids: Int): BatchSubjectDetails {
        return batchGetSubjectDetails(listOf(ids)).first()
    }

    suspend fun batchGetSubjectDetails(ids: List<Int>): List<BatchSubjectDetails> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val resp = client.executeGraphQL(
            """
            fragment Ep on Episode {
              id
              type
              name
              name_cn
              airdate
              comment
              description
              sort
            }

            fragment SubjectFragment on Subject {
              id
              name
              name_cn
              images{large, common}
              characters {
                order
                type
                character {
                  id
                  name
                }
              }
              infobox {
                values {
                  k
                  v
                }
                key
              }
              summary
              eps
              collection{collect , doing, dropped, on_hold, wish}
              airtime{date}
              rating{count, rank, score, total}
              nsfw
              tags{count, name}
              
              leadingEpisodes : episodes(limit: 1) { ...Ep }
              trailingEpisodes : episodes(limit: 1, offset:-1) { ...Ep }
            }

            query BatchGetSubjectQuery {
              ${
                ids.joinToString(separator = "\n") { id ->
                    """
                        "s$id:subject(id: $id){...SubjectFragment}"
                """
                }
            }
        """.trimIndent(),
        )
        val list = resp
            .getOrFail("data")
            .jsonObject.values.map {
                it.jsonObject.toBatchSubjectDetails()
            }
        return list
    }
}

private fun SubjectEntity.toSubjectInfo(): me.him188.ani.app.data.repository.SubjectInfo {
    return me.him188.ani.app.data.repository.SubjectInfo(
        subjectId = id,
        subjectType = SubjectType.ANIME,
        name = name,
        nameCn = nameCn,
        summary = summary,
        nsfw = nsfw,
        imageLarge = imageLarge,
        totalEpisodes = totalEpisodes,
        airDateString = airDateString,
        tags = tags,
        aliases = aliases,
        ratingInfo = ratingInfo,
        collectionStats = collectionStats,
        completeDate = completionDate,
    )
}

private fun JsonElement.vSequence(): Sequence<String> {
    return when (this) {
        is JsonArray -> this.asSequence().flatMap { it.vSequence() }
        is JsonPrimitive -> sequenceOf(content)
        is JsonObject -> this["v"]?.vSequence() ?: emptySequence()
        else -> emptySequence()
    }
}

private fun JsonObject.toBatchSubjectDetails(): SubjectRepository.BatchSubjectDetails {
    fun infobox(key: String): Sequence<String> = sequence {
        for (jsonElement in getOrFail("infobox").jsonArray) {
            if (jsonElement.jsonObject.getStringOrFail("key") == key) {
                yieldAll(jsonElement.jsonObject.getOrFail("values").vSequence())
            }
        }
    }

    val completionDate = (infobox("播放结束") + infobox("放送结束"))
        .firstOrNull()
        ?.let {
            PackedDate.parseFromDate(
                it.replace('年', '-')
                    .replace('月', '-')
                    .removeSuffix("日"),
            )
        }
        ?: PackedDate.Invalid

    return SubjectRepository.BatchSubjectDetails(
        me.him188.ani.app.data.repository.SubjectInfo(
            subjectId = getIntOrFail("id"),
            subjectType = SubjectType.ANIME,
            name = getStringOrFail("name"),
            nameCn = getStringOrFail("name_cn"),
            summary = getStringOrFail("summary"),
            nsfw = getBooleanOrFail("nsfw"),
            imageLarge = getOrFail("images").jsonObject.getStringOrFail("large"),
            totalEpisodes = getIntOrFail("eps"),
            airDateString = getOrFail("airtime").jsonObject.getStringOrFail("date"),
            tags = getOrFail("tags").jsonArray.map {
                val obj = it.jsonObject
                Tag(
                    obj.getStringOrFail("name"),
                    obj.getIntOrFail("count"),
                )
            },
            aliases = infobox("别名").filter { it.isNotEmpty() }.toList(),
            ratingInfo = getOrFail("rating").jsonObject.let { rating ->
                RatingInfo(
                    rank = rating.getIntOrFail("rank"),
                    total = rating.getIntOrFail("total"),
                    count = rating.getOrFail("count").jsonArray.let { array ->
                        RatingCounts(
                            s1 = array[0].jsonPrimitive.int,
                            s2 = array[1].jsonPrimitive.int,
                            s3 = array[2].jsonPrimitive.int,
                            s4 = array[3].jsonPrimitive.int,
                            s5 = array[4].jsonPrimitive.int,
                            s6 = array[5].jsonPrimitive.int,
                            s7 = array[6].jsonPrimitive.int,
                            s8 = array[7].jsonPrimitive.int,
                            s9 = array[8].jsonPrimitive.int,
                            s10 = array[9].jsonPrimitive.int,
                        )
                    },
                    score = rating.getStringOrFail("score"),
                )
            },
            collectionStats = getOrFail("collection").jsonObject.let { collection ->
                SubjectCollectionStats(
                    wish = collection.getIntOrFail("wish"),
                    doing = collection.getIntOrFail("doing"),
                    done = collection.getIntOrFail("collect"),
                    onHold = collection.getIntOrFail("on_hold"),
                    dropped = collection.getIntOrFail("dropped"),
                )
            },
            completeDate = completionDate,
        ),
    )
}


private fun JsonObject.getOrFail(key: String): JsonObject {
    return get(key)?.jsonObject ?: throw NoSuchElementException("key $key not found")
}

private fun JsonObject.getIntOrFail(key: String): Int {
    return get(key)?.jsonPrimitive?.int ?: throw NoSuchElementException("key $key not found")
}

private fun JsonObject.getStringOrFail(key: String): String {
    return get(key)?.jsonPrimitive?.content ?: throw NoSuchElementException("key $key not found")
}

private fun JsonObject.getBooleanOrFail(key: String): Boolean {
    return get(key)?.jsonPrimitive?.boolean ?: throw NoSuchElementException("key $key not found")
}
