/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject

import android.graphics.Bitmap
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.platform.app.InstrumentationRegistry
import com.github.panpf.sketch.Sketch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UIRichText
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.models.subject.TestSubjectProgressInfos
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.subject.collection.progress.createTestSubjectProgressState
import me.him188.ani.app.ui.subject.createTestAiringLabelState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.components.LocalTvDetailsBackdropImage
import me.him188.ani.leanback.ui.subject.components.TvDetailsBackdropImage
import me.him188.ani.utils.platform.annotations.TestOnly
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

@OptIn(TestOnly::class)
class TvSubjectDetailsUiTest {
    private fun <T : Any> completedPage(items: List<T>) = PagingData.from(
        items,
        sourceLoadStates = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true)),
    )
    private fun characters() = (1..12).map {
        RelatedCharacterInfo(it, CharacterInfo(it, "Character $it", "角色 $it",
            emptyList(), "", ""), CharacterRole.MAIN)
    }
    private fun staff() = (1..12).map {
        RelatedPersonInfo(it, PersonInfo(it, "Staff $it", PersonType.Individual, emptyList(),
            "", "", "", null), PersonPosition.Director)
    }
    private fun reviews() = (1..36).map {
        UIComment(it.toLong(), "review-$it", UserInfo("$it", null, "观众 $it"),
            UIRichText(listOf(UIRichElement.AnnotatedText(listOf(
                UIRichElement.Annotated.Text("比赛的临场感非常出色，角色在赛场上全力奔跑的瞬间让人难忘。音乐、演出和人物的成长都很有感染力。"),
                UIRichElement.Annotated.Text("未公开的剧情 $it", mask = true),
            )))),
            1_720_000_000_000L, emptyList(), emptyList(), 0, 8,
        )
    }
    private fun reviewContent() = content().let {
        it.copy(info = it.info.copy(ratingInfo = RatingInfo(310, 3703,
            RatingCounts(s1 = 9, s2 = 8, s3 = 10, s4 = 7, s5 = 10, s6 = 114, s7 = 700, s8 = 1900, s9 = 531, s10 = 414), "7.9")),
            commentsPager = flowOf(completedPage(reviews())), commentCount = 128)
    }
    private fun content() = TvSubjectDetailsContentState(
        info = SubjectInfo.Empty.copy(subjectId = 42, name = "劇場版 『ウマ娘 プリティーダービー 新時代の扉』",
            nameCn = "", summary = ("自由気ままなフリースタイル・レースで、最強を目指して走り続けてきたウマ娘の少女。\n").repeat(12),
            airDate = PackedDate(2024, 5, 24), tags = listOf(Tag("2024年5月", 100), Tag("运动", 80), Tag("剧场版", 60)),
            ratingInfo = RatingInfo.Empty.copy(score = "8.3"),
            aliases = listOf("赛马娘 新时代之扉", "Uma Musume: Beginning of a New Era")),
        episodes = (1..40).map { EpisodeListItem(it, EpisodeSort(it), null, "Episode $it", "第 $it 话",
            UnifiedCollectionType.NOT_COLLECTED, true) },
        episodesLoading = false, playTargetId = 27, watchedCount = 2,
        exposedCharactersPager = flowOf(completedPage(characters())), totalCharactersCount = 12,
        exposedStaffPager = flowOf(completedPage(staff())), totalStaffCount = 12,
        relatedSubjectsPager = flowOf(completedPage((1..8).map {
            RelatedSubjectInfo(100 + it, SubjectRelation.SEQUEL, "Related anime $it", "关联条目 $it", "")
        })),
        commentsPager = flowOf(PagingData.empty()), commentCount = 0,
        collectionType = UnifiedCollectionType.DOING, selfRating = SelfRatingInfo(7, "existing review", emptyList(), true),
        mainEpisodeIds = (1..40).toSet(),
        progress = createTestSubjectProgressState(TestSubjectProgressInfos.ContinueWatching2),
        airing = createTestAiringLabelState(SubjectAiringInfo.EmptyCompleted.copy(mainEpisodeCount = 40)),
    )

    private fun AniComposeUiTest.mount(
        state: () -> TvSubjectDetailsUiState,
        onIntent: (TvSubjectDetailsIntent) -> Unit = {},
        width: Int = 960,
        initialTag: String = "tv-details-play",
        fontScale: Float? = null,
        reference: Boolean = false,
        inset: Int = 0,
        backdropImage: TvDetailsBackdropImage? = null,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sketch = Sketch.Builder(context).build()
        val timeFormatter = TimeFormatter()
        val image = if (reference) {
            val file = File(context.cacheDir, "tv-details-poster.jpg")
            InstrumentationRegistry.getInstrumentation().context.assets.open("tv-details-poster.jpg")
                .use { input -> file.outputStream().use { input.copyTo(it) } }
            file.toURI().toString()
        } else ""
        setContent {
            DisposableEffect(sketch) { onDispose { sketch.shutdown() } }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalSketch provides sketch,
                LocalTimeFormatter provides timeFormatter,
                LocalTvDetailsBackdropImage provides backdropImage,
                LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                AniTvTheme {
                    Box(Modifier.padding(start = inset.dp, top = inset.dp).width(width.dp)) {
                        TvSubjectDetailsScreen(state().let {
                            if (reference) it.copy(images = TvSubjectImages(backdrop = TvBackdropState(image))) else it
                        }, onIntent)
                    }
                }
            }
        }
        awaitFocus(initialTag)
    }

    @Test fun heroActionsBlurTheirBackdropAndPlayFocusAddsGlow() = runAniComposeUiTest {
        val pattern = Bitmap.createBitmap(
            IntArray(960 * 540) { index ->
                if ((index % 960) / 4 % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF101010.toInt()
            },
            960, 540, Bitmap.Config.ARGB_8888,
        ).asImageBitmap()
        val backdrop = TvDetailsBackdropImage("test:striped-backdrop", pattern)
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true,
            images = TvSubjectImages(backdrop = TvBackdropState(backdrop.url))) }, backdropImage = backdrop)
        val page = onNodeWithTag("tv-subject-details").fetchSemanticsNode().boundsInRoot
        val play = onNodeWithTag("tv-details-play").fetchSemanticsNode().boundsInRoot
        val density = page.width / 960f
        val focused = onNodeWithTag("tv-subject-details").captureToImage().toPixelMap()
        key(Key.DirectionUp)
        onNodeWithTag("tv-details-summary").assertIsFocused()
        val unfocused = onNodeWithTag("tv-subject-details").captureToImage().toPixelMap()
        capture("frosted-actions-pattern", "tv-subject-details")

        fun contrast(bounds: Rect, verticalOffset: Float): Float {
            val y = (bounds.top - page.top + verticalOffset * density).toInt()
            val start = (bounds.left - page.left + 24 * density).toInt()
            val end = (bounds.right - page.left - 24 * density).toInt()
            val values = (start..end).map { unfocused[it, y].red }
            return values.max() - values.min()
        }
        for (action in listOf("play", "collection", "rating")) {
            val bounds = onNodeWithTag("tv-details-$action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val backgroundContrast = contrast(bounds, -10f)
            assertTrue(backgroundContrast > .12f, "The backdrop outside $action must stay sharp")
            assertTrue(contrast(bounds, 4f) < backgroundContrast * .35f,
                "The $action background must blur the stripes inside its pill")
            assertTrue(contrast(bounds, 20f) > .25f, "The $action foreground must stay crisp")
        }
        val haloX = (play.left - page.left - 6 * density).toInt()
        val haloY = (play.center.y - page.top).toInt()
        val haloDifference = (-8..8).map { delta ->
            focused[haloX, haloY + delta].red - unfocused[haloX, haloY + delta].red
        }.average()
        assertTrue(haloDifference > .03, "Only the focused play button must emit an outer glow")
        key(Key.DirectionDown)
        onNodeWithTag("tv-details-play").assertIsFocused()
        assertEquals(play, onNodeWithTag("tv-details-play").fetchSemanticsNode().boundsInRoot,
            "The glow must not change the button bounds")
    }

    @Test fun overviewMatchesLauncherGeometryAndShowsEpisodesBelowActions() = runAniComposeUiTest {
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) }, { intents += it }, reference = true)
        onNodeWithText("2024-05").assertIsDisplayed()
        onNodeWithText("运动").assertIsDisplayed()
        onNodeWithTag("tv-details-all-episodes").assertExists()
        onNodeWithTag("tv-details-comments-all").assertDoesNotExist()
        val page = onNodeWithTag("tv-subject-details").fetchSemanticsNode().boundsInRoot
        val title = onNodeWithTag("tv-details-title").fetchSemanticsNode().boundsInRoot
        val card = onNodeWithTag("tv-details-summary").fetchSemanticsNode().boundsInRoot
        val play = onNodeWithTag("tv-details-play").fetchSemanticsNode().boundsInRoot
        assertTrue(title.left / page.width in .055f.. .070f)
        assertTrue(card.width / page.width in .41f.. .46f)
        assertTrue(title.bottom < card.top && card.bottom < play.top)
        assertTrue(play.bottom / page.height in .80f.. .90f)
        capture("overview", "tv-subject-details")
        capture("description-card-unfocused", "tv-details-summary")
        key(Key.DirectionCenter)
        assertEquals(TvSubjectDetailsIntent.Resume, intents.single())
        key(Key.DirectionUp)
        onNodeWithTag("tv-details-summary").assertIsFocused()
        capture("overview-description-focus", "tv-subject-details")
        capture("description-card-focused", "tv-details-summary")
    }

    @Test fun episodesResumePlayAndRestoreBetweenHeroCharactersAndAllEpisodes() = runAniComposeUiTest {
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) }, { intents += it }, reference = true)
        key(Key.DirectionDown)
        awaitFocus("tv-details-episode:27")
        onNodeWithTag("tv-details-episode:27").assertIsDisplayed()
        val episode = onNodeWithTag("tv-details-episode:27").fetchSemanticsNode().boundsInRoot
        val characters = onNodeWithTag("tv-details-characters-heading").fetchSemanticsNode().boundsInRoot
        assertTrue(episode.bottom < characters.top, "Episodes must appear above characters")
        capture("episodes", "tv-subject-details")
        key(Key.DirectionRight)
        awaitFocus("tv-details-episode:28")
        key(Key.DirectionCenter)
        assertEquals(TvSubjectDetailsIntent.PlayEpisode(28), intents.single())
        key(Key.DirectionDown)
        awaitFocus("tv-details-character:1")
        key(Key.DirectionUp)
        awaitFocus("tv-details-episode:28")
        key(Key.Back)
        awaitFocus("tv-details-play")
        key(Key.DirectionDown)
        awaitFocus("tv-details-episode:28")
        key(Key.DirectionUp)
        awaitFocus("tv-details-all-episodes")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-episode:28")
        assertNoDialogWindow()
        capture("episodes-panel", "tv-details-panel")
        key(Key.DirectionCenter)
        assertEquals(listOf<TvSubjectDetailsIntent>(TvSubjectDetailsIntent.PlayEpisode(28), TvSubjectDetailsIntent.PlayEpisode(28)), intents)
        key(Key.Back)
        awaitFocus("tv-details-all-episodes")
        key(Key.DirectionDown)
        awaitFocus("tv-details-episode:28")
    }

    @Test fun longPressEpisodeMarksItWithoutStartingPlayback() = runAniComposeUiTest {
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) }, { intents += it })
        key(Key.DirectionDown)
        awaitFocus("tv-details-episode:27")
        onNodeWithTag("tv-details-episode:27").performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(1_000)
            keyUp(Key.DirectionCenter)
        }
        waitForIdle()
        assertEquals(27, (intents.single() as TvSubjectDetailsIntent.ToggleEpisode).episodeId)
        onNodeWithTag("tv-details-episode:27").assertIsFocused()
    }

    @Test fun episodeLoadingAndEmptyStatesKeepNavigationAvailable() = runAniComposeUiTest {
        val details = content()
        var state by mutableStateOf(TvSubjectDetailsUiState(content = details.copy(episodes = emptyList(), episodesLoading = true)))
        mount({ state })
        key(Key.DirectionDown)
        awaitFocus("tv-details-all-episodes")
        onNodeWithTag("tv-details-episodes-loading").assertIsDisplayed()
        key(Key.DirectionDown)
        awaitFocus("tv-details-character:1")
        runOnIdle { state = state.copy(content = details) }
        onNodeWithTag("tv-details-character:1").assertIsFocused()
        key(Key.DirectionUp)
        awaitFocus("tv-details-episode:27")
        runOnIdle { state = state.copy(content = details.copy(episodes = emptyList())) }
        awaitFocus("tv-details-all-episodes")
        onNodeWithTag("tv-details-episodes-loading").assertDoesNotExist()
        key(Key.DirectionDown)
        awaitFocus("tv-details-character:1")
        key(Key.DirectionUp)
        awaitFocus("tv-details-all-episodes")
        key(Key.DirectionUp)
        awaitFocus("tv-details-play")
    }

    @Test fun episodeReorderingAndRemovalPreserveBusinessIdentityAndNeighbour() = runAniComposeUiTest {
        var details by mutableStateOf(content())
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) })
        key(Key.DirectionDown)
        awaitFocus("tv-details-episode:27")
        runOnIdle { details = details.copy(episodes = details.episodes.reversed()) }
        awaitFocus("tv-details-episode:27")
        onNodeWithTag("tv-details-episode:27").assertIsDisplayed()
        runOnIdle { details = details.copy(episodes = details.episodes.filter { it.episodeId != 27 }) }
        awaitFocus("tv-details-episode:26")
        key(Key.Back)
        awaitFocus("tv-details-play")
        key(Key.DirectionDown)
        awaitFocus("tv-details-episode:26")
    }

    @Test fun descriptionScrollsUnderFixedHeaderAndReturnsToItsEntry() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) }, reference = true)
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        awaitFocus("tv-description-tag:2024年5月")
        key(Key.DirectionDown)
        awaitFocus("tv-details-panel-summary-text")
        assertNoDialogWindow()
        val header = onNodeWithTag("tv-description-title").fetchSemanticsNode().boundsInRoot
        val tags = onNodeWithTag("tv-description-tags").fetchSemanticsNode().boundsInRoot
        val body = onNodeWithTag("tv-details-panel-summary-text").fetchSemanticsNode().boundsInRoot
        assertTrue(tags.bottom < body.top, "Tags must stay above the scrollable description")
        capture("description-top", "tv-details-panel")
        repeat(18) { key(Key.DirectionDown) }
        assertEquals(header, onNodeWithTag("tv-description-title").fetchSemanticsNode().boundsInRoot)
        assertEquals(tags, onNodeWithTag("tv-description-tags").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        onNodeWithTag("tv-description-info").assertIsDisplayed()
        capture("description-bottom", "tv-details-panel")
        key(Key.Back)
        awaitFocus("tv-details-summary")
        key(Key.DirectionDown)
        awaitFocus("tv-details-play")
    }

    @Test fun bangumiScoreOpensCommentsAndRestoresItsFocus() = runAniComposeUiTest {
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = false) }, { intents += it }, reference = true)
        capture("bgm-rating-unfocused", "tv-subject-details")
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        awaitFocus("tv-details-bgm-rating")
        capture("bgm-rating-focused", "tv-subject-details")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-review-status")
        assertNoDialogWindow()
        onNodeWithTag("tv-comments-title").assertIsDisplayed()
        assertTrue(intents.isEmpty())
        capture("comments", "tv-reviews-page")
        key(Key.Back)
        awaitFocus("tv-details-bgm-rating")
        key(Key.DirectionDown)
        awaitFocus("tv-details-summary")
        key(Key.DirectionUp)
        key(Key.Back)
        awaitFocus("tv-details-play")
    }

    @Test fun reviewsKeepOverviewFixedAndRestoreTheSelectedComment() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = reviewContent(), loggedIn = true) }, reference = true)
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review:review-1")
        assertNoDialogWindow()
        onNodeWithTag("tv-comments-title").assertTextContains("Review")
        onNodeWithTag("tv-review-count").assertTextContains("128", substring = true)
        onNodeWithTag("tv-review-votes").assertTextContains("3,703", substring = true)
        val stars = onNodeWithTag("tv-review-stars").fetchSemanticsNode().boundsInRoot
        val votes = onNodeWithTag("tv-review-votes").fetchSemanticsNode().boundsInRoot
        val average = onNodeWithTag("tv-review-average").fetchSemanticsNode().boundsInRoot
        assertEquals(stars.left, votes.left)
        assertTrue(votes.top >= stars.bottom && stars.left > average.right)
        onNodeWithTag("tv-review-score:review-1", useUnmergedTree = true).assertTextEquals("8")
        onNodeWithText("未公开的剧情 1", substring = true).assertDoesNotExist()
        val overview = onNodeWithTag("tv-review-overview").fetchSemanticsNode().boundsInRoot
        val header = onNodeWithTag("tv-comments-title").fetchSemanticsNode().boundsInRoot
        capture("reviews", "tv-reviews-page")
        key(Key.DirectionDown)
        awaitFocus("tv-details-review:review-2")
        val second = onNodeWithTag("tv-details-review:review-2").fetchSemanticsNode().boundsInRoot
        val viewport = onNodeWithTag("tv-review-list").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(second.center.y - viewport.center.y) <= 1.5f, "The second review should move to the viewport center")
        repeat(8) { key(Key.DirectionDown) }
        awaitFocus("tv-details-review:review-10")
        assertEquals(overview, onNodeWithTag("tv-review-overview").fetchSemanticsNode().boundsInRoot)
        assertEquals(header, onNodeWithTag("tv-comments-title").fetchSemanticsNode().boundsInRoot)
        val comment = onNodeWithTag("tv-details-review:review-10").fetchSemanticsNode().boundsInRoot
        assertTrue(comment.top >= viewport.top && comment.bottom <= viewport.bottom)
        assertTrue(abs(comment.center.y - viewport.center.y) <= 1.5f, "A scrolled review should stay centered")
        key(Key.DirectionLeft)
        awaitFocus("tv-details-review-rating")
        key(Key.DirectionRight)
        awaitFocus("tv-details-review:review-10")
        assertEquals(comment, onNodeWithTag("tv-details-review:review-10").fetchSemanticsNode().boundsInRoot)
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-comment-text")
        assertNoDialogWindow()
        val popup = onNodeWithTag("tv-review-reader-popup").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val page = onNodeWithTag("tv-details-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(popup.width < page.width * .7f && popup.height < page.height)
        assertTrue(abs(popup.center.x - page.center.x) <= 1.5f && abs(popup.center.y - page.center.y) <= 1.5f)
        val actions = onNodeWithTag("tv-review-actions").fetchSemanticsNode().boundsInRoot
        val original = onNodeWithTag("tv-details-panel-original").fetchSemanticsNode().boundsInRoot
        assertTrue(original.right <= actions.right, "The two common review actions should fit together")
        capture("review-reader", "tv-details-panel")
        key(Key.DirectionDown)
        awaitFocus("tv-details-panel-reveal")
        key(Key.DirectionRight)
        awaitFocus("tv-details-panel-original")
        key(Key.DirectionUp)
        awaitFocus("tv-details-panel-comment-text")
        key(Key.DirectionDown)
        awaitFocus("tv-details-panel-original")
        key(Key.Back)
        awaitFocus("tv-details-review:review-10")
        assertEquals(comment, onNodeWithTag("tv-details-review:review-10").fetchSemanticsNode().boundsInRoot)
        key(Key.Back)
        awaitFocus("tv-details-bgm-rating")
    }

    @Test fun reviewsGainRemoteFocusAfterOpeningWithAPointer() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = reviewContent(), loggedIn = true) })
        onNodeWithTag("tv-details-bgm-rating").performTouchInput { click() }
        onNodeWithTag("tv-comments-title").assertIsDisplayed()
        awaitFocus("tv-details-review:review-1")
        key(Key.DirectionDown)
        awaitFocus("tv-details-review:review-2")
    }

    @Test fun reviewsAnimateAndNewNavigationContinuesFromTheFocusedCard() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = reviewContent(), loggedIn = true) }, reference = true)
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review:review-1")
        val viewport = onNodeWithTag("tv-review-list").fetchSemanticsNode().boundsInRoot
        val before = onNodeWithTag("tv-details-review:review-2").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        key(Key.DirectionDown)
        mainClock.advanceTimeBy(64)
        onNodeWithTag("tv-details-review:review-2").assertIsFocused()
        val during = onNodeWithTag("tv-details-review:review-2").fetchSemanticsNode().boundsInRoot
        assertTrue(during.center.y < before.center.y && during.center.y > viewport.center.y + 2f,
            "Focus scrolling must pass through intermediate positions")
        capture("reviews-scroll-in-progress", "tv-reviews-page")
        key(Key.DirectionDown)
        mainClock.autoAdvance = true
        waitForIdle()
        awaitFocus("tv-details-review:review-3")
        val third = onNodeWithTag("tv-details-review:review-3").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(third.center.y - viewport.center.y) <= 1.5f)
        key(Key.DirectionLeft)
        awaitFocus("tv-details-review-rating")
        key(Key.DirectionRight)
        awaitFocus("tv-details-review:review-3")
    }

    @Test fun reviewReaderKeepsActionsVisibleAndRestoresFromReport() = runAniComposeUiTest {
        val comments = reviews().toMutableList()
        comments[0] = UIComment(1L, "review-1", UserInfo("1", null, "观众 1"),
            UIRichText(listOf(UIRichElement.AnnotatedText(listOf(
            UIRichElement.Annotated.Text("赛场上的故事与角色的成长，都值得仔细回味。\n".repeat(24)),
            UIRichElement.Annotated.Text("隐藏的结局", mask = true),
        )))), 1_720_000_000_000L, emptyList(), emptyList(), 0, 8)
        val details = reviewContent().copy(commentsPager = flowOf(completedPage(comments)), canReport = true)
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) }, reference = true)
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review:review-1")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-comment-text")
        val footer = onNodeWithTag("tv-review-actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val text = onNodeWithTag("tv-details-panel-comment-text").fetchSemanticsNode().boundsInRoot
        assertTrue(text.bottom < footer.top)
        key(Key.DirectionDown)
        onNodeWithTag("tv-details-panel-comment-text").assertIsFocused()
        assertEquals(footer, onNodeWithTag("tv-review-actions").fetchSemanticsNode().boundsInRoot)
        repeat(24) { key(Key.DirectionDown) }
        awaitFocus("tv-details-panel-reveal")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-reveal")
        capture("review-actions", "tv-details-panel")
        key(Key.DirectionRight)
        awaitFocus("tv-details-panel-report")
        key(Key.DirectionUp)
        awaitFocus("tv-details-panel-comment-text")
        key(Key.DirectionDown)
        awaitFocus("tv-details-panel-report")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-report-info")
        key(Key.Back)
        awaitFocus("tv-details-panel-report")
        key(Key.DirectionRight)
        awaitFocus("tv-details-panel-original")
        onNodeWithTag("tv-details-panel-original").assertIsDisplayed()
        key(Key.Back)
        awaitFocus("tv-details-review:review-1")
    }

    @Test fun reviewsClampCenteringAtTheFirstAndLastItems() = runAniComposeUiTest {
        val details = reviewContent().copy(commentsPager = flowOf(completedPage(reviews().take(3))))
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) })
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review:review-1")
        val viewport = onNodeWithTag("tv-review-list").fetchSemanticsNode().boundsInRoot
        val first = onNodeWithTag("tv-details-review:review-1").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top >= viewport.top && first.top - viewport.top < viewport.height * .03f)
        repeat(2) { key(Key.DirectionDown) }
        awaitFocus("tv-details-review:review-3")
        val last = onNodeWithTag("tv-details-review:review-3").fetchSemanticsNode().boundsInRoot
        assertTrue(last.bottom <= viewport.bottom && viewport.bottom - last.bottom < viewport.height * .03f)
    }

    @Test fun reviewSystemBackRestoresTheReviewBeforeLeavingThePage() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = reviewContent(), loggedIn = true) })
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review:review-1")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-comment-text")
        // Exercise Android's actual Back dispatch, not only Compose's synthetic key pipeline.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(AndroidKeyEvent.KEYCODE_BACK)
        awaitFocus("tv-details-review:review-1")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(AndroidKeyEvent.KEYCODE_BACK)
        awaitFocus("tv-details-bgm-rating")
    }

    @Test fun reviewRatingReusesTheEditorAndReturnsToItsAnchor() = runAniComposeUiTest {
        var state by mutableStateOf(TvSubjectDetailsUiState(content = reviewContent(), loggedIn = true))
        val scores = mutableListOf<TvSubjectDetailsIntent.SetScore>()
        mount({ state }, { intent ->
            if (intent is TvSubjectDetailsIntent.SetScore) {
                scores += intent
                state = state.copy(content = state.content!!.copy(selfRating = SelfRatingInfo(intent.score, "", emptyList(), false)),
                    operation = TvSubjectOperation(intent.requestId, completed = true))
            }
        }, reference = true)
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review:review-1")
        key(Key.DirectionDown)
        awaitFocus("tv-details-review:review-2")
        key(Key.DirectionLeft)
        awaitFocus("tv-details-review-rating")
        val source = onNodeWithTag("tv-details-review-rating").fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-rating-control")
        assertAnchoredOverlay(source)
        onNodeWithTag("tv-review-count").assertDoesNotExist()
        key(Key.DirectionRight)
        capture("review-rating", "tv-details-panel")
        key(Key.Back)
        awaitFocus("tv-details-review-rating")
        assertTrue(scores.isEmpty())
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-rating-control")
        onNodeWithTag("tv-rating-description", useUnmergedTree = true).assertTextContains("7", substring = true)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        awaitFocus("tv-details-review-rating")
        assertEquals(8, scores.single().score)
        onNodeWithTag("tv-review-count").assertTextContains("128", substring = true)
        key(Key.DirectionRight)
        awaitFocus("tv-details-review:review-2")
    }

    @Test fun reviewRatingRemainsFocusableBeforeCollection() = runAniComposeUiTest {
        val details = reviewContent().copy(collectionType = UnifiedCollectionType.NOT_COLLECTED, selfRating = SelfRatingInfo.Empty)
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) }, { intents += it })
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review:review-1")
        key(Key.DirectionLeft)
        awaitFocus("tv-details-review-rating")
        onNodeWithTag("tv-details-review-rating").assertIsNotEnabled()
        onNodeWithTag("tv-rating-collection-tooltip").assertIsDisplayed()
        key(Key.DirectionCenter)
        assertTrue(intents.isEmpty())
        onNodeWithTag("tv-details-panel-rating-control").assertDoesNotExist()
        key(Key.DirectionRight)
        awaitFocus("tv-details-review:review-1")
        onNodeWithTag("tv-rating-collection-tooltip").assertDoesNotExist()
    }

    @Test fun reviewsRestoreByIdentityAndChooseANeighborAfterRemoval() = runAniComposeUiTest {
        val pages = MutableStateFlow(completedPage(reviews()))
        val details = reviewContent().copy(commentsPager = pages)
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) })
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review:review-1")
        repeat(7) { key(Key.DirectionDown) }
        awaitFocus("tv-details-review:review-8")
        runOnIdle { pages.value = completedPage(reviews().filterNot { it.id == 8L }) }
        awaitFocus("tv-details-review:review-9")
        runOnIdle { pages.value = completedPage(reviews().reversed()) }
        awaitFocus("tv-details-review:review-9")
        key(Key.DirectionLeft)
        key(Key.DirectionRight)
        awaitFocus("tv-details-review:review-9")
    }

    @Test fun reviewsDoNotStealFocusWhenLoadingCompletes() = runAniComposeUiTest {
        val pages = MutableStateFlow(PagingData.empty<UIComment>(sourceLoadStates =
            LoadStates(LoadState.Loading, LoadState.NotLoading(true), LoadState.NotLoading(false))))
        val details = reviewContent().copy(commentsPager = pages, commentCount = null)
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) })
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review-status")
        key(Key.DirectionLeft)
        awaitFocus("tv-details-review-rating")
        runOnIdle {
            pages.value = PagingData.from(reviews(), sourceLoadStates =
                LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(false)))
        }
        waitForIdle()
        onNodeWithTag("tv-details-review-rating").assertIsFocused()
        onNodeWithTag("tv-review-count").assertTextContains("36+", substring = true)
        runOnIdle { pages.value = completedPage(reviews()) }
        waitForIdle()
        onNodeWithTag("tv-details-review-rating").assertIsFocused()
        key(Key.DirectionRight)
        awaitFocus("tv-details-review:review-1")
        onNodeWithTag("tv-review-count").assertTextContains("36", substring = true)
        assertTrue(onNodeWithTag("tv-review-count").fetchSemanticsNode().config[SemanticsProperties.Text].none { '+' in it.text })
    }

    @Test fun reviewsRetryAFailedLoadWithTheRemote() = runAniComposeUiTest {
        var fail by mutableStateOf(true)
        val pager = Pager(PagingConfig(pageSize = 20)) {
            object : PagingSource<Int, UIComment>() {
                override fun getRefreshKey(state: PagingState<Int, UIComment>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UIComment> =
                    if (fail) LoadResult.Error(IOException("offline"))
                    else LoadResult.Page(reviews(), prevKey = null, nextKey = null)
            }
        }
        val details = reviewContent().copy(commentsPager = pager.flow, commentCount = null)
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) })
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review-status")
        runOnIdle { fail = false }
        key(Key.DirectionCenter)
        awaitFocus("tv-details-review:review-1")
    }

    @Test fun reviewsSupportLargeTextInANarrowViewport() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = reviewContent(), loggedIn = true) }, width = 640, fontScale = 1.3f, reference = true)
        focusAndClick("bgm-rating")
        awaitFocus("tv-details-review:review-1")
        val overview = onNodeWithTag("tv-review-overview").fetchSemanticsNode().boundsInRoot
        val list = onNodeWithTag("tv-review-list").fetchSemanticsNode().boundsInRoot
        assertTrue(overview.right < list.left)
        onNodeWithTag("tv-review-histogram").assertIsDisplayed()
        assertEquals(onNodeWithTag("tv-review-stars").fetchSemanticsNode().boundsInRoot.left,
            onNodeWithTag("tv-review-votes").fetchSemanticsNode().boundsInRoot.left)
        val labels = mutableListOf<TextLayoutResult>()
        onNodeWithText("10", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(labels) }
        assertEquals(1, labels.single().lineCount)
        assertTrue(!labels.single().hasVisualOverflow, "Histogram label must fit: ${labels.single().size}, ${labels.single().layoutInput.style}")
        key(Key.DirectionDown)
        awaitFocus("tv-details-review:review-2")
        key(Key.DirectionLeft)
        awaitFocus("tv-details-review-rating")
        val source = onNodeWithTag("tv-details-review-rating").fetchSemanticsNode().boundsInRoot
        capture("reviews-large-font", "tv-reviews-page")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-rating-control")
        assertAnchoredOverlay(source)
        key(Key.Back)
        awaitFocus("tv-details-review-rating")
        key(Key.DirectionRight)
        awaitFocus("tv-details-review:review-2")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-comment-text")
        repeat(8) { key(Key.DirectionDown) }
        awaitFocus("tv-details-panel-reveal")
        key(Key.DirectionRight)
        awaitFocus("tv-details-panel-original")
        val footer = onNodeWithTag("tv-review-actions").fetchSemanticsNode().boundsInRoot
        val action = onNodeWithTag("tv-details-panel-original").fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= footer.left && action.right <= footer.right)
        capture("review-actions-large-font", "tv-details-panel")
    }

    @Test fun browseHeadingsGrowOnlyForTheFocusedRow() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) })
        val sections = listOf("episodes", "characters", "staff", "related", "info")
        fun assertHeadings(focused: String? = null) {
            sections.forEach { section ->
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithTag("tv-details-$section-heading", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                    it(layouts)
                }
                assertEquals(if (section == focused) 26.sp else 16.sp, layouts.single().layoutInput.style.fontSize, section)
            }
        }
        fun relatedHeadingBrightness(): Float {
            val pixels = onNodeWithTag("tv-details-related-heading").captureToImage().toPixelMap()
            var brightest = 0f
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                brightest = maxOf(brightest, pixels[x, y].red)
            }
            return brightest
        }
        assertHeadings()
        key(Key.DirectionDown)
        awaitFocus("tv-details-episode:27")
        assertHeadings("episodes")
        key(Key.DirectionDown)
        awaitFocus("tv-details-character:1")
        assertHeadings("characters")
        key(Key.DirectionRight)
        assertHeadings("characters")
        key(Key.DirectionDown)
        awaitFocus("tv-details-staff:1:${PersonPosition.Director}")
        assertHeadings("staff")
        key(Key.DirectionDown)
        awaitFocus("tv-details-related:101")
        assertHeadings("related")
        val normalBrightness = relatedHeadingBrightness()
        key(Key.DirectionDown)
        awaitFocus("tv-details-info")
        assertHeadings("info")
        assertTrue(relatedHeadingBrightness() < normalBrightness * .75f, "Surrounding content must fade while reading information")
        key(Key.DirectionUp)
        awaitFocus("tv-details-related:101")
        assertHeadings("related")
        assertTrue(relatedHeadingBrightness() > normalBrightness * .95f, "Surrounding content must recover after focus leaves information")
        key(Key.DirectionUp)
        awaitFocus("tv-details-staff:1:${PersonPosition.Director}")
        assertHeadings("staff")
        key(Key.Back)
        awaitFocus("tv-details-play")
        assertHeadings()
    }

    @Test fun informationBelowRelatedIsOneReadingTarget() = runAniComposeUiTest {
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        val details = content()
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) }, { intents += it }, reference = true)
        repeat(4) { key(Key.DirectionDown) }
        awaitFocus("tv-details-related:101")
        key(Key.DirectionRight)
        awaitFocus("tv-details-related:102")
        val headingBefore = mutableListOf<TextLayoutResult>()
        onNodeWithTag("tv-details-info-heading", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(headingBefore) }
        key(Key.DirectionDown)
        awaitFocus("tv-details-info")
        onNodeWithTag("tv-details-info").assertIsDisplayed()
            .assertTextContains("Information")
            .assertTextContains("2024/5/24")
            .assertTextContains("40")
            .assertTextContains(details.info.aliases.joinToString(" / "))
        onAllNodes(hasAnyAncestor(hasTestTag("tv-details-info")) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused), useUnmergedTree = true).assertCountEquals(0)
        val headingAfter = mutableListOf<TextLayoutResult>()
        onNodeWithTag("tv-details-info-heading", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(headingAfter) }
        assertEquals(16.sp, headingBefore.single().layoutInput.style.fontSize)
        assertEquals(26.sp, headingAfter.single().layoutInput.style.fontSize)
        capture("information", "tv-subject-details")
        capture("information-block", "tv-details-info")
        key(Key.DirectionRight)
        key(Key.DirectionLeft)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        onNodeWithTag("tv-details-info").assertIsFocused()
        onNodeWithTag("tv-details-panel").assertDoesNotExist()
        assertTrue(intents.isEmpty())
        key(Key.DirectionUp)
        awaitFocus("tv-details-related:102")
        capture("information-related-restored", "tv-subject-details")
        key(Key.DirectionDown)
        awaitFocus("tv-details-info")
        key(Key.Back)
        awaitFocus("tv-details-play")
    }

    @Test fun collectionOverlayCopiesItsTriggerAndKeepsFocusAboveTheUnderlay() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) }, reference = true, width = 900, inset = 24)
        val source = onNodeWithTag("tv-details-collection").fetchSemanticsNode().boundsInRoot
        focusAndClick("collection")
        awaitFocus("tv-details-panel-collection:DOING")
        assertNoDialogWindow()
        assertAnchoredOverlay(source)
        onNodeWithTag("tv-details-play").assertDoesNotExist()
        capture("collection-anchored", "tv-details-panel")
        repeat(8) { key(Key.DirectionDown) }
        awaitFocus("tv-details-overlay-trigger")
        key(Key.DirectionDown)
        onNodeWithTag("tv-details-overlay-trigger").assertIsFocused()
        key(Key.DirectionUp)
        onNodeWithTag("tv-details-panel-collection:NOT_COLLECTED").assertIsFocused()
        key(Key.Back)
        awaitFocus("tv-details-collection")
        onNodeWithTag("tv-details-play").assertIsDisplayed()
    }

    @Test fun ratingOverlayFitsAboveItsTriggerWithLargeTextAndAnOffsetViewport() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) },
            reference = true, width = 640, fontScale = 1.3f, inset = 24)
        val source = onNodeWithTag("tv-details-rating").fetchSemanticsNode().boundsInRoot
        focusAndClick("rating")
        awaitFocus("tv-details-panel-rating-control")
        assertNoDialogWindow()
        assertAnchoredOverlay(source)
        onNodeWithTag("tv-details-panel-rating-submit").assertDoesNotExist()
        onNodeWithTag("tv-details-panel-rating-cancel").assertDoesNotExist()
        onNodeWithTag("tv-rating-controls-hint").assertIsDisplayed()
        capture("rating-anchored-large-font", "tv-details-panel")
        key(Key.DirectionDown)
        awaitFocus("tv-details-overlay-trigger")
        key(Key.DirectionUp)
        awaitFocus("tv-details-panel-rating-control")
        key(Key.Back)
        awaitFocus("tv-details-rating")
    }

    @Test fun relatedLoadingFinishesForPopulatedAndEmptyResponses() = runAniComposeUiTest {
        val related = MutableStateFlow(PagingData.empty<RelatedSubjectInfo>(
            sourceLoadStates = LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false)),
        ))
        val state = TvSubjectDetailsUiState(content = content().copy(relatedSubjectsPager = related), loggedIn = true)
        mount({ state }, reference = true)
        onNodeWithTag("tv-details-related-loading").assertExists()
        repeat(4) { key(Key.DirectionDown) }
        awaitFocus("tv-details-info")
        key(Key.DirectionUp)
        awaitFocus("tv-details-staff:1:${PersonPosition.Director}")
        key(Key.Back)
        awaitFocus("tv-details-play")
        runOnIdle {
            related.value = completedPage(listOf(
                RelatedSubjectInfo(101, SubjectRelation.SEQUEL, "Related anime", "关联条目", ""),
            ))
        }
        repeat(4) { key(Key.DirectionDown) }
        awaitFocus("tv-details-related:101")
        onNodeWithTag("tv-details-related-loading").assertDoesNotExist()
        capture("related-loaded-single", "tv-subject-details")
        runOnIdle { related.value = completedPage(emptyList()) }
        awaitFocus("tv-details-relateds-all")
        onNodeWithTag("tv-details-relateds-all").assertIsDisplayed()
        val emptyLayout = mutableListOf<TextLayoutResult>()
        onNodeWithTag("tv-details-relateds-all").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(emptyLayout) }
        assertEquals(TvSubjectDetailsDefaults.SecondaryContent, emptyLayout.single().layoutInput.style.color)
        capture("related-empty", "tv-subject-details")
        onNodeWithTag("tv-details-related-loading").assertDoesNotExist()
        key(Key.DirectionDown)
        awaitFocus("tv-details-info")
        key(Key.DirectionUp)
        awaitFocus("tv-details-relateds-all")
    }

    @Test fun tagsOpenSearchAndBackReturnsToDescription() = runAniComposeUiTest {
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        val details = content().let {
            it.copy(info = it.info.copy(tags = it.info.tags +
                listOf("友情与团队", "坚持梦想", "竞技成长", "热血比赛").map { name -> Tag(name, 20) }))
        }
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) }, { intents += it }, width = 640, reference = true)
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        awaitFocus("tv-description-tag:2024年5月")
        val tagBounds = details.info.tags.associate { tag ->
            tag.name to onNodeWithTag("tv-description-tag:${tag.name}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        val firstTag = tagBounds.getValue("2024年5月")
        val secondRowTag = tagBounds.entries.first { it.value.top >= firstTag.bottom }.key
        capture("description-wrapped-tags", "tv-details-panel")
        key(Key.DirectionDown)
        awaitFocus("tv-description-tag:$secondRowTag")
        key(Key.DirectionDown)
        awaitFocus("tv-details-panel-summary-text")
        key(Key.DirectionUp)
        awaitFocus("tv-description-tag:2024年5月")
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(TvSubjectDetailsIntent.SearchTag("运动"), intents.single())
        key(Key.Back)
        awaitFocus("tv-description-tag:2024年5月")
    }

    @Test fun scoreUsesFiveStarsAndCommitsOnlyOnConfirm() = runAniComposeUiTest {
        var state by mutableStateOf(TvSubjectDetailsUiState(content = content(), loggedIn = true))
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ state }, { intent ->
            intents += intent
            if (intent is TvSubjectDetailsIntent.SetScore) state = state.copy(
                operation = TvSubjectOperation(requestId = intent.requestId, completed = true))
        }, reference = true)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        val source = onNodeWithTag("tv-details-rating").fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-rating-control")
        assertAnchoredOverlay(source, expectedAnchorFraction = TvSubjectDetailsDefaults.RatingPanelAnchorFraction)
        assertTrue(onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        onNodeWithTag("tv-rating-description", useUnmergedTree = true).assertTextContains("7", substring = true)
        key(Key.DirectionRight)
        onNodeWithTag("tv-details-panel-rating-control").assertIsFocused()
        assertTrue(intents.isEmpty())
        onNodeWithTag("tv-rating-description", useUnmergedTree = true).assertTextContains("8", substring = true)
        capture("rating", "tv-details-panel")
        key(Key.DirectionCenter)
        assertEquals(8, (intents.single() as TvSubjectDetailsIntent.SetScore).score)
        awaitFocus("tv-details-rating")
    }

    @Test fun scoreBoundsClearAndFailureKeepTheDraft() = runAniComposeUiTest {
        var state by mutableStateOf(TvSubjectDetailsUiState(content = content().copy(selfRating = SelfRatingInfo.Empty), loggedIn = true))
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ state }, { intent ->
            intents += intent
            if (intent is TvSubjectDetailsIntent.SetScore) state = state.copy(
                operation = TvSubjectOperation(intent.requestId, error = LoadError.fromException(IOException("offline"))))
        })
        focusAndClick("rating")
        awaitFocus("tv-details-panel-rating-control")
        repeat(12) { key(Key.DirectionRight) }
        onNodeWithTag("tv-rating-description", useUnmergedTree = true).assertTextContains("10", substring = true)
        repeat(12) { key(Key.DirectionLeft) }
        assertTrue(intents.isEmpty())
        key(Key.DirectionCenter)
        assertEquals(0, (intents.last() as TvSubjectDetailsIntent.SetScore).score)
        onNodeWithTag("tv-rating-error").assertIsDisplayed()
        onNodeWithTag("tv-details-panel-rating-control").assertIsFocused()
        key(Key.Back)
        awaitFocus("tv-details-rating")
    }

    @Test fun uncollectedRatingShowsTooltipWithoutOpeningAnOverlay() = runAniComposeUiTest {
        var state by mutableStateOf(TvSubjectDetailsUiState(
            content = content().copy(collectionType = UnifiedCollectionType.NOT_COLLECTED, selfRating = SelfRatingInfo.Empty),
            loggedIn = true,
        ))
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ state }, { intents += it }, reference = true)
        onNodeWithTag("tv-rating-collection-tooltip").assertDoesNotExist()
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        awaitFocus("tv-details-rating")
        onNodeWithTag("tv-details-rating").assertIsNotEnabled()
        onNodeWithTag("tv-rating-collection-tooltip").assertIsDisplayed()
        capture("rating-collection-tooltip", "tv-rating-collection-tooltip")
        captureWindow("rating-collection-tooltip-context")
        key(Key.DirectionCenter)
        assertTrue(intents.isEmpty())
        assertNoDialogWindow()
        onNodeWithTag("tv-details-panel").assertDoesNotExist()
        onNodeWithTag("tv-details-rating").assertIsFocused()
        key(Key.DirectionLeft)
        awaitFocus("tv-details-collection")
        onNodeWithTag("tv-rating-collection-tooltip").assertDoesNotExist()
        key(Key.DirectionRight)
        awaitFocus("tv-details-rating")
        runOnIdle { state = state.copy(content = state.content!!.copy(collectionType = UnifiedCollectionType.DOING)) }
        onNodeWithTag("tv-rating-collection-tooltip").assertDoesNotExist()
        onNodeWithTag("tv-details-rating").assertIsFocused().assertIsEnabled()
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-rating-control")
        key(Key.Back)
        awaitFocus("tv-details-rating")
    }

    @Test fun collectionTooltipAnimatesInAndOutWithoutTakingFocus() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = content().copy(collectionType = UnifiedCollectionType.NOT_COLLECTED),
            loggedIn = true) }, reference = true)
        key(Key.DirectionRight)
        mainClock.autoAdvance = false
        key(Key.DirectionRight)
        mainClock.advanceTimeBy(64)
        onNodeWithTag("tv-rating-collection-tooltip").assertExists()
        onNodeWithTag("tv-details-rating").assertIsFocused()
        captureWindow("tooltip-entering")
        mainClock.advanceTimeBy(240)
        captureWindow("tooltip-visible")
        key(Key.DirectionLeft)
        mainClock.advanceTimeBy(48)
        onNodeWithTag("tv-rating-collection-tooltip").assertExists()
        onNodeWithTag("tv-details-collection").assertIsFocused()
        captureWindow("tooltip-exiting")
        mainClock.advanceTimeBy(200)
        onNodeWithTag("tv-rating-collection-tooltip").assertDoesNotExist()
        mainClock.autoAdvance = true
    }

    @Test fun episodeGroupHeadingsUseThePanelForeground() = runAniComposeUiTest {
        val details = content().let { it.copy(episodes = it.episodes.take(4), mainEpisodeIds = setOf(1, 2), playTargetId = 1) }
        mount({ TvSubjectDetailsUiState(content = details, loggedIn = true) }, reference = true)
        focusAndClick("all-episodes")
        awaitFocus("tv-details-panel-episode:1")
        listOf("true", "false").forEach { group ->
            val layout = mutableListOf<TextLayoutResult>()
            onNodeWithTag("tv-details-panel-heading:$group").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertEquals(TvOptionDefaults.Content, layout.single().layoutInput.style.color)
        }
        capture("episode-heading-colors", "tv-details-panel")
    }

    @Test fun unratedDraftUsesRoundedAverageAndCancelDiscardsChanges() = runAniComposeUiTest {
        val details = content().let {
            it.copy(selfRating = SelfRatingInfo.Empty, info = it.info.copy(ratingInfo = it.info.ratingInfo.copy(score = "8.5")))
        }
        var state by mutableStateOf(TvSubjectDetailsUiState(content = details, loggedIn = true))
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ state }, { intents += it })
        focusAndClick("rating")
        awaitFocus("tv-details-panel-rating-control")
        onNodeWithTag("tv-rating-description", useUnmergedTree = true).assertTextContains("9", substring = true)
        key(Key.DirectionLeft)
        runOnIdle {
            state = state.copy(content = details.copy(info = details.info.copy(ratingInfo = details.info.ratingInfo.copy(score = "6.1"))))
        }
        onNodeWithTag("tv-rating-description", useUnmergedTree = true).assertTextContains("8", substring = true)
        key(Key.Back)
        awaitFocus("tv-details-rating")
        assertTrue(intents.isEmpty())
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-rating-control")
        onNodeWithTag("tv-rating-description", useUnmergedTree = true).assertTextContains("6", substring = true)
        key(Key.Back)
        awaitFocus("tv-details-rating")
        assertTrue(intents.isEmpty())
    }

    @Test fun collectionOptionsAndConfirmationsDisableUntilTheRequestFinishes() = runAniComposeUiTest {
        var state by mutableStateOf(TvSubjectDetailsUiState(content = content(), loggedIn = true))
        val requests = mutableListOf<TvSubjectDetailsIntent>()
        mount({ state }, { intent ->
            val requestId = when (intent) {
                is TvSubjectDetailsIntent.SetCollection -> intent.requestId
                is TvSubjectDetailsIntent.MarkAllWatched -> intent.requestId
                else -> return@mount
            }
            requests += intent
            state = state.copy(operation = TvSubjectOperation(requestId, busy = true))
        }, reference = true)
        fun assertPending(vararg keys: String) {
            keys.forEach { onNodeWithTag("tv-details-panel-$it").assertIsNotEnabled() }
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            val count = requests.size
            key(Key.DirectionCenter)
            assertEquals(count, requests.size, "Pending options must not submit again")
        }
        focusAndClick("collection")
        awaitFocus("tv-details-panel-collection:DOING")
        key(Key.DirectionDown)
        awaitFocus("tv-details-panel-collection:DONE")
        val label = onNodeWithTag("tv-details-panel-collection:DONE").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        val labelBounds = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        assertPending(*UnifiedCollectionType.entries.map { "collection:${it.name}" }.toTypedArray())
        onNodeWithTag("tv-details-panel-collection:DONE").assertIsFocused()
        assertEquals(labelBounds, onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        capture("collection-pending", "tv-details-panel")
        runOnIdle { state = state.copy(operation = state.operation.copy(busy = false, completed = true,
            error = LoadError.fromException(IOException("offline")))) }
        onNodeWithTag("tv-details-panel-collection:DONE").assertIsEnabled().assertIsFocused()
        key(Key.DirectionCenter)
        assertEquals(2, requests.size)
        runOnIdle { state = state.copy(content = state.content!!.copy(collectionType = UnifiedCollectionType.DONE),
            operation = state.operation.copy(busy = false, completed = true, offerMarkAllWatched = true)) }
        awaitFocus("tv-details-panel-mark-all")
        capture("collection-watched-prompt", "tv-details-panel")
        key(Key.DirectionCenter)
        assertTrue(requests.last() is TvSubjectDetailsIntent.MarkAllWatched)
        assertPending("mark-all", "mark-ignore")
        capture("collection-watched-pending", "tv-details-panel")
        runOnIdle { state = state.copy(operation = state.operation.copy(busy = false, completed = true)) }
        awaitFocus("tv-details-panel-collection:DONE")
        onNodeWithTag("tv-details-panel-collection:NOT_COLLECTED")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-remove-confirm")
        key(Key.DirectionCenter)
        assertEquals(UnifiedCollectionType.NOT_COLLECTED,
            (requests.last() as TvSubjectDetailsIntent.SetCollection).type)
        assertPending("remove-confirm", "remove-cancel")
        capture("collection-remove-pending", "tv-details-panel")
        runOnIdle { state = state.copy(content = state.content!!.copy(collectionType = UnifiedCollectionType.NOT_COLLECTED),
            operation = state.operation.copy(busy = false, completed = true)) }
        awaitFocus("tv-details-collection")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-collection:WISH")
        onNodeWithTag("tv-details-panel-collection:NOT_COLLECTED").assertDoesNotExist()
    }

    @Test fun completedCollectionOffersMarkAllAndCancelKeepsTheCollection() = runAniComposeUiTest {
        var state by mutableStateOf(TvSubjectDetailsUiState(content = content(), loggedIn = true))
        mount({ state }, { intent ->
            if (intent is TvSubjectDetailsIntent.SetCollection) state = state.copy(
                content = state.content!!.copy(collectionType = intent.type),
                operation = TvSubjectOperation(intent.requestId, completed = true, offerMarkAllWatched = true))
        }, reference = true)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-collection:DOING")
        capture("collection", "tv-details-panel")
        key(Key.DirectionDown)
        onNodeWithTag("tv-details-panel-collection:DONE").assertIsFocused()
        key(Key.DirectionCenter)
        awaitFocus("tv-details-panel-mark-all")
        key(Key.Back)
        onNodeWithTag("tv-details-panel-collection:DONE").assertIsDisplayed()
        assertEquals(UnifiedCollectionType.DONE, state.content!!.collectionType)
    }

    @Test fun personRowsRelatedNavigationAndBackRestoreHero() = runAniComposeUiTest {
        val intents = mutableListOf<TvSubjectDetailsIntent>()
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) }, { intents += it }, reference = true)
        repeat(2) { key(Key.DirectionDown) }
        awaitFocus("tv-details-character:1")
        key(Key.DirectionRight)
        awaitFocus("tv-details-character:2")
        capture("characters", "tv-subject-details")
        key(Key.DirectionDown)
        awaitFocus("tv-details-staff:1:${PersonPosition.Director}")
        capture("staff", "tv-subject-details")
        key(Key.DirectionDown)
        awaitFocus("tv-details-related:101")
        capture("related", "tv-subject-details")
        key(Key.DirectionCenter)
        assertEquals(TvSubjectDetailsIntent.OpenRelatedSubject(101), intents.single())
        key(Key.Back)
        awaitFocus("tv-details-play")
    }

    @Test fun removingOrReorderingFocusedCharacterPreservesBusinessIdentity() = runAniComposeUiTest {
        val characters = MutableStateFlow(completedPage(characters()))
        val state = TvSubjectDetailsUiState(content = content().copy(charactersPager = characters), loggedIn = true)
        mount({ state })
        repeat(2) { key(Key.DirectionDown) }
        key(Key.DirectionRight)
        awaitFocus("tv-details-character:2")
        runOnIdle { characters.value = completedPage(characters().filter { it.character.id != 2 }) }
        awaitFocus("tv-details-character:3")
        runOnIdle { characters.value = completedPage(characters().reversed()) }
        awaitFocus("tv-details-character:3")
        onNodeWithTag("tv-details-character:3").assertIsDisplayed()
    }

    @Test fun loadingErrorRetryHandsFocusToTheAvailableControl() = runAniComposeUiTest {
        var state by mutableStateOf(TvSubjectDetailsUiState())
        mount({ state }, { if (it == TvSubjectDetailsIntent.Retry) state = TvSubjectDetailsUiState(content = content()) },
            initialTag = "tv-details-loading")
        runOnIdle { state = state.copy(error = LoadError.fromException(IOException("offline"))) }
        awaitFocus("tv-details-retry")
        key(Key.DirectionCenter)
        awaitFocus("tv-details-play")
    }

    @Test fun lateScoreCompletionLeavesTheNewDescriptionOpen() = runAniComposeUiTest {
        var state by mutableStateOf(TvSubjectDetailsUiState(content = content(), loggedIn = true))
        var requestId = -1
        mount({ state }, { intent ->
            if (intent is TvSubjectDetailsIntent.SetScore) {
                requestId = intent.requestId
                state = state.copy(operation = TvSubjectOperation(requestId, busy = true))
            }
        })
        focusAndClick("rating")
        awaitFocus("tv-details-panel-rating-control")
        key(Key.DirectionCenter)
        onNodeWithTag("tv-rating-busy").assertIsDisplayed()
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        onNodeWithTag("tv-rating-description", useUnmergedTree = true).assertTextContains("7", substring = true)
        key(Key.Back)
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        awaitFocus("tv-description-tag:2024年5月")
        runOnIdle { state = state.copy(operation = TvSubjectOperation(requestId, completed = true)) }
        onNodeWithTag("tv-description-tag:2024年5月").assertIsFocused()
    }

    @Test fun largerFontKeepsPrimaryAndCompactActionsVisible() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) }, width = 640, fontScale = 1.3f)
        onNodeWithTag("tv-details-play").assertIsDisplayed()
        key(Key.DirectionRight)
        onNodeWithTag("tv-details-collection").assertIsFocused().assertIsDisplayed()
        key(Key.DirectionRight)
        onNodeWithTag("tv-details-rating").assertIsFocused().assertIsDisplayed()
        capture("large-font", "tv-subject-details")
        repeat(5) { key(Key.DirectionDown) }
        awaitFocus("tv-details-info")
        onNodeWithText(content().info.aliases.joinToString(" / "), useUnmergedTree = true).assertIsDisplayed()
        capture("information-large-font", "tv-subject-details")
    }

    @Test fun holdingBackClosesOnlyOneLayerOnRelease() = runAniComposeUiTest {
        mount({ TvSubjectDetailsUiState(content = content(), loggedIn = true) })
        focusAndClick("collection")
        onNodeWithTag("tv-details-panel-collection:NOT_COLLECTED").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key(Key.DirectionCenter)
        onAllNodes(isRoot()).onLast().performKeyInput { keyDown(Key.Back) }
        onNodeWithTag("tv-details-panel-remove-description").assertIsDisplayed()
        onAllNodes(isRoot()).onLast().performKeyInput { advanceEventTime(1_000); keyUp(Key.Back) }
        awaitFocus("tv-details-panel-collection:NOT_COLLECTED")
        key(Key.Back)
        awaitFocus("tv-details-collection")
    }

    private fun AniComposeUiTest.awaitFocus(tag: String) {
        try {
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "focus-failure.txt").writeText(
                "Expected: $tag\n" + onAllNodes(isRoot()).onLast().printToString(),
            )
            throw error
        }
    }
    private fun AniComposeUiTest.assertNoDialogWindow() {
        assertTrue(onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog)).fetchSemanticsNodes().isEmpty())
    }
    private fun AniComposeUiTest.assertAnchoredOverlay(source: Rect, expectedAnchorFraction: Float? = null) {
        val trigger = onNodeWithTag("tv-details-overlay-trigger").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val panel = onNodeWithTag("tv-details-operation-panel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val viewport = onNodeWithTag("tv-details-panel").fetchSemanticsNode().boundsInRoot
        listOf(source.left - trigger.left, source.top - trigger.top, source.width - trigger.width, source.height - trigger.height)
            .forEach { assertTrue(abs(it) <= 1.5f, "The overlay trigger must keep its original bounds: $source vs $trigger") }
        assertTrue(panel.bottom < trigger.top, "The options must be above the trigger")
        assertTrue(panel.left >= viewport.left && panel.right <= viewport.right && panel.top >= viewport.top)
        expectedAnchorFraction?.let {
            assertTrue(abs(panel.left + panel.width * it - trigger.left) <= 1.5f,
                "The trigger's left edge must align with $it of the panel width: $panel vs $trigger")
        }
    }
    private fun AniComposeUiTest.focusAndClick(id: String) {
        onNodeWithTag("tv-details-$id").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        onNodeWithTag("tv-details-$id").assertIsFocused()
        key(Key.DirectionCenter)
    }
    private fun AniComposeUiTest.key(key: Key) {
        onAllNodes(isRoot() and hasAnyDescendant(isFocused())).onLast().performKeyInput { pressKey(key) }
        waitForIdle()
    }
    private fun AniComposeUiTest.capture(name: String, tag: String) {
        onNodeWithTag(tag).assertScreenshot("tv-details/$name")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "tv-details-$name.png")
        output.outputStream().use { onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Compose node captures exclude Popup windows; capture the viewport to review the anchor in context. */
    private fun AniComposeUiTest.captureWindow(name: String) {
        waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val output = File(instrumentation.targetContext.getExternalFilesDir(null), "tv-details-$name.png")
            output.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }
    }
}
