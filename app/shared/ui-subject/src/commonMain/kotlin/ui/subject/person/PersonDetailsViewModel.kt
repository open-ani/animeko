/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.person

import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.person.PersonCommentInfo
import me.him188.ani.app.data.repository.person.PersonDetailsRepository
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

class PersonDetailsViewModel(personId: Int) : AbstractViewModel(), KoinComponent {
    private val repository: PersonDetailsRepository by inject()

    val details = repository.personDetailsFlow(personId)
        .retryWithBackoff()
        .stateInBackground(null)
    val castsPager = repository.personCastsPager(personId).cachedIn(backgroundScope)
    val worksPager = repository.personWorksPager(personId).cachedIn(backgroundScope)

    /** 复用剧集/条目评论 UI ([me.him188.ani.app.ui.comment.CommentColumn]); 人物评论无评分、只读 (不支持贴表情). */
    val commentState = CommentState(
        list = repository.personCommentsPager(personId).toUICommentPager().cachedIn(backgroundScope),
        countState = details.map { it?.commentCount }.produceState(null),
        onSubmitCommentReaction = { _, _, _ -> },
        backgroundScope = backgroundScope,
    )
}

class CharacterDetailsViewModel(characterId: Int) : AbstractViewModel(), KoinComponent {
    private val repository: PersonDetailsRepository by inject()

    val details = repository.characterDetailsFlow(characterId)
        .retryWithBackoff()
        .stateInBackground(null)
    val subjectsPager = repository.characterSubjectsPager(characterId).cachedIn(backgroundScope)

    /** @see PersonDetailsViewModel.commentState */
    val commentState = CommentState(
        list = repository.characterCommentsPager(characterId).toUICommentPager().cachedIn(backgroundScope),
        countState = details.map { it?.commentCount }.produceState(null),
        onSubmitCommentReaction = { _, _, _ -> },
        backgroundScope = backgroundScope,
    )
}

private fun Flow<PagingData<PersonCommentInfo>>.toUICommentPager(): Flow<PagingData<UIComment>> =
    map { page -> page.map { it.toUIComment() } }

private fun PersonCommentInfo.toUIComment(): UIComment {
    return UIComment(
        id = id,
        stableId = id.toString(),
        author = if (authorId == null && authorNickname == null && authorAvatarUrl == null) null else UserInfo(
            id = authorId ?: "",
            username = null,
            nickname = authorNickname,
            avatarUrl = authorAvatarUrl,
        ),
        content = CommentMapperContext.parseBBCode(content),
        createdAt = createdAt.toEpochMilliseconds(),
        reactions = emptyList(),
        briefReplies = emptyList(),
        replyCount = replyCount,
        rating = null,
    )
}

private fun <T> Flow<T>.retryWithBackoff() = retryWhen { _, attempt ->
    delay(2.seconds * (attempt + 1).coerceAtMost(5).toInt())
    true
}
