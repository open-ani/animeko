/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.schedule.AnimeSeason
import me.him188.ani.app.data.models.schedule.AnimeSeasonId
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.doesNotExist
import me.him188.ani.app.ui.framework.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.search.TestSearchState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class SearchPageWideLayoutTest {
    @Test
    fun `wide desktop shows side filters and no legacy row`() {
        runSearchPageTest(widthDp = 840) {
            assertTrue(onNodeWithTag(SearchFilterSidePanelTestTags.Panel).exists())
            assertTrue(onNodeWithTag(SearchPageTestTags.LegacyFilterRow).doesNotExist())
            onNodeWithTag(SearchFilterSidePanelTestTags.group(SearchFilterTimeGroupKey)).assertIsDisplayed()
            assertTrue(onNodeWithTag(SearchTimeFilterTestTags.Content).doesNotExist())
            assertTrue(onNodeWithTag(SearchPageTestTags.YearFilter).doesNotExist())
            for (chip in createUiTestState().searchFilterState.chips) {
                assertTrue(onNodeWithTag(SearchFilterSidePanelTestTags.group(chip.groupKey)).exists())
                assertFalse(
                    onNodeWithTag(SearchFilterSidePanelTestTags.value(chip.groupKey, chip.values.first())).exists(),
                )
            }
        }
    }

    @Test
    fun `wide mode follows desktop scaffold horizontal partitions`() {
        assertFalse(shouldUseWideDesktopFilterPanel(true, 1))
        assertTrue(shouldUseWideDesktopFilterPanel(true, 2))
        assertFalse(shouldUseWideDesktopFilterPanel(false, 2))
    }

    @Test
    fun `desktop below wide breakpoint keeps legacy filter row`() {
        runSearchPageTest(widthDp = 839) {
            assertTrue(onNodeWithTag(SearchFilterSidePanelTestTags.Panel).doesNotExist())
            assertTrue(onNodeWithTag(SearchPageTestTags.LegacyFilterRow).exists())
            onNodeWithTag(SearchPageTestTags.YearFilter).assertIsDisplayed()
            onNodeWithTag(SearchPageTestTags.SeasonFilter).onChild().assertIsNotEnabled()
        }
    }

    @Test
    fun `wide desktop date filters emit year and season intents`() {
        val intents = mutableListOf<SearchPageIntent>()
        runSearchPageTest(
            widthDp = 1400,
            state = createUiTestState(
                query = SubjectSearchQuery("test", year = 2025),
            ).copy(
                seasons = listOf(AnimeSeasonId(2026, AnimeSeason.SPRING)),
                expandedFilterGroups = setOf(SearchFilterTimeGroupKey),
            ),
            onIntent = { intents += it },
        ) {
            onNodeWithTag(SearchTimeFilterTestTags.YearSelector)
                .assert(hasAnyAncestor(hasTestTag(SearchFilterSidePanelTestTags.Panel)))
                .performClick()
            onNodeWithTag(SearchTimeFilterTestTags.year(2026)).performClick()
            onNodeWithTag(SearchTimeFilterTestTags.season(AnimeSeason.SPRING)).assertIsEnabled().performClick()
            runOnIdle {
                assertEquals(
                    listOf(
                        SearchPageIntent.ChangeYear(2026),
                        SearchPageIntent.ChangeSeason(AnimeSeason.SPRING),
                    ),
                    intents,
                )
            }
        }
    }

    @Test
    fun `wide desktop detail mode hides both filter presentations`() {
        runSearchPageTest(
            widthDp = 1400,
            state = createTestSearchPageState().copy(selectedItemIndex = 0),
        ) {
            assertTrue(onNodeWithTag(SearchFilterSidePanelTestTags.Panel).doesNotExist())
            assertTrue(onNodeWithTag(SearchPageTestTags.LegacyFilterRow).doesNotExist())
            assertTrue(onNodeWithTag(SearchTimeFilterTestTags.Content).doesNotExist())
        }
    }

    @Test
    fun `returning to search restores remembered expanded groups`() {
        val state = createUiTestState()
        val expandedGroup = state.searchFilterState.chips[0]
        val collapsedGroup = state.searchFilterState.chips[1]
        var showPage by mutableStateOf(true)
        runSearchPageTest(
            widthDp = 1400,
            state = state.copy(expandedFilterGroups = setOf(expandedGroup.groupKey, SearchFilterTimeGroupKey)),
            showPage = { showPage },
        ) {
            val expandedValue = onNodeWithTag(
                SearchFilterSidePanelTestTags.value(expandedGroup.groupKey, expandedGroup.values.first()),
            )
            val collapsedValue = onNodeWithTag(
                SearchFilterSidePanelTestTags.value(collapsedGroup.groupKey, collapsedGroup.values.first()),
            )
            assertTrue(expandedValue.exists())
            assertFalse(collapsedValue.exists())
            onNodeWithTag(SearchTimeFilterTestTags.YearSelector).assertIsDisplayed()
            runOnIdle { showPage = false }
            assertTrue(onNodeWithTag(SearchFilterSidePanelTestTags.Panel).doesNotExist())
            runOnIdle { showPage = true }
            assertTrue(expandedValue.exists())
            assertFalse(collapsedValue.exists())
            onNodeWithTag(SearchTimeFilterTestTags.YearSelector).assertIsDisplayed()
        }
    }

    @Test
    fun `wide desktop group headers report expansion intents`() {
        val intents = mutableListOf<SearchPageIntent>()
        runSearchPageTest(widthDp = 1400, onIntent = { intents += it }) {
            val groupKey = SearchFilterTimeGroupKey
            onNodeWithTag(SearchFilterSidePanelTestTags.group(groupKey)).performClick()
            runOnIdle {
                assertEquals(listOf<SearchPageIntent>(SearchPageIntent.ToggleFilterGroup(groupKey)), intents)
            }
        }
    }

    @Test
    fun `side filters expand collapse select and clear a group`() = runAniComposeUiTest {
        val genre = createTestSearchPageState().searchFilterState.chips.first()
        val selectedValue = genre.values.first()
        val unselectedValue = genre.values[1]
        var selectedTags by mutableStateOf(listOf(selectedValue))
        var expandedGroups by mutableStateOf(emptySet<String>())

        setContent {
            ProvideCompositionLocalsForPreview {
                Surface(Modifier.size(width = SearchFilterSidePanelWidth, height = 720.dp)) {
                    val filterState = buildSearchFilterState(selectedTags)
                    SearchFilterSidePanel(
                        state = filterState,
                        expandedGroupKeys = expandedGroups,
                        onToggleGroup = { groupKey ->
                            expandedGroups = if (groupKey in expandedGroups) {
                                expandedGroups - groupKey
                            } else {
                                expandedGroups + groupKey
                            }
                        },
                        onCheckedChange = { _, value ->
                            selectedTags = if (value in selectedTags) {
                                selectedTags - value
                            } else {
                                selectedTags + value
                            }
                        },
                        onClearGroup = { chip ->
                            selectedTags = selectedTags.filterNot { it in chip.values }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val groupKey = genre.groupKey
        val selectedNode = onNodeWithTag(
            SearchFilterSidePanelTestTags.value(groupKey, selectedValue),
        )
        val unselectedNode = onNodeWithTag(
            SearchFilterSidePanelTestTags.value(groupKey, unselectedValue),
        )
        assertFalse(selectedNode.exists())
        onNodeWithTag(SearchFilterSidePanelTestTags.group(groupKey)).performClick()
        selectedNode.assertIsSelected()
        unselectedNode.assertIsNotSelected()

        onNodeWithTag(SearchFilterSidePanelTestTags.group(groupKey)).performClick()
        assertFalse(selectedNode.exists())
        onNodeWithTag(SearchFilterSidePanelTestTags.group(groupKey)).performClick()
        unselectedNode.performClick().assertIsSelected()

        onNodeWithTag(SearchFilterSidePanelTestTags.all(groupKey)).performClick().assertIsSelected()
        selectedNode.assertIsNotSelected()
        unselectedNode.assertIsNotSelected()
        runOnIdle { assertEquals(emptyList(), selectedTags) }
    }

    private fun runSearchPageTest(
        widthDp: Int,
        state: SearchPageState = createUiTestState(),
        onIntent: (SearchPageIntent) -> Unit = {},
        showPage: () -> Boolean = { true },
        block: SkikoComposeUiTest.() -> Unit,
    ) {
        runSkikoComposeUiTest(
            size = Size(widthDp.toFloat(), 900f),
            density = Density(1f),
        ) {
            setContent {
                ProvideCompositionLocalsForPreview {
                    if (showPage()) {
                        SearchPage(
                            state = state,
                            onIntent = onIntent,
                            suggestionsPager = { MutableStateFlow(PagingData.empty()) },
                            detailContent = { Surface(Modifier.fillMaxSize()) {} },
                        )
                    }
                }
            }
            waitForIdle()
            block()
        }
    }

    private fun createUiTestState(
        query: SubjectSearchQuery = SubjectSearchQuery("test"),
    ): SearchPageState = createTestSearchPageState(
        searchState = TestSearchState(
            MutableStateFlow(
                MutableStateFlow(PagingData.from(TestSubjectPreviewItemInfos.take(12))),
            ),
        ),
        query = query,
    )
}
