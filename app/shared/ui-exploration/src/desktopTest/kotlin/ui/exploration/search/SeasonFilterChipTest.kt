/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search_filter_season_all
import me.him188.ani.app.ui.lang.exploration_search_filter_year_all
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

class YearFilterChipTest {
    @Test
    fun `shows all years label when nothing selected`() = runAniComposeUiTest {
        val allYearsText = runBlocking { getString(Lang.exploration_search_filter_year_all) }
        setContent {
            ProvideCompositionLocalsForPreview {
                YearFilterChip(
                    years = listOf(2026, 2025),
                    selectedYear = null,
                    onSelect = {},
                )
            }
        }

        onNodeWithText(allYearsText).assertIsDisplayed()
    }

    @Test
    fun `selecting a year from dropdown reports callback`() = runAniComposeUiTest {
        val allYearsText = runBlocking { getString(Lang.exploration_search_filter_year_all) }
        var selected: Int? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                YearFilterChip(
                    years = listOf(2026, 2025),
                    selectedYear = selected,
                    onSelect = { selected = it },
                )
            }
        }

        // chip 与菜单首项同文案, 取第一个 (chip 本身) 展开下拉
        onAllNodesWithText(allYearsText).onFirst().performClick()
        onNodeWithText("2026").performClick()

        runOnIdle {
            assertEquals(2026, selected)
        }
    }
}

class QuarterFilterChipTest {
    @Test
    fun `shows all seasons label when nothing selected`() = runAniComposeUiTest {
        val allSeasonsText = runBlocking { getString(Lang.exploration_search_filter_season_all) }
        setContent {
            ProvideCompositionLocalsForPreview {
                QuarterFilterChip(
                    selectedQuarter = null,
                    onSelect = {},
                    enabled = true,
                )
            }
        }

        onNodeWithText(allSeasonsText).assertIsDisplayed()
    }

    @Test
    fun `selecting a quarter from dropdown reports callback`() = runAniComposeUiTest {
        val allSeasonsText = runBlocking { getString(Lang.exploration_search_filter_season_all) }
        var selected: Int? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                QuarterFilterChip(
                    selectedQuarter = selected,
                    onSelect = { selected = it },
                    enabled = true,
                )
            }
        }

        onAllNodesWithText(allSeasonsText).onFirst().performClick()
        onNodeWithText("Q3").performClick()

        runOnIdle {
            assertEquals(3, selected)
        }
    }
}
