/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

internal enum class TvPeopleOverlay { None, Introduction, Discussion, Image }
internal enum class TvPeopleDiscussionPage { List, Comment, Report, Image }

@Stable
internal class TvPeoplePresentationState {
    var focused by mutableStateOf("intro")
    var rowFocus by mutableStateOf<Map<String, String>>(emptyMap())
    var overlay by mutableStateOf(TvPeopleOverlay.None)
        private set
    var discussionPage by mutableStateOf(TvPeopleDiscussionPage.List)
        private set
    var commentId by mutableStateOf<String?>(null)
        private set
    var commentFocus by mutableStateOf<String?>(null)
    var image by mutableStateOf("")
        private set
    var generation by mutableIntStateOf(0)
        private set

    fun rememberFocus(key: String) {
        focused = key
        if (':' in key) rowFocus = rowFocus + (key.substringBefore(':') to key)
    }

    fun open(next: TvPeopleOverlay) {
        generation++
        overlay = next
        discussionPage = TvPeopleDiscussionPage.List
        commentId = null
    }

    fun openComment(id: String) {
        generation++
        commentId = id
        commentFocus = "comment:$id"
        discussionPage = TvPeopleDiscussionPage.Comment
    }

    fun report() { generation++; discussionPage = TvPeopleDiscussionPage.Report }
    fun showImage(url: String) { generation++; image = url; discussionPage = TvPeopleDiscussionPage.Image }

    fun back(expectedGeneration: Int = generation) {
        if (expectedGeneration != generation) return
        generation++
        if (overlay == TvPeopleOverlay.Discussion && discussionPage != TvPeopleDiscussionPage.List) {
            discussionPage = when (discussionPage) {
                TvPeopleDiscussionPage.Report, TvPeopleDiscussionPage.Image -> TvPeopleDiscussionPage.Comment
                else -> TvPeopleDiscussionPage.List
            }
        } else {
            focused = when (overlay) {
                TvPeopleOverlay.Discussion -> "discussion"
                TvPeopleOverlay.Image -> "image"
                else -> "intro"
            }
            overlay = TvPeopleOverlay.None
        }
    }

    companion object {
        val Saver = listSaver<TvPeoplePresentationState, Any>(
            save = { listOf(it.focused, it.overlay.name, it.discussionPage.name, it.commentId.orEmpty(),
                it.commentFocus.orEmpty(), it.image, it.generation) + it.rowFocus.flatMap { listOf(it.key, it.value) } },
            restore = { saved -> TvPeoplePresentationState().apply {
                focused = saved[0] as String
                overlay = TvPeopleOverlay.valueOf(saved[1] as String)
                discussionPage = TvPeopleDiscussionPage.valueOf(saved[2] as String)
                commentId = (saved[3] as String).ifEmpty { null }
                commentFocus = (saved[4] as String).ifEmpty { null }
                image = saved[5] as String
                generation = saved[6] as Int
                rowFocus = saved.drop(7).chunked(2).associate { (it[0] as String) to (it[1] as String) }
            } },
        )
    }
}
