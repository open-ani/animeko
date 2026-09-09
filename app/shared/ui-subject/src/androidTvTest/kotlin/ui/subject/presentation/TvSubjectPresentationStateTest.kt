package me.him188.ani.leanback.ui.subject.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvSubjectPresentationStateTest {
    @Test fun staleSubmissionDoesNotCloseANewerPanel() {
        val state = TvSubjectPresentationState()
        state.open(TvDetailsPanelKind.Rating, origin = "rating")
        val requestId = state.generation
        state.close()
        state.open(TvDetailsPanelKind.Characters, origin = "characters-all")
        assertFalse(state.complete(requestId, false))
        assertEquals(TvDetailsPanelKind.Characters, state.panel?.kind)
    }

    @Test fun removingCollectionClosesBothLevelsAndRestoresTheAction() {
        val state = TvSubjectPresentationState()
        state.open(TvDetailsPanelKind.Collection, origin = "collection")
        state.open(TvDetailsPanelKind.RemoveCollection, origin = "collection:NOT_COLLECTED")
        assertTrue(state.complete(state.generation, false))
        assertNull(state.panel)
        assertEquals("collection", state.restoreTarget)
    }

    @Test fun markAllConfirmationReturnsToCollectionWhenDismissed() {
        val state = TvSubjectPresentationState()
        state.open(TvDetailsPanelKind.Collection, origin = "collection")
        state.complete(state.generation, true)
        assertEquals(TvDetailsPanelKind.MarkAllWatched, state.panel?.kind)
        state.close()
        assertEquals(TvDetailsPanelKind.Collection, state.panel?.kind)
    }

    @Test fun nestedImageReturnsToTheCommentBeforeLeavingTheList() {
        val state = TvSubjectPresentationState()
        state.open(TvDetailsPanelKind.Comments, origin = "comments-all")
        state.open(TvDetailsPanelKind.Comment, "review-42", "comment:review-42")
        state.open(TvDetailsPanelKind.Image, "https://example.org/image", "image:cover")
        state.close()
        assertEquals(TvDetailsPanelKind.Comment, state.panel?.kind)
        state.close()
        assertEquals("comment:review-42", state.restoreTarget)
        assertEquals(TvDetailsPanelKind.Comments, state.panel?.kind)
    }

    @Test fun removedFocusedItemSelectsItsNeighbourAndEmptyListsUseTheirEntry() {
        assertEquals("c", detailsFocusFallback("b", listOf("a", "b", "c"), listOf("a", "c"), "entry"))
        assertEquals("b", detailsFocusFallback("b", listOf("a", "b"), listOf("b", "a"), "entry"))
        assertEquals("entry", detailsFocusFallback("b", listOf("b"), emptyList(), "entry"))
    }
}
