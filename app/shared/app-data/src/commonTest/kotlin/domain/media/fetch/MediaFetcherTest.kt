/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.mediasource.web.CaptchaRequiredException
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.WebCaptchaRequest
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.test.assertCoroutineSuspends
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * @see MediaFetcher
 */
class MediaFetcherTest {
    private suspend fun createFetcher(
        vararg instances: MediaSourceInstance
    ): MediaSourceMediaFetcher {
        return MediaSourceMediaFetcher(
            { MediaFetcherConfig.Default },
            listOf(*instances),
            currentCoroutineContext()[ContinuationInterceptor] ?: EmptyCoroutineContext,
        )
    }

    private val request1 = MediaFetchRequest(
        subjectId = "123123",
        episodeId = "1231231",
        subjectNames = listOf("夜晚的水母不会游泳"),
        episodeSort = EpisodeSort("03"),
        episodeName = "测试剧集2",
    )

    ///////////////////////////////////////////////////////////////////////////
    // Completeness
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `toList flow does not complete`() = runTest {
        val session = createFetcher(createTestMediaSourceInstance(TestHttpMediaSource()))
            .newSession(request1)
        assertCoroutineSuspends {
            session.cumulativeResults.toList()
        }

        session.awaitCompletedResults()

        assertCoroutineSuspends {
            session.hasCompleted.toList()
        }
    }

    @Test
    fun `hasCompleted flow does not complete`() = runTest {
        val session = createFetcher(createTestMediaSourceInstance(TestHttpMediaSource()))
            .newSession(request1)
        assertCoroutineSuspends {
            session.hasCompleted.toList()
        }

        session.awaitCompletedResults()

        assertCoroutineSuspends {
            session.hasCompleted.toList()
        }
    }

    @Test
    fun `collect hasCompleted does not start fetch`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        fail("Should not fetch")
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        assertEquals(false, session.hasCompleted.first().allCompleted())
    }

    ///////////////////////////////////////////////////////////////////////////
    // hasCompleted
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `hasCompleted is initially true if no source`() = runTest {
        val session = createFetcher().newSession(request1)
        assertEquals(0, session.mediaSourceResults.size)
        assertEquals(true, session.hasCompleted.first().allCompleted())
    }

    @Test
    fun `hasCompleted is initially false when all sources are enabled`() = runTest {
        val session = createFetcher(createTestMediaSourceInstance(TestHttpMediaSource())).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        assertEquals(false, session.hasCompleted.first().allCompleted())
    }

    @Test
    fun `hasCompleted is initially true when all sources are disabled`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(TestHttpMediaSource(), isEnabled = false),
            createTestMediaSourceInstance(TestHttpMediaSource(), isEnabled = false),
        ).newSession(request1)
        assertEquals(2, session.mediaSourceResults.size)
        assertIs<MediaSourceFetchState.Disabled>(session.mediaSourceResults.first().state.value)
        assertIs<MediaSourceFetchState.Disabled>(session.mediaSourceResults.toList()[1].state.value)
        assertEquals(true, session.hasCompleted.first().allCompleted())
    }

    @Test
    fun `hasCompleted is initially false when at least one source is enabled`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(TestHttpMediaSource(), isEnabled = false),
            createTestMediaSourceInstance(TestHttpMediaSource()),
        ).newSession(request1)
        assertEquals(2, session.mediaSourceResults.size)
        assertIs<MediaSourceFetchState.Disabled>(session.mediaSourceResults.first().state.value)
        assertIs<MediaSourceFetchState.Idle>(session.mediaSourceResults[1].state.value)
        assertEquals(false, session.hasCompleted.first().allCompleted())
    }

    ///////////////////////////////////////////////////////////////////////////
    // cumulativeResults
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `awaitCompletedResults from one source`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        assertEquals(5, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
    }

    @Test
    fun `captcha required becomes completed captcha state`() = runTest {
        val request = WebCaptchaRequest(
            mediaSourceId = "test-source",
            pageUrl = "https://example.com/search",
            kind = WebCaptchaKind.Cloudflare,
        )
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        throw CaptchaRequiredException(request)
                    },
                ),
            ),
        ).newSession(request1)

        assertEquals(emptyList(), session.awaitCompletedResults())

        val state = session.mediaSourceResults.first().state.value
        assertIs<MediaSourceFetchState.CaptchaRequired>(state)
        assertEquals(request, state.request)
        assertTrue(session.hasCompleted.first().allCompleted())
    }

    @Test
    fun `captcha required source can restart and succeed later`() = runTest {
        val fetchCalled = AtomicInteger(0)
        val captchaRequest = WebCaptchaRequest(
            mediaSourceId = "test-source",
            pageUrl = "https://example.com/search",
            kind = WebCaptchaKind.Cloudflare,
        )
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        if (fetchCalled.incrementAndGet() == 1) {
                            throw CaptchaRequiredException(captchaRequest)
                        }
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        val result = session.mediaSourceResults.first()

        assertEquals(emptyList(), session.awaitCompletedResults())
        assertIs<MediaSourceFetchState.CaptchaRequired>(result.state.value)

        result.restart()
        assertIs<MediaSourceFetchState.Idle>(result.state.value)

        assertEquals(5, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Succeed>(result.state.value)
        assertEquals(2, fetchCalled.get())
    }

    // 从两个不同的源获取数据, 但是数据是相同的, 需要去重
    @Test
    fun `awaitCompletedResults from two sources distinct`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(2, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        assertEquals(5, session.awaitCompletedResults().size) // because the same media is returned from both sources
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
    }

    @Test
    fun `awaitCompletedResults from two sources`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.take(1).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.drop(1).take(1).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(2, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        assertEquals(2, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
    }

    @Test
    fun `initial empty cumulative list`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.cumulativeResults.first()
        assertEquals(0, res.size)
    }

    @Test
    fun `source result is shared and has replay`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertEquals(5, session.awaitCompletedResults().size)
        assertEquals(5, session.cumulativeResults.first().size)
        assertEquals(5, res.results.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // source 之间不影响
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `collecting one source does not start the other`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.take(2).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        fail("Should not fetch")
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(2, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertEquals(2, res.awaitCompletedResults().size)

        val res2 = session.mediaSourceResults[1]
        assertIs<MediaSourceFetchState.Idle>(res2.state.value)
    }

    ///////////////////////////////////////////////////////////////////////////
    // disable sources
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `disable source`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertIs<MediaSourceFetchState.Disabled>(res.state.value)
        assertEquals(0, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Disabled>(res.state.value)
    }

    @Test
    fun `disable sources result is empty`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertIs<MediaSourceFetchState.Disabled>(res.state.value)
        assertEquals(0, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Disabled>(res.state.value)
    }

    @Test
    fun `collect from enabled source but not disabled`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.take(3).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(2, session.mediaSourceResults.size)
        assertIs<MediaSourceFetchState.Disabled>(session.mediaSourceResults.first().state.value)
        assertEquals(3, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Succeed>(session.mediaSourceResults[1].state.value)
    }

    @Test
    fun `hasCompleted can be true if all sources are disabled`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.take(3).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
        ).newSession(request1)
        assertEquals(2, session.mediaSourceResults.size)
        assertIs<MediaSourceFetchState.Disabled>(session.mediaSourceResults.first().state.value)
        assertIs<MediaSourceFetchState.Disabled>(session.mediaSourceResults[1].state.value)
        assertEquals(0, session.awaitCompletedResults().size)
        assertEquals(true, session.hasCompleted.first().allCompleted())
    }

    ///////////////////////////////////////////////////////////////////////////
    // resultsIfEnabled
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `resultsIfEnabled is empty if source is disabled`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Disabled>(res.state.value)
        assertEquals(0, res.resultsIfEnabled.first().size)
    }

    @Test
    fun `resultsIfEnabled is the same as results if source is enabled`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        assertEquals(5, res.resultsIfEnabled.first().size)
        assertEquals(5, res.results.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // restart
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `double awaitCompletedResults`() = runTest {
        val firstFetchCalled = AtomicInteger(0)
        val secondFetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        firstFetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.take(2).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        secondFetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.drop(2).take(3).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)

        val res1 = session.mediaSourceResults[0]
        val res2 = session.mediaSourceResults[1]
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res1.state.value)
        assertIs<MediaSourceFetchState.Succeed>(res2.state.value)
        assertEquals(1, firstFetchCalled.get())
        assertEquals(1, secondFetchCalled.get())
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res1.state.value)
        assertIs<MediaSourceFetchState.Succeed>(res2.state.value)
        assertEquals(1, firstFetchCalled.get())
        assertEquals(1, secondFetchCalled.get())
    }

    @Test
    fun `fetch is called once and then cached`() = runTest {
        val fetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        fetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        val res = session.mediaSourceResults.first()
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        assertEquals(1, fetchCalled.get())
        session.awaitCompletedResults()
        assertEquals(1, fetchCalled.get())
        res.results.first()
        assertEquals(1, fetchCalled.get())
    }

    @Test
    fun `restart calls fetch again`() = runTest {
        val fetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        fetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        val res = session.mediaSourceResults.first()
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        assertEquals(1, fetchCalled.get())
        res.restart()
        session.awaitCompletedResults()
        assertEquals(2, fetchCalled.get())
    }

    @Test
    fun `restart resets state to be Idle`() = runTest {
        val fetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        fetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        val res = session.mediaSourceResults.first()
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        assertEquals(1, fetchCalled.get())
        res.restart()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Completed>(res.state.value)
        assertEquals(2, fetchCalled.get())
    }

    @Test
    fun `restart does not clear the existing result immediately`() = runTest {
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        assertEquals(5, res.results.first().size)

        res.restart()
        assertEquals(5, res.results.first().size)
    }

    @Test
    fun `new result is made available to cumulativeResults`() = runTest {
        val fetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        if (fetchCalled.incrementAndGet() == 1) {
                            SinglePagePagedSource {
                                TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        } else {
                            SinglePagePagedSource {
                                TestMediaList.take(3).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        assertEquals(5, res.results.first().size)

        res.restart()
        session.awaitCompletedResults()
        assertEquals(3, res.results.first().size)
        assertEquals(3, session.cumulativeResults.first().size)
    }

    @Test
    fun `restarting one source does not restart other completed ones`() = runTest {
        val firstFetchCalled = AtomicInteger(0)
        val secondFetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        firstFetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.take(2).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        secondFetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.drop(2).take(3).map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
            ),
        ).newSession(request1)
        assertEquals(2, session.mediaSourceResults.size)
        val res1 = session.mediaSourceResults[0]
        val res2 = session.mediaSourceResults[1]
        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res1.state.value)
        assertIs<MediaSourceFetchState.Succeed>(res2.state.value)
        assertEquals(2, res1.results.first().size)
        assertEquals(3, res2.results.first().size)
        assertEquals(1, firstFetchCalled.get())
        assertEquals(1, secondFetchCalled.get())

        res1.restart()
        assertIs<MediaSourceFetchState.Succeed>(res2.state.value)
        assertEquals(1, firstFetchCalled.get())
        assertEquals(1, secondFetchCalled.get())

        assertIs<MediaSourceFetchState.Succeed>(res2.state.value)
        assertEquals(3, res2.results.first().size)
        assertEquals(1, secondFetchCalled.get())

        session.awaitCompletedResults()
        assertIs<MediaSourceFetchState.Succeed>(res2.state.value)

        assertEquals(2, firstFetchCalled.get())
        assertEquals(1, secondFetchCalled.get())

        assertEquals(2, res1.results.first().size)
        assertEquals(3, res2.results.first().size)
        assertEquals(5, session.cumulativeResults.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // enable
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `enable disabled source before collecting result`() = runTest {
        val fetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        fetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        res.enable()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        assertEquals(5, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
    }

    @Test
    fun `enable twice does not restart`() = runTest {
        val fetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        fetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        res.enable()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        assertEquals(5, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        res.enable()
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        assertEquals(1, fetchCalled.get())
    }

    @Test
    fun `enable restarted does not resatrt`() = runTest {
        val fetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        fetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        res.restart()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        assertEquals(5, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        res.enable()
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        assertEquals(5, session.awaitCompletedResults().size)
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
        assertEquals(1, fetchCalled.get())
    }

    @Test
    fun `enable disabled source after collecting result`() = runTest {
        val fetchCalled = AtomicInteger(0)
        val session = createFetcher(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    fetch = {
                        fetchCalled.incrementAndGet()
                        SinglePagePagedSource {
                            TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = false,
            ),
        ).newSession(request1)
        assertEquals(1, session.mediaSourceResults.size)
        val res = session.mediaSourceResults.first()
        assertIs<MediaSourceFetchState.Disabled>(res.state.value)
        assertEquals(0, fetchCalled.get())
        assertEquals(0, session.awaitCompletedResults().size)
        assertEquals(0, fetchCalled.get())
        assertIs<MediaSourceFetchState.Disabled>(res.state.value)

        res.enable()
        assertIs<MediaSourceFetchState.Idle>(res.state.value)
        assertEquals(5, session.awaitCompletedResults().size)
        assertEquals(1, fetchCalled.get())
        assertIs<MediaSourceFetchState.Succeed>(res.state.value)
    }
}

class AtomicInteger(initialValue: Int = 0) {
    private val _value = atomic(initialValue)
    fun get() = _value.value
    fun incrementAndGet() = _value.incrementAndGet()
    var value
        get() = _value.value
        set(value) {
            _value.value = value
        }
}
