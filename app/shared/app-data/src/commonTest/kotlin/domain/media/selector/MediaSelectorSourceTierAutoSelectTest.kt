/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeOriginalMediaAccess::class)

package me.him188.ani.app.domain.media.selector

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.testFramework.FetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.assert
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.tier
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind.BitTorrent
import me.him188.ani.datasources.api.source.MediaSourceKind.WEB
import me.him188.ani.test.DisabledOnNative
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * @see DefaultMediaSelector.filteredCandidates
 * @see MediaSelectorSourceTiers
 * @see me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
 */
@DisabledOnNative // TODO: ContextParameters crashes on Native
class MediaSelectorSourceTierAutoSelectTest {
    @Test
    fun `control group - auto select when all sources complete`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        val (handles, session, sources) = configureFetchSession {
            request {
                this.subjectNameCN = initApi.subjectName
            }
            object {
                val bt1 by bt()

                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }

        createFastSelectFlow(session).test {
            // Initially no sources are completed
            expectNoEvents()

            // Complete all sources
            sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
            sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
            sources.bt1.complete(media(kind = BitTorrent, subjectName = initApi.subjectName))
            testScope().runCurrent()

            // Now we should auto select
            listOf(assertNotNull(awaitItem())).assert {
                single().assert(source = sources.web1)
            }
            selector.filteredCandidates.first().assert {
                next().assert(included = true, source = sources.web1)
                next().assert(included = true, source = sources.web2)
                next().assert(included = false, kind = BitTorrent)
                assertNoMoreElements()
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auto select only t0 when it completes`() = runFetchMediaSelectorTestSuite {
        initSubject()
        val (handles, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()

                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        createFastSelectFlow(session).test {
            expectNoEvents()
            sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
            testScope().runCurrent()
            listOf(assertNotNull(awaitItem())).assert {
                single().assert(source = sources.web1)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auto select t0 when t0 and t1 completed`() = runFetchMediaSelectorTestSuite {
        initSubject()
        val (handles, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()

                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        createFastSelectFlow(session).test {
            expectNoEvents()
            sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
            sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
            testScope().runCurrent()
            listOf(assertNotNull(awaitItem())).assert {
                single().assert(source = sources.web1)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dont auto select t1 when it completes`() = runFetchMediaSelectorTestSuite {
        initSubject()
        val (handles, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()

                val web1 by web { tier = 1 }
                val web2 by web { tier = 2 }
            }
        }
        createFastSelectFlow(session).test {
            expectNoEvents()
            sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
            testScope().runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dont auto select t2 when it completes`() = runFetchMediaSelectorTestSuite {
        initSubject()
        val (handles, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()

                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        createFastSelectFlow(session).test {
            expectNoEvents()
            sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
            testScope().runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auto select t2 after timeout`() = runFetchMediaSelectorTestSuite {
        initSubject()
        val (handles, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()

                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        createFastSelectFlow(session).test {
            expectNoEvents()
            sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
            testScope().runCurrent() // Should not select yet
            expectNoEvents()

            testScope().advanceTimeBy(5.seconds) // Wait for timeout
            testScope().runCurrent()

            listOf(assertNotNull(awaitItem())).assert {
                single().assert(source = sources.web2)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dont auto select t0 if it does not match`() = runFetchMediaSelectorTestSuite {
        initSubject()
        val (handles, session, sources) = configureFetchSession {
            object {
                val bt1 by bt()

                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        createFastSelectFlow(session).test {
            expectNoEvents()
            sources.web1.complete(media(kind = WEB, subjectName = "Invalid subject name"))
            testScope().runCurrent()
            expectNoEvents()

            // Let's check the media is indeed filtered out
            selector.filteredCandidates.first().assert {
                next().assert(included = false, source = sources.web1)
                assertNoMoreElements()
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun FetchMediaSelectorTestSuite.createFastSelectFlow(
        session: MediaFetchSession,
    ): Flow<Media?> = suspend {
        selector.autoSelect.fastSelectWebSources(
            session,
            sourceTiers = preferenceApi.sourceTiers!!,
        )
    }.asFlow()

    private fun MediaSelectorTestSuite.initSubject() {
        initSubject("test")
    }

    context(scope: TestScope)
    private fun testScope(): TestScope = implicit()
}


context(scope: T)
@Suppress("NOTHING_TO_INLINE")
inline fun <T> implicit(): T = scope
