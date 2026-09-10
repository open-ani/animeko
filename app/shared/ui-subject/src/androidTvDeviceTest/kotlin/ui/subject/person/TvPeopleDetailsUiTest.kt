/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person

import android.graphics.Bitmap
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.person.CharacterSubjectInfo
import me.him188.ani.app.data.models.person.InfoboxRowInfo
import me.him188.ani.app.data.models.person.PersonCastInfo
import me.him188.ani.app.data.models.person.PersonSubjectSummary
import me.him188.ani.app.data.models.person.PersonWorkInfo
import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UIRichText
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import me.him188.ani.leanback.ui.foundation.focus.tvBackKey
import me.him188.ani.leanback.ui.subject.assertDetailsEndPaddingAligned
import me.him188.ani.leanback.ui.subject.assertCommentRefreshCleanup
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TvPeopleDetailsUiTest {
    private fun <T : Any> page(items: List<T>) = PagingData.from(items,
        sourceLoadStates = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true)))
    private fun subject(id: Int) = PersonSubjectSummary(id, "Work $id", "参与作品 $id", image())
    private fun actor(id: Int) = PersonInfo(id, "Voice actor $id", PersonType.Individual, emptyList(), image(), image(), "", null)
    private fun character(id: Int) = CharacterInfo(id, "Character $id", "角色 $id", emptyList(), image(), image())
    private fun comments(shortFirst: Boolean = false) = (1..24).map { id -> UIComment(
        id.toLong(), "review-$id", UserInfo("$id", null, "观众 $id"),
        UIRichText(listOf(UIRichElement.AnnotatedText(listOf(
            UIRichElement.Annotated.Text(if (shortFirst && id == 1) "很喜欢这个角色。"
                else "这个角色的台词和表演很有辨识度。每次出场都会期待新的细节。".repeat(6)),
            UIRichElement.Annotated.Text(if (shortFirst && id == 1) "" else "保密的剧情 $id", mask = true),
        )))), 1_720_000_000_000L, emptyList(), emptyList(), 0, null,
        source = if (id == 2) UICommentSource.BANGUMI else UICommentSource.ANI,
    ) }
    private fun profile() = TvPeopleProfile("爱丽速子", "アグネスタキオン", image(),
        "不断探索速度极限，对未知的可能性充满好奇。\n她以自己的方式追求答案，也逐渐理解与伙伴一同前进的意义。",
        listOf(InfoboxRowInfo("别名", "アグネスタキオン"), InfoboxRowInfo("生日", "4 月 13 日"), InfoboxRowInfo("网站", "https://example.com/people/long-information")),
        1284, careers = listOf("seiyu", "singer"), actors = listOf(actor(21)), workCount = 20, castCount = 24)
    private fun state(kind: TvPeopleKind) = TvPeopleDetailsUiState(
        TvPeopleTarget(1, kind), profile().copy(name = when (kind) {
            TvPeopleKind.Character -> "爱丽速子"
            TvPeopleKind.VoiceActor -> "声优示例"
            TvPeopleKind.Staff -> "制作人示例"
        }), loading = false,
        subjects = if (kind == TvPeopleKind.Character) flowOf(page((1..20).map { CharacterSubjectInfo(subject(it), CharacterRole.MAIN, listOf(actor(21))) })) else null,
        casts = if (kind == TvPeopleKind.VoiceActor) flowOf(page((1..24).map { PersonCastInfo(subject(it), character(it % 4)) })) else null,
        works = if (kind != TvPeopleKind.Character) flowOf(page((1..20).map { PersonWorkInfo(subject(it), listOf(PersonPosition.Director)) })) else null,
        comments = flowOf(page(comments())),
    )

    private fun image(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.cacheDir, "people-test-portrait.jpg")
        if (!target.exists()) instrumentation.context.assets.open("tv-details-poster.jpg").use { input -> target.outputStream().use { input.copyTo(it) } }
        return target.toURI().toString()
    }

    private fun AniComposeUiTest.mount(
        state: () -> TvPeopleDetailsUiState,
        width: Int = 960,
        fontScale: Float = 1f,
        onOpenUrl: (String) -> Unit = {},
        onIntent: (TvPeopleIntent) -> Unit = {},
    ) {
        mountContent(width, fontScale) { TvPeopleDetailsScreen(state(), onIntent, onOpenUrl = onOpenUrl) }
        awaitFocus("tv-people-intro")
    }

    private fun AniComposeUiTest.mountContent(width: Int = 960, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sketch = Sketch.Builder(context).build()
        val time = TimeFormatter()
        val previousLocale = LocaleList.getDefault()
        LocaleList.setDefault(LocaleList(Locale.forLanguageTag("zh-CN")))
        setContent {
            DisposableEffect(sketch) { onDispose { sketch.shutdown(); LocaleList.setDefault(previousLocale) } }
            CompositionLocalProvider(LocalSketch provides sketch, LocalTimeFormatter provides time,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                AniTvTheme { Box(Modifier.width(width.dp)) { content() } }
            }
        }
    }

    @Test fun characterHeroAndFullWidthSections() = runAniComposeUiTest {
        val state = state(TvPeopleKind.Character).copy(comments = flowOf(page(comments(shortFirst = true))))
        val intents = mutableListOf<TvPeopleIntent>()
        mount({ state }, onIntent = { intents += it })
        capture("character")
        val image = onNodeWithTag("tv-people-portrait").fetchSemanticsNode().boundsInRoot
        val hero = onNodeWithTag("tv-people-hero").fetchSemanticsNode().boundsInRoot
        val actors = onNodeWithTag("tv-people-section:actors").fetchSemanticsNode().boundsInRoot
        assertTrue(image.bottom < actors.top)
        assertTrue(actors.width > hero.width * .9f)
        assertEquals(onNodeWithTag("tv-people-intro").fetchSemanticsNode().boundsInRoot.bottom,
            onNodeWithTag("tv-people-discussion").fetchSemanticsNode().boundsInRoot.bottom)
        key(Key.DirectionDown)
        awaitFocus("tv-people-image")
        key(Key.DirectionDown)
        awaitFocus("tv-people-actors:21")
        key(Key.DirectionCenter)
        assertTrue(intents.contains(TvPeopleIntent.OpenPerson(TvPeopleTarget(21, TvPeopleKind.VoiceActor))))
        key(Key.DirectionDown)
        awaitFocus("tv-people-works:1")
        assertDetailsEndPaddingAligned("tv-people-details")
        capture("character-last-section")
        key(Key.DirectionCenter)
        assertTrue(intents.any { it is TvPeopleIntent.OpenSubject && it.subject.subjectId == 1 })
        key(Key.Back)
        awaitFocus("tv-people-intro")
    }

    @Test fun voiceActorCastsUseCharacterAndWorkIdentity() = runAniComposeUiTest {
        val state = state(TvPeopleKind.VoiceActor)
        mount({ state })
        capture("voice-actor")
        repeat(2) { key(Key.DirectionDown) }
        awaitFocus("tv-people-casts:1:1")
        repeat(4) { key(Key.DirectionRight) }
        awaitFocus("tv-people-casts:1:5")
        key(Key.DirectionDown)
        awaitFocus("tv-people-works:1")
        assertDetailsEndPaddingAligned("tv-people-details")
        key(Key.DirectionUp)
        awaitFocus("tv-people-casts:1:5")
        capture("voice-actor-section")
    }

    @Test fun staffStartsWithWorksAndHasNoCastRow() = runAniComposeUiTest {
        val state = state(TvPeopleKind.Staff)
        mount({ state })
        capture("staff")
        onNodeWithTag("tv-people-section:casts").assertDoesNotExist()
        onNodeWithTag("tv-people-section:actors").assertDoesNotExist()
        repeat(2) { key(Key.DirectionDown) }
        awaitFocus("tv-people-works:1")
        assertDetailsEndPaddingAligned("tv-people-details")
        val first = onNodeWithTag("tv-people-works:1").fetchSemanticsNode().boundsInRoot
        repeat(6) { key(Key.DirectionRight) }
        awaitFocus("tv-people-works:7")
        assertDetailsEndPaddingAligned("tv-people-details")
        assertEquals(first.top, onNodeWithTag("tv-people-works:7").fetchSemanticsNode().boundsInRoot.top, 1f)
        capture("staff-last-section")
        key(Key.Back)
        awaitFocus("tv-people-intro")
    }

    @Test fun emptyWorksMakesActorsTheLastSection() = runAniComposeUiTest {
        val subjects = MutableStateFlow(page(listOf(CharacterSubjectInfo(subject(1), CharacterRole.MAIN, emptyList()))))
        val state = state(TvPeopleKind.Character).copy(subjects = subjects)
        mount({ state }, width = 900, fontScale = 1.3f)
        runOnIdle { subjects.value = page(emptyList()) }
        onNodeWithTag("tv-people-section:works").assertDoesNotExist()
        repeat(2) { key(Key.DirectionDown) }
        awaitFocus("tv-people-actors:21")
        assertDetailsEndPaddingAligned("tv-people-details")
        capture("actors-last-section")
        key(Key.DirectionUp)
        awaitFocus("tv-people-image")
    }

    @Test fun introductionAndInfoboxShareReaderAndRestoreCard() = runAniComposeUiTest {
        val state = state(TvPeopleKind.Character).let { it.copy(profile = it.profile!!.copy(summary = it.profile.summary.repeat(18))) }
        mount({ state })
        key(Key.DirectionCenter)
        awaitFocus("tv-people-reader")
        capture("introduction")
        val readerScroll = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            hasAnyAncestor(hasTestTag("tv-people-reader"))
        assertEquals(0f, onNode(readerScroll).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
        repeat(70) { key(Key.DirectionDown) }
        onNodeWithText("https://example.com/people/long-information", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(onNode(readerScroll).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        capture("introduction-info")
        key(Key.Back)
        awaitFocus("tv-people-intro")
    }

    @Test fun emptyIntroductionStillShowsAvailableInformation() = runAniComposeUiTest {
        val state = state(TvPeopleKind.Staff).let { it.copy(profile = it.profile!!.copy(summary = "")) }
        mount({ state })
        key(Key.DirectionCenter)
        awaitFocus("tv-people-reader")
        onNodeWithTag("tv-people-biography").assertDoesNotExist()
        onNodeWithTag("tv-people-infobox").assertIsDisplayed()
        capture("information-only")
    }

    @Test fun discussionKeepsOneModalAndRestoresCommentAndCard() = runAniComposeUiTest {
        val state = state(TvPeopleKind.Character)
        mount({ state })
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-comment:review-1")
        assertSingleModal()
        capture("discussion-list")
        repeat(5) { key(Key.DirectionDown) }
        awaitFocus("tv-people-discussion-comment:review-6")
        val before = onNodeWithTag("tv-people-discussion-comment:review-6").fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-body")
        assertSingleModal()
        onNodeWithTag("tv-people-intro").assertDoesNotExist()
        onNodeWithText("保密的剧情 6", substring = true, useUnmergedTree = true).assertDoesNotExist()
        capture("discussion-comment")
        key(Key.Back)
        awaitFocus("tv-people-discussion-comment:review-6")
        assertEquals(before, onNodeWithTag("tv-people-discussion-comment:review-6").fetchSemanticsNode().boundsInRoot)
        key(Key.Back)
        awaitFocus("tv-people-discussion")
    }

    @Test fun readOnlySourceHidesVotesAndAllowsOriginalLink() = runAniComposeUiTest {
        val state = state(TvPeopleKind.VoiceActor)
        val urls = mutableListOf<String>()
        mount({ state }, onOpenUrl = { urls.add(it) })
        key(Key.DirectionRight); key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-comment:review-1")
        key(Key.DirectionDown); key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-body")
        onNodeWithTag("tv-people-action:like").assertDoesNotExist()
        onNodeWithTag("tv-people-action:dislike").assertDoesNotExist()
        onNodeWithTag("tv-people-action:original").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key(Key.DirectionCenter)
        assertEquals(listOf("https://bgm.tv/person/1"), urls)
        assertSingleModal()
    }

    @Test fun failedProfileKeepsIntroductionAndRetryReachable() = runAniComposeUiTest {
        val state = state(TvPeopleKind.Staff).copy(
            profile = null,
            error = LoadError.fromException(IOException("Test profile failure")),
            works = flowOf(page(emptyList())),
        )
        val intents = mutableListOf<TvPeopleIntent>()
        mount({ state }) { intents.add(it) }
        onNodeWithTag("tv-people-image").assertDoesNotExist()
        key(Key.DirectionDown)
        awaitFocus("tv-people-retry")
        key(Key.DirectionCenter)
        assertTrue(TvPeopleIntent.Retry in intents)
        key(Key.DirectionUp)
        awaitFocus("tv-people-intro")
        key(Key.DirectionCenter)
        awaitFocus("tv-people-reader")
        onNodeWithTag("tv-people-biography").assertDoesNotExist()
        key(Key.Back)
        awaitFocus("tv-people-intro")
    }

    @Test fun deletedAndReorderedCommentsRestoreStableNeighbor() = runAniComposeUiTest {
        val comments = MutableStateFlow(page(comments()))
        val state = state(TvPeopleKind.Character).copy(comments = comments)
        mount({ state })
        key(Key.DirectionRight); key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-comment:review-1")
        repeat(3) { key(Key.DirectionDown) }
        awaitFocus("tv-people-discussion-comment:review-4")
        key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-body")
        runOnIdle { comments.value = page(comments().filter { it.stableId != "review-4" }) }
        key(Key.Back)
        awaitFocus("tv-people-discussion-comment:review-5")
        runOnIdle { comments.value = page(comments().reversed()) }
        awaitFocus("tv-people-discussion-comment:review-5")
    }

    @Test fun loadingCompletionDoesNotStealFocusAndEmptyRowsDisappear() = runAniComposeUiTest {
        val ready = state(TvPeopleKind.Character)
        var state by mutableStateOf(ready.copy(profile = null, loading = true, subjects = flowOf(page(emptyList()))))
        mount({ state })
        key(Key.DirectionRight)
        awaitFocus("tv-people-discussion")
        runOnIdle { state = state.copy(profile = profile().copy(actors = emptyList()), loading = false) }
        awaitFocus("tv-people-discussion")
        onNodeWithTag("tv-people-section:actors").assertDoesNotExist()
        onNodeWithTag("tv-people-section:works").assertDoesNotExist()
    }

    @Test fun characterLoadingSkeletonKeepsReaderAndDiscussionFocus() = assertProfileSkeleton(TvPeopleKind.Character)
    @Test fun voiceActorLoadingSkeletonKeepsItsLayout() = assertProfileSkeleton(TvPeopleKind.VoiceActor)
    @Test fun staffLoadingSkeletonSupportsNarrowViewportAndLargeText() = assertProfileSkeleton(TvPeopleKind.Staff, 640, 1.3f)

    private fun assertProfileSkeleton(kind: TvPeopleKind, width: Int = 960, fontScale: Float = 1f) = runAniComposeUiTest {
        fun <T : Any> loadingPage() = PagingData.empty<T>(sourceLoadStates =
            LoadStates(LoadState.Loading, LoadState.NotLoading(true), LoadState.NotLoading(false)))
        val ready = state(kind)
        val comments = MutableStateFlow(loadingPage<UIComment>())
        var state by mutableStateOf(ready.copy(profile = null, loading = true, comments = comments,
            subjects = ready.subjects?.let { flowOf(loadingPage<CharacterSubjectInfo>()) },
            casts = ready.casts?.let { flowOf(loadingPage<PersonCastInfo>()) },
            works = ready.works?.let { flowOf(loadingPage<PersonWorkInfo>()) }))
        mount({ state }, width = width, fontScale = fontScale)
        onNodeWithTag("tv-people-identity-loading").assertIsDisplayed().assertHasNoClickAction()
        onNodeWithTag("tv-people-portrait-loading").assertIsDisplayed().assertHasNoClickAction()
        onNodeWithTag("tv-people-discussion-preview-loading", useUnmergedTree = true).assertExists().assertHasNoClickAction()
        for (card in listOf("tv-people-intro", "tv-people-discussion")) {
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
                hasAnyAncestor(hasTestTag(card)), useUnmergedTree = true).assertCountEquals(0)
        }
        val portrait = onNodeWithTag("tv-people-portrait").fetchSemanticsNode().boundsInRoot
        val intro = onNodeWithTag("tv-people-intro").fetchSemanticsNode().boundsInRoot
        val root = onNodeWithTag("tv-people-details").fetchSemanticsNode().boundsInRoot
        assertTrue(intro.right < portrait.left && portrait.right <= root.right)
        capture("${kind.name.lowercase()}-loading-skeleton")
        key(Key.DirectionCenter)
        awaitFocus("tv-people-reader")
        onNodeWithTag("tv-people-reader-loading", useUnmergedTree = true).assertExists().assertHasNoClickAction()
        if (kind == TvPeopleKind.Character) capture("introduction-loading-skeleton")
        runOnIdle { state = ready.copy(comments = comments) }
        awaitFocus("tv-people-reader")
        onNodeWithTag("tv-people-reader-loading", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("tv-people-biography").assertExists()
        key(Key.Back)
        awaitFocus("tv-people-intro")
        key(Key.DirectionRight); key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-status")
        onNodeWithTag("tv-review-placeholder-0", useUnmergedTree = true).assertIsDisplayed().assertHasNoClickAction()
        val first = onNodeWithTag("tv-review-placeholder-0", useUnmergedTree = true).fetchSemanticsNode()
        val viewport = onNodeWithTag("tv-people-discussion-list").fetchSemanticsNode().boundsInRoot
        assertTrue(first.positionInRoot.y >= viewport.top && first.positionInRoot.y + first.size.height <= viewport.bottom,
            "The first loading card must stay fully visible")
        if (kind == TvPeopleKind.Character) capture("discussion-loading-skeleton")
        runOnIdle { comments.value = page(comments()) }
        awaitFocus("tv-people-discussion-comment:review-1")
        key(Key.Back)
        awaitFocus("tv-people-discussion")
        // Refreshing an existing profile keeps both its contents and the user's current focus.
        runOnIdle { state = state.copy(loading = true) }
        onNodeWithTag("tv-people-identity-loading").assertDoesNotExist()
        awaitFocus("tv-people-discussion")
    }

    @Test fun returningBetweenProfilesRestoresHorizontalAndVerticalPosition() = runAniComposeUiTest {
        var stack by mutableStateOf(listOf(TvPeopleTarget(1, TvPeopleKind.VoiceActor)))
        val states = TvPeopleKind.entries.associateWith(::state)
        mountContent {
            val saved = rememberSaveableStateHolder()
            val target = stack.last()
            BackHandler(stack.size > 1) { stack = stack.dropLast(1) }
            // Synthetic keys do not reach the activity's system Back dispatcher.
            Box(Modifier.tvBackKey(stack.size > 1) { stack = stack.dropLast(1) }) {
                saved.SaveableStateProvider("${target.kind}:${target.id}") {
                    TvPeopleDetailsScreen(states.getValue(target.kind).copy(target = target), {
                        if (it is TvPeopleIntent.OpenPerson) stack = stack + it.target
                    })
                }
            }
        }
        awaitFocus("tv-people-intro")
        repeat(2) { key(Key.DirectionDown) }
        repeat(9) { key(Key.DirectionRight) }
        awaitFocus("tv-people-casts:2:10")
        val before = onNodeWithTag("tv-people-casts:2:10").fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        awaitFocus("tv-people-intro")
        key(Key.Back)
        awaitFocus("tv-people-casts:2:10")
        assertEquals(before, onNodeWithTag("tv-people-casts:2:10").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun returningWaitsForRelationshipLayoutBeforeRestoringPosition() = runAniComposeUiTest {
        val records = listOf(CharacterSubjectInfo(subject(1), CharacterRole.MAIN, emptyList()))
        val subjects = MutableStateFlow(page(records))
        val characterState = state(TvPeopleKind.Character).let {
            it.copy(profile = it.profile!!.copy(actors = listOf(actor(21), actor(22))), subjects = subjects)
        }
        val voiceState = state(TvPeopleKind.VoiceActor)
        var inVoice by mutableStateOf(false)
        mountContent {
            val saved = rememberSaveableStateHolder()
            Box(Modifier.tvBackKey(inVoice) { inVoice = false }) {
                saved.SaveableStateProvider(inVoice) {
                    TvPeopleDetailsScreen(if (inVoice) voiceState else characterState, {
                        if (it is TvPeopleIntent.OpenPerson) inVoice = true
                    })
                }
            }
        }
        awaitFocus("tv-people-intro")
        repeat(2) { key(Key.DirectionDown) }
        key(Key.DirectionRight)
        awaitFocus("tv-people-actors:22")
        val before = onNodeWithTag("tv-people-actors:22").fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        awaitFocus("tv-people-intro")
        runOnIdle {
            subjects.value = PagingData.from(emptyList(), sourceLoadStates = LoadStates(
                LoadState.Loading, LoadState.NotLoading(true), LoadState.NotLoading(false),
            ))
        }
        key(Key.Back)
        waitUntil { onAllNodes(hasTestTag("tv-people-works:loading")).fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { subjects.value = page(records) }
        awaitFocus("tv-people-actors:22")
        assertEquals(before, onNodeWithTag("tv-people-actors:22").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun narrowViewportLargeFontKeepsReaderAndDiscussionUsable() = runAniComposeUiTest {
        val state = state(TvPeopleKind.Staff)
        mount({ state }, width = 640, fontScale = 1.3f)
        key(Key.DirectionCenter)
        awaitFocus("tv-people-reader")
        repeat(10) { key(Key.DirectionDown) }
        capture("large-font-introduction")
        key(Key.Back)
        awaitFocus("tv-people-intro")
        onNodeWithTag("tv-people-discussion").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-comment:review-1")
        assertSingleModal()
        capture("large-font-discussion")
    }

    @Test fun pagedWorksKeepContentOnAppendFailureAndRetry() = runAniComposeUiTest {
        var failAppend = true
        val pager = Pager(PagingConfig(pageSize = 3, initialLoadSize = 3, prefetchDistance = 1, enablePlaceholders = false)) {
            object : PagingSource<Int, PersonWorkInfo>() {
                override fun getRefreshKey(state: PagingState<Int, PersonWorkInfo>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PersonWorkInfo> {
                    val offset = params.key ?: 0
                    if (offset > 0 && failAppend) return LoadResult.Error(IOException("Test append failure"))
                    return LoadResult.Page((offset + 1..offset + 3).map { PersonWorkInfo(subject(it), listOf(PersonPosition.Director)) },
                        prevKey = null, nextKey = if (offset == 0) 3 else null)
                }
            }
        }.flow
        val state = state(TvPeopleKind.Staff).copy(works = pager)
        mount({ state })
        repeat(2) { key(Key.DirectionDown) }
        awaitFocus("tv-people-works:1")
        key(Key.DirectionRight)
        awaitFocus("tv-people-works:2")
        waitUntil { onAllNodes(hasTestTag("tv-people-works:retry")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tv-people-works:2").assertIsFocused()
        key(Key.DirectionRight); key(Key.DirectionRight)
        awaitFocus("tv-people-works:retry")
        runOnIdle { failAppend = false }
        key(Key.DirectionCenter)
        awaitFocus("tv-people-works:4")
        capture("works-retry")
    }

    @Test fun emptyDiscussionAndImageReturnToTheirOwnEntries() = runAniComposeUiTest {
        val state = state(TvPeopleKind.Character).copy(comments = flowOf(page(emptyList())))
        mount({ state })
        key(Key.DirectionDown); key(Key.DirectionCenter)
        awaitFocus("tv-people-full-image")
        key(Key.Back)
        awaitFocus("tv-people-image")
        key(Key.DirectionUp); key(Key.DirectionRight); key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-status")
        assertSingleModal()
        key(Key.Back)
        awaitFocus("tv-people-discussion")
    }

    @Test fun revealActionProtectsSpoilersAndModalBlocksBackgroundFocus() = runAniComposeUiTest {
        val state = state(TvPeopleKind.Character)
        mount({ state })
        key(Key.DirectionRight); key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-comment:review-1")
        key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-body")
        onNodeWithText("保密的剧情 1", substring = true, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("tv-people-action:reveal").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key(Key.DirectionCenter)
        onNodeWithText("保密的剧情 1", substring = true, useUnmergedTree = true).assertExists()
        key(Key.DirectionCenter)
        onNodeWithText("保密的剧情 1", substring = true, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("tv-people-intro").assertDoesNotExist()
        assertSingleModal()
        onAllNodes(isRoot()).onLast().performKeyInput { keyDown(Key.Back); advanceEventTime(1_000); keyUp(Key.Back) }
        awaitFocus("tv-people-discussion-comment:review-1")
    }

    @Test fun commentsRefreshClearsSettledVotesAndKeepsPendingVotes() = runAniComposeUiTest {
        assertCommentRefreshCleanup { shared, onRefreshed ->
            val state = state(TvPeopleKind.Character).copy(comments = shared.list, commentPresentation = shared::withOverlay)
            mount({ state }, onIntent = { if (it == TvPeopleIntent.CommentsRefreshed) onRefreshed() })
        }
    }

    @Test fun reportCompletionOnlyClosesItsOwnReportAndKeepsModalHistory() = runAniComposeUiTest {
        var state by mutableStateOf(state(TvPeopleKind.Character))
        val reports = mutableListOf<TvPeopleIntent.Report>()
        mount({ state }, onIntent = { intent ->
            if (intent is TvPeopleIntent.Report) {
                reports += intent
                state = state.copy(reportBusy = true)
            }
        })
        fun openReport() {
            onNodeWithTag("tv-people-action:report").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            key(Key.DirectionCenter)
            awaitFocus("tv-people-discussion-reason:SPAM")
        }
        fun submitReport() {
            repeat(6) { key(Key.DirectionDown) }
            awaitFocus("tv-people-discussion-report-submit")
            key(Key.DirectionCenter)
        }
        key(Key.DirectionRight); key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-comment:review-1")
        key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-body")
        openReport()
        submitReport()
        assertEquals("review-1", reports.single().comment.stableId)
        key(Key.Back)
        awaitFocus("tv-people-discussion-body")
        key(Key.Back)
        awaitFocus("tv-people-discussion-comment:review-1")
        key(Key.DirectionDown); key(Key.DirectionCenter)
        awaitFocus("tv-people-discussion-body")
        // The previous submit is still running while a new report is opened.
        openReport()
        runOnIdle { state = state.copy(reportBusy = false, reportCompleted = reports.first().requestId) }
        awaitFocus("tv-people-discussion-reason:SPAM")
        assertSingleModal()
        submitReport()
        assertEquals("review-2", reports.last().comment.stableId)
        assertNotEquals(reports.first().requestId, reports.last().requestId)
        runOnIdle { state = state.copy(reportBusy = false, reportCompleted = reports.last().requestId) }
        awaitFocus("tv-people-discussion-body")
        assertSingleModal()
        key(Key.Back)
        awaitFocus("tv-people-discussion-comment:review-2")
        key(Key.Back)
        awaitFocus("tv-people-discussion")
    }

    private fun AniComposeUiTest.assertSingleModal() {
        onAllNodes(hasTestTag("tv-people-discussion-surface")).assertCountEquals(1)
        assertTrue(onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog)).fetchSemanticsNodes().isEmpty())
    }
    private fun AniComposeUiTest.awaitFocus(tag: String) {
        try {
            waitUntil(timeoutMillis = 8_000) { onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "people-focus-failure.txt").writeText("Expected: $tag\n" + onAllNodes(isRoot()).onLast().printToString())
            throw error
        }
    }
    private fun AniComposeUiTest.key(key: Key) {
        onAllNodes(isRoot() and hasAnyDescendant(isFocused())).onLast().performKeyInput { pressKey(key) }
        waitForIdle()
    }
    private fun AniComposeUiTest.capture(name: String) {
        onNodeWithTag("tv-people-details").assertScreenshot("tv-people/$name")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "tv-people-$name.png").outputStream().use {
            onNodeWithTag("tv-people-details").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
