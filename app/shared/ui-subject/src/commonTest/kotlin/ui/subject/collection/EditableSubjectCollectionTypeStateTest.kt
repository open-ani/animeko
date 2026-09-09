/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.subject.collection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditableSubjectCollectionTypeStateTest {
    @Test
    fun completedCollectionSurvivesDecliningTheEpisodePrompt() = runTest {
        val collection = MutableStateFlow(UnifiedCollectionType.DOING)
        var markedEpisodes = false
        val state = EditableSubjectCollectionTypeState(collection, { true }, { collection.value = it },
            { markedEpisodes = true }, backgroundScope)
        assertNull(state.setSelfCollectionType(UnifiedCollectionType.DONE))
        assertTrue(state.shouldOfferMarkAllWatched)
        state.dismissSetAllEpisodesDoneDialog()
        assertEquals(UnifiedCollectionType.DONE, collection.value)
        assertFalse(markedEpisodes)
        assertFalse(state.shouldOfferMarkAllWatched)
    }

    @Test
    fun markAllFailureKeepsThePromptAvailableForRetry() = runTest {
        val collection = MutableStateFlow(UnifiedCollectionType.DOING)
        var attempts = 0
        val state = EditableSubjectCollectionTypeState(collection, { true }, { collection.value = it },
            { if (++attempts == 1) error("offline") }, backgroundScope)
        assertNull(state.setSelfCollectionType(UnifiedCollectionType.DONE))
        assertNotNull(state.setAllEpisodesWatchedAwait())
        assertTrue(state.shouldOfferMarkAllWatched)
        assertNull(state.setAllEpisodesWatchedAwait())
        assertEquals(2, attempts)
        assertEquals(UnifiedCollectionType.DONE, collection.value)
    }

    @Test
    fun failedCollectionDoesNotOfferTheEpisodePrompt() = runTest {
        val collection = MutableStateFlow(UnifiedCollectionType.DOING)
        val state = EditableSubjectCollectionTypeState(collection, { true }, { error("offline") }, {}, backgroundScope)
        assertNotNull(state.setSelfCollectionType(UnifiedCollectionType.DONE))
        assertFalse(state.shouldOfferMarkAllWatched)
        assertEquals(UnifiedCollectionType.DOING, collection.value)
    }
}
