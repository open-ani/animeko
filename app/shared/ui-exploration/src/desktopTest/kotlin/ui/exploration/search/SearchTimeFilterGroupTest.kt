/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.schedule.AnimeSeason
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.search.withYearFilter
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTimeFilterGroupTest {
    @Test
    fun `time category starts collapsed and exposes disabled seasons when opened`() = runAniComposeUiTest {
        var expanded by mutableStateOf(false)
        setContent {
            ProvideCompositionLocalsForPreview {
                Surface(Modifier.size(SearchFilterSidePanelWidth, 600.dp)) {
                    SearchTimeFilterGroup(
                        years = listOf(2026, 2025),
                        selectedYear = null,
                        selectedSeason = null,
                        expanded = expanded,
                        onToggleExpanded = { expanded = !expanded },
                        onYearChange = {},
                        onSeasonChange = {},
                    )
                }
            }
        }

        assertFalse(onNodeWithTag(SearchTimeFilterTestTags.Content).exists())
        onNodeWithTag(SearchFilterSidePanelTestTags.group(SearchFilterTimeGroupKey)).performClick()
        onNodeWithTag(SearchTimeFilterTestTags.YearSelector).assertIsDisplayed()
        for (season in AnimeSeason.entries) {
            onNodeWithTag(SearchTimeFilterTestTags.season(season)).assertIsNotEnabled()
        }
        onNodeWithTag(SearchFilterSidePanelTestTags.group(SearchFilterTimeGroupKey)).performClick()
        assertFalse(onNodeWithTag(SearchTimeFilterTestTags.Content).exists())
    }

    @Test
    fun `custom year popup selects years and clears the dependent season`() = runAniComposeUiTest {
        var query by mutableStateOf(SubjectSearchQuery(""))
        setContent {
            ProvideCompositionLocalsForPreview {
                Surface(Modifier.size(SearchFilterSidePanelWidth, 600.dp)) {
                    SearchTimeFilterGroup(
                        years = listOf(2026, 2025, 2024),
                        selectedYear = query.year,
                        selectedSeason = query.season,
                        expanded = true,
                        onToggleExpanded = {},
                        onYearChange = { query = query.withYearFilter(it) },
                        onSeasonChange = { query = query.copy(season = it) },
                    )
                }
            }
        }

        onNodeWithTag(SearchTimeFilterTestTags.YearSelector).performClick()
        onNodeWithTag(SearchTimeFilterTestTags.YearPopup).assertIsDisplayed()
        val popupBounds = onNodeWithTag(SearchTimeFilterTestTags.YearPopup).getUnclippedBoundsInRoot()
        val popupHeight = popupBounds.bottom - popupBounds.top
        assertTrue(popupHeight <= 140.dp, "A short year list should wrap its content: $popupHeight")
        onNodeWithTag(SearchTimeFilterTestTags.year(2026)).performClick()
        assertFalse(onNodeWithTag(SearchTimeFilterTestTags.YearPopup).exists())
        onNodeWithTag(SearchTimeFilterTestTags.season(AnimeSeason.SPRING))
            .assertIsEnabled().performClick().assertIsSelected()
        runOnIdle {
            assertEquals(2026, query.year)
            assertEquals(AnimeSeason.SPRING, query.season)
        }

        onNodeWithTag(SearchTimeFilterTestTags.YearSelector).performClick()
        onNodeWithTag(SearchTimeFilterTestTags.year(null)).performClick()
        onNodeWithTag(SearchTimeFilterTestTags.season(null)).assertIsSelected().assertIsNotEnabled()
        onNodeWithTag(SearchTimeFilterTestTags.season(AnimeSeason.SPRING)).assertIsNotSelected().assertIsNotEnabled()
        runOnIdle {
            assertEquals(null, query.year)
            assertEquals(null, query.season)
        }
    }

    @Test
    fun `year popup brings the saved year into view`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Surface(Modifier.size(SearchFilterSidePanelWidth, 600.dp)) {
                    SearchTimeFilterGroup(
                        years = (1950..2027).toList(),
                        selectedYear = 1994,
                        selectedSeason = null,
                        expanded = true,
                        onToggleExpanded = {},
                        onYearChange = {},
                        onSeasonChange = {},
                    )
                }
            }
        }

        onNodeWithTag(SearchTimeFilterTestTags.YearSelector).performClick()
        onNodeWithTag(SearchTimeFilterTestTags.year(1994)).assertIsDisplayed().assertIsSelected()
        onNodeWithTag(SearchTimeFilterTestTags.year(null)).assertIsDisplayed()
        val popupBounds = onNodeWithTag(SearchTimeFilterTestTags.YearPopup).getUnclippedBoundsInRoot()
        val popupHeight = popupBounds.bottom - popupBounds.top
        assertTrue(popupHeight <= 304.dp, "A long year list should stay within a compact popup: $popupHeight")
    }
}
