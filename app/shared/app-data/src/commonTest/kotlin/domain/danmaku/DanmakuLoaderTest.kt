/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuMatchInfo
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class DanmakuLoaderTest {
    @Test
    fun `remote no match does not override local cache`() = runTest {
        val localResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val remoteResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val loader = createLoader(localResults, remoteResults)

        loader.fetchResultFlow.test {
            assertEquals(emptyList(), awaitItem())

            localResults.emit(listOf(cachedResult()))
            assertResultIds(listOf("cached"), awaitItem())

            remoteResults.emit(noMatchResults())
            expectNoEvents()
        }
    }

    @Test
    fun `remote matched result overrides local cache`() = runTest {
        val localResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val remoteResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val loader = createLoader(localResults, remoteResults)

        loader.fetchResultFlow.test {
            assertEquals(emptyList(), awaitItem())

            localResults.emit(listOf(cachedResult()))
            assertResultIds(listOf("cached"), awaitItem())

            remoteResults.emit(listOf(remoteResult()))
            assertResultIds(listOf("remote"), awaitItem())
        }
    }

    @Test
    fun `local cache can recover after remote no match emits first`() = runTest {
        val localResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val remoteResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val loader = createLoader(localResults, remoteResults)

        loader.fetchResultFlow.test {
            assertEquals(emptyList(), awaitItem())

            remoteResults.emit(noMatchResults())
            assertResultIds(emptyList(), awaitItem())

            localResults.emit(listOf(cachedResult()))
            assertResultIds(listOf("cached"), awaitItem())
        }
    }

    @Test
    fun `manual override survives resubscription`() = runTest {
        val localResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val remoteResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val loader = createLoader(localResults, remoteResults)

        loader.fetchResultFlow.test {
            assertEquals(emptyList(), awaitItem())

            remoteResults.emit(listOf(remoteResult()))
            assertResultIds(listOf("remote"), awaitItem())

            loader.overrideResults(DanmakuProviderId.Animeko, listOf(overrideResult()))
            assertResultIds(listOf("override"), awaitItem())
        }

        // UI 停止收集后重新订阅 (切换全屏、进出内嵌详情页等), 手动匹配的结果不应丢失
        loader.fetchResultFlow.test {
            awaitResultIds(listOf("override"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `manual override is dropped after switching episode`() = runTest {
        val localResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val remoteResults = MutableSharedFlow<List<DanmakuFetchResult>>()
        val requestFlow = MutableStateFlow(createRequest())
        val loader = createLoader(localResults, remoteResults, requestFlow)

        loader.fetchResultFlow.test {
            assertEquals(emptyList(), awaitItem())

            loader.overrideResults(DanmakuProviderId.Animeko, listOf(overrideResult()))
            assertResultIds(listOf("override"), awaitItem())

            requestFlow.value = createRequest(episodeId = OTHER_EPISODE_ID)
            awaitResultIds(emptyList())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.createLoader(
        localResults: Flow<List<DanmakuFetchResult>>,
        remoteResults: Flow<List<DanmakuFetchResult>>,
        requestFlow: Flow<SearchDanmakuRequest?> = MutableStateFlow(createRequest()),
    ): DanmakuLoaderImpl {
        return DanmakuLoaderImpl(
            requestFlow = requestFlow,
            flowScope = backgroundScope,
            fetchFromLocal = { localResults },
            fetchFromAllRemotes = { remoteResults },
            cacheDanmakuIfNeeded = { _, _, _ -> },
        )
    }

    private fun assertResultIds(expected: List<String>, actual: List<DanmakuFetchResult>?) {
        assertEquals(expected, actual.orEmpty().flatMap { result -> result.list.map { it.id } })
    }

    /**
     * 等待直到收到 [expected]. 切换剧集与重新订阅都可能先重放上一个状态, 这些中间值不是本测试关心的.
     */
    private suspend fun ReceiveTurbine<List<DanmakuFetchResult>?>.awaitResultIds(expected: List<String>) {
        repeat(10) {
            val actual = awaitItem().orEmpty().flatMap { result -> result.list.map { it.id } }
            if (actual == expected) return
        }
        fail("Expected $expected but never received it")
    }

    private fun createRequest(episodeId: Int = EPISODE_ID): SearchDanmakuRequest {
        return SearchDanmakuRequest(
            subjectInfo = SubjectInfo.createPlaceholder(SUBJECT_ID, "Subject", image = ""),
            episodeInfo = EpisodeInfo.Empty.copy(episodeId = episodeId, name = "Episode 1"),
            episodeId = episodeId,
        )
    }

    private fun cachedResult(): DanmakuFetchResult {
        return DanmakuFetchResult(
            providerId = DanmakuProviderId.Local,
            matchInfo = DanmakuMatchInfo(
                serviceId = DanmakuServiceId.Dandanplay,
                count = 1,
                method = DanmakuMatchMethod.ExactId(SUBJECT_ID, EPISODE_ID),
            ),
            list = listOf(danmakuInfo("cached", DanmakuServiceId.Dandanplay)),
        )
    }

    private fun remoteResult(): DanmakuFetchResult {
        return DanmakuFetchResult(
            providerId = DanmakuProviderId.Animeko,
            matchInfo = DanmakuMatchInfo(
                serviceId = DanmakuServiceId.Animeko,
                count = 1,
                method = DanmakuMatchMethod.ExactId(SUBJECT_ID, EPISODE_ID),
            ),
            list = listOf(danmakuInfo("remote", DanmakuServiceId.Animeko)),
        )
    }

    private fun overrideResult(): DanmakuFetchResult {
        return DanmakuFetchResult(
            providerId = DanmakuProviderId.Animeko,
            matchInfo = DanmakuMatchInfo(
                serviceId = DanmakuServiceId.Animeko,
                count = 1,
                method = DanmakuMatchMethod.ExactId(SUBJECT_ID, EPISODE_ID),
            ),
            list = listOf(danmakuInfo("override", DanmakuServiceId.Animeko)),
        )
    }

    private fun noMatchResults(): List<DanmakuFetchResult> {
        return listOf(
            DanmakuFetchResult.noMatch(DanmakuProviderId.Animeko, DanmakuServiceId.Animeko),
            DanmakuFetchResult.noMatch(DanmakuProviderId.Dandanplay, DanmakuServiceId.Dandanplay),
        )
    }

    private fun danmakuInfo(id: String, serviceId: DanmakuServiceId): DanmakuInfo {
        return DanmakuInfo(
            id = id,
            serviceId = serviceId,
            senderId = "sender",
            content = DanmakuContent(
                playTimeMillis = 0,
                color = 0,
                text = id,
                location = DanmakuLocation.NORMAL,
            ),
        )
    }

    private companion object {
        private const val SUBJECT_ID = 101
        private const val EPISODE_ID = 202
        private const val OTHER_EPISODE_ID = 203
    }
}
