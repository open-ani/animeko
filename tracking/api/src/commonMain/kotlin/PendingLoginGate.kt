/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.tracking.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Accepts a single OAuth redirect only if the user started a login within [window].
 * Custom-scheme redirects can be fired by any app or web page, so an unsolicited token must be ignored.
 */
class PendingLoginGate(
    private val window: Duration = 5.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val startedAt = MutableStateFlow<TimeMark?>(null)

    fun begin() {
        startedAt.value = timeSource.markNow()
    }

    fun consume(): Boolean {
        val mark = startedAt.getAndUpdate { null } ?: return false
        return mark.elapsedNow() <= window
    }
}
