/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.person.CharacterDetailsInfo
import me.him188.ani.app.data.models.person.CharacterSubjectInfo
import me.him188.ani.app.data.models.person.InfoboxRowInfo
import me.him188.ani.app.data.models.person.PersonCastInfo
import me.him188.ani.app.data.models.person.PersonDetailsInfo
import me.him188.ani.app.data.models.person.PersonSubjectSummary
import me.him188.ani.app.data.models.person.PersonWorkInfo
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.CommentReportReason
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentVote

enum class TvPeopleKind { Character, VoiceActor, Staff }

data class TvPeopleTarget(val id: Int, val kind: TvPeopleKind)

data class TvPeopleProfile(
    val name: String,
    val originalName: String,
    val image: String,
    val summary: String,
    val infobox: List<InfoboxRowInfo>,
    val collects: Int,
    val careers: List<String> = emptyList(),
    val role: Int = 1,
    val portrait: Boolean = true,
    val actors: List<PersonInfo> = emptyList(),
    val workCount: Int = 0,
    val castCount: Int = 0,
) {
    companion object {
        fun from(details: CharacterDetailsInfo) = TvPeopleProfile(
            details.character.displayName, details.character.name, details.character.imageLarge,
            details.summary, details.infobox, details.collects, role = details.role,
            actors = details.character.actors.distinctBy { it.id }, workCount = details.subjectCount,
        )

        fun from(details: PersonDetailsInfo) = TvPeopleProfile(
            details.person.displayName, details.person.name, details.person.imageLarge,
            details.person.summary, details.infobox, details.collects, careers = details.career,
            portrait = details.person.type == PersonType.Individual,
            workCount = details.workCount, castCount = details.castCount,
        )
    }
}

/** Preserve full relationship records, including actor lists, subject identity and positions. */
data class TvPeopleDetailsUiState(
    val target: TvPeopleTarget,
    val profile: TvPeopleProfile? = null,
    val loading: Boolean = true,
    val error: LoadError? = null,
    val subjects: Flow<PagingData<CharacterSubjectInfo>>? = null,
    val casts: Flow<PagingData<PersonCastInfo>>? = null,
    val works: Flow<PagingData<PersonWorkInfo>>? = null,
    val comments: Flow<PagingData<UIComment>>,
    val commentPresentation: (UIComment) -> UIComment = { it },
    val bangumiUnavailable: Boolean = false,
    val reportBusy: Boolean = false,
    val reportCompleted: Int? = null,
)

sealed interface TvPeopleIntent {
    data object Retry : TvPeopleIntent
    data class OpenPerson(val target: TvPeopleTarget) : TvPeopleIntent
    data class OpenSubject(val subject: PersonSubjectSummary) : TvPeopleIntent
    data class Vote(val comment: UIComment, val vote: UICommentVote) : TvPeopleIntent
    data class Report(val comment: UIComment, val reason: CommentReportReason, val requestId: Int) : TvPeopleIntent
    data object CommentsRefreshed : TvPeopleIntent
}
