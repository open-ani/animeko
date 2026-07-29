/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.subject.episode.list.createTestEpisodeListItem
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
}

private const val EPISODE_CELL_TAG = "episode_grid_cell"
