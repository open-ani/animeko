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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.testFramework.FetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.channelTiers
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.tier
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind.LocalCache
import me.him188.ani.datasources.api.source.MediaSourceKind.WEB
import me.him188.ani.test.DisabledOnNative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@DisabledOnNative
class MediaAutoSelectorWebTest {
    @Test
    fun `exact T0 at eight seconds beats an earlier fuzzy source`() = runFetchMediaSelectorTestSuite(cachingEnabled = true) {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val haixing by web { tier = 2 }
                val xifan by web { tier = 0 }
            }
        }
        val job = launchSelection(session)
        sources.haixing.complete(media(kind = WEB, subjectName = "Example Series Special"))
        testScope().runCurrent()
        testScope().advanceTimeBy(5.seconds)
        testScope().runCurrent()
        assertNull(selector.selected.value)
        testScope().advanceTimeBy(3.seconds)
        sources.xifan.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().runCurrent()
        assertEquals(sources.xifan.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `all sources complete with fuzzy T0 but must wait until fifteen seconds`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
            }
        }
        val job = launchSelection(session)
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series Special"))
        testScope().runCurrent()
        assertEquals(MatchMetadata.SubjectMatchKind.FUZZY, selector.filteredCandidates.first()
            .filterIsInstance<MaybeExcludedMedia.Included>().single().metadata.subjectMatchKind)
        testScope().advanceTimeBy(15.seconds)
        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)
        testScope().runCurrent()
        assertEquals(sources.web1.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `first deadline selects exact by channel tier regardless of source order and preferences`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val first by web { tier = 2 }
                val second by web {
                    tier = 3
                    channelTiers("good" to 1)
                }
            }
        }
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(mediaSourceId = sources.first.instance.mediaSourceId)
        val job = launchSelection(session)
        sources.first.complete(media(kind = WEB, subjectName = "Example Series", resolution = "2160P"))
        sources.second.complete(media(kind = WEB, subjectName = "Example Series", alliance = "good", resolution = "720P"))
        testScope().runCurrent()
        assertNull(selector.selected.value)
        testScope().advanceTimeBy(5.seconds)
        testScope().runCurrent()
        assertEquals(sources.second.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `fuzzy stage still prefers exact T2 over fuzzy T0 in the same snapshot`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val fuzzy by web { tier = 0 }
                val exact by web { tier = 2 }
            }
        }
        val context = preferenceApi.mediaSelectorContext.value
        preferenceApi.mediaSelectorContext.value = context.copy(subjectFinished = null)
        val job = launchSelection(session)
        sources.fuzzy.complete(media(kind = WEB, subjectName = "Example Series Special"))
        sources.exact.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().advanceTimeBy(15.seconds)
        testScope().runCurrent()
        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)
        // Both candidates become selectable after the fuzzy deadline.
        preferenceApi.mediaSelectorContext.value = context
        testScope().runCurrent()
        assertEquals(sources.exact.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `fuzzy fallback ranks by tier rather than source order`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val first by web { tier = 3 }
                val second by web { tier = 1 }
            }
        }
        val job = launchSelection(session)
        sources.first.complete(media(kind = WEB, subjectName = "Example Series Special"))
        sources.second.complete(media(kind = WEB, subjectName = "Example Series Special"))
        testScope().runCurrent()
        testScope().advanceTimeBy(15.seconds)
        testScope().runCurrent()
        assertEquals(sources.second.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `both deadlines begin after preferred source fails`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val remembered by web { tier = 2 }
                val fuzzy by web { tier = 0 }
            }
        }
        val job = launchSelection(session, preferredSourceId = sources.remembered.instance.mediaSourceId)
        sources.fuzzy.complete(media(kind = WEB, subjectName = "Example Series Special"))
        testScope().runCurrent()
        testScope().advanceTimeBy(20.seconds)
        testScope().runCurrent()
        assertNull(selector.selected.value)
        sources.remembered.complete(emptyList<Media>())
        testScope().runCurrent()
        testScope().advanceTimeBy(15.seconds)
        assertNull(selector.selected.value)
        testScope().runCurrent()
        assertEquals(sources.fuzzy.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `remembered fuzzy source retains priority over ready exact T0`() = runFetchMediaSelectorTestSuite(cachingEnabled = true) {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val remembered by web { tier = 2 }
                val exact by web { tier = 0 }
            }
        }
        val job = launchSelection(session, preferredSourceId = sources.remembered.instance.mediaSourceId)
        sources.exact.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().runCurrent()
        assertNull(selector.selected.value)
        sources.remembered.complete(media(kind = WEB, subjectName = "Example Series Special"))
        testScope().runCurrent()
        assertEquals(sources.remembered.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `no candidates at fifteen seconds continues listening to pending source`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 2 }
            }
        }
        val job = launchSelection(session)
        testScope().runCurrent()
        testScope().advanceTimeBy(16.seconds)
        testScope().runCurrent()
        assertFalse(job.isCompleted)
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series Special"))
        testScope().runCurrent()
        assertEquals(sources.web1.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `manual selection stops waiting and deadlines cannot overwrite it`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 2 }
            }
        }
        val job = launchSelection(session)
        testScope().runCurrent()
        val manual = media(kind = WEB, subjectName = "Example Series")
        selector.select(manual)
        testScope().runCurrent()
        assertTrue(job.isCompleted)
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().advanceTimeBy(16.seconds)
        testScope().runCurrent()
        assertEquals(manual, selector.selected.value)
    }

    @Test
    fun `infinite tolerance never enables exact high tier or fuzzy fallback`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val exact by web { tier = 2 }
                val fuzzy by web { tier = 0 }
            }
        }
        val job = launchSelection(session, exactAfter = Duration.INFINITE)
        sources.exact.complete(media(kind = WEB, subjectName = "Example Series"))
        sources.fuzzy.complete(media(kind = WEB, subjectName = "Example Series Special"))
        testScope().runCurrent()
        testScope().advanceTimeBy(120.seconds)
        testScope().runCurrent()
        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)
        job.cancel()
    }

    @Test
    fun `all empty sources finish without waiting for deadlines`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web()
            }
        }
        val job = launchSelection(session)
        sources.web1.complete(emptyList<Media>())
        testScope().runCurrent()
        assertTrue(job.isCompleted)
        assertNull(selector.selected.value)
    }

    @Test
    fun `standalone selection drives lazy fetch and sees all results when source completes`() = runFetchMediaSelectorTestSuite(cachingEnabled = true) {
        initWeb()
        val (_, session, sources) = configureFetchSession(startInBackground = false) {
            object {
                val web1 by web { tier = 0 }
            }
        }
        val job = launchSelection(session)
        sources.web1.complete(
            media(kind = WEB, subjectName = "Example Series Special"),
            media(kind = WEB, subjectName = "Example Series"),
        )
        testScope().runCurrent()
        assertEquals(1, sources.web1.fetchCount)
        assertEquals("Example Series", selector.selected.value?.properties?.subjectName)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `disabled fast select still enforces fuzzy deadline after all sources finish`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
            }
        }
        val job = testScope().launch {
            MediaAutoSelector(selector).select(session, MediaAutoSelector.Config(
                web = MediaAutoSelector.Web(preferenceApi.sourceTiers!!, fastSelect = false),
            ))
        }
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series Special"))
        testScope().runCurrent()
        testScope().advanceTimeBy(15.seconds)
        assertNull(selector.selected.value)
        testScope().runCurrent()
        assertEquals(sources.web1.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `zero tolerance accepts exact high tier immediately but still delays fuzzy`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val fuzzy by web { tier = 0 }
                val exact by web { tier = 2 }
            }
        }
        val job = launchSelection(session, exactAfter = Duration.ZERO)
        sources.fuzzy.complete(media(kind = WEB, subjectName = "Example Series Special"))
        testScope().runCurrent()
        assertNull(selector.selected.value)
        sources.exact.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().runCurrent()
        assertEquals(sources.exact.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `automatic override does not overwrite a new manual choice while waiting`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 2 }
            }
        }
        val previous = media(kind = WEB, subjectName = "Example Series")
        selector.select(previous)
        val job = testScope().launch {
            MediaAutoSelector(selector).select(
                session,
                MediaAutoSelector.Config(web = MediaAutoSelector.Web(preferenceApi.sourceTiers!!)),
                expectedSelection = previous,
            )
        }
        testScope().runCurrent()
        val manual = media(kind = WEB, subjectName = "Example Series")
        selector.select(manual)
        sources.web1.complete(media(kind = WEB, subjectName = "Example Series"))
        testScope().runCurrent()
        testScope().advanceTimeBy(15.seconds)
        testScope().runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(manual, selector.selected.value)
    }

    @Test
    fun `completed empty Web sources keep waiting for a pending cache`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web()
                val cached by localCache()
            }
        }
        val job = launchSelection(session)
        sources.web1.complete(emptyList<Media>())
        testScope().advanceTimeBy(15.seconds)
        testScope().runCurrent()
        assertFalse(job.isCompleted)
        assertNull(selector.selected.value)
        sources.cached.complete(media(kind = LocalCache, subjectName = "Example Series"))
        testScope().runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(sources.cached.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
    }

    private fun FetchMediaSelectorTestSuite.initWeb() {
        initSubject("Example Series")
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = MediaSelectorSettings.AllVisible.copy(
            preferKind = WEB, preferSeasons = false, hideSingleEpisodeForCompleted = false,
        )
    }

    context(scope: TestScope)
    private fun FetchMediaSelectorTestSuite.launchSelection(
        session: MediaFetchSession,
        preferredSourceId: String? = null,
        exactAfter: Duration = 5.seconds,
    ): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        MediaAutoSelector(selector).select(session, MediaAutoSelector.Config(
            preferredSourceId = preferredSourceId,
            web = MediaAutoSelector.Web(
                sourceTiers = preferenceApi.sourceTiers!!,
                exactMatchAfter = exactAfter,
            ),
        ))
    }

    context(scope: TestScope)
    private fun testScope(): TestScope = implicit()
}
