/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.paging.PagingData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search_placeholder
import me.him188.ani.app.ui.search.TestSearchState
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchPageSearchBarTest {
    private val url = "https://bgm.tv/subject/622288"
    private val candidates = listOf("番剧名称", "622288", url)

    private class Fixture(
        val suggestions: (String) -> Flow<PagingData<String>>,
    ) {
        var recomposition by mutableStateOf(0)
        val intents = mutableListOf<SearchPageIntent>()
        val state = createTestSearchPageState(
            searchState = TestSearchState(MutableStateFlow(flowOf(PagingData.empty()))),
            query = SubjectSearchQuery("", tags = listOf("百合")),
            hasActiveSearch = false,
        ).copy(searchHistoryPager = flowOf(PagingData.empty()))
    }

    private fun AniComposeUiTest.showSearchBar(fixture: Fixture) {
        setContent {
            ProvideCompositionLocalsForPreview {
                SearchPage(
                    state = fixture.state,
                    onIntent = { fixture.intents += it },
                    suggestionsPager = fixture.suggestions,
                    detailContent = {},
                    modifier = Modifier.testTag("search-bar-${fixture.recomposition}"),
                    contentWindowInsets = WindowInsets(0),
                )
            }
        }
    }

    private fun AniComposeUiTest.enterQuery(text: String) {
        onNode(hasSetTextAction()).performClick().performTextInput(text)
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun AniComposeUiTest.candidate(text: String) = onNode(hasText(text) and !hasSetTextAction())

    @Test
    fun `placeholder describes keyword id and url input`() = runAniComposeUiTest {
        showSearchBar(Fixture { flowOf(PagingData.empty()) })
        val placeholder = runBlocking { getString(Lang.exploration_search_placeholder) }
        onNodeWithText(placeholder).assertIsDisplayed()
    }

    @Test
    fun `candidate order is name id then original url`() = runAniComposeUiTest {
        showSearchBar(Fixture { flowOf(PagingData.from(candidates)) })
        enterQuery(url)
        val tops = candidates.map { candidate(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        assertTrue(tops.zipWithNext().all { (first, second) -> first < second })
    }

    @Test
    fun `late specific candidates appear above already displayed ordinary candidates`() = runAniComposeUiTest {
        val resolved = CompletableDeferred<Unit>()
        val fixture = Fixture {
            flow {
                emit(PagingData.from(listOf("普通候选")))
                resolved.await()
                emit(PagingData.from(candidates + "普通候选"))
            }
        }
        showSearchBar(fixture)
        enterQuery(url)
        candidate("普通候选").assertIsDisplayed()
        onNodeWithText(candidates.first()).assertDoesNotExist()
        runOnIdle { resolved.complete(Unit) }
        waitForIdle()
        val tops = (candidates + "普通候选").map {
            candidate(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(tops.zipWithNext().all { (first, second) -> first < second })
        candidate(candidates.first()).performClick()
        runOnIdle {
            assertEquals(candidates.first(), (fixture.intents.single() as SearchPageIntent.UpdateQuery).query.keywords)
        }
    }

    private fun assertCandidateSearch(text: String) = runAniComposeUiTest {
        val fixture = Fixture { flowOf(PagingData.from(candidates)) }
        showSearchBar(fixture)
        enterQuery(url)
        candidate(text).performClick()
        onNode(hasSetTextAction()).assertTextEquals(text)
        candidate(text).assertDoesNotExist()
        runOnIdle {
            assertEquals(
                listOf<SearchPageIntent>(
                    SearchPageIntent.UpdateQuery(fixture.state.query.copy(keywords = text), submit = true),
                ),
                fixture.intents,
            )
        }
    }

    @Test
    fun `clicking title submits title as keyword`() = assertCandidateSearch(candidates[0])

    @Test
    fun `clicking id submits id as keyword`() = assertCandidateSearch(candidates[1])

    @Test
    fun `clicking original url submits url as keyword`() = assertCandidateSearch(url)

    @Test
    fun `enter submits original input without selecting a candidate`() = runAniComposeUiTest {
        val fixture = Fixture { flowOf(PagingData.from(candidates)) }
        showSearchBar(fixture)
        enterQuery(url)
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        onNode(hasSetTextAction()).assertTextEquals(url)
        runOnIdle {
            assertEquals(
                listOf<SearchPageIntent>(
                    SearchPageIntent.UpdateQuery(fixture.state.query.copy(keywords = url), submit = true),
                ),
                fixture.intents,
            )
        }
    }

    @Test
    fun `ime search submits original input`() = runAniComposeUiTest {
        val fixture = Fixture { flowOf(PagingData.from(candidates)) }
        showSearchBar(fixture)
        enterQuery(url)
        onNode(hasSetTextAction()).performImeAction()
        runOnIdle {
            assertEquals(
                listOf<SearchPageIntent>(
                    SearchPageIntent.UpdateQuery(fixture.state.query.copy(keywords = url), submit = true),
                ),
                fixture.intents,
            )
        }
    }

    @Test
    fun `editing cancels old request and only latest query supplies candidates`() = runAniComposeUiTest {
        val started = mutableListOf<String>()
        val oldResponse = CompletableDeferred<Unit>()
        var oldCancelled = false
        val fixture = Fixture { query ->
            flow {
                started += query
                if (query == url) {
                    emit(PagingData.from(listOf("普通候选")))
                    try {
                        oldResponse.await()
                        emit(PagingData.from(listOf("过期候选")))
                    } finally {
                        oldCancelled = true
                    }
                } else {
                    emit(PagingData.from(listOf("最新候选")))
                }
            }
        }
        showSearchBar(fixture)
        enterQuery(url)
        candidate("普通候选").assertIsDisplayed()
        runOnIdle { assertEquals(listOf(url), started) }

        mainClock.autoAdvance = false
        onNode(hasSetTextAction()).performTextReplacement("622289")
        mainClock.advanceTimeByFrame()
        runOnIdle { assertTrue(oldCancelled) }
        mainClock.advanceTimeBy(300)
        onNode(hasSetTextAction()).performTextReplacement("622290")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(600)
        runOnIdle { assertEquals(listOf(url), started) }
        mainClock.advanceTimeBy(400)
        mainClock.autoAdvance = true
        waitForIdle()

        candidate("最新候选").assertIsDisplayed()
        runOnIdle {
            assertEquals(listOf(url, "622290"), started)
            oldResponse.complete(Unit)
        }
        waitForIdle()
        onNodeWithText("过期候选").assertDoesNotExist()
        onNodeWithText("普通候选").assertDoesNotExist()
        candidate("最新候选").assertIsDisplayed()
    }

    @Test
    fun `unrelated recomposition does not restart suggestions`() = runAniComposeUiTest {
        var requests = 0
        val fixture = Fixture {
            requests++
            flowOf(PagingData.from(candidates))
        }
        showSearchBar(fixture)
        enterQuery(url)
        candidate(candidates.first()).assertIsDisplayed()
        runOnIdle { fixture.recomposition++ }
        mainClock.advanceTimeBy(2_000)
        runOnIdle { assertEquals(1, requests) }
    }
}
