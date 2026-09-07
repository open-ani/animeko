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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.domain.media.selector.testFramework.collectEvents
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.tier
import me.him188.ani.app.domain.player.extension.PlayerLoadErrorHandler
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceKind.WEB
import me.him188.ani.test.DisabledOnNative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * @see PlayerLoadErrorHandler
 * @see me.him188.ani.app.domain.player.extension.SwitchMediaOnPlayerErrorExtension
 */
@DisabledOnNative // TODO: ContextParameters crashes on Native
class PlayerLoadErrorHandlerTest {
    @Test
    fun `ERR-05 preferKind WEB 播放失败换源到其他 WEB media 并拉黑当前`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 0 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA)
        sources.webB.complete(mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().runCurrent()

        // PINNED: ERR-05 先拉黑当前 selected, 再 delay 1s, delay 期间不换源
        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)

        // PINNED: ERR-05 延迟时长恰好是 1s (不只是"存在延迟"): 999ms 时仍未换源.
        // 只用一次 advanceTimeBy(1.5s) 跨过去的话, delay 被改成 1.4s 也照样绿.
        testScope().advanceTimeBy(999.milliseconds)
        testScope().runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)

        // PINNED: ERR-05 跨过 1s 边界后立刻换源 (两个源都是 tier=0, 走 instant 路径, 无需等容忍窗)
        testScope().advanceTimeBy(2.milliseconds)
        testScope().runCurrent()

        assertTrue(job.isCompleted)
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
    }

    @Test
    fun `ERR-05 高 tier 源只能由 1s 容忍窗超时 fallback 选中`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                // tier=1 高于 InstantSelectTierThreshold(0), 该源永远进不了 instant 候选,
                // 因此只有 lowTierToleranceDuration 超时后的 fallback 能选出东西.
                val webA by web { tier = 1 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA, mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().runCurrent()

        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)

        // t=1999: delay(1s) 已过, fastSelectWebSources 已进入 select{}, 容忍窗还差 1ms.
        // PINNED: ERR-05 lowTierToleranceDuration 恰好是 1s.
        testScope().advanceTimeBy(1.seconds + 999.milliseconds)
        testScope().runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)

        // t=2001: 容忍窗超时, 从已成功查询的源里 fallback 选择, 跳过黑名单里的 mediaA
        testScope().advanceTimeBy(2.milliseconds)
        testScope().runCurrent()

        assertTrue(job.isCompleted)
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
    }

    @Test
    fun `ERR-05 preferKind BT 不自动换源`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 0 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA)
        sources.webB.complete(mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.BitTorrent },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().advanceUntilIdle()

        // PINNED: ERR-05 preferKind=BT 无自动换源, 但当前 media 仍在 delay 前被拉黑
        assertTrue(job.isCompleted)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
    }

    @Test
    fun `ERR-05 候选全在黑名单时保持当前选择`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 0 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA)
        sources.webB.complete(mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        testScope().launch { handler.handleError(session, selector) }
        testScope().advanceUntilIdle()
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)

        lateinit var job: Job
        val collected = selector.collectEvents {
            job = testScope().launch { handler.handleError(session, selector) }
            testScope().advanceUntilIdle()
        }

        // PINNED: ERR-05 候选全在黑名单: instant 与 1s 超时 fallback 均选不出, 返回 null 并保持当前选择.
        // 只断 selected 值是不够的: 即便违规地把 mediaB 又选了一遍, selectImpl 也会因
        // previous == candidate && !force 直接返回 false, selected 逐字节不变. 故用事件收集口径,
        // 断言这期间一次 onSelect 都没有 (进而也没有 onBeforeSelect / 偏好写入).
        assertEquals(0, collected.onSelect.size)
        collected.expectNoEvents()
        assertTrue(job.isCompleted)
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaA.mediaId, mediaB.mediaId), handler.blacklist)
    }

    @Test
    fun `ERR-04 trySelectDefault 的 onSelect previousMedia 为 null 不进黑名单`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            mediaApi.addMedia(media(alliance = "组A"))
            mediaApi.addMedia(media(alliance = "组B"))
        },
    ) {
        val handler = PlayerLoadErrorHandler(
            getPreferKind = { null },
            getSourceTiers = { MediaSelectorSourceTiers(emptyMap()) },
        )

        coroutineScope {
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                handler.observeMediaSelectorBlacklist(
                    mediaSelectorFlow = flowOf(selector),
                )
            }
            testScope.runCurrent()

            val defaultSelected = assertNotNull(selector.trySelectDefault())
            testScope.advanceUntilIdle()

            // PINNED: ERR-04 trySelectDefault 产生的 onSelect previousMedia=null, 不拉黑
            assertTrue(handler.blacklist.isEmpty())

            selector.select(mediaApi.mediaList.value.first { it.mediaId != defaultSelected.mediaId })
            testScope.advanceUntilIdle()
            assertEquals(setOf(defaultSelected.mediaId), handler.blacklist)

            job.cancel()
        }
    }

    @Test
    fun `ERR-03 黑名单不跨 handler 实例共享`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 0 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA)
        sources.webB.complete(mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler1 = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        testScope().launch { handler1.handleError(session, selector) }
        testScope().advanceUntilIdle()
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)

        val handler2 = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        assertTrue(handler2.blacklist.isEmpty())
        val job = testScope().launch { handler2.handleError(session, selector) }
        testScope().advanceUntilIdle()

        // PINNED: ERR-03 黑名单为 handler 实例级, 新 handler 可选回旧 handler 拉黑的 media
        assertTrue(job.isCompleted)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaB.mediaId), handler2.blacklist)
        assertEquals(setOf(mediaA.mediaId), handler1.blacklist)
    }

    @Test
    fun `manual choice during player error delay cancels automatic replacement`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession { object { val web1 by web { tier = 0 } } }
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()
        val failed = media(kind = WEB, subjectName = initApi.subjectName)
        selector.select(failed)
        val handler = PlayerLoadErrorHandler(getPreferKind = { WEB }, getSourceTiers = { preferenceApi.sourceTiers!! })
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().runCurrent()
        val manual = media(kind = WEB, subjectName = initApi.subjectName)
        selector.select(manual)
        testScope().advanceUntilIdle()
        assertTrue(job.isCompleted)
        assertEquals(manual, selector.selected.value)
    }

    @Test
    fun `player error does not fall back to BT when WEB candidates are exhausted`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
                val bt1 by bt()
            }
        }
        val failed = media(kind = WEB, subjectName = initApi.subjectName)
        sources.web1.complete(failed)
        sources.bt1.complete(media(kind = MediaSourceKind.BitTorrent, subjectName = initApi.subjectName))
        testScope().runCurrent()
        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == failed.mediaId })

        val handler = PlayerLoadErrorHandler(getPreferKind = { WEB }, getSourceTiers = { preferenceApi.sourceTiers!! })
        lateinit var job: Job
        val events = selector.collectEvents {
            job = testScope().launch { handler.handleError(session, selector) }
            testScope().advanceUntilIdle()
        }

        assertTrue(job.isCompleted)
        assertEquals(failed.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(failed.mediaId), handler.blacklist)
        events.expectNoEvents()
    }

    @Test
    fun `player error skips ready and pending local caches`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val cached by localCache()
                val pendingCache by localCache()
                val web1 by web { tier = 0 }
            }
        }
        val failed = media(kind = WEB, subjectName = initApi.subjectName)
        val replacement = media(kind = WEB, subjectName = initApi.subjectName)
        sources.web1.complete(failed, replacement)
        sources.cached.complete(media(kind = MediaSourceKind.LocalCache, subjectName = initApi.subjectName))
        testScope().runCurrent()
        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == failed.mediaId })

        val handler = PlayerLoadErrorHandler(getPreferKind = { WEB }, getSourceTiers = { preferenceApi.sourceTiers!! })
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(replacement.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(failed.mediaId), handler.blacklist)
    }

    context(scope: TestScope)
    private fun testScope(): TestScope = implicit()
}
