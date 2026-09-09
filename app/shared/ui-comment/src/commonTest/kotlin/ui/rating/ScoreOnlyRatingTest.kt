/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.rating

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreOnlyRatingTest {
    @Test fun changingOrClearingScorePreservesExistingReviewAndPrivacy() = runTest {
        val submitted = mutableListOf<RateRequest>()
        val state = EditableRatingState(
            mutableStateOf(RatingInfo.Empty),
            mutableStateOf(SelfRatingInfo(7, "Existing review [mask]spoiler[/mask]", emptyList(), true)),
            mutableStateOf(true), { true }, { submitted += it }, backgroundScope,
        )
        assertNull(state.updateScore(9))
        assertNull(state.updateScore(0))
        assertEquals(listOf(9, 0), submitted.map { it.score })
        assertTrue(submitted.all { it.comment == "Existing review [mask]spoiler[/mask]" && it.isPrivate })
    }

    @Test fun failedScoreSubmissionReturnsAnErrorWithoutChangingCurrentRating() = runTest {
        val rating = SelfRatingInfo(7, null, emptyList(), false)
        val state = EditableRatingState(mutableStateOf(RatingInfo.Empty), mutableStateOf(rating),
            mutableStateOf(true), { true }, { error("offline") }, backgroundScope)
        assertNotNull(state.updateScore(8))
        assertEquals(rating, state.selfRatingInfo)
    }
}
