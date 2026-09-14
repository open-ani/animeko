/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person.discussion

import androidx.compose.runtime.Immutable

internal enum class TvPeopleDiscussionPage { List, Comment, Report, Image }

/** Read-only discussion snapshot; the screen owns navigation and business result handling. */
@Immutable
internal data class TvPeopleDiscussionState(
    val name: String,
    val page: TvPeopleDiscussionPage,
    val commentId: String?,
    val commentFocus: String?,
    val image: String,
    val generation: Int,
    val bangumiUnavailable: Boolean,
    val reportBusy: Boolean,
)

/** Local presentation events, separate from vote and report submission callbacks. */
internal sealed interface TvPeopleDiscussionAction {
    data class FocusComment(val key: String) : TvPeopleDiscussionAction
    data class OpenComment(val id: String) : TvPeopleDiscussionAction
    data object ShowReport : TvPeopleDiscussionAction
    data class ShowImage(val url: String) : TvPeopleDiscussionAction
    data object Close : TvPeopleDiscussionAction
}
