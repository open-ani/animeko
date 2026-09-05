/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.testFramework.FetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceKind.BitTorrent
import me.him188.ani.datasources.api.source.MediaSourceKind.LocalCache
import me.him188.ani.datasources.api.source.MediaSourceKind.WEB
import me.him188.ani.test.DisabledOnNative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@DisabledOnNative
class MediaAutoSelectorTest {
    @Test
    fun `BT waits for its sources and selects before pending WEB completes`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession(startInBackground = false) {
            object {
                val bt1 by bt()
                val bt2 by bt()
                val web1 by web()
            }
        }
        val selection = launchSelection(session)
        sources.bt1.complete(media(kind = BitTorrent, subjectName = "Example Series"))
        testScope().runCurrent()
        assertFalse(selection.isCompleted)
        sources.bt2.complete(emptyList<Media>())
        testScope().runCurrent()
        assertEquals(sources.bt1.instance.mediaSourceId, selection.await()?.mediaSourceId)
        assertEquals(1, sources.web1.fetchCount)
        assertNull(MediaAutoSelector(selector).select(session))
    }

    @Test
    fun `completed WEB alone does not release the BT completion gate`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
                val web1 by web()
            }
        }
        val selection = launchSelection(session)
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().advanceTimeBy(30.seconds)
        testScope().runCurrent()
        assertFalse(selection.isCompleted)
        assertNull(selector.selected.value)
        sources.bt1.complete(media(kind = BitTorrent, subjectName = "Example Series"))
        testScope().runCurrent()
        assertEquals(sources.bt1.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    @Test
    fun `remembered WEB source can win while BT is pending`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
                val web1 by web()
            }
        }
        val selection = launchSelection(session, "web1")
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().runCurrent()
        assertEquals(sources.web1.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    @Test
    fun `pending remembered WEB source does not block BT completion`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
                val web1 by web()
            }
        }
        val selection = launchSelection(session, "web1")
        sources.bt1.complete(media(kind = BitTorrent, subjectName = "Example Series"))
        testScope().runCurrent()
        assertEquals(sources.bt1.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    @Test
    fun `no preferred kind waits for all source kinds`() = runFetchMediaSelectorTestSuite {
        initSelection(preferKind = null)
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
                val web1 by web()
            }
        }
        val selection = launchSelection(session)
        sources.bt1.complete(media(kind = BitTorrent, subjectName = "Example Series"))
        testScope().runCurrent()
        assertFalse(selection.isCompleted)
        sources.web1.complete(emptyList<Media>())
        testScope().runCurrent()
        assertEquals(sources.bt1.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    @Test
    fun `disabled preferred kind waits for remaining sources`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt(enabled = false)
                val web1 by web()
                val web2 by web()
            }
        }
        val selection = launchSelection(session)
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().runCurrent()
        assertFalse(selection.isCompleted)
        sources.web2.complete(emptyList<Media>())
        testScope().runCurrent()
        assertEquals(sources.web1.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    @Test
    fun `missing preferred kind waits for remaining sources`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web()
            }
        }
        val selection = launchSelection(session)
        testScope().runCurrent()
        assertFalse(selection.isCompleted)
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().runCurrent()
        assertEquals(sources.web1.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    @Test
    fun `empty BT results can select WEB results already available`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
                val web1 by web()
            }
        }
        val selection = launchSelection(session)
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().runCurrent()
        sources.bt1.complete(emptyList<Media>())
        testScope().runCurrent()
        assertEquals(sources.web1.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    @Test
    fun `empty BT results end selection even while WEB is pending`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
                val web1 by web()
            }
        }
        val selection = launchSelection(session)
        sources.bt1.complete(emptyList<Media>())
        testScope().runCurrent()
        assertTrue(selection.isCompleted)
        assertNull(selection.await())
    }

    @Test
    fun `cache can win while BT and remembered WEB are pending`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val cached by localCache()
                val bt1 by bt()
                val web1 by web()
            }
        }
        val selection = launchSelection(session, "web1")
        sources.cached.complete(media(kind = LocalCache, subjectName = "Example Series"))
        testScope().runCurrent()
        assertEquals(sources.cached.instance.mediaSourceId, selection.await()?.mediaSourceId)
        assertNull(MediaAutoSelector(selector).select(session))
    }

    @Test
    fun `multiple caches retain list order even when resolution preference differs`() = runFetchMediaSelectorTestSuite {
        initSelection()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(resolution = "720P")
        val (_, session, sources) = configureFetchSession {
            object {
                val cached by localCache()
            }
        }
        val selection = launchSelection(session)
        sources.cached.complete(
            media(kind = LocalCache, subjectName = "Example Series", resolution = "1080P"),
            media(kind = LocalCache, subjectName = "Example Series", resolution = "720P"),
        )
        testScope().runCurrent()
        // Local caches bypass preference filtering, matching trySelectCached behavior.
        assertEquals("1080P", selection.await()?.properties?.resolution)
    }

    @Test
    fun `cache can bypass unmatched preferences`() = runFetchMediaSelectorTestSuite {
        initSelection()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(alliance = "missing")
        val (_, session, sources) = configureFetchSession {
            object {
                val cached by localCache()
            }
        }
        val selection = launchSelection(session)
        sources.cached.complete(media(kind = LocalCache, subjectName = "Example Series", alliance = "available"))
        testScope().runCurrent()
        assertEquals(sources.cached.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    @Test
    fun `no results ends normally without selection`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
                val cached by localCache()
            }
        }
        val selection = launchSelection(session)
        sources.cached.complete(emptyList<Media>())
        sources.bt1.complete(emptyList<Media>())
        testScope().runCurrent()
        assertTrue(selection.isCompleted)
        assertFalse(selection.isCancelled)
        assertNull(selection.await())
    }

    @Test
    fun `manual selection stops waiting without another source update`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, _) = configureFetchSession {
            object {
                val bt1 by bt()
            }
        }
        val selection = launchSelection(session)
        testScope().runCurrent()
        val manual = media(kind = BitTorrent, subjectName = "Example Series")
        selector.select(manual)
        testScope().runCurrent()
        assertTrue(selection.isCompleted)
        assertNull(selection.await())
        assertEquals(manual, selector.selected.value)
    }

    @Test
    fun `completion selection waits for context before applying preferences`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
            }
        }
        val context = preferenceApi.mediaSelectorContext.value
        preferenceApi.mediaSelectorContext.value = context.copy(subjectFinished = null)
        val selection = launchSelection(session)
        sources.bt1.complete(media(kind = BitTorrent, subjectName = "Example Series"))
        testScope().runCurrent()
        assertFalse(selection.isCompleted)
        preferenceApi.mediaSelectorContext.value = context
        testScope().runCurrent()
        assertEquals(sources.bt1.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    @Test
    fun `completion selection uses the current preferred kind`() = runFetchMediaSelectorTestSuite {
        initSelection()
        val (_, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()
                val web1 by web()
            }
        }
        val selection = launchSelection(session)
        testScope().runCurrent()
        preferenceApi.mediaSelectorSettings.value = preferenceApi.mediaSelectorSettings.value.copy(preferKind = WEB)
        sources.bt1.complete(media(kind = BitTorrent, subjectName = "Example Series"))
        testScope().runCurrent()
        assertFalse(selection.isCompleted)
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().runCurrent()
        assertEquals(sources.web1.instance.mediaSourceId, selection.await()?.mediaSourceId)
    }

    private fun FetchMediaSelectorTestSuite.initSelection(preferKind: MediaSourceKind? = BitTorrent) {
        initSubject("Example Series")
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = MediaSelectorSettings.AllVisible.copy(
            preferKind = preferKind, preferSeasons = false, hideSingleEpisodeForCompleted = false,
        )
    }

    context(scope: TestScope)
    private fun FetchMediaSelectorTestSuite.launchSelection(
        session: MediaFetchSession,
        preferredSourceId: String? = null,
    ): Deferred<Media?> = scope.async(start = CoroutineStart.UNDISPATCHED) {
        MediaAutoSelector(selector).select(session, MediaAutoSelector.Config(preferredSourceId = preferredSourceId))
    }

    context(scope: TestScope)
    private fun testScope(): TestScope = implicit()
}
