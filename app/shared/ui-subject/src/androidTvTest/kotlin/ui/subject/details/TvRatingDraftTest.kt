/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details

import kotlin.test.Test
import kotlin.test.assertEquals

class TvRatingDraftTest {
    @Test
    fun `own score takes precedence and an unrated draft rounds the average`() {
        assertEquals(7, initialTvRatingScore(7, "8.5"))
        assertEquals(8, initialTvRatingScore(0, "8.3"))
        assertEquals(9, initialTvRatingScore(0, "8.5"))
        assertEquals(10, initialTvRatingScore(0, "9.9"))
    }

    @Test
    fun `missing or invalid averages remain unrated`() {
        listOf("", "–", "NaN", "Infinity", "0").forEach {
            assertEquals(0, initialTvRatingScore(0, it))
        }
    }
}
