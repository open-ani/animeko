/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.testFramework.FetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.tier
import me.him188.ani.app.domain.player.extension.PlayerLoadErrorHandler
import me.him188.ani.app.domain.player.extension.PlayerLoadErrorHandling
import me.him188.ani.app.platform.NetworkEnvironment
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.WebMediaRoute
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** 在真实选择器和查询流上验证跨集承接及失败换源，运行记录不改写用户偏好。 */
class PlaybackMemoryAutoSelectTest {
    private fun FetchMediaSelectorTestSuite.initWeb() {
        initSubject("Example Series")
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = MediaSelectorSettings.AllVisible.copy(
            preferKind = MediaSourceKind.WEB, preferSeasons = false, hideSingleEpisodeForCompleted = false,
        )
    }

    private fun DefaultMedia.withRoute(channel: String = "line-1") = copy(
        properties = properties.copy(webRoute = WebMediaRoute("https://site/subject/1", channel, "config-1")),
    )

    private fun PlaybackSourceMemory.fail(media: DefaultMedia) {
        recordFailure(beginAttempt(1, media), PlaybackSourceMemory.FailureReason.RESOLUTION)
    }

    context(scope: TestScope)
    private fun FetchMediaSelectorTestSuite.newSelector(session: MediaFetchSession, episode: Int): MediaSelector {
        val context = preferenceApi.mediaSelectorContext.value
        preferenceApi.mediaSelectorContext.value = context.copy(
            episodeInfo = context.episodeInfo!!.copy(episodeId = episode, sort = EpisodeSort(episode), ep = EpisodeSort(episode)),
        )
        return DefaultMediaSelector(
            preferenceApi.mediaSelectorContext, session.cumulativeResults,
            preferenceApi.savedUserPreference, preferenceApi.savedDefaultPreference, preferenceApi.mediaSelectorSettings,
            flowCoroutineContext = StandardTestDispatcher(scope.testScheduler), enableCaching = false,
        )
    }

    context(scope: TestScope)
    private suspend fun FetchMediaSelectorTestSuite.choose(
        session: MediaFetchSession, selector: MediaSelector, memory: PlaybackSourceMemory,
        preferredSourceId: String? = null,
    ): MediaAutoSelectOutcome = MediaAutoSelector(selector).select(session, MediaAutoSelector.Config(
        preferredSourceId = preferredSourceId,
        playbackMemory = memory.snapshot(),
        web = MediaAutoSelector.Web(preferenceApi.sourceTiers!!),
    ))

    @Test
    fun `three episodes inherit success and skip failed routes across selector recreation`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 1 }
                val webC by web { tier = 2 }
                val webD by web { tier = 3 }
            }
        }
        val episodes = (1..3).map { ep ->
            media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName, episodeRange = EpisodeRange.single(EpisodeSort(ep))).withRoute()
        }
        sources.webA.complete(episodes)
        sources.webB.complete(episodes.map { it.copy(mediaId = "B-${it.mediaId}") })
        sources.webC.complete(episodes.map { it.copy(mediaId = "C-${it.mediaId}") })
        sources.webD.complete(episodes.map { it.copy(mediaId = "D-${it.mediaId}") })
        val memory = PlaybackSourceMemory()
        var current = newSelector(session, 1)
        val handler = PlayerLoadErrorHandler({ MediaSourceKind.WEB }, { preferenceApi.sourceTiers!! }, memory)

        assertEquals("webA", assertIs<MediaAutoSelectOutcome.Selected>(choose(session, current, memory)).media.mediaSourceId)
        memory.fail(current.selected.value as DefaultMedia)
        handler.handleError(session, current)
        assertEquals("webB", current.selected.value?.mediaSourceId)
        memory.fail(current.selected.value as DefaultMedia)
        handler.handleError(session, current)
        assertEquals("webC", current.selected.value?.mediaSourceId)
        memory.recordSuccess(memory.beginAttempt(1, current.selected.value!!))

        current = newSelector(session, 2)
        assertEquals("webC", assertIs<MediaAutoSelectOutcome.Selected>(choose(session, current, memory)).media.mediaSourceId)
        memory.fail(current.selected.value as DefaultMedia)
        PlayerLoadErrorHandler({ MediaSourceKind.WEB }, { preferenceApi.sourceTiers!! }, memory).handleError(session, current)
        assertEquals("webD", current.selected.value?.mediaSourceId)
        memory.recordSuccess(memory.beginAttempt(2, current.selected.value!!))

        current = newSelector(session, 3)
        assertEquals("webD", assertIs<MediaAutoSelectOutcome.Selected>(choose(session, current, memory)).media.mediaSourceId)
        assertEquals(3, memory.snapshot().failures.size)
        assertEquals(MediaPreference.Any, preferenceApi.savedUserPreference.value)
        assertEquals(listOf(1, 1, 1, 1), listOf(sources.webA, sources.webB, sources.webC, sources.webD).map { it.fetchCount })
    }

    @Test
    fun `failed manual preferred route does not hide another channel in that source`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession { object { val webA by web { tier = 0 } } }
        val first = media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName, alliance = "line-1").withRoute()
        val other = media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName, alliance = "line-2").withRoute("line-2")
        sources.webA.complete(first, other)
        val memory = PlaybackSourceMemory()
        memory.fail(first.copy(mediaSourceId = "webA"))
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(alliance = "line-1", mediaSourceId = "webA")
        val outcome = assertIs<MediaAutoSelectOutcome.Selected>(choose(session, selector, memory, "webA"))
        assertEquals(other.mediaId, outcome.media.mediaId)
        assertEquals(MediaAutoSelectReason.PREFERRED_SOURCE, outcome.reason)
    }

    @Test
    fun `recent success has a finite wait and shares the tier deadline`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val recent by web { tier = 2 }
                val ready by web { tier = 1 }
            }
        }
        val old = media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName).withRoute().copy(mediaSourceId = "recent")
        val memory = PlaybackSourceMemory()
        memory.recordSuccess(memory.beginAttempt(1, old))
        sources.ready.complete(media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName).withRoute())
        val selection = implicit<TestScope>().async { choose(session, selector, memory) }
        implicit<TestScope>().advanceTimeBy(4_999)
        implicit<TestScope>().runCurrent()
        assertFalse(selection.isCompleted)
        implicit<TestScope>().advanceTimeBy(1)
        implicit<TestScope>().runCurrent()
        assertTrue(selection.isCompleted)
        assertEquals("ready", assertIs<MediaAutoSelectOutcome.Selected>(selection.await()).media.mediaSourceId)
    }

    @Test
    fun `exhaustion does not clear failures or automatically restart the round`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession { object { val webA by web { tier = 0 } } }
        val failed = media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName).withRoute()
        sources.webA.complete(failed)
        val memory = PlaybackSourceMemory()
        memory.fail(failed.copy(mediaSourceId = "webA"))
        assertIs<MediaAutoSelectOutcome.Exhausted>(choose(session, selector, memory))
        assertEquals(1, memory.snapshot().failures.size)
        memory.retryFailures()
        assertEquals("webA", assertIs<MediaAutoSelectOutcome.Selected>(choose(session, selector, memory)).media.mediaSourceId)
    }

    @Test
    fun `recent success without the requested episode falls back without recording failure`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val recent by web { tier = 0 }
                val other by web { tier = 2 }
            }
        }
        val old = media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName).withRoute()
        sources.recent.complete(old)
        sources.other.complete(media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName,
            episodeRange = EpisodeRange.single(EpisodeSort(2))).withRoute())
        val memory = PlaybackSourceMemory()
        memory.recordSuccess(memory.beginAttempt(1, old.copy(mediaSourceId = "recent")))
        assertEquals("other", assertIs<MediaAutoSelectOutcome.Selected>(choose(session, newSelector(session, 2), memory)).media.mediaSourceId)
        assertTrue(memory.snapshot().failures.isEmpty())
    }

    @Test
    fun `local cache wins over a recent successful web route`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val recent by web { tier = 0 }
                val cache by localCache()
            }
        }
        val old = media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName).withRoute()
        sources.recent.complete(old)
        sources.cache.complete(media(kind = MediaSourceKind.LocalCache, subjectName = initApi.subjectName))
        val memory = PlaybackSourceMemory()
        memory.recordSuccess(memory.beginAttempt(1, old.copy(mediaSourceId = "recent")))
        assertEquals(MediaAutoSelectReason.LOCAL_CACHE, assertIs<MediaAutoSelectOutcome.Selected>(choose(session, selector, memory)).reason)
    }

    @Test
    fun `recent success cannot enable a disabled source`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession {
            object {
                val recent by web(enabled = false) { tier = 0 }
                val other by web { tier = 2 }
            }
        }
        val old = media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName).withRoute()
        sources.recent.complete(old)
        sources.other.complete(old.copy(mediaId = "other-1"))
        val memory = PlaybackSourceMemory()
        memory.recordSuccess(memory.beginAttempt(1, old.copy(mediaSourceId = "recent")))
        assertEquals("other", assertIs<MediaAutoSelectOutcome.Selected>(choose(session, selector, memory)).media.mediaSourceId)
        assertEquals(0, sources.recent.fetchCount)
    }

    @Test
    fun `known offline environment stops automatic switching without blacklisting routes`() = runFetchMediaSelectorTestSuite {
        initWeb()
        val (_, session, sources) = configureFetchSession { object { val webA by web { tier = 0 } } }
        val current = media(kind = MediaSourceKind.WEB, subjectName = initApi.subjectName).withRoute()
        sources.webA.complete(current)
        val memory = PlaybackSourceMemory()
        choose(session, selector, memory)
        memory.updateEnvironment(NetworkEnvironment(null, false))
        val handler = PlayerLoadErrorHandler({ MediaSourceKind.WEB }, { preferenceApi.sourceTiers!! }, memory)
        assertEquals(PlayerLoadErrorHandling.SkippedNetworkUnavailable, handler.handleError(session, selector))
        assertTrue(handler.blacklist.isEmpty())
        assertTrue(memory.snapshot().failures.isEmpty())
        assertEquals(current.mediaId, selector.selected.value?.mediaId)
    }
}
