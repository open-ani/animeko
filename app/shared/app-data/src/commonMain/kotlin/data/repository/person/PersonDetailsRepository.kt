/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.person

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import me.him188.ani.app.data.models.person.CharacterDetailsInfo
import me.him188.ani.app.data.models.person.CharacterSubjectInfo
import me.him188.ani.app.data.models.person.InfoboxRowInfo
import me.him188.ani.app.data.models.person.PersonCastInfo
import me.him188.ani.app.data.models.person.PersonCommentInfo
import me.him188.ani.app.data.models.person.PersonDetailsInfo
import me.him188.ani.app.data.models.person.PersonSubjectSummary
import me.him188.ani.app.data.models.person.PersonWorkInfo
import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.CharactersAniApi
import me.him188.ani.client.apis.PersonsAniApi
import me.him188.ani.client.models.AniCharacter
import me.him188.ani.client.models.AniInfobox
import me.him188.ani.client.models.AniPerson
import me.him188.ani.client.models.AniPersonComment
import me.him188.ani.client.models.AniSubjectSummary
import me.him188.ani.utils.ktor.ApiInvoker

/**
 * 人物 (声优/制作人员) 与角色详情页数据仓库, 数据来自 ani 服务端 `/persons` 与 `/characters` 接口.
 */
class PersonDetailsRepository(
    private val personsApi: ApiInvoker<PersonsAniApi>,
    private val charactersApi: ApiInvoker<CharactersAniApi>,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {

    fun personDetailsFlow(personId: Int): Flow<PersonDetailsInfo> = flow {
        val details = try {
            withContext(defaultDispatcher) {
                personsApi { getPersonDetails(personId.toLong()).body() }
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        emit(
            PersonDetailsInfo(
                person = details.person.toPersonInfo(),
                career = details.career,
                infobox = details.person.infobox.toRows(),
                collects = details.collects,
                commentCount = details.commentCount,
                workCount = details.workCount,
                castCount = details.castCount,
            ),
        )
    }

    fun characterDetailsFlow(characterId: Int): Flow<CharacterDetailsInfo> = flow {
        val details = try {
            withContext(defaultDispatcher) {
                charactersApi { getCharacterDetails(characterId.toLong()).body() }
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        emit(
            CharacterDetailsInfo(
                character = details.character.toCharacterInfo(),
                role = details.role,
                summary = details.summary,
                infobox = details.infobox.toRows(),
                collects = details.collects,
                commentCount = details.commentCount,
                subjectCount = details.subjectCount,
            ),
        )
    }

    fun personWorksPager(personId: Int): Flow<PagingData<PersonWorkInfo>> = offsetPager { offset, limit ->
        personsApi { getPersonWorks(personId.toLong(), offset, limit).body() }.let { page ->
            Paged(
                total = page.total,
                items = page.items.map {
                    PersonWorkInfo(
                        subject = it.subject.toSummary(),
                        positions = it.positions.map(::PersonPosition),
                    )
                },
            )
        }
    }

    fun personCastsPager(personId: Int): Flow<PagingData<PersonCastInfo>> = offsetPager { offset, limit ->
        personsApi { getPersonCasts(personId.toLong(), offset, limit).body() }.let { page ->
            Paged(
                total = page.total,
                items = page.items.map {
                    PersonCastInfo(
                        subject = it.subject.toSummary(),
                        character = it.character.toCharacterInfo(),
                    )
                },
            )
        }
    }

    fun characterSubjectsPager(characterId: Int): Flow<PagingData<CharacterSubjectInfo>> =
        offsetPager { offset, limit ->
            charactersApi { getCharacterSubjects(characterId.toLong(), offset, limit).body() }.let { page ->
                Paged(
                    total = page.total,
                    items = page.items.map {
                        CharacterSubjectInfo(
                            subject = it.subject.toSummary(),
                            role = CharacterRole(it.type),
                            actors = it.actors.map { actor -> actor.toPersonInfo() },
                        )
                    },
                )
            }
        }

    fun personCommentsPager(personId: Int): Flow<PagingData<PersonCommentInfo>> = offsetPager { offset, limit ->
        personsApi { getPersonComments(personId.toLong(), offset, limit).body() }.let { page ->
            Paged(total = page.total, items = page.items.map { it.toInfo() })
        }
    }

    fun characterCommentsPager(characterId: Int): Flow<PagingData<PersonCommentInfo>> = offsetPager { offset, limit ->
        charactersApi { getCharacterComments(characterId.toLong(), offset, limit).body() }.let { page ->
            Paged(total = page.total, items = page.items.map { it.toInfo() })
        }
    }

    private class Paged<T>(val total: Int, val items: List<T>)

    private fun <T : Any> offsetPager(
        fetch: suspend (offset: Int, limit: Int) -> Paged<T>,
    ): Flow<PagingData<T>> = Pager(
        config = defaultPagingConfig,
        initialKey = 0,
        pagingSourceFactory = { OffsetPagingSource(fetch) },
    ).flow

    private inner class OffsetPagingSource<T : Any>(
        private val fetch: suspend (offset: Int, limit: Int) -> Paged<T>,
    ) : PagingSource<Int, T>() {
        override fun getRefreshKey(state: PagingState<Int, T>): Int? = state.anchorPosition

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = withContext(defaultDispatcher) {
            val offset = params.key ?: 0
            // 服务端 limit 上限为 100
            val limit = params.loadSize.coerceIn(1, 100)
            try {
                val page = fetch(offset, limit)
                val nextOffset = offset + page.items.size
                LoadResult.Page(
                    data = page.items,
                    prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (page.items.isNotEmpty() && nextOffset < page.total) nextOffset else null,
                )
            } catch (e: Exception) {
                LoadResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }
        }
    }
}

private fun AniPerson.toPersonInfo(): PersonInfo {
    return PersonInfo(
        id = id.toInt(),
        name = name,
        type = PersonType.fromId(type),
        careers = emptyList(),
        imageLarge = imageLarge,
        imageMedium = imageMedium,
        summary = summary,
        locked = false,
        nameCn = nameCn,
    )
}

private fun AniCharacter.toCharacterInfo(): CharacterInfo {
    return CharacterInfo(
        id = id.toInt(),
        name = name,
        nameCn = nameCn,
        actors = actors.map { it.toPersonInfo() },
        imageMedium = imageMedium,
        imageLarge = imageLarge,
    )
}

private fun AniSubjectSummary.toSummary(): PersonSubjectSummary {
    return PersonSubjectSummary(
        subjectId = id.toInt(),
        name = name,
        nameCn = nameCn,
        imageLarge = imageLarge,
    )
}

/** 与名字重复或不适合在“基本信息”表展示的 infobox 字段. */
private val HIDDEN_INFOBOX_KEYS = setOf("简体中文名")

private fun AniInfobox?.toRows(): List<InfoboxRowInfo> {
    if (this == null) return emptyList()
    return fields.mapNotNull { item ->
        if (item.key in HIDDEN_INFOBOX_KEYS) return@mapNotNull null
        val value = item.propertyValues.joinToString("、") { v ->
            if (v.k != null) "${v.k} ${v.v}" else v.v
        }
        if (value.isBlank()) return@mapNotNull null
        InfoboxRowInfo(key = item.key, value = value)
    }
}

private fun AniPersonComment.toInfo(): PersonCommentInfo {
    return PersonCommentInfo(
        id = id,
        authorId = author?.id,
        authorNickname = author?.nickname,
        authorAvatarUrl = author?.avatarUrl,
        content = content,
        createdAt = Instant.parse(createdAt),
        replyCount = replyCount,
    )
}
