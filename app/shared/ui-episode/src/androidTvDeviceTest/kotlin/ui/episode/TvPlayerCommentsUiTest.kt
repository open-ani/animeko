/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.platform.app.InstrumentationRegistry
import com.github.panpf.sketch.Sketch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import me.him188.ani.leanback.ui.watchtogether.TvTogetherState
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlayerCommentsUiTest {
    private class Fixture(
        val comments: List<EpisodeComment>,
        val pager: Flow<PagingData<EpisodeComment>> = flowOf(PagingData.from(comments)),
    ) {
        val commands = mutableListOf<TvPlaybackCommand>()
        val machine = TvPlayerPresentationState({ TvPlaybackSnapshot(true, 20_000, 60_000) }, commands::add)
        lateinit var backDispatcher: OnBackPressedDispatcher
    }

    @Test
    fun loadingAndEmptyPanelsCanCloseWithoutAFocusableRoot() = runAniComposeUiTest {
        val loaded = CompletableDeferred<Unit>()
        val pager = Pager(PagingConfig(pageSize = 1)) {
            object : PagingSource<Int, EpisodeComment>() {
                override fun getRefreshKey(state: PagingState<Int, EpisodeComment>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, EpisodeComment> {
                    loaded.await()
                    return LoadResult.Page(emptyList(), null, null)
                }
            }
        }
        val fixture = Fixture(emptyList(), pager.flow)
        showPlayer(fixture)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        onNodeWithTag("tv-comments-loading").assertIsDisplayed()
        onNodeWithTag("tv-player-sidebar").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        onNodeWithTag("tv-sidebar-back").assertDoesNotExist()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-player-sidebar").assertDoesNotExist()
        onNodeWithTag("tv-player-chip-Comments").assertIsFocused()

        runOnIdle { loaded.complete(Unit) }
        key(Key.DirectionCenter)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("暂无评论").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-player-sidebar").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        onNodeWithTag("tv-sidebar-back").assertDoesNotExist()
        saveScreenshot("comments-empty-without-back-button")
        key(Key.DirectionLeft)
        onNodeWithTag("tv-player-sidebar").assertDoesNotExist()
        onNodeWithTag("tv-player-chip-Comments").assertIsFocused()
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun failedRefreshCanBeRetriedWithTheRemoteAndAppendShowsLoading() = runAniComposeUiTest {
        val refreshed = CompletableDeferred<Unit>()
        val appended = CompletableDeferred<Unit>()
        var refreshAttempts = 0
        val pager = Pager(PagingConfig(pageSize = 1, initialLoadSize = 1, enablePlaceholders = false)) {
            object : PagingSource<Int, EpisodeComment>() {
                override fun getRefreshKey(state: PagingState<Int, EpisodeComment>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, EpisodeComment> {
                    if (params.key == null) {
                        if (++refreshAttempts == 1) return LoadResult.Error(IOException("test network failure"))
                        refreshed.await()
                        return LoadResult.Page(listOf(comment("recovered", "重试后加载的评论")), null, 1)
                    }
                    appended.await()
                    return LoadResult.Page(emptyList(), 0, null)
                }
            }
        }
        val fixture = Fixture(emptyList(), pager.flow)
        showPlayer(fixture)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("tv-comments-retry")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("暂无评论").assertDoesNotExist()
        onNodeWithTag("tv-comments-retry").assertIsFocused()
        saveScreenshot("comments-refresh-error")
        key(Key.DirectionCenter)
        waitUntil(timeoutMillis = 5_000) { refreshAttempts == 2 }
        onNodeWithTag("tv-comments-loading").assertIsDisplayed()
        runOnIdle { refreshed.complete(Unit) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("tv-comment-recovered")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-comments-retry").assertDoesNotExist()
        onNodeWithTag("tv-comments-append-loading").assertIsDisplayed()
        onNodeWithTag("tv-comment-recovered").assertIsFocused()
        runOnIdle { appended.complete(Unit) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("tv-comments-append-loading")).fetchSemanticsNodes().isEmpty() }
        assertEquals(2, refreshAttempts)
    }

    @Test
    fun cardsRenderFormattedPreviewsAndReturnFocusToTheOpenedComment() = runAniComposeUiTest {
        val fixture = Fixture(listOf(
            comment("first", "[b]这一集的演出太精彩了[/b] (bgm38) " + "画面和配乐都让人印象深刻。".repeat(20)),
            comment("second", "[i]细节值得再看一遍[/i] [url=https://example.com]参考链接[/url] [quote]引用内容[/quote][img]${testImage()}[/img]"),
        ))
        showPlayer(fixture)
        openComments()
        onNodeWithTag("tv-comment-first").assertIsFocused()
        val preview = textLayout("这一集的演出太精彩了")
        assertEquals(3, preview.lineCount)
        assertTrue(preview.isLineEllipsized(2))
        assertFalse(preview.layoutInput.text.text.contains("[b]"))
        assertTrue(preview.layoutInput.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertEquals(26.sp, preview.layoutInput.style.lineHeight)
        saveScreenshot("comments-card-focused")

        key(Key.DirectionDown)
        onNodeWithTag("tv-comment-second").assertIsFocused()
        assertTrue(textLayout("细节值得再看一遍").layoutInput.text.text.contains("一遍 参考链接"))
        key(Key.DirectionCenter)
        onNodeWithTag("tv-comment-full-text").assertIsFocused()
        waitForText("细节值得再看一遍")
        onNodeWithText("引用内容", useUnmergedTree = true).assertIsDisplayed()
        // Images and inline links cannot introduce dead-end remote focus targets.
        onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused) and
                    hasAnyAncestor(hasTestTag("tv-comment-full-text")),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        key(Key.DirectionLeft)
        onNodeWithTag("tv-comment-full-text").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-comment-second").assertIsFocused()
        onNodeWithTag("tv-sidebar-back").assertDoesNotExist()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-player-sidebar").assertDoesNotExist()
        onNodeWithTag("tv-player-chip-Comments").assertIsFocused()
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun fullCommentScrollsAndRevealsNestedSpoilersWithTheRemote() = runAniComposeUiTest {
        val fixture = Fixture(listOf(comment(
            "spoiler",
            "[b]这一集的演出太精彩了[/b]\n[quote]引用中的 [mask]隐藏结局[/mask][/quote]\n" +
                    "[img]${testImage()}[/img]\n" + (1..24).joinToString("\n") { "第 $it 段：画面和配乐都让人印象深刻。" },
        )))
        showPlayer(fixture)
        openComments()
        key(Key.DirectionCenter)
        onNodeWithTag("tv-comment-full-text").assertIsFocused()
        waitForText("引用中的")
        val hidden = textLayout("引用中的")
        val mask = hidden.layoutInput.text.spanStyles.last { it.item.background != Color.Unspecified }
        assertEquals(mask.item.color, mask.item.background)
        assertEquals(18.sp, mask.item.fontSize)
        key(Key.DirectionCenter)
        onNodeWithText("↑↓ 滚动 · 按确定收起隐藏内容").assertIsDisplayed()
        val revealed = textLayout("引用中的").layoutInput.text
        val unmasked = revealed.spanStyles.single { it.start == mask.start && it.end == mask.end }
        assertTrue(unmasked.item.color != unmasked.item.background)
        saveScreenshot("comments-rich-detail")
        key(Key.DirectionCenter)
        onNodeWithText("↑↓ 滚动 · 按确定显示隐藏内容").assertIsDisplayed()

        val reader = onNodeWithTag("tv-comment-full-text")
        val before = reader.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        repeat(4) { key(Key.DirectionDown) }
        assertTrue(reader.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > before)
        reader.assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-comment-spoiler").assertIsFocused()
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun previewKeepsMasksAndUsesPlaceholdersForImagesAndQuotes() = runAniComposeUiTest {
        val fixture = Fixture(listOf(comment(
            "brief",
            "开头 [b]粗体[/b] [mask]隐藏结局[/mask] (bgm38) [quote]引用中的剧透[/quote][img]${testImage()}[/img]",
        )))
        showPlayer(fixture)
        openComments()
        waitForText("开头")
        val preview = textLayout("开头").layoutInput.text
        assertTrue(preview.text.contains("[引用]"))
        assertTrue(preview.text.contains("[图片]"))
        assertFalse(preview.text.contains("引用中的剧透"))
        assertFalse(preview.text.contains("file:"))
        val mask = preview.spanStyles.last { it.item.background != Color.Unspecified }
        assertEquals(mask.item.color, mask.item.background)
        onNodeWithTag("tv-comment-brief").assertIsFocused()
        key(Key.DirectionLeft)
        onNodeWithTag("tv-player-sidebar").assertDoesNotExist()
        onNodeWithTag("tv-player-chip-Comments").assertIsFocused()
        assertTrue(fixture.commands.isEmpty())
    }

    private fun AniComposeUiTest.showPlayer(fixture: Fixture) {
        setContent {
            val sketch = remember { Sketch.Builder(InstrumentationRegistry.getInstrumentation().targetContext).build() }
            DisposableEffect(sketch) { onDispose(sketch::shutdown) }
            CompositionLocalProvider(LocalSketch provides sketch) {
                AniTvTheme {
                    val dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    SideEffect { fixture.backDispatcher = dispatcher }
                    TvEpisodeScreen(
                        uiState = TvEpisodeUiState(
                            title = TvEpisodeTitle("测试番剧", "第 1 集"),
                            loadingState = VideoLoadingState.Succeed(false),
                            positionMillis = 20_000,
                            durationMillis = 60_000,
                        ),
                        togetherState = TvTogetherState(),
                        onTogetherIntent = {},
                        commentsPager = fixture.pager,
                        presentationState = fixture.machine,
                        actionEvents = emptyFlow(),
                        onIntent = { true },
                        video = { Box(it.background(Color(0xFF1E2A38))) },
                        resolver = {},
                        danmaku = {},
                        modifier = Modifier.testTag("tv-comment-test"),
                    )
                }
            }
        }
    }

    private fun AniComposeUiTest.openComments() {
        onNodeWithTag("tv-player-seekbar").assertIsFocused()
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithComment().fetchSemanticsNodes().isNotEmpty() }
    }

    private fun AniComposeUiTest.onAllNodesWithComment() = onAllNodes(
        SemanticsMatcher("comment card") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("tv-comment-") &&
                    it.config.contains(SemanticsActions.OnClick)
        },
    )

    private fun AniComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(SemanticsMatcher("text contains $text") { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { text in it.text }
            }, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
    }

    private fun AniComposeUiTest.textLayout(text: String): TextLayoutResult {
        waitForText(text)
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, substring = true, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun AniComposeUiTest.key(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun AniComposeUiTest.saveScreenshot(name: String) {
        val bitmap = onNodeWithTag("tv-comment-test").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir("screenshots"), "$name.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun testImage(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "comment-illustration.png")
        val bitmap = Bitmap.createBitmap(240, 72, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF615078.toInt()) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file).toString()
    }

    private fun comment(id: String, content: String) = EpisodeComment(
        stableId = id,
        source = EpisodeCommentSource.BANGUMI,
        sourceCommentId = id,
        commentId = id,
        episodeId = 1,
        createdAt = 1_788_739_200_000L,
        content = content,
        author = UserInfo(id, "viewer", "追番的观众"),
    )
}
