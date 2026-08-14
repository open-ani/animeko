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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import kotlinx.coroutines.flow.MutableStateFlow
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
        }
    }

    @Test
    fun `side filters expand collapse select and clear a group`() = runAniComposeUiTest {
        val genre = createTestSearchPageState().searchFilterState.chips.first()
        val selectedValue = genre.values.first()
        val unselectedValue = genre.values[1]
        var selectedTags by mutableStateOf(listOf(selectedValue))
        var collapsedGroups by mutableStateOf(emptyList<String>())

        setContent {
            ProvideCompositionLocalsForPreview {
                Surface(Modifier.size(width = SearchFilterSidePanelWidth, height = 720.dp)) {
                    val filterState = buildSearchFilterState(selectedTags)
                    SearchFilterSidePanel(
                        state = filterState,
                        collapsedGroupKeys = collapsedGroups,
                        onToggleGroup = { groupKey ->
                            collapsedGroups = if (groupKey in collapsedGroups) {
                                collapsedGroups - groupKey
                            } else {
                                collapsedGroups + groupKey
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
        block: SkikoComposeUiTest.() -> Unit,
    ) {
        runSkikoComposeUiTest(
            size = Size(widthDp.toFloat(), 900f),
            density = Density(1f),
        ) {
            setContent {
                ProvideCompositionLocalsForPreview {
                    SearchPage(
                        state = state,
                        onIntent = {},
                        suggestionsPager = { MutableStateFlow(PagingData.empty()) },
                        detailContent = { Surface(Modifier.fillMaxSize()) {} },
                    )
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
