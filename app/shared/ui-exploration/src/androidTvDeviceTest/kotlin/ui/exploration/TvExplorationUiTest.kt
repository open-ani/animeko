/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.tv.ui.exploration

import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
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
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isDisplayed
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
import me.him188.ani.app.data.models.subject.SubjectAiringKind
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
import me.him188.ani.tv.ui.foundation.focus.LocalTvFocusMemory
import me.him188.ani.tv.ui.foundation.focus.TvFocusMemory
import me.him188.ani.tv.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.tv.ui.foundation.theme.AniTvTheme
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor
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
        trendingFlow: Flow<PagingData<TrendingSubjectInfo>>? = null,
        visible: () -> Boolean = { true },
        lifecycleOwner: LifecycleOwner? = null,
        fontScale: Float = 1f,
        trendingCount: Int = 3,
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
        val trends = trendingFlow ?: flowOf(completedPage((1..trendingCount).map { TrendingSubjectInfo(it, titles[(it - 1) % titles.size], image) }))
        val recs = recommendationFlow ?: flowOf(
            completedPage<RecommendedItemInfo>(
                (21..44).map {
                    RecommendedSubjectInfo(it, titles[(it - 1) % titles.size], "", image)
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
                    Box(
                        Modifier.fillMaxSize().background(tvShellBackgroundColor())
                            .testTag("tv-exploration-shell").padding(start = 48.dp),
                    ) {
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
    fun heroZoomAndHorizontalTransitionsStayOutsideTheNavigationRail() = runAniComposeUiTest {
        mount()
        val original = railPixels()
        fun assertRailUnchanged(stage: String) {
            assertTrue(original.contentEquals(railPixels()), "Home content crossed into the navigation rail: $stage")
        }

        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle(160)
        assertRailUnchanged("zoom midpoint")
        settle(750)
        assertRailUnchanged("zoomed background")
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-12")
        settle(160)
        assertRailUnchanged("preview selection")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
        settle(160)
        assertRailUnchanged("return to hero")
        settle(750)
        key(Key.DirectionLeft)
        settle(100)
        assertRailUnchanged("carousel moving right")
        settle(550)
        key(Key.DirectionRight)
        settle(100)
        assertRailUnchanged("carousel moving left")
    }

    @Test
    fun officialGeometryAndTwoStageNavigation() = runAniComposeUiTest {
        mount()
        val px = bounds("tv-exploration").height / 540f
        val fixedBackdrop = bounds("tv-exploration-backdrop")
        val restingCard = bounds("tv-exploration-followed-11")
        val restingLabel = bounds("tv-exploration-row-title-followed")
        assertTrue(onAllNodes(hasText("最高热度")).fetchSemanticsNodes().isEmpty())
        onNodeWithTag("tv-exploration-followed-11").assertWidthIsEqualTo(153.dp)
        val restingTop = bounds("tv-exploration-followed-11").top / px
        assertTrue(abs(restingTop - 494f) < 2f, "Resting card top: $restingTop dp, page ${bounds("tv-exploration")}")
        assertTrue(abs(bounds("tv-exploration-featured-title").left / px - 58f) < 1f)
        capture("01-featured")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        assertTrue(abs(bounds("tv-exploration-followed-11").top / px - 358f) < 4f)
        assertEquals(fixedBackdrop, bounds("tv-exploration-backdrop"))
        onNodeWithTag("tv-exploration-preview-caption").assertTextContains("9", substring = true)
        onNodeWithTag("tv-exploration-preview-summary").assertTextContains("相遇与告别", substring = true)
        onNodeWithTag("tv-exploration-preview-metadata").assert(hasAnyDescendant(hasText("8.3")))
        onNodeWithTag("tv-exploration-followed-11").assertContentDescriptionEquals(titles[0])
        assertFalse(onNodeWithTag("tv-exploration-followed-11").fetchSemanticsNode()
            .config.contains(SemanticsProperties.Text))
        val focusedCard = bounds("tv-exploration-followed-11")
        assertEquals(restingCard.width, focusedCard.width)
        onNodeWithTag("tv-exploration-followed-11").assertHeightIsEqualTo(86.dp)
        assertTrue(bounds("tv-exploration-row-title-followed").height > restingLabel.height * 1.5f)
        capture("02-immersive")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        settle()
        assertTrue(glowProgress() > .99f)
        assertTrue(abs(bounds("tv-exploration-rec-21").top / px - 120f) < 6f)
        assertTrue(bounds("tv-exploration-rec-25").top > bounds("tv-exploration-rec-21").bottom)
        onNodeWithTag("tv-exploration-row-trending").assertDoesNotExist()
        assertEquals(fixedBackdrop, bounds("tv-exploration-backdrop"))
        capture("03-ordinary-row")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-followed-11")
        settle()
        assertTrue(glowProgress() < .01f)
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
        settle()
        assertTrue(abs(bounds("tv-exploration-followed-11").top / px - 494f) < 2f)
        capture("04-featured-restored")
    }

    @Test
    fun continueAndRecommendationCardsShareACompleteFocusRingWithoutResizing() = runAniComposeUiTest {
        mount()
        onNodeWithTag("tv-exploration-details").assertContentDescriptionEquals("更多详情")
        val resting = onNodeWithTag("tv-exploration-followed-11").getUnclippedBoundsInRoot()
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        val density = bounds("tv-exploration").height / 540f
        fun ringColor(tag: String): Int {
            val bitmap = onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val inset = density.toInt()
            val samples = listOf(
                bitmap.getPixel(bitmap.width / 2, inset),
                bitmap.getPixel(bitmap.width / 2, bitmap.height - 1 - inset),
                bitmap.getPixel(inset, bitmap.height / 2),
                bitmap.getPixel(bitmap.width - 1 - inset, bitmap.height / 2),
            )
            assertTrue(samples.all { it == samples.first() }, "All four sides must show the same unclipped focus ring")
            assertTrue(bitmap.getPixel(bitmap.width / 2, (3.5f * density).toInt()) != samples.first(),
                "There must be visible space between the focus ring and the image")
            return samples.first()
        }
        val continueColor = ringColor("tv-exploration-followed-11")
        val focused = onNodeWithTag("tv-exploration-followed-11").getUnclippedBoundsInRoot()
        assertEquals(resting.right - resting.left, focused.right - focused.left)
        assertEquals(resting.bottom - resting.top, focused.bottom - focused.top)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        settle()
        assertEquals(continueColor, ringColor("tv-exploration-rec-21"))
        capture("unified-card-focus")
    }

    @Test
    fun recommendationGridKeepsColumnsAndContinueRowKeepsItsSelection() = runAniComposeUiTest {
        mount()
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-12")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-22")
        settle()
        val left = bounds("tv-exploration-rec-22").left
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-rec-23")
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-rec-24")
        key(Key.DirectionRight)
        onNodeWithTag("tv-exploration-rec-24").assertIsFocused()
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-28")
        key(Key.DirectionLeft)
        awaitFocus("tv-exploration-rec-27")
        key(Key.DirectionLeft)
        awaitFocus("tv-exploration-rec-26")
        settle()
        assertTrue(abs(bounds("tv-exploration-rec-26").left - left) < 2f)
        capture("05-recommendation-grid")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-30")
        settle()
        capture("06-grid-scroll")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-rec-26")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-rec-22")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-followed-12")
        settle()
        onNodeWithTag("tv-exploration-preview-title").assertTextContains(titles[1])
        capture("07-row-memory")
    }

    @Test
    fun panelAndScrollTransitionsAcceptReversalBeforeTheyFinish() = runAniComposeUiTest {
        mount()
        val expandedHeight = bounds("tv-exploration-hero").height
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle(64)
        assertTrue(bounds("tv-exploration-hero").height < expandedHeight)
        capture("08-panel-midpoint")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        key(Key.DirectionDown)
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            glowProgress() > 0f
        }
        onNodeWithTag("tv-exploration-rec-21").assertIsFocused()
        assertTrue(glowProgress() < 1f)
        capture("09-scroll-midpoint")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-followed-11")
        settle()
        assertTrue(glowProgress() < .01f)
        capture("10-reversal")
    }

    @Test
    fun noCollectionNavigatesDirectlyFromHeroToRecommendationGrid() = runAniComposeUiTest {
        mount(withContinue = false)
        onNodeWithTag("tv-exploration-row-followed").assertDoesNotExist()
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        settle()
        assertTrue(glowProgress() > .99f)
        val first = bounds("tv-exploration-rec-21")
        assertTrue(bounds("tv-exploration-rec-24").left > first.right)
        assertTrue(bounds("tv-exploration-rec-25").top > first.bottom)
        capture("11-no-collection")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-25")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-rec-21")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
    }

    @Test
    fun heroUsesDetailsMetadataIncludingMissingScoreAndAiringProgress() = runAniComposeUiTest {
        mount(collectionTransform = { collection ->
            collection.copy(subjectInfo = collection.subjectInfo.copy(summary = "", ratingInfo = RatingInfo.Empty.copy(score = "")))
        })
        onNodeWithTag("tv-exploration-featured-metadata-1").assert(hasAnyDescendant(hasText("–")))
        onNodeWithTag("tv-exploration-featured-metadata-1").assert(hasAnyDescendant(hasText("动画")))
        onNodeWithTag("tv-exploration-featured-metadata-1").assert(hasAnyDescendant(hasText("2024-05")))
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        onNodeWithTag("tv-exploration-preview-title").assertTextContains(titles[0])
        onNodeWithTag("tv-exploration-preview-summary").assertDoesNotExist()
        onNodeWithTag("tv-exploration-preview-metadata").assert(hasAnyDescendant(hasText("–")))
        onNodeWithTag("tv-exploration-preview-metadata").assert(hasAnyDescendant(hasText("动画")))
        onNodeWithTag("tv-exploration-preview-airing").assert(hasAnyDescendant(hasText("连载", substring = true)))
        capture("18-details-metadata")
    }

    @Test
    fun continuePreviewKeepsMetadataBelowTitleAndCombinesStatusAboveProgress() = runAniComposeUiTest {
        mount(collectionTransform = {
            it.copy(airingInfo = it.airingInfo.copy(latestSort = EpisodeSort(9)))
        })
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        val summary = bounds("tv-exploration-preview-summary")
        val caption = bounds("tv-exploration-preview-caption")
        val track = bounds("tv-exploration-watching-progress")
        onNodeWithTag("tv-exploration-watching-progress").assertHeightIsEqualTo(20.dp)
        val metadata = bounds("tv-exploration-preview-metadata")
        val airing = bounds("tv-exploration-preview-airing")
        assertTrue(metadata.top >= bounds("tv-exploration-preview-title").bottom)
        assertTrue(summary.top > metadata.bottom)
        assertTrue(caption.top > summary.bottom)
        assertTrue(track.top > caption.bottom)
        assertTrue(airing.bottom < track.top)
        assertTrue(airing.left > caption.right)
        assertTrue(abs(airing.center.y - caption.center.y) < 2f)
        assertTrue(track.bottom < bounds("tv-exploration-row-followed").top)
        assertEquals(track.width, bounds("tv-exploration-preview-status").width)
        assertEquals(1, textLayout("tv-exploration-preview-caption").lineCount)
        onNodeWithTag("tv-exploration-watching-counts").assertDoesNotExist()
        onNodeWithTag("tv-exploration-preview-metadata").assert(hasAnyDescendant(hasText("8.3")))
        onNodeWithTag("tv-exploration-preview-metadata").assert(hasAnyDescendant(hasText("动画")))
        onNodeWithTag("tv-exploration-preview-metadata").assert(hasAnyDescendant(hasText("2024-05")))
        onNodeWithTag("tv-exploration-preview-metadata").assert(!hasAnyDescendant(hasText("连载", substring = true)))
        onNodeWithTag("tv-exploration-preview-airing").assert(hasAnyDescendant(hasText("连载", substring = true)))
        onNodeWithTag("tv-exploration-preview-airing").assert(!hasAnyDescendant(hasText("8.3")))
        onNodeWithTag("tv-exploration-preview-airing").assert(!hasAnyDescendant(hasText("动画")))
        onNodeWithTag("tv-exploration-preview-airing").assert(!hasAnyDescendant(hasText("2024-05")))
        onNodeWithTag("tv-exploration-watching-progress").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已看 3 / 已播 9 / 共 12 集"),
        )
        val range = onNodeWithTag("tv-exploration-watching-progress").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(3f, range.current)
        assertEquals(0f..12f, range.range)
        assertTrue(abs(bounds("tv-exploration-watching-watched").width / track.width - .25f) < .01f)
        assertTrue(abs(bounds("tv-exploration-watching-aired").width / track.width - .75f) < .01f)
        val bitmap = progressBitmap()
        val borderColor = bitmap.getPixel(bitmap.width * 7 / 8, 1)
        val trackColor = bitmap.getPixel(bitmap.width * 7 / 8, bitmap.height / 2)
        assertTrue(((borderColor shr 16) and 255) > ((trackColor shr 16) and 255) + 40, "The light border should outline the unfilled track")
        capture("24-watching-progress-on-air")
    }

    @Test
    fun completedAndUnstartedProgressUseTwoCountsAndUnknownTotalsStayUnknown() = runAniComposeUiTest {
        mount(collectionTransform = { info ->
            when (info.subjectId) {
                11 -> info.copy(airingInfo = info.airingInfo.copy(kind = SubjectAiringKind.COMPLETED))
                12 -> info.copy(
                    airingInfo = info.airingInfo.copy(kind = SubjectAiringKind.COMPLETED),
                    episodes = info.episodes.map { it.copy(collectionType = UnifiedCollectionType.NOT_COLLECTED) },
                    progressInfo = SubjectProgressInfo(ContinueWatchingStatus.Start, 1201),
                )
                13 -> info.copy(
                    episodes = emptyList(), airingInfo = info.airingInfo.copy(mainEpisodeCount = 0, latestSort = null),
                )
                else -> info
            }
        })
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        onNodeWithTag("tv-exploration-watching-progress").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已看 3 / 共 12 集"),
        )
        onNodeWithTag("tv-exploration-watching-aired").assertDoesNotExist()
        capture("25-watching-progress-completed")
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-12")
        settle()
        onNodeWithTag("tv-exploration-preview-caption").assertTextContains("开始观看")
        assertTrue(bounds("tv-exploration-preview-caption").top > bounds("tv-exploration-preview-summary").bottom)
        onNodeWithTag("tv-exploration-watching-progress").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已看 0 / 共 12 集"),
        )
        capture("26-watching-progress-start")
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-13")
        settle()
        onNodeWithTag("tv-exploration-watching-progress").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已看 0 / 已播 – / 共 – 集"),
        )
        capture("27-watching-progress-unknown")
    }

    @Test
    fun switchingContinueItemsAnimatesBothProgressSegmentsAndCanReverse() = runAniComposeUiTest {
        mount(collectionTransform = { info ->
            val watched = if (info.subjectId == 11) 3 else 6
            info.copy(
                airingInfo = info.airingInfo.copy(latestSort = EpisodeSort(if (info.subjectId == 11) 6 else 10)),
                episodes = info.episodes.mapIndexed { index, episode ->
                    episode.copy(collectionType = if (index < watched) UnifiedCollectionType.DONE else UnifiedCollectionType.NOT_COLLECTED)
                },
            )
        })
        fun fraction(segment: String) = bounds("tv-exploration-watching-$segment").width /
            bounds("tv-exploration-watching-progress").width
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        assertTrue(abs(fraction("watched") - .25f) < .01f)
        assertTrue(abs(fraction("aired") - .5f) < .01f)
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-12")
        settle(96)
        assertTrue(fraction("watched") > .25f && fraction("watched") < .5f)
        assertTrue(fraction("aired") > .5f && fraction("aired") < 10f / 12f)
        capture("32-progress-transition-midpoint")
        key(Key.DirectionLeft)
        awaitFocus("tv-exploration-followed-11")
        assertTrue(fraction("watched") > .25f && fraction("watched") < .5f)
        settle(500)
        assertTrue(abs(fraction("watched") - .25f) < .01f)
        assertTrue(abs(fraction("aired") - .5f) < .01f)
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-12")
        settle(500)
        assertTrue(abs(fraction("watched") - .5f) < .01f)
        assertTrue(abs(fraction("aired") - 10f / 12f) < .01f)
        capture("33-progress-transition-finished")
    }

    @Test
    fun chargingParticlesStayInsideWatchedSegmentAndPauseWithPage() = runAniComposeUiTest {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        mount(lifecycleOwner = owner, collectionTransform = {
            it.copy(airingInfo = it.airingInfo.copy(latestSort = EpisodeSort(9)))
        })
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        val before = progressBitmap()
        settle(320)
        val after = progressBitmap()
        assertFalse(before.sameAs(after), "The geometric particles should move while continue watching is active")
        val watchedWidth = bounds("tv-exploration-watching-watched").width.toInt()
        for (y in 0 until before.height) for (x in watchedWidth until before.width) {
            assertEquals(before.getPixel(x, y), after.getPixel(x, y), "A particle escaped the watched segment at $x,$y")
        }
        onNodeWithTag("tv-exploration-followed-11").assertIsFocused()
        capture("30-geometric-watching-progress")

        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        settle(100)
        val paused = progressBitmap()
        settle(480)
        assertTrue(paused.sameAs(progressBitmap()), "Particles should pause while the page is in the background")
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        settle(320)
        assertFalse(paused.sameAs(progressBitmap()), "Particles should resume with the page")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        settle()
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-followed-11")
    }

    @Test
    fun zeroWatchedProgressIsStaticAndFullProgressFillsTheCapsule() = runAniComposeUiTest {
        mount(collectionTransform = { info ->
            info.copy(
                airingInfo = info.airingInfo.copy(kind = SubjectAiringKind.COMPLETED),
                episodes = info.episodes.map {
                    it.copy(collectionType = if (info.subjectId == 11) UnifiedCollectionType.NOT_COLLECTED else UnifiedCollectionType.DONE)
                },
            )
        })
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        val empty = progressBitmap()
        settle(480)
        assertTrue(empty.sameAs(progressBitmap()), "An empty progress bar should remain static")
        key(Key.DirectionRight)
        awaitFocus("tv-exploration-followed-12")
        settle()
        assertEquals(bounds("tv-exploration-watching-progress").width, bounds("tv-exploration-watching-watched").width)
        val full = progressBitmap()
        settle(320)
        assertFalse(full.sameAs(progressBitmap()))
        capture("31-geometric-progress-full")
    }

    @Test
    fun disabledSystemMotionKeepsChargingParticlesStatic() = runAniComposeUiTest(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    ) {
        mount()
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        val before = progressBitmap()
        settle(480)
        assertTrue(before.sameAs(progressBitmap()), "System animation settings should also control the custom particles")
        onNodeWithTag("tv-exploration-followed-11").assertIsFocused()
    }

    private fun AniComposeUiTest.progressBitmap() =
        onNodeWithTag("tv-exploration-watching-progress").captureToImage().asAndroidBitmap()

    @Test
    fun continuePreviewExpandsVerticallyAndCanReverseWithoutChangingTextSize() = runAniComposeUiTest {
        mount()
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle(32)
        val initialHeight = bounds("tv-exploration-preview-reveal").height
        val textSize = textLayout("tv-exploration-preview-caption").layoutInput.style.fontSize
        settle(64)
        val middleHeight = bounds("tv-exploration-preview-reveal").height
        assertTrue(initialHeight > 0f && middleHeight > initialHeight)
        capture("28-preview-expanding")
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
        settle(48)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        assertTrue(bounds("tv-exploration-preview-reveal").height > middleHeight)
        assertEquals(textSize, textLayout("tv-exploration-preview-caption").layoutInput.style.fontSize)
        assertEquals(bounds("tv-exploration-hero").height, bounds("tv-exploration-preview-reveal").height)
        onNodeWithTag("tv-exploration-followed-11").assertIsFocused()
        capture("29-preview-expanded-after-reversal")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        settle()
        onNodeWithTag("tv-exploration-preview-reveal").assertDoesNotExist()
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-followed-11")
        settle(32)
        val returningHeight = bounds("tv-exploration-preview-reveal").height
        settle(64)
        val returningMiddleHeight = bounds("tv-exploration-preview-reveal").height
        assertTrue(returningMiddleHeight > returningHeight, "Returning preview height: $returningHeight → $returningMiddleHeight")
        settle()
        assertTrue(onNodeWithTag("tv-exploration-preview-status").isDisplayed())
        assertTrue(onNodeWithTag("tv-exploration-watching-progress").isDisplayed())
    }

    @Test
    fun carouselIncludesEveryTrendingItemAndCentersSelectedIndicator() = runAniComposeUiTest {
        mount(trendingCount = 9)
        val viewport = bounds("tv-exploration-indicators")
        onNodeWithTag("tv-exploration-indicators").assertWidthIsEqualTo(72.dp)
        for (index in 1..8) {
            key(Key.DirectionRight)
            settle(550)
            onNodeWithTag("tv-exploration-dot-$index").assertIsSelected()
            assertTrue(abs(bounds("tv-exploration-dot-$index").center.x - viewport.center.x) < 2f)
            val dots = onAllNodes(SemanticsMatcher("indicator") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tv-exploration-dot-") == true
            }).fetchSemanticsNodes()
            val visible = dots.count { onNodeWithTag(it.config[SemanticsProperties.TestTag]).isDisplayed() }
            assertTrue(visible <= 5, "Indicators at $index, viewport=$viewport: " + dots.joinToString { "${it.config[SemanticsProperties.TestTag]}=${it.boundsInRoot}" })
        }
        capture("19-all-trending")
        key(Key.DirectionRight)
        settle(550)
        onNodeWithTag("tv-exploration-dot-0").assertIsSelected()
        assertTrue(abs(bounds("tv-exploration-dot-0").center.x - viewport.center.x) < 2f)
    }

    @Test
    fun carouselWrapsAndAutoAdvanceStopsWhenHeroLosesFocus() = runAniComposeUiTest {
        mount()
        key(Key.DirectionLeft)
        settle(550)
        onNodeWithTag("tv-exploration-dot-2").assertIsSelected()
        key(Key.DirectionRight)
        settle(550)
        onNodeWithTag("tv-exploration-dot-0").assertIsSelected()
        settle(12_200)
        onNodeWithTag("tv-exploration-dot-1").assertIsSelected()
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle(13_000)
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
        settle(550)
        onNodeWithTag("tv-exploration-dot-1").assertIsSelected()
    }

    @Test
    fun carouselLoadsSubsequentTrendingPages() = runAniComposeUiTest {
        val loadedPages = mutableListOf<Int>()
        val intents = mutableListOf<TvExplorationIntent>()
        val pager = Pager(PagingConfig(pageSize = 3, initialLoadSize = 3, prefetchDistance = 1, enablePlaceholders = false)) {
            object : PagingSource<Int, TrendingSubjectInfo>() {
                override fun getRefreshKey(state: PagingState<Int, TrendingSubjectInfo>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TrendingSubjectInfo> {
                    val start = params.key ?: 0
                    loadedPages += start
                    return LoadResult.Page(
                        (start + 1..start + 3).map { TrendingSubjectInfo(it, titles[(it - 1) % titles.size], "") },
                        prevKey = null, nextKey = (start + 3).takeIf { it < 9 },
                    )
                }
            }
        }
        mount(trendingFlow = pager.flow, onIntent = { intents += it })
        for (index in 1..8) {
            key(Key.DirectionRight)
            settle(600)
            onNodeWithTag("tv-exploration-dot-$index").assertIsSelected()
        }
        key(Key.DirectionCenter)
        assertEquals(listOf(0, 3, 6), loadedPages)
        assertEquals(9, intents.filterIsInstance<TvExplorationIntent.OpenSubject>().last().subject.subjectId)
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
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        key(Key.DirectionCenter)
        assertEquals(21, intents.filterIsInstance<TvExplorationIntent.OpenSubject>().last().subject.subjectId)
    }

    @Test
    fun carouselMovesForegroundAcrossTheScreenAndKeepsOneActionFocused() = runAniComposeUiTest {
        mount()
        val left = bounds("tv-exploration-featured-title").left
        val backdrop = bounds("tv-exploration-backdrop")
        val action = bounds("tv-exploration-action-1", unmerged = true)
        val profile = pillProfile("tv-exploration-action-1")
        key(Key.DirectionRight)
        settle(100)
        onNodeWithTag("tv-exploration-details").assertIsFocused()
        val incoming = onNode(hasTestTag("tv-exploration-featured-title") and hasText(titles[1])).fetchSemanticsNode()
        val outgoing = onNode(hasTestTag("tv-exploration-featured-title") and hasText(titles[0])).fetchSemanticsNode()
        assertTrue(incoming.boundsInRoot.left > left)
        assertTrue(outgoing.boundsInRoot.left < left)
        assertEquals(backdrop, bounds("tv-exploration-backdrop"))
        val incomingAction = bounds("tv-exploration-action-2", unmerged = true)
        assertTrue(abs(action.width - incomingAction.width) < 2f)
        assertTrue(abs(action.height - incomingAction.height) < 2f)
        assertTrue(abs((incoming.boundsInRoot.left - left) - (incomingAction.left - action.left)) < 2f)
        profile.zip(pillProfile("tv-exploration-action-2")).forEach { (before, during) ->
            assertTrue(abs(before - during) <= 5, "Button silhouette changed: $before → $during")
        }
        capture("12-carousel-midpoint")
        settle(550)
        key(Key.DirectionLeft)
        settle(550)
        onNodeWithTag("tv-exploration-dot-0").assertIsSelected()
        assertEquals(1, onAllNodes(hasTestTag("tv-exploration-details")).fetchSemanticsNodes().size)
    }

    @Test
    fun bothHeroesUseDetailsTypographyWithoutDisplacingActionOrFirstShelf() = runAniComposeUiTest {
        mount(collectionTransform = { collection ->
            collection.copy(subjectInfo = collection.subjectInfo.copy(
                nameCn = if (collection.subjectId % 2 == 1) "短标题"
                else "转生之后在异世界展开的漫长冒险与新的旅程",
                summary = "相遇与告别之间，寻找属于自己的答案。".repeat(12),
            ))
        })
        val action = bounds("tv-exploration-details")
        val shelf = bounds("tv-exploration-row-followed")
        val shortStyle = textLayout("tv-exploration-featured-title").layoutInput.style
        assertEquals(TvSubjectDetailsDefaults.TitleSize, shortStyle.fontSize)
        assertEquals(TvSubjectDetailsDefaults.TitleLineHeight, shortStyle.lineHeight)
        capture("13-short-title")
        key(Key.DirectionRight)
        settle(550)
        val title = textLayout("tv-exploration-featured-title")
        assertEquals(2, title.lineCount)
        assertEquals(shortStyle, title.layoutInput.style)
        assertEquals(action, bounds("tv-exploration-details"))
        assertEquals(shelf, bounds("tv-exploration-row-followed"))
        assertTrue(textLayout("tv-exploration-featured-summary").isLineEllipsized(1))
        capture("14-long-title")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        settle()
        assertEquals(shortStyle, textLayout("tv-exploration-preview-title").layoutInput.style)
    }

    @Test
    fun followedReorderingKeepsBusinessIdentityAndLargeTextFits() = runAniComposeUiTest {
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
        assertTrue(bounds("tv-exploration-preview-title").bottom < bounds("tv-exploration-followed-12").top)
        onNodeWithTag("tv-exploration-preview-caption").assertTextContains("9", substring = true)
        capture("15-large-text-reordered")
    }

    @Test
    fun recommendationReorderingRestoresTheSameSubjectInItsNewGridRow() = runAniComposeUiTest {
        val values = (21..44).map { RecommendedSubjectInfo(it, titles[(it - 1) % titles.size], "", "") }
        val flow = MutableStateFlow(completedPage<RecommendedItemInfo>(values))
        mount(recommendationFlow = flow)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-25")
        runOnIdle { flow.value = completedPage(values.reversed()) }
        awaitFocus("tv-exploration-rec-25")
        settle()
        onNodeWithTag("tv-exploration-rec-25").assertIsFocused()
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-rec-29")
        runOnIdle { flow.value = completedPage(values.filterNot { it.bangumiId == 29 }) }
        awaitFocus("tv-exploration-rec-21")
    }

    @Test
    fun removingFocusedCollectionRecoversAndEmptyCollectionUsesNextShelf() = runAniComposeUiTest {
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
        assertTrue(glowProgress() > .99f)
    }

    @Test
    fun recommendationFailureAndRetryRemainNavigable() = runAniComposeUiTest {
        var attempts = 0
        val pager = Pager(PagingConfig(pageSize = 4)) {
            object : PagingSource<Int, RecommendedItemInfo>() {
                override fun getRefreshKey(state: PagingState<Int, RecommendedItemInfo>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RecommendedItemInfo> =
                    if (attempts++ == 0) LoadResult.Error(IOException("Offline fixture"))
                    else LoadResult.Page(listOf(RecommendedSubjectInfo(21, "重新加载的番剧", "", "")), null, null)
            }
        }
        mount(withContinue = false, recommendationFlow = pager.flow)
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-feed-status")
        settle()
        capture("16-retry")
        key(Key.DirectionCenter)
        awaitFocus("tv-exploration-rec-21")
        assertEquals(2, attempts)
        key(Key.DirectionUp)
        awaitFocus("tv-exploration-details")
    }

    @Test
    fun routeReturnRestoresHorizontalPositionAndOrdinaryShelf() = runAniComposeUiTest {
        var visible by mutableStateOf(true)
        mount(visible = { visible })
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-followed-11")
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-21")
        for (id in 22..24) {
            key(Key.DirectionRight)
            awaitFocus("tv-exploration-rec-" + id)
        }
        key(Key.DirectionDown)
        awaitFocus("tv-exploration-rec-28")
        settle()
        val original = bounds("tv-exploration-rec-28")
        runOnIdle { visible = false }
        settle()
        runOnIdle { visible = true }
        awaitFocus("tv-exploration-rec-28")
        settle()
        assertTrue(glowProgress() > .99f)
        assertTrue(abs(bounds("tv-exploration-rec-28").top - original.top) < 2f)
        assertTrue(abs(bounds("tv-exploration-rec-28").left - original.left) < 2f)
        capture("17-route-return")
    }

    @Test
    fun returningTransitionCannotReplaceTheSavedCardBeforeResume() = runAniComposeUiTest {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
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
        onNodeWithTag("tv-exploration-rec-22").performSemanticsAction(SemanticsActions.RequestFocus) {
            assertFalse(it())
        }
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        awaitFocus("tv-exploration-rec-21")
        settle()
        assertTrue(glowProgress() > .99f)
    }

    private fun AniComposeUiTest.railPixels(): IntArray {
        val width = bounds("tv-exploration").left.toInt()
        val bitmap = onNodeWithTag("tv-exploration-shell").captureToImage().asAndroidBitmap()
        return IntArray(width * bitmap.height).also {
            bitmap.getPixels(it, 0, width, 0, 0, width, bitmap.height)
        }
    }

    private fun AniComposeUiTest.pillProfile(tag: String): List<Int> {
        val bitmap = onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        return listOf(.04f, .1f, .2f, .3f).map { fraction ->
            val y = (bitmap.height * fraction).toInt()
            (0 until bitmap.width).count { x ->
                val pixel = bitmap.getPixel(x, y)
                (pixel shr 16 and 255) > 220 && (pixel shr 8 and 255) > 220 && (pixel and 255) > 220
            }
        }
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
            File(context.getExternalFilesDir(null), "tv-exploration-focus-failure-$tag.txt")
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
