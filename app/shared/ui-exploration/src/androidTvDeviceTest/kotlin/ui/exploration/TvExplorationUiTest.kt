/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.platform.app.InstrumentationRegistry
import com.github.panpf.sketch.Sketch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.models.subject.TestSubjectAiringInfos
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.data.models.subject.createTestFollowedSubjectInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.foundation.focus.LocalTvFocusMemory
import me.him188.ani.leanback.ui.foundation.focus.TvFocusMemory
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import me.him188.ani.utils.platform.annotations.TestOnly
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Compose input and geometry assertions; saved PNGs also cover Android's no-op screenshot matcher. */
@OptIn(TestOnly::class)
class TvExplorationUiTest {
    private val titles = listOf("赛马娘 新时代之扉", "葬送的芙莉莲", "孤独摇滚！", "迷宫饭", "夏目友人帐")

    private fun collection(id: Int, image: String = ""): SubjectCollectionInfo {
        val episodes = (1..12).map { number ->
            EpisodeCollectionInfo(
                EpisodeInfo(
                    episodeId = id * 100 + number, type = EpisodeType.MainStory,
                    name = "Episode $number", nameCn = "第 $number 话", comment = 0, desc = "",
                    sort = EpisodeSort(number), ep = null,
                ),
                if (number in listOf(1, 4, 8)) UnifiedCollectionType.DONE else UnifiedCollectionType.NOT_COLLECTED,
            )
        }
        val progress = SubjectProgressInfo(
            ContinueWatchingStatus.Continue(null, EpisodeSort(9), null, EpisodeSort(8)),
            id * 100 + 9,
        )
        return TestSubjectCollections.first().copy(
            subjectInfo = SubjectInfo.Empty.copy(
                subjectId = id, nameCn = titles[(id - 1) % titles.size],
                name = titles[(id - 1) % titles.size], imageLarge = image, totalEpisodes = 12,
                summary = "向着新的旅途出发，在相遇与告别之间，寻找属于自己的答案。每一个全力以赴的瞬间，都值得被铭记。",
                tags = listOf(Tag("2024年5月", 100), Tag("动画", 80)),
                airDate = PackedDate(2024, 5, 24), ratingInfo = RatingInfo.Empty.copy(score = "8.3"),
            ),
            episodes = episodes, progressInfo = progress, airingInfo = TestSubjectAiringInfos.OnAir12Eps,
        )
    }

    private fun followed(image: String = "") = (11..18).map { id ->
        val info = collection(id, image)
        createTestFollowedSubjectInfo(info, info.airingInfo, info.progressInfo)
    }

    private fun <T : Any> completedPage(items: List<T>) = PagingData.from(
        items,
        sourceLoadStates = LoadStates(
            LoadState.NotLoading(true),
            LoadState.NotLoading(true),
            LoadState.NotLoading(true),
        ),
    )

    private fun AniComposeUiTest.mount(
        withContinue: Boolean = true,
        onIntent: (TvExplorationIntent) -> Unit = {},
        followedFlow: Flow<PagingData<FollowedSubjectInfo>>? = null,
        recommendationFlow: Flow<PagingData<RecommendedItemInfo>>? = null,
        visible: () -> Boolean = { true },
        lifecycleOwner: LifecycleOwner? = null,
        fontScale: Float = 1f,
        collectionTransform: (SubjectCollectionInfo) -> SubjectCollectionInfo = { it },
    ) {
        mainClock.autoAdvance = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "tv-exploration-poster.jpg")
        InstrumentationRegistry.getInstrumentation().context.assets.open(file.name)
            .use { input -> file.outputStream().use { input.copyTo(it) } }
        val image = file.toURI().toString()
        val sketch = Sketch.Builder(context).build()
        val timeFormatter = TimeFormatter()
        val focusMemory = TvFocusMemory()
        val previousLocale = LocaleList.getDefault()
        LocaleList.setDefault(LocaleList(Locale.forLanguageTag("zh-CN")))
        val trends = flowOf(completedPage((1..3).map { TrendingSubjectInfo(it, titles[it - 1], image) }))
        val recs = recommendationFlow ?: flowOf(
            completedPage<RecommendedItemInfo>(
                (21..44).map {
                    RecommendedSubjectInfo(it, titles[(it - 1) % titles.size], image)
                },
            ),
        )
        val follows = followedFlow ?: flowOf(completedPage(if (withContinue) followed(image) else emptyList()))
        val media =
            TvSubjectMediaUiState(infoCache = (1..44).associateWith { collectionTransform(collection(it, image)) })
        setContent {
            DisposableEffect(sketch) { onDispose { sketch.shutdown(); LocaleList.setDefault(previousLocale) } }
            // Preserve the TV display density; only the accessibility font scale is varied.
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalSketch provides sketch, LocalTimeFormatter provides timeFormatter,
                LocalDensity provides Density(density, fontScale),
                LocalLifecycleOwner provides (lifecycleOwner ?: LocalLifecycleOwner.current),
            ) {
                AniTvTheme {
                    val saved = rememberSaveableStateHolder()
                    // The route uses VM-owned presenters that survive a details push/pop.
                    val trendingItems = trends.collectAsLazyPagingItems()
                    val recommendationItems = recs.collectAsLazyPagingItems()
                    val followedItems = follows.collectAsLazyPagingItems()
                    Box(Modifier.fillMaxSize().padding(start = 48.dp)) {
                        if (visible()) saved.SaveableStateProvider("exploration") {
                            CompositionLocalProvider(LocalTvFocusMemory provides focusMemory) {
                                focusMemory.ArmOnRouteReturn()
                                TvExplorationScreen(trendingItems, recommendationItems, followedItems, media, onIntent)
                            }
                        }
                    }
                }
            }
        }
        awaitFocus("tv-exploration-details")
        settle()
    }

    @Test
    fun firstRowCompactsHeroThenRecommendationsScrollTheWholePageAndRestoreFocus() = runAniComposeUiTest {
        mount()
        val expandedHero = bounds("tv-exploration-hero")
        val fixedBackdrop = bounds("tv-exploration-backdrop")
        onNodeWithTag("tv-exploration-followed-11").assertWidthIsEqualTo(198.dp)
        capture("01-featured")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        val compactHero = bounds("tv-exploration-hero")
        assertTrue(compactHero.height < expandedHero.height * .8f)
        assertTrue(abs(compactHero.top - expandedHero.top) < 2f, "First row must compact without scrolling the page")
        assertEquals(fixedBackdrop, bounds("tv-exploration-backdrop"))
        onNodeWithTag("tv-exploration-hero-status-11", true).assertTextContains("9", substring = true)
        onNodeWithTag("tv-exploration-followed-11").assertContentDescriptionEquals(titles[0])
        assertFalse(
            onNodeWithTag("tv-exploration-followed-11").fetchSemanticsNode()
                .config.contains(SemanticsProperties.Text),
            "Image cards must not contain visible text",
        )
        val progress = onNodeWithTag("tv-exploration-watched-progress-11", true).fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(.25f, progress.current)
        assertTrue(
            bounds("tv-exploration-watched-progress-11", true).bottom < compactHero.bottom,
            "Watching progress belongs inside the hero",
        )
        capture("02-continue")
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-12")
        settle()
        onNodeWithTag("tv-exploration-hero-status-12", true).assertTextContains("9", substring = true)
        assertTrue(onAllNodes(hasTestTag("tv-exploration-hero-status-11")).fetchSemanticsNodes().isEmpty())
        assertTrue(abs(bounds("tv-exploration-hero").top - compactHero.top) < 2f)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-22")
        settle()
        capture("03-recommendations")
        assertTrue(
            glowProgress() > .9f,
            "Scrolling past Continue Watching must remove the hero artwork: ${glowProgress()}",
        )
        assertEquals(
            fixedBackdrop,
            bounds("tv-exploration-backdrop"),
            "The backdrop must stay fixed while the content scrolls",
        )
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-26")
        settle()
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-rec-22")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-followed-12")
        settle()
        assertTrue(glowProgress() < .01f)
        capture("04-continue-restored")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
        settle()
        assertTrue(abs(bounds("tv-exploration-hero").height - expandedHero.height) < 2f)
    }

    @Test
    fun emptyContinueWatchingSkipsCompactStateAndScrollsHeroAway() = runAniComposeUiTest {
        mount(withContinue = false)
        val height = bounds("tv-exploration-hero").height
        assertTrue(onAllNodes(hasTestTag("tv-exploration-row-followed")).fetchSemanticsNodes().isEmpty())
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        settle()
        capture("05-without-continue")
        assertTrue(glowProgress() > .9f, "Expected a scrolled-away hero, got ${glowProgress()}")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
        settle()
        assertTrue(abs(bounds("tv-exploration-hero").height - height) < 2f)
    }

    @Test
    fun carouselWrapsAndAutoAdvanceStopsWhenHeroLosesFocus() = runAniComposeUiTest {
        mount()
        assertTrue(onAllNodes(hasTestTag("tv-exploration-auto-progress")).fetchSemanticsNodes().isEmpty())
        key(Key.DirectionLeft)
        settle(400)
        onNodeWithTag("tv-exploration-dot-2").assertIsSelected()
        key(Key.DirectionRight)
        settle(400)
        onNodeWithTag("tv-exploration-dot-0").assertIsSelected()
        settle(6_200)
        onNodeWithTag("tv-exploration-dot-1").assertIsSelected()
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle(7_000)
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
        settle(400)
        onNodeWithTag("tv-exploration-dot-1").assertIsSelected()
    }

    @Test
    fun continueCardUsesResolvedEpisodeAndHeroOpensDetails() = runAniComposeUiTest {
        val intents = mutableListOf<TvExplorationIntent>()
        mount(onIntent = { intents += it })
        key(Key.DirectionCenter)
        assertEquals(1, intents.filterIsInstance<TvExplorationIntent.OpenSubject>().single().subject.subjectId)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        key(Key.DirectionCenter)
        assertEquals(1109, intents.filterIsInstance<TvExplorationIntent.ContinueWatching>().single().episodeId)
    }

    @Test
    fun carouselSlidesIdentityWhileBackdropStaysFixedAndActionKeepsFocus() = runAniComposeUiTest {
        mount()
        val originalLeft = bounds("tv-exploration-hero-title").left
        val fixedBackdrop = bounds("tv-exploration-backdrop-1")
        key(Key.DirectionRight)
        settle(100)
        onNodeWithTag("tv-exploration-details").assertIsFocused()
        val incoming = onNode(hasTestTag("tv-exploration-hero-title") and hasText(titles[1])).fetchSemanticsNode()
        val outgoing = onNode(hasTestTag("tv-exploration-hero-title") and hasText(titles[0])).fetchSemanticsNode()
        assertTrue(incoming.boundsInRoot.left > originalLeft)
        assertTrue(outgoing.boundsInRoot.left < originalLeft)
        assertEquals(fixedBackdrop, bounds("tv-exploration-backdrop-1"))
        assertEquals(fixedBackdrop, bounds("tv-exploration-backdrop-2"))
        capture("09-carousel-right-midpoint")
        settle(450)
        key(Key.DirectionLeft)
        settle(100)
        onNodeWithTag("tv-exploration-details").assertIsFocused()
        val returning = onNode(hasTestTag("tv-exploration-hero-title") and hasText(titles[0])).fetchSemanticsNode()
        val leaving = onNode(hasTestTag("tv-exploration-hero-title") and hasText(titles[1])).fetchSemanticsNode()
        assertTrue(returning.boundsInRoot.left < originalLeft)
        assertTrue(leaving.boundsInRoot.left > originalLeft)
        assertEquals(fixedBackdrop, bounds("tv-exploration-backdrop-1"))
        assertEquals(fixedBackdrop, bounds("tv-exploration-backdrop-2"))
        capture("10-carousel-left-midpoint")
        settle(450)
        onNodeWithTag("tv-exploration-dot-0").assertIsSelected()
        assertEquals(1, onAllNodes(hasTestTag("tv-exploration-details")).fetchSemanticsNodes().size)
    }

    @Test
    fun followedReorderingKeepsBusinessIdentityAndLargeTextStillFits() = runAniComposeUiTest {
        val values = followed()
        val flow = MutableStateFlow(completedPage(values))
        mount(followedFlow = flow, fontScale = 1.2f)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-12")
        runOnIdle { flow.value = completedPage(values.reversed()) }
        settle()
        awaitFocus("tv-exploration-followed-12")
        capture("06-large-text-reordered")
        assertTrue(bounds("tv-exploration-hero-title").bottom < bounds("tv-exploration-row-followed").top)
        assertTrue(bounds("tv-exploration-watched-progress-12", true).bottom < bounds("tv-exploration-hero").bottom)
        assertEquals(2, textLayout("tv-exploration-hero-summary").lineCount)
    }

    @Test
    fun singleAndDoubleLineTitlesKeepMetadataSummaryAndActionsAtTheSamePosition() = runAniComposeUiTest {
        val shortTitle = "短标题"
        val longTitle = "转生之后在异世界展开的漫长冒险与新的旅程"
        val shortSummary = "一段短简介。"
        val longSummary = "向着新的旅途出发，在相遇与告别之间，寻找属于自己的答案。".repeat(8)
        mount(
            collectionTransform = { collection ->
                collection.copy(
                    subjectInfo = collection.subjectInfo.copy(
                        nameCn = if (collection.subjectId % 2 == 1) shortTitle else longTitle,
                        summary = if (collection.subjectId % 2 == 1) shortSummary else longSummary,
                    ),
                )
            },
        )
        val title = bounds("tv-exploration-hero-title")
        val titleBaseline = textLayout("tv-exploration-hero-title").firstBaseline
        val metadata = bounds("tv-details-metadata")
        val summary = bounds("tv-exploration-hero-summary")
        val action = bounds("tv-exploration-details")
        capture("11-single-line-title")
        key(Key.DirectionRight)
        settle(450)
        assertEquals(2, textLayout("tv-exploration-hero-title").lineCount)
        assertEquals(title, bounds("tv-exploration-hero-title"))
        assertEquals(titleBaseline, textLayout("tv-exploration-hero-title").firstBaseline)
        assertEquals(metadata, bounds("tv-details-metadata"))
        assertEquals(summary, bounds("tv-exploration-hero-summary"))
        assertEquals(action, bounds("tv-exploration-details"))
        assertEquals(2, textLayout("tv-exploration-hero-summary").lineCount)
        assertTrue(textLayout("tv-exploration-hero-summary").isLineEllipsized(1))
        capture("12-double-line-title")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        val compactTitle = bounds("tv-exploration-hero-title")
        val compactTitleBaseline = textLayout("tv-exploration-hero-title").firstBaseline
        val compactSummary = bounds("tv-exploration-hero-summary")
        val progress = bounds("tv-exploration-hero-progress-11")
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-12")
        settle()
        assertEquals(compactTitle, bounds("tv-exploration-hero-title"))
        assertEquals(compactTitleBaseline, textLayout("tv-exploration-hero-title").firstBaseline)
        assertEquals(compactSummary, bounds("tv-exploration-hero-summary"))
        assertEquals(progress, bounds("tv-exploration-hero-progress-12"))
        capture("13-double-line-continue")
    }

    @Test
    fun removingTheFocusedCollectionRecoversThenSkipsAnEmptiedContinueRow() = runAniComposeUiTest {
        val values = followed()
        val flow = MutableStateFlow(completedPage(values))
        mount(followedFlow = flow)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        runOnIdle { flow.value = completedPage(values.drop(1)) }
        awaitFocus("tv-exploration-followed-12")
        runOnIdle { flow.value = completedPage(emptyList()) }
        awaitFocus("tv-exploration-rec-21")
        settle()
        assertTrue(onAllNodes(hasTestTag("tv-exploration-row-followed")).fetchSemanticsNodes().isEmpty())
        assertTrue(glowProgress() > .9f)
    }

    @Test
    fun recommendationFailureRemainsNavigableAndRetryRecoversToARealCard() = runAniComposeUiTest {
        var attempts = 0
        val pager = Pager(PagingConfig(pageSize = 4)) {
            object : PagingSource<Int, RecommendedItemInfo>() {
                override fun getRefreshKey(state: PagingState<Int, RecommendedItemInfo>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RecommendedItemInfo> {
                    return if (attempts++ == 0) LoadResult.Error(IOException("Offline fixture"))
                    else LoadResult.Page(listOf(RecommendedSubjectInfo(21, "重新加载的番剧", "")), null, null)
                }
            }
        }
        mount(withContinue = false, recommendationFlow = pager.flow)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-feed-status")
        settle()
        capture("07-retry")
        key(Key.DirectionCenter)
        awaitFocus("tv-exploration-rec-21")
        assertEquals(2, attempts)
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
    }

    @Test
    fun returningFromAnotherRouteRestoresAnOffscreenHeroAndTheOriginalGridCard() = runAniComposeUiTest {
        var visible by mutableStateOf(true)
        mount(visible = { visible })
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-25")
        settle()
        val original = bounds("tv-exploration-rec-25")
        runOnIdle { visible = false }
        settle()
        runOnIdle { visible = true }
        awaitFocus("tv-exploration-rec-25")
        settle()
        assertTrue(glowProgress() > .9f)
        assertTrue(abs(bounds("tv-exploration-rec-25").top - original.top) < 2f)
        capture("08-route-return")
    }

    @Test
    fun returningTransitionCannotReplaceTheSavedCardBeforeThePageResumes() = runAniComposeUiTest {
        val owner = object : LifecycleOwner {
            override val lifecycle =
                LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var visible by mutableStateOf(true)
        mount(visible = { visible }, lifecycleOwner = owner)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        settle()
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED; visible = false }
        settle()
        runOnIdle { visible = true }
        settle()
        onNodeWithTag("tv-exploration-rec-25").performSemanticsAction(SemanticsActions.RequestFocus) {
            assertFalse(it(), "The return animation must not replace the saved card")
        }
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        awaitFocus("tv-exploration-rec-21")
        settle()
        assertTrue(glowProgress() > .9f)
    }

    private fun AniComposeUiTest.settle(millis: Long = 900) {
        mainClock.advanceTimeBy(millis)
        waitForIdle()
    }

    private fun AniComposeUiTest.awaitFocus(tag: String) {
        try {
            waitUntil(timeoutMillis = 5_000) {
                mainClock.advanceTimeByFrame()
                onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(tag).assertIsFocused()
        } catch (error: Throwable) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "tv-exploration-focus-failure.txt")
                .writeText("Expected: $tag\n" + onAllNodes(isRoot()).onLast().printToString())
            capture("failure")
            throw error
        }
    }

    private fun AniComposeUiTest.key(key: Key) {
        onAllNodes(isRoot() and hasAnyDescendant(isFocused())).onLast().performKeyInput { pressKey(key) }
        mainClock.advanceTimeByFrame()
    }

    private fun AniComposeUiTest.bounds(tag: String, unmerged: Boolean = false) =
        onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInRoot

    private fun AniComposeUiTest.textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun AniComposeUiTest.glowProgress() = onNodeWithTag("tv-exploration-glow")
        .fetchSemanticsNode().config[SemanticsProperties.StateDescription].toFloat()

    private fun AniComposeUiTest.capture(name: String) {
        onNodeWithTag("tv-exploration").assertScreenshot("tv-exploration/$name")
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "tv-exploration-$name.png",
        )
        output.outputStream().use {
            onNodeWithTag("tv-exploration").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
