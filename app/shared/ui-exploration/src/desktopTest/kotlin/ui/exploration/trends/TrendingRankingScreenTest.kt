/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.trends

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_trending_ranking_empty
import me.him188.ani.app.ui.lang.exploration_trending_ranking_heat
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class TrendingRankingScreenTest {
    @Test
    fun `shows rank and heat of each subject`() = runAniComposeUiTest {
        val pager = createTestPager(listOf(item(rank = 1, heat = 7559, "条目一"), item(rank = 2, heat = 5330, "条目二")))
        setContent {
            ProvideCompositionLocalsForPreview {
                TrendingRankingScreen(pager.collectAsLazyPagingItems(), onClickItem = {})
            }
        }

        onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("2", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(heatText(7559), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(heatText(5330), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `clicking a subject reports it`() = runAniComposeUiTest {
        val pager = createTestPager(listOf(item(rank = 1, heat = 7559, "条目一"), item(rank = 2, heat = 5330, "条目二")))
        var clicked: TrendingRankingItemPresentation? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                TrendingRankingScreen(pager.collectAsLazyPagingItems(), onClickItem = { clicked = it })
            }
        }

        onNodeWithText("条目二", useUnmergedTree = true).performClick()

        runOnIdle {
            assertEquals(2, clicked?.rank)
        }
    }

    @Test
    fun `shows empty text when the ranking has no subjects`() = runAniComposeUiTest {
        val pager = createTestPager(emptyList<TrendingRankingItemPresentation>())
        setContent {
            ProvideCompositionLocalsForPreview {
                TrendingRankingScreen(pager.collectAsLazyPagingItems(), onClickItem = {})
            }
        }

        onNodeWithText(runBlocking { getString(Lang.exploration_trending_ranking_empty) }).assertIsDisplayed()
    }

    private fun heatText(heat: Int) = runBlocking { getString(Lang.exploration_trending_ranking_heat, heat.toString()) }

    private fun item(rank: Int, heat: Int, title: String) = TrendingRankingItemPresentation(
        rank = rank,
        heat = heat,
        subject = SubjectPreviewItemInfo(
            subjectId = rank,
            imageUrl = "",
            title = title,
            tags = "2026 年 10 月 · 全 12 话",
            staff = null,
            actors = null,
            rating = RatingInfo(rank = 100, total = 500, count = RatingCounts.Zero, score = "7.5"),
            nsfw = false,
            nsfwMode = NsfwMode.DISPLAY,
        ),
    )
}
