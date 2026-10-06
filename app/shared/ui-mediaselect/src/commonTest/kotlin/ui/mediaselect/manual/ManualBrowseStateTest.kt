/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.manual

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.data.repository.media.ManualBrowseMemory
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.BlockedException
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.captcha.BrowserCookie
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowser
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.InterceptDecision
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.app.domain.mediasource.web.captcha.createTestWebSessionManager
import me.him188.ani.app.ui.mediafetch.TestBrowsableMediaSource
import me.him188.ani.app.ui.mediafetch.TestBrowseSubjects
import me.him188.ani.app.ui.mediafetch.createTestManualBrowseState
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ManualBrowseState] 的纯 Kotlin 单测: 用 [TestBrowsableMediaSource] 假源, 断言一律经 [ManualBrowseState.presentationFlow] 的 `first { }`
 * (订阅才会拉起上游), 不在动作之前假设 `presentationFlow.value` 已就绪.
 *
 * 状态的后台作用域用 [stateScope] 而不是 `TestScope.backgroundScope`: 后者的 supervisor 会把 `async` 子协程的失败当作测试失败上报,
 * 而 `play` 的 onPlay 抛异常正是要测的路径.
 */
@OptIn(TestOnly::class)
class ManualBrowseStateTest {
    private val target12 = ManualBrowseTarget(1, "命运石之门", EpisodeSort(12), "12")
    private val target25 = ManualBrowseTarget(1, "命运石之门", EpisodeSort(25), "25")

    private val TestScope.stateScope: CoroutineScope
        get() = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))

    private fun TestScope.createState(
        source: MediaSource = TestBrowsableMediaSource(),
        target: ManualBrowseTarget? = target12,
        onPlay: suspend (ManualBrowsePick, ManualBrowseMemory?) -> Unit = { _, _ -> },
        webSessionManager: WebSessionManager = createTestWebSessionManager(backgroundScope),
        rememberSelection: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): ManualBrowseState = createTestManualBrowseState(
        stateScope,
        source = source,
        target = target,
        onPlay = onPlay,
        webSessionManager = webSessionManager,
        rememberSelection = rememberSelection,
    )

    private suspend fun ManualBrowseState.awaitResults(): ManualLoadState<List<BrowseSubject>> =
        presentationFlow.first { it.results is ManualLoadState.Success || it.results is ManualLoadState.Failed }.results

    private suspend fun ManualBrowseState.awaitChannels(): ManualBrowsePresentation =
        presentationFlow.first { it.channels is ManualLoadState.Success || it.channels is ManualLoadState.Failed }

    private fun captchaException(mediaSourceId: String = "test-browse") = BlockedException(
        reason = BlockReason.Captcha(WebCaptchaKind.Cloudflare),
        request = SolveRequest(
            mediaSourceId = mediaSourceId,
            pageUrl = "https://example.com/search",
            kind = WebCaptchaKind.Cloudflare,
            expectation = PageExpectation.AnyContent,
        ),
    )

    // region 默认源与关键字

    @Test
    fun `default source prefers preferred id then first and explicit choice falls back when it disappears`() = runTest {
        val a = createTestMediaSourceInstance(TestBrowsableMediaSource("a"), instanceId = "ia")
        val b = createTestMediaSourceInstance(TestBrowsableMediaSource("b"), instanceId = "ib")
        val sources = MutableStateFlow(listOf(a, b))
        val preferred = MutableStateFlow<String?>("b")
        val state = ManualBrowseState(
            browsableSources = sources,
            webSessionManager = createTestWebSessionManager(backgroundScope),
            target = flowOf(target12),
            preferredSourceId = preferred,
            rememberSelection = flowOf(true),
            onRememberSelectionChange = {},
            onPlay = { _, _ -> },
            backgroundScope = stateScope,
        )

        assertEquals("ib", state.presentationFlow.first { it.sources.isNotEmpty() }.selectedSourceId)

        preferred.value = null
        assertEquals("ia", state.presentationFlow.first { it.selectedSourceId == "ia" }.selectedSourceId)

        state.selectSource("ib")
        assertEquals("ib", state.presentationFlow.first { it.selectedSourceId == "ib" }.selectedSourceId)

        sources.value = listOf(a)
        val presentation = state.presentationFlow.first { it.sources.size == 1 }
        assertEquals("ia", presentation.selectedSourceId)
        assertEquals(listOf("ia"), presentation.sources.map { it.instanceId })
    }

    @Test
    fun `no browsable source yields null selection`() = runTest {
        val state = ManualBrowseState(
            browsableSources = flowOf(emptyList()),
            webSessionManager = createTestWebSessionManager(backgroundScope),
            target = flowOf(target12),
            preferredSourceId = flowOf(null),
            rememberSelection = flowOf(true),
            onRememberSelectionChange = {},
            onPlay = { _, _ -> },
            backgroundScope = stateScope,
        )
        val presentation = state.presentationFlow.first { it.target != null }
        assertNull(presentation.selectedSourceId)
        assertTrue(presentation.sources.isEmpty())
    }

    @Test
    fun `keyword defaults to target subject name until user types`() = runTest {
        val state = createState()
        assertEquals("命运石之门", state.presentationFlow.first { it.target != null }.keyword)

        state.setKeyword("石头门")
        assertEquals("石头门", state.presentationFlow.first { it.keyword == "石头门" }.keyword)
    }

    // endregion

    // region 搜索

    @Test
    fun `search goes loading then success with the effective keyword`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val keywords = mutableListOf<String>()
        val state = createState(
            source = TestBrowsableMediaSource(
                searchDelegate = { keyword ->
                    keywords += keyword
                    gate.await()
                    TestBrowseSubjects
                },
            ),
        )

        state.search()
        assertIs<ManualLoadState.Loading>(state.presentationFlow.first { it.results !is ManualLoadState.Idle }.results)

        gate.complete(Unit)
        val success = assertIs<ManualLoadState.Success<List<BrowseSubject>>>(state.awaitResults())
        assertEquals(TestBrowseSubjects, success.value)
        assertEquals(listOf("命运石之门"), keywords)
    }

    @Test
    fun `search failure is exposed as Failed without captcha flag`() = runTest {
        val state = createState(
            source = TestBrowsableMediaSource(searchDelegate = { throw IllegalStateException("boom") }),
        )
        state.search()
        val failed = assertIs<ManualLoadState.Failed>(state.awaitResults())
        assertIs<IllegalStateException>(failed.error)
        assertFalse(failed.captchaUnsupported)
    }

    @Test
    fun `search with blank keyword does nothing`() = runTest {
        var calls = 0
        val state = createState(
            source = TestBrowsableMediaSource(searchDelegate = { calls++; TestBrowseSubjects }),
        )
        state.setKeyword("   ")
        state.search()
        advanceUntilIdle()
        assertIs<ManualLoadState.Idle>(state.presentationFlow.first { it.target != null }.results)
        assertEquals(0, calls)
    }

    @Test
    fun `empty search result is Success with empty list`() = runTest {
        val state = createState(source = TestBrowsableMediaSource(searchDelegate = { emptyList() }))
        state.search()
        val success = assertIs<ManualLoadState.Success<List<BrowseSubject>>>(state.awaitResults())
        assertTrue(success.value.isEmpty())
    }

    @Test
    fun `retry re-runs a failed search`() = runTest {
        var calls = 0
        val state = createState(
            source = TestBrowsableMediaSource(
                searchDelegate = {
                    calls++
                    if (calls == 1) throw IllegalStateException("first fails") else TestBrowseSubjects
                },
            ),
        )
        state.search()
        assertIs<ManualLoadState.Failed>(state.awaitResults())

        state.retry()
        val success = assertIs<ManualLoadState.Success<List<BrowseSubject>>>(
            state.presentationFlow.first { it.results is ManualLoadState.Success }.results,
        )
        assertEquals(4, success.value.size)
        assertEquals(2, calls)
    }

    /*
     * 状态的请求跑在 backgroundScope 上, TestScope.advanceUntilIdle 不等它们: 要等请求发生用 `first { }` 挂起,
     * 断言「没有再请求」前用 runCurrent (会执行当前时刻所有待办, 含后台).
     */

    @Test
    fun `searchIfNeeded searches once with the effective keyword`() = runTest {
        val keywords = MutableStateFlow(listOf<String>())
        val state = createState(source = TestBrowsableMediaSource(searchDelegate = { k -> keywords.update { it + k }; TestBrowseSubjects }))

        state.searchIfNeeded()
        state.searchIfNeeded()
        assertIs<ManualLoadState.Success<List<BrowseSubject>>>(state.awaitResults())
        state.searchIfNeeded()
        runCurrent()
        assertEquals(listOf("命运石之门"), keywords.value)
    }

    @Test
    fun `search always sends a new request even with the same keyword`() = runTest {
        val calls = MutableStateFlow(0)
        val state = createState(source = TestBrowsableMediaSource(searchDelegate = { calls.update { it + 1 }; TestBrowseSubjects }))
        state.search()
        state.awaitResults()
        state.search()
        calls.first { it == 2 }
    }

    private fun TestScope.twoSourceState(
        searchA: suspend (String) -> List<BrowseSubject>,
        searchB: suspend (String) -> List<BrowseSubject>,
    ): ManualBrowseState = ManualBrowseState(
        browsableSources = flowOf(
            listOf(
                createTestMediaSourceInstance(TestBrowsableMediaSource("a", searchDelegate = searchA), instanceId = "ia"),
                createTestMediaSourceInstance(TestBrowsableMediaSource("b", searchDelegate = searchB), instanceId = "ib"),
            ),
        ),
        webSessionManager = createTestWebSessionManager(backgroundScope),
        target = flowOf(target12),
        preferredSourceId = flowOf(null),
        rememberSelection = flowOf(true),
        onRememberSelectionChange = {},
        onPlay = { _, _ -> },
        backgroundScope = stateScope,
    )

    @Test
    fun `results are cached per source and switching back does not search again`() = runTest {
        val a = MutableStateFlow(listOf<String>())
        val b = MutableStateFlow(listOf<String>())
        val state = twoSourceState(
            searchA = { k -> a.update { it + k }; TestBrowseSubjects.take(1) },
            searchB = { k -> b.update { it + k }; TestBrowseSubjects.take(2) },
        )

        state.searchIfNeeded()
        assertEquals(1, assertIs<ManualLoadState.Success<List<BrowseSubject>>>(state.awaitResults()).value.size)

        // 换源自动搜索新源
        state.selectSource("ib")
        val onB = state.presentationFlow.first { it.selectedSourceId == "ib" && it.results is ManualLoadState.Success }
        assertEquals(2, assertIs<ManualLoadState.Success<List<BrowseSubject>>>(onB.results).value.size)

        // 切回来直接显示缓存
        state.selectSource("ia")
        val onA = state.presentationFlow.first { it.selectedSourceId == "ia" }
        assertEquals(1, assertIs<ManualLoadState.Success<List<BrowseSubject>>>(onA.results).value.size)
        runCurrent()
        assertEquals(listOf("命运石之门"), a.value)
        assertEquals(listOf("命运石之门"), b.value)
    }

    @Test
    fun `a new keyword re-searches a cached source when switching back`() = runTest {
        val a = MutableStateFlow(listOf<String>())
        val b = MutableStateFlow(listOf<String>())
        val state = twoSourceState(
            searchA = { k -> a.update { it + k }; TestBrowseSubjects },
            searchB = { k -> b.update { it + k }; TestBrowseSubjects },
        )
        state.searchIfNeeded()
        state.awaitResults()

        state.selectSource("ib")
        state.presentationFlow.first { it.selectedSourceId == "ib" && it.results is ManualLoadState.Success }
        state.setKeyword("石头门")
        state.search()
        b.first { it == listOf("命运石之门", "石头门") }

        state.selectSource("ia")
        a.first { it == listOf("命运石之门", "石头门") }
    }

    @Test
    fun `a failed source is not searched again on switch until retry`() = runTest {
        val aCalls = MutableStateFlow(0)
        val state = twoSourceState(
            searchA = { aCalls.update { it + 1 }; throw IllegalStateException("boom") },
            searchB = { TestBrowseSubjects },
        )
        state.searchIfNeeded()
        assertIs<ManualLoadState.Failed>(state.awaitResults())

        state.selectSource("ib")
        state.presentationFlow.first { it.selectedSourceId == "ib" && it.results is ManualLoadState.Success }
        state.selectSource("ia")
        assertIs<ManualLoadState.Failed>(state.presentationFlow.first { it.selectedSourceId == "ia" }.results)
        runCurrent()
        assertEquals(1, aCalls.value)

        state.retry()
        aCalls.first { it == 2 }
    }

    @Test
    fun `switching away does not cancel the previous source search`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val aCalls = MutableStateFlow(0)
        val state = twoSourceState(
            searchA = { aCalls.update { it + 1 }; gate.await(); TestBrowseSubjects.take(1) },
            searchB = { TestBrowseSubjects },
        )
        state.searchIfNeeded()
        state.presentationFlow.first { it.results is ManualLoadState.Loading }

        state.selectSource("ib")
        state.presentationFlow.first { it.selectedSourceId == "ib" && it.results is ManualLoadState.Success }
        gate.complete(Unit)

        state.selectSource("ia")
        val onA = state.presentationFlow.first { it.selectedSourceId == "ia" && it.results is ManualLoadState.Success }
        assertEquals(1, assertIs<ManualLoadState.Success<List<BrowseSubject>>>(onA.results).value.size)
        runCurrent()
        assertEquals(1, aCalls.value)
    }

    // endregion

    // region 验证码

    @Test
    fun `captcha without interactive support yields Failed with captchaUnsupported`() = runTest {
        var calls = 0
        val state = createState(
            source = TestBrowsableMediaSource(searchDelegate = { calls++; throw captchaException() }),
        )
        state.search()
        val failed = assertIs<ManualLoadState.Failed>(state.awaitResults())
        assertTrue(failed.captchaUnsupported)
        assertIs<BlockedException>(failed.error)
        assertEquals(1, calls)
    }

    @Test
    fun `captcha solved interactively retries the same request once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val source = TestBrowsableMediaSource(
                searchDelegate = {
                    calls++
                    if (calls == 1) throw captchaException() else TestBrowseSubjects
                },
            )
            val factory = SolvingCaptchaBrowserFactory()
            val manager = WebSessionManager(
                browserFactory = factory,
                evaluator = PageEvaluator(),
                cookieJar = WebSourceCookieJar(),
                identityRegistry = WebSourceIdentityRegistry(),
                client = createDefaultHttpClient().asScopedHttpClient(),
                backgroundScope = backgroundScope,
                ioContext = EmptyCoroutineContext,
            )
            val state = createState(source = source, webSessionManager = manager)

            state.search()
            val success = assertIs<ManualLoadState.Success<List<BrowseSubject>>>(state.awaitResults())
            assertEquals(4, success.value.size)
            assertEquals(2, calls)
            assertEquals(1, factory.createCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `captcha that stays unsolved yields Failed without retrying`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val source = TestBrowsableMediaSource(searchDelegate = { calls++; throw captchaException() })
            val manager = WebSessionManager(
                browserFactory = SolvingCaptchaBrowserFactory(solves = false),
                evaluator = PageEvaluator(),
                cookieJar = WebSourceCookieJar(),
                identityRegistry = WebSourceIdentityRegistry(),
                client = createDefaultHttpClient().asScopedHttpClient(),
                backgroundScope = backgroundScope,
                ioContext = EmptyCoroutineContext,
            )
            val state = createState(source = source, webSessionManager = manager)

            state.search()
            // 对话框弹出后用户关闭它
            val ui = manager.interactiveUi.first { it != null }!!
            ui.onDismiss()
            val failed = assertIs<ManualLoadState.Failed>(state.awaitResults())
            assertFalse(failed.captchaUnsupported)
            assertEquals(1, calls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // endregion

    // region 打开条目与选择

    @Test
    fun `openSubject loads channels and preselects the episode matching target sort`() = runTest {
        val state = createState(target = target12)
        state.search()
        state.awaitResults()

        state.openSubject(TestBrowseSubjects[0])
        assertIs<ManualLoadState.Loading>(state.presentationFlow.first { it.openedSubject != null }.channels)
        val presentation = state.awaitChannels()
        assertEquals(TestBrowseSubjects[0], presentation.openedSubject)
        assertEquals(0, presentation.selectedChannelIndex)
        assertEquals(11, presentation.selectedEpisodeIndex)
        assertEquals("12", presentation.selectedEpisode?.name)
    }

    @Test
    fun `no episode is preselected when target sort is absent`() = runTest {
        val state = createState(target = target25)
        state.openSubject(TestBrowseSubjects[0])
        val presentation = state.awaitChannels()
        assertNull(presentation.selectedEpisodeIndex)
        assertNull(presentation.selectedEpisode)
    }

    @Test
    fun `selectChannel clears user episode choice and re-preselects`() = runTest {
        val state = createState(target = target12)
        state.openSubject(TestBrowseSubjects[0])
        state.awaitChannels()

        assertEquals(true, state.play(3))
        assertEquals(3, state.presentationFlow.first { it.selectedEpisodeIndex == 3 }.selectedEpisodeIndex)

        state.selectChannel(2)
        val presentation = state.presentationFlow.first { it.selectedChannelIndex == 2 }
        assertEquals("线路3", presentation.selectedChannel?.name)
        assertEquals(11, presentation.selectedEpisodeIndex)
    }

    @Test
    fun `closeSubject returns to first page and keeps results`() = runTest {
        val state = createState()
        state.search()
        state.awaitResults()
        state.openSubject(TestBrowseSubjects[1])
        state.awaitChannels()

        state.closeSubject()
        val presentation = state.presentationFlow.first { it.openedSubject == null }
        assertIs<ManualLoadState.Idle>(presentation.channels)
        assertIs<ManualLoadState.Success<List<BrowseSubject>>>(presentation.results)
    }

    @Test
    fun `selectSource closes the opened subject and keeps the cached results`() = runTest {
        var calls = 0
        val state = createState(source = TestBrowsableMediaSource(searchDelegate = { calls++; TestBrowseSubjects }))
        state.search()
        state.awaitResults()
        state.openSubject(TestBrowseSubjects[1])
        val opened = state.awaitChannels()
        val sourceId = assertNotNull(opened.selectedSourceId)

        state.selectSource(sourceId)
        val presentation = state.presentationFlow.first { it.openedSubject == null }
        assertIs<ManualLoadState.Idle>(presentation.channels)
        assertIs<ManualLoadState.Success<List<BrowseSubject>>>(presentation.results)
        assertEquals(sourceId, presentation.selectedSourceId)
        runCurrent()
        assertEquals(1, calls)
    }

    @Test
    fun `retry re-runs a failed browse when a subject is opened`() = runTest {
        var calls = 0
        val state = createState(
            source = TestBrowsableMediaSource(
                channels = {
                    calls++
                    if (calls == 1) throw IllegalStateException("first fails") else TestBrowsableMediaSource().channels(it)
                },
            ),
        )
        state.openSubject(TestBrowseSubjects[0])
        assertIs<ManualLoadState.Failed>(state.awaitChannels().channels)

        state.retry()
        val presentation = state.presentationFlow.first { it.channels is ManualLoadState.Success }
        assertEquals(3, (presentation.channels as ManualLoadState.Success).value.size)
        assertEquals(2, calls)
    }

    // endregion

    // region 播放

    @Test
    fun `clicking an episode plays it at once and remembers channel and position`() = runTest {
        var captured: Pair<Media, ManualBrowseMemory?>? = null
        val state = createState(target = target25, onPlay = { pick, memory -> captured = pick.media to memory })
        state.openSubject(TestBrowseSubjects[0])
        state.awaitChannels()

        assertEquals(true, state.play(25)) // SP

        val (media, memory) = assertNotNull(captured)
        assertNotNull(memory)
        assertEquals("test-browse", memory.mediaSourceId)
        assertEquals(TestBrowseSubjects[0], memory.subject)
        assertEquals(0, memory.channelIndex)
        assertEquals("线路1", memory.channelName)
        assertEquals(25, memory.episodeIndex)
        assertEquals(EpisodeSort(25), memory.playedAsSort)
        assertEquals(EpisodeRange.single(EpisodeSort(25)), media.episodeRange)
        val presentation = state.presentationFlow.first { !it.isPlaying }
        assertEquals(25, presentation.selectedEpisodeIndex)
    }

    @Test
    fun `pick creates resources for other episodes by position in the channel`() = runTest {
        var captured: ManualBrowsePick? = null
        val state = createState(target = target12, onPlay = { pick, _ -> captured = pick })
        state.openSubject(TestBrowseSubjects[0])
        state.awaitChannels()

        // 线路 1 的第 3 项 ("04") 被当作第 12 集
        assertEquals(true, state.play(3))
        val pick = assertNotNull(captured)
        assertEquals(EpisodeSort(12), pick.pickedAs)
        assertEquals("https://example.com/play/1/4", pick.media.originalUrl)

        val next = assertNotNull(pick.createMediaFor(EpisodeSort(13)))
        assertEquals("https://example.com/play/1/5", next.originalUrl)
        assertEquals(EpisodeRange.single(EpisodeSort(13)), next.episodeRange)
        assertEquals("https://example.com/play/1/1", pick.createMediaFor(EpisodeSort(9))?.originalUrl)
        // 越界, 或编号类型不同, 都推算不出
        assertNull(pick.createMediaFor(EpisodeSort(8)))
        assertNull(pick.createMediaFor(EpisodeSort("SP")))
    }

    @Test
    fun `episode clicked for a previous target no longer overrides the preselection`() = runTest {
        val target = MutableStateFlow<ManualBrowseTarget?>(target12)
        val state = ManualBrowseState(
            browsableSources = flowOf(listOf(createTestMediaSourceInstance(TestBrowsableMediaSource()))),
            webSessionManager = createTestWebSessionManager(backgroundScope),
            target = target,
            preferredSourceId = flowOf(null),
            rememberSelection = MutableStateFlow(true),
            onRememberSelectionChange = {},
            onPlay = { _, _ -> },
            backgroundScope = stateScope,
        )
        state.openSubject(TestBrowseSubjects[0])
        state.awaitChannels()

        assertEquals(true, state.play(3))
        assertEquals(3, state.presentationFlow.first { !it.isPlaying }.selectedEpisodeIndex)

        // 回放把下一集切过来: 上一集的点选不再算数, 按当前集号预选
        target.value = ManualBrowseTarget(1, "命运石之门", EpisodeSort(13), "13")
        val presentation = state.presentationFlow.first { it.target?.episodeSort == EpisodeSort(13) }
        assertEquals(12, presentation.selectedEpisodeIndex)
        assertEquals("13", presentation.selectedEpisode?.name)
    }

    @Test
    fun `remember switch off plays without memory`() = runTest {
        var captured: Pair<Media, ManualBrowseMemory?>? = null
        val remember = MutableStateFlow(true)
        val state = createState(target = target12, onPlay = { pick, memory -> captured = pick.media to memory }, rememberSelection = remember)
        state.openSubject(TestBrowseSubjects[0])
        state.awaitChannels()

        state.setRememberSelection(false)
        assertFalse(state.presentationFlow.first { !it.rememberSelection }.rememberSelection)
        assertFalse(remember.value)

        assertEquals(true, state.play(11))
        val (media, memory) = assertNotNull(captured)
        assertNull(memory)
        assertEquals(EpisodeRange.single(EpisodeSort(12)), media.episodeRange)
    }

    @Test
    fun `play returns false before a subject is opened or for a missing episode`() = runTest {
        var calls = 0
        val state = createState(target = target25, onPlay = { _, _ -> calls++ })
        assertEquals(false, state.play(0))

        state.openSubject(TestBrowseSubjects[0])
        state.awaitChannels()
        assertEquals(false, state.play(999))
        assertEquals(0, calls)
    }

    @Test
    fun `play returns false when target is missing`() = runTest {
        var calls = 0
        val state = createState(target = null, onPlay = { _, _ -> calls++ })
        state.openSubject(TestBrowseSubjects[0])
        state.awaitChannels()
        assertEquals(false, state.play(0))
        assertEquals(0, calls)
    }

    @Test
    fun `play returns false when onPlay throws and resets isPlaying`() = runTest {
        val state = createState(target = target12, onPlay = { _, _ -> throw IllegalStateException("select failed") })
        state.openSubject(TestBrowseSubjects[0])
        state.awaitChannels()

        assertEquals(false, state.play(11))
        val presentation = state.presentationFlow.first { !it.isPlaying }
        assertEquals(TestBrowseSubjects[0], presentation.openedSubject)
        assertEquals(11, presentation.selectedEpisodeIndex)
    }

    @Test
    fun `second play while the first is in flight is ignored immediately`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val state = createState(target = target12, onPlay = { _, _ -> calls++; gate.await() })
        state.openSubject(TestBrowseSubjects[0])
        state.awaitChannels()

        val first = async { state.play(11) }
        assertTrue(state.presentationFlow.first { it.isPlaying }.isPlaying)
        assertNull(state.play(3), "已有进行中的播放: 被忽略, 不是失败")

        gate.complete(Unit)
        assertEquals(true, first.await())
        assertFalse(state.presentationFlow.first { !it.isPlaying }.isPlaying)
        assertEquals(1, calls)

        // 上一次完成后可以再次播放
        assertEquals(true, state.play(3))
        assertEquals(2, calls)
    }

    // endregion
}

/**
 * 支持交互解决的浏览器工厂: 导航到验证页后立刻给出可解析的页面, 交互 solve 随即成功.
 * [solves] = false 时页面保持为验证页, 等待用户关闭对话框.
 */
private class SolvingCaptchaBrowserFactory(
    private val solves: Boolean = true,
) : CaptchaBrowserFactory {
    var createCount = 0
        private set

    override val isSupported: Boolean get() = true

    override suspend fun create(): CaptchaBrowser {
        createCount++
        return SolvingCaptchaBrowser(solves)
    }
}

private class SolvingCaptchaBrowser(
    private val solves: Boolean,
) : CaptchaBrowser {
    override val userAgent: String get() = "SolvingBrowser/1.0"

    private val _pageLoads = MutableSharedFlow<LoadedPage>(replay = 1)
    override val pageLoads: SharedFlow<LoadedPage> get() = _pageLoads

    override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)

    private var current: LoadedPage? = null

    override suspend fun navigate(url: String) {
        val page = if (solves) {
            LoadedPage(url, "<html><body><div class=\"content\">solved</div></body></html>")
        } else {
            LoadedPage(url, "<html><title>Just a moment...</title><div id=\"challenge-error-text\">Enable JavaScript and cookies to continue</div></html>")
        }
        current = page
        _pageLoads.emit(page)
    }

    override suspend fun currentPage(): LoadedPage? = current

    override suspend fun executeJavaScript(script: String) {
    }

    override suspend fun collectCookies(urls: List<String>): List<BrowserCookie> = emptyList()

    override fun setResourceInterceptor(handler: ((url: String) -> InterceptDecision)?) {
    }

    @Composable
    override fun View(modifier: Modifier) {
    }

    override fun close() {
    }
}
