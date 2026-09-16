/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.subject.episode.list.EPISODE_STILL_TAG
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.app.ui.subject.episode.list.TestEpisodeStillUrl
import me.him188.ani.app.ui.subject.episode.list.createTestEpisodeListItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class EpisodesSectionTest {
    @Test
    fun `episode grid cell handles click and long click separately`() = runAniComposeUiTest {
        var clickCount = 0
        var longClickCount = 0

        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = createTestEpisodeListItem(),
                    isPlaying = false,
                    onClick = { clickCount++ },
                    onLongClick = { longClickCount++ },
                    modifier = Modifier.testTag(EPISODE_CELL_TAG),
                )
            }
        }

        onNodeWithTag(EPISODE_CELL_TAG).performClick()
        runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(0, longClickCount)
        }

        onNodeWithTag(EPISODE_CELL_TAG).performTouchInput { longClick() }
        runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(1, longClickCount)
        }
    }

    @Test
    fun `episode grid cell with still shows the image keeps text and handles clicks`() = runAniComposeUiTest {
        var clickCount = 0
        var longClickCount = 0

        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = stillItem(imageMedium = TestEpisodeStillUrl),
                    isPlaying = false,
                    onClick = { clickCount++ },
                    onLongClick = { longClickCount++ },
                    modifier = Modifier.testTag(EPISODE_CELL_TAG).width(104.dp),
                    height = 64.dp,
                )
            }
        }

        onNodeWithTag(EPISODE_STILL_TAG, useUnmergedTree = true).assertExists()
        onNodeWithText("13", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("第三话", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag(EPISODE_CELL_TAG).performClick()
        runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(0, longClickCount)
        }
        onNodeWithTag(EPISODE_CELL_TAG).performTouchInput { longClick() }
        runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(1, longClickCount)
        }
    }

    @Test
    fun `episode grid cell without still has no image layer`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = stillItem(imageMedium = null),
                    isPlaying = false,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.testTag(EPISODE_CELL_TAG).width(104.dp),
                    height = 64.dp,
                )
            }
        }

        onNodeWithTag(EPISODE_STILL_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("第三话", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `episode grid cell hides the still when images are disabled`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = stillItem(imageMedium = TestEpisodeStillUrl),
                    isPlaying = true,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.testTag(EPISODE_CELL_TAG).width(104.dp),
                    height = 64.dp,
                    showImage = false,
                )
            }
        }

        onNodeWithTag(EPISODE_STILL_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("13", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `episodes row renders cells with and without stills`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodesRow(
                    episodes = listOf(
                        stillItem(imageMedium = TestEpisodeStillUrl, episodeId = 1, sort = 1),
                        stillItem(imageMedium = null, episodeId = 2, sort = 2),
                    ),
                    currentEpisodeId = 1,
                    onEpisodeClick = {},
                    onEpisodeLongClick = {},
                    modifier = Modifier.testTag(EPISODES_ROW_TAG),
                )
            }
        }

        onNodeWithTag(EPISODES_ROW_TAG).assertExists()
        onAllNodesWithTag(EPISODE_STILL_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    private fun stillItem(
        imageMedium: String?,
        episodeId: Int = 13,
        sort: Int = 13,
    ): EpisodeListItem = createTestEpisodeListItem(
        sort = EpisodeSort(sort),
        episodeId = episodeId,
        nameCn = "第三话",
        collectionType = UnifiedCollectionType.WISH,
        isBroadcast = true,
        imageMedium = imageMedium,
    )
}

private const val EPISODE_CELL_TAG = "episode_grid_cell"
private const val EPISODES_ROW_TAG = "episodes_row"
