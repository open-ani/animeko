/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.rating

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreOnlyRatingTest {
    @Test fun changingOrClearingScorePreservesExistingReviewAndPrivacy() = runTest {
        val submitted = mutableListOf<RateRequest>()
        val rating = SelfRatingInfo(7, "Existing review [mask]spoiler[/mask]", emptyList(), true)
        val controller = RatingEditController({ true }, { rating }, { submitted += it }, backgroundScope)
        assertNull(controller.updateScore(9))
        assertNull(controller.updateScore(0))
        assertEquals(listOf(9, 0), submitted.map { it.score })
        assertTrue(submitted.all { it.comment == "Existing review [mask]spoiler[/mask]" && it.isPrivate })
    }

    @Test fun failedScoreSubmissionReturnsAnError() = runTest {
        val rating = SelfRatingInfo(7, null, emptyList(), false)
        val controller = RatingEditController({ true }, { rating }, { error("offline") }, backgroundScope)
        assertNotNull(controller.updateScore(8))
    }
}
