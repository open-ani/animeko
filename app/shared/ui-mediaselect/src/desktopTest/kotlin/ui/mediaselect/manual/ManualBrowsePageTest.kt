/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.manual

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.repository.media.ManualBrowseMemory
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.web.captcha.createTestWebSessionManager
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcher
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_manual_no_sources
import me.him188.ani.app.ui.lang.media_selector_manual_play_as_next
import me.him188.ani.app.ui.lang.media_selector_manual_result_count
import me.him188.ani.app.ui.mediafetch.TestBrowsableMediaSource
import me.him188.ani.app.ui.mediafetch.TestBrowseSubjects
import me.him188.ani.app.ui.mediafetch.createTestManualBrowseState
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorChromeTestTags
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ManualBrowsePage] 的交互测试: 堆叠版式 (400dp 宽) 与双栏版式 (900dp 宽) 各走一遍搜索 → 打开条目 → 播放.
 */
@OptIn(TestOnly::class)
class ManualBrowsePageTest {
    private val target = ManualBrowseTarget(1, "命运石之门", EpisodeSort(12), "12")
    private val topBarText = "host-top-bar"

    private class Harness {
        lateinit var state: ManualBrowseState
        var played = 0
        val plays = mutableListOf<ManualBrowseMemory?>()
    }

    /**
     * @param backDispatcher 替换预览环境的返回键分发器, 测试用它触发系统返回.
     * @param createState 默认用 [createTestManualBrowseState]; 占位态测试直接构造 [ManualBrowseState].
     */
    private fun ComposeUiTest.setPage(
        width: Int,
        height: Int,
        source: MediaSource = TestBrowsableMediaSource(),
        inlineTitle: Boolean = false,
        backDispatcher: OnBackPressedDispatcher? = null,
        createState: (scope: CoroutineScope, harness: Harness) -> ManualBrowseState = { scope, harness ->
            createTestManualBrowseState(
                scope,
                source = source,
                target = target,
                onPlay = { _, memory -> harness.plays += memory },
            )
        },
    ): Harness {
        val harness = Harness()
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberBackgroundScope()
                val state = remember { createState(scope.backgroundScope, harness).also { harness.state = it } }
                val page = @Composable {
                    Box(Modifier.size(width.dp, height.dp)) {
                        ManualBrowsePage(
                            state,
                            watching = WatchingEpisode("12", "Episode Twelve"),
                            onPlayed = { harness.played++ },
                            topBar = { Text(topBarText) },
                            closeButton = { Text("close") },
                            inlineTitle = if (inlineTitle) {
                                { Text("inline-title") }
                            } else {
                                null
                            },
                        )
                    }
                }
                if (backDispatcher == null) {
                    page()
                } else {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val backOwner = remember(backDispatcher) {
                        object : OnBackPressedDispatcherOwner {
                            override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher
                            override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
                        }
                    }
                    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) { page() }
                }
            }
        }
        return harness
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `stacked layout searches, opens subject and plays with memory`() = runAniComposeUiTest {
        val harness = setPage(width = 400, height = 800)

        onNodeWithText(topBarText).assertExists()
        onNodeWithTag(MediaSelectorChromeTestTags.WATCHING).assertExists()
        onNodeWithTag(ManualBrowsePageTestTags.SEARCH_FIELD).performImeAction()

        awaitTag(ManualBrowsePageTestTags.result(0))
        onNodeWithText(runBlocking { getString(Lang.media_selector_manual_result_count, 4) }).assertExists()
        onNodeWithTag(ManualBrowsePageTestTags.result(0)).performClick()

        awaitTag(ManualBrowsePageTestTags.episode(11))
        onNodeWithText(topBarText).assertDoesNotExist()
        onNodeWithTag(ManualBrowsePageTestTags.BACK).assertExists()
        onNodeWithText("close").assertExists()
        onNodeWithTag(ManualBrowsePageTestTags.channelChip(0)).assertIsSelected()
        onNodeWithTag(ManualBrowsePageTestTags.episode(11)).assertIsSelected()
        onNodeWithText(runBlocking { getString(Lang.media_selector_manual_play_as_next, "12", "13") }).assertExists()

        onNodeWithTag(ManualBrowsePageTestTags.PLAY_REMEMBER).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 10_000) { harness.played == 1 }
        runOnIdle {
            val memory = assertNotNull(harness.plays.single())
            assertEquals(11, memory.episodeIndex)
            assertEquals(EpisodeSort(12), memory.playedAsSort)
        }
    }

    @Test
    fun `stacked layout back returns to results`() = runAniComposeUiTest {
        val harness = setPage(width = 400, height = 800)
        harness.state.search()
        awaitTag(ManualBrowsePageTestTags.result(1))
        onNodeWithTag(ManualBrowsePageTestTags.result(1)).performClick()
        awaitTag(ManualBrowsePageTestTags.BACK)

        onNodeWithTag(ManualBrowsePageTestTags.BACK).performClick()
        awaitTag(ManualBrowsePageTestTags.result(1))
        onNodeWithText(topBarText).assertExists()
        onNodeWithTag(ManualBrowsePageTestTags.BACK).assertDoesNotExist()
    }

    @Test
    fun `stacked layout system back returns to results and first page leaves back to the host`() = runAniComposeUiTest {
        var hostBack = 0
        val dispatcher = OnBackPressedDispatcher(fallbackOnBackPressed = { hostBack++ })
        val harness = setPage(width = 400, height = 800, backDispatcher = dispatcher)
        harness.state.search()
        awaitTag(ManualBrowsePageTestTags.result(1))
        onNodeWithTag(ManualBrowsePageTestTags.result(1)).performClick()
        awaitTag(ManualBrowsePageTestTags.BACK)

        runOnIdle { dispatcher.onBackPressed() }

        awaitTag(ManualBrowsePageTestTags.result(1))
        onNodeWithText(topBarText).assertExists()
        onNodeWithTag(ManualBrowsePageTestTags.BACK).assertDoesNotExist()
        runOnIdle { assertEquals(0, hostBack, "第二页拦截返回键, 不交给宿主") }

        // 第一页不拦截: 返回键交给宿主关闭容器
        runOnIdle { dispatcher.onBackPressed() }
        runOnIdle { assertEquals(1, hostBack) }
        onNodeWithTag(ManualBrowsePageTestTags.result(1)).assertExists()
    }

    @Test
    fun `placeholder shows no hint and a really empty source list shows the no sources hint`() = runAniComposeUiTest {
        val noSourcesText = runBlocking { getString(Lang.media_selector_manual_no_sources) }
        // 源列表尚未发射: 组合流不发射, presentationFlow 停留在 Empty 占位, 不能当成「没有支持浏览的数据源」
        val sources = MutableSharedFlow<List<MediaSourceInstance>>(replay = 1)
        val harness = setPage(
            width = 400, height = 800,
            createState = { scope, _ -> createPageState(scope, browsableSources = sources) },
        )
        onNodeWithText(topBarText).assertExists()
        onNodeWithTag(ManualBrowsePageTestTags.SEARCH_FIELD).assertExists()
        runOnIdle { assertTrue(harness.state.presentationFlow.value.isPlaceholder) }
        onNodeWithText(noSourcesText).assertDoesNotExist()

        // 源列表就绪且确实为空: 显示提示
        runOnIdle { assertTrue(sources.tryEmit(emptyList())) }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(noSourcesText)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun createPageState(
        scope: CoroutineScope,
        browsableSources: Flow<List<MediaSourceInstance>>,
    ) = ManualBrowseState(
        browsableSources = browsableSources,
        webSessionManager = createTestWebSessionManager(scope),
        target = flowOf(target),
        preferredSourceId = flowOf(null),
        onPlay = { _, _ -> },
        backgroundScope = scope,
    )

    @Test
    fun `wide layout shows both columns and plays temporarily`() = runAniComposeUiTest {
        val harness = setPage(width = 900, height = 600, inlineTitle = true)

        onNodeWithText("inline-title").assertExists()
        onNodeWithText(topBarText).assertDoesNotExist()
        onNodeWithTag(MediaSelectorChromeTestTags.WATCHING).assertExists()

        harness.state.search()
        awaitTag(ManualBrowsePageTestTags.result(0))
        onNodeWithTag(ManualBrowsePageTestTags.result(0)).performClick()
        awaitTag(ManualBrowsePageTestTags.episode(0))

        // 双栏: 结果列表与剧集网格同时可见, 没有返回按钮
        onNodeWithTag(ManualBrowsePageTestTags.result(0)).assertExists()
        onNodeWithTag(ManualBrowsePageTestTags.BACK).assertDoesNotExist()
        onNodeWithText("inline-title").assertExists()

        onNodeWithTag(ManualBrowsePageTestTags.episode(2)).performClick()
        onNodeWithTag(ManualBrowsePageTestTags.episode(2)).assertIsSelected()
        onNodeWithTag(ManualBrowsePageTestTags.PLAY_TEMPORARY).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 10_000) { harness.played == 1 }
        runOnIdle {
            assertTrue(harness.plays.size == 1)
            assertNull(harness.plays.single())
        }
    }

    @Test
    fun `wide layout without inline title renders host top bar`() = runAniComposeUiTest {
        setPage(width = 900, height = 600, inlineTitle = false)
        onNodeWithText(topBarText).assertExists()
        onNodeWithTag(MediaSelectorChromeTestTags.WATCHING).assertExists()
    }

    @Test
    fun `failed search shows retry which reloads`() = runAniComposeUiTest {
        var calls = 0
        val harness = setPage(
            width = 400,
            height = 800,
            source = TestBrowsableMediaSource(
                searchDelegate = {
                    calls++
                    if (calls == 1) throw IllegalStateException("first fails") else TestBrowseSubjects
                },
            ),
        )
        harness.state.search()
        awaitTag(ManualBrowsePageTestTags.RETRY)
        onNodeWithTag(ManualBrowsePageTestTags.RETRY).performClick()
        awaitTag(ManualBrowsePageTestTags.result(0))
        onNodeWithTag(ManualBrowsePageTestTags.RETRY).assertDoesNotExist()
    }

    @Test
    fun `play buttons are disabled without a selected episode`() = runAniComposeUiTest {
        val harness = setPage(
            width = 400,
            height = 800,
            source = TestBrowsableMediaSource(
                channels = { TestBrowsableMediaSource().channels(it).map { channel -> channel.copy(episodes = channel.episodes.drop(12)) } },
            ),
        )
        harness.state.openSubject(TestBrowseSubjects[0])
        awaitTag(ManualBrowsePageTestTags.PLAY_REMEMBER)
        onNodeWithTag(ManualBrowsePageTestTags.PLAY_REMEMBER).assertIsNotEnabled()
        onNodeWithTag(ManualBrowsePageTestTags.PLAY_TEMPORARY).assertIsNotEnabled()

        onNodeWithTag(ManualBrowsePageTestTags.episode(0)).performClick()
        onNodeWithTag(ManualBrowsePageTestTags.PLAY_REMEMBER).assertIsEnabled()
    }
}
