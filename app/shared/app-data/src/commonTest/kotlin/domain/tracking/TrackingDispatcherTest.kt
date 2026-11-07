/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.tracking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrackingDispatcherTest {
    private val change = TrackingChange.Subject(1, UnifiedCollectionType.DOING)

    @Test
    fun `a failing tracker does not prevent another tracker receiving a change`() = runTest {
        val received = mutableListOf<TrackingChange>()
        val failures = mutableListOf<String>()
        val failing = tracker("failed") { error("offline") }
        val working = tracker("working") { received += it }
        TrackingDispatcher(listOf(failing, working)) { id, _ -> failures += id }.dispatch(change)
        assertEquals(listOf("failed"), failures)
        assertEquals(listOf<TrackingChange>(change), received)
    }

    @Test
    fun `a failed failure reporter does not prevent later dispatch`() = runTest {
        val received = mutableListOf<TrackingChange>()
        val failing = tracker("failed") { error("offline") }
        val working = tracker("working") { received += it }
        TrackingDispatcher(listOf(failing, working)) { _, _ -> error("report failed") }.dispatch(change)
        assertEquals(listOf<TrackingChange>(change), received)
    }

    @Test
    fun `failure reporter cancellation propagates`() = runTest {
        val failing = tracker("failed") { error("offline") }
        assertFailsWith<CancellationException> {
            TrackingDispatcher(listOf(failing)) { _, _ -> throw CancellationException("cancelled") }.dispatch(change)
        }
    }

    @Test
    fun `cancellation stops dispatch and propagates`() = runTest {
        var called = false
        val cancelled = tracker("cancelled") { throw CancellationException("cancelled") }
        val next = tracker("next") { called = true }
        assertFailsWith<CancellationException> {
            TrackingDispatcher(listOf(cancelled, next)) { _, _ -> error("unexpected") }.dispatch(change)
        }
        assertEquals(false, called)
    }

    @Test
    fun `tracker IDs must be unique`() {
        val duplicate = tracker("same") {}
        assertFailsWith<IllegalArgumentException> {
            TrackingDispatcher(listOf(duplicate, duplicate)) { _, _ -> }
        }
    }

    private fun tracker(trackerId: String, action: suspend (TrackingChange) -> Unit) = object : Tracker {
        override val id = trackerId
        override suspend fun push(change: TrackingChange) = action(change)
    }
}
