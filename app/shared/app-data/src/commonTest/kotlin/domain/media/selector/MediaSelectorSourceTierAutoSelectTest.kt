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
import me.him188.ani.app.domain.media.selector.testFramework.channelTiers
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

    @Test
    fun `channel tier - auto select t0 channel of a high tier source`() = runFetchMediaSelectorTestSuite {
        initSubject()
        val (handles, session, sources) = configureFetchSession {
            object {
                val web1 by web {
                    tier = 2
                    channelTiers("channel-a" to 0)
                }
                val web2 by web { tier = 2 }
            }
        }
        createFastSelectFlow(session).test {
            expectNoEvents()
            sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName, alliance = "channel-a"))
            testScope().runCurrent()
            listOf(assertNotNull(awaitItem())).assert {
                single().assert(source = sources.web1)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `channel tier - dont auto select media from demoted channel`() = runFetchMediaSelectorTestSuite {
        // 数据源整体 tier 0, 但结果全在被降到 tier 2 的 channel, 不能秒选
        initSubject()
        val (handles, session, sources) = configureFetchSession {
            object {
                val web1 by web {
                    tier = 0
                    channelTiers("bad-channel" to 2)
                }
                val web2 by web { tier = 2 }
            }
        }
        createFastSelectFlow(session).test {
            expectNoEvents()
            sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName, alliance = "bad-channel"))
            testScope().runCurrent()
            expectNoEvents()

            testScope().advanceTimeBy(5.seconds) // Wait for timeout
            testScope().runCurrent()

            // 超时后 fallback 仍可以选择
            listOf(assertNotNull(awaitItem())).assert {
                single().assert(source = sources.web1)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `channel tier - t0 channel selected instantly while t1 channel of other source waits`() =
        runFetchMediaSelectorTestSuite {
            // 用户场景: 优先等 A 源的 channel A/B (tier 0), 其次才是 B 源的 channel C (tier 1)
            initSubject()
            val (handles, session, sources) = configureFetchSession {
                object {
                    val webA by web {
                        tier = 3
                        channelTiers("channel-a" to 0, "channel-b" to 0)
                    }
                    val webB by web {
                        tier = 3
                        channelTiers("channel-c" to 1)
                    }
                }
            }
            createFastSelectFlow(session).test {
                expectNoEvents()
                // B 源先完成, 但其最优 channel 只有 tier 1, 不满足秒选
                sources.webB.complete(media(kind = WEB, subjectName = initApi.subjectName, alliance = "channel-c"))
                testScope().runCurrent()
                expectNoEvents()

                // A 源完成, channel-b 是 tier 0, 立即选择
                sources.webA.complete(media(kind = WEB, subjectName = initApi.subjectName, alliance = "channel-b"))
                testScope().runCurrent()
                listOf(assertNotNull(awaitItem())).assert {
                    single().assert(source = sources.webA)
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
