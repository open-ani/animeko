/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person

import me.him188.ani.app.data.models.person.PersonDetailsInfo
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.PersonDetailRole
import me.him188.ani.leanback.ui.subject.person.discussion.peopleDiscussionCount
import me.him188.ani.leanback.ui.subject.person.presentation.TvPeopleDiscussionPage
import me.him188.ani.leanback.ui.subject.person.presentation.TvPeopleOverlay
import me.him188.ani.leanback.ui.subject.person.presentation.TvPeoplePresentationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class TvPeoplePresentationTest {
    @Test fun discussionBackReturnsToListThenCardAndIgnoresDuplicateKeyUp() {
        val state = TvPeoplePresentationState()
        state.open(TvPeopleOverlay.Discussion)
        state.openComment("ANI:20")
        val generation = state.generation
        state.back(generation)
        state.back(generation)
        assertEquals(TvPeopleOverlay.Discussion, state.overlay)
        assertEquals(TvPeopleDiscussionPage.List, state.discussionPage)
        assertEquals("comment:ANI:20", state.commentFocus)
        state.back()
        assertEquals(TvPeopleOverlay.None, state.overlay)
        assertEquals("discussion", state.focused)
    }

    @Test fun lateReportCompletionDoesNotDismissNewComment() {
        val state = TvPeoplePresentationState()
        state.open(TvPeopleOverlay.Discussion)
        state.openComment("ANI:1")
        state.report()
        val request = state.generation
        state.back()
        state.back()
        state.openComment("ANI:2")
        state.back(request)
        assertEquals("ANI:2", state.commentId)
        assertEquals(TvPeopleDiscussionPage.Comment, state.discussionPage)
    }

    @Test fun personEntryRoleSeparatesSavedNavigationIdentity() {
        assertNotEquals(NavRoutes.PersonDetail(1, PersonDetailRole.VoiceActor), NavRoutes.PersonDetail(1, PersonDetailRole.Staff))
        val voice = TvPeoplePresentationState().apply { rememberFocus("casts:1:20") }
        val staff = TvPeoplePresentationState().apply { rememberFocus("works:20") }
        assertEquals("casts:1:20", voice.rowFocus["casts"])
        assertEquals("works:20", staff.rowFocus["works"])
    }

    @Test fun mixedCountsStayLowerBoundsUntilAllSourcesEnd() {
        assertEquals("—", peopleDiscussionCount(0, false, false, false))
        assertEquals("20+", peopleDiscussionCount(20, true, false, false))
        assertEquals("20+", peopleDiscussionCount(20, true, true, true))
        assertEquals("20", peopleDiscussionCount(20, true, true, false))
        assertEquals("0", peopleDiscussionCount(0, true, true, false))
        assertEquals("—", peopleDiscussionCount(0, true, true, true))
    }

    @Test fun organizationsFitImagesAndPreserveCareersAndCounts() {
        val profile = TvPeopleProfile.from(PersonDetailsInfo(
            PersonInfo(1, "Studio", PersonType.Corporation, emptyList(), "large", "medium", "summary", null),
            listOf("producer", "seiyu"), emptyList(), 12, 7, 40, 62,
        ))
        assertFalse(profile.portrait)
        assertEquals(listOf("producer", "seiyu"), profile.careers)
        assertEquals(40, profile.workCount)
        assertEquals(62, profile.castCount)
    }
}
