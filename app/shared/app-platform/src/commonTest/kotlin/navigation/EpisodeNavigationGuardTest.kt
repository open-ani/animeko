package me.him188.ani.app.navigation

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeNavigationGuardTest {
    @Test
    fun `registers emits denial and disposes`() = runTest {
        val handle = EpisodeNavigationGuardRegistry.register { subjectId, episodeId ->
            if (subjectId == 1 && episodeId == 2) null else "blocked"
        }
        try {
            assertNull(EpisodeNavigationGuardRegistry.check(1, 2))
            assertEquals("blocked", EpisodeNavigationGuardRegistry.check(1, 3))

            EpisodeNavigationGuardRegistry.denialEvents.test {
                EpisodeNavigationGuardRegistry.emitDenial("blocked")
                assertEquals("blocked", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            handle.dispose()
        }
        assertNull(EpisodeNavigationGuardRegistry.check(1, 3))
    }

    @Test
    fun `checkOrNotifyDenied allows silently or emits the denial`() = runTest {
        val handle = EpisodeNavigationGuardRegistry.register { subjectId, episodeId ->
            if (subjectId == 1 && episodeId == 2) null else "blocked"
        }
        try {
            EpisodeNavigationGuardRegistry.denialEvents.test {
                assertTrue(EpisodeNavigationGuardRegistry.checkOrNotifyDenied(1, 2))
                expectNoEvents()

                assertFalse(EpisodeNavigationGuardRegistry.checkOrNotifyDenied(1, 3))
                assertEquals("blocked", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            handle.dispose()
        }
        assertTrue(EpisodeNavigationGuardRegistry.checkOrNotifyDenied(1, 3))
    }
}
