/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 覆盖收藏统计一行在窄宽度下的显示: 完整文本放不下时缩写为 k, 放得下时保留原数字.
 *
 * 这一行在条目详情页的窄屏头部里与收藏按钮同排, 宽度不足时原先会被按钮边界切掉,
 * 因此缩写的触发条件必须是"真的放不下", 而不是固定宽度阈值.
 */
class SubjectCollectionDataLayoutTest {
    private val stats = SubjectCollectionStats(
        wish = 0,
        doing = 0,
        done = 0,
        onHold = 0,
        dropped = 1555,
    )

    private fun ComposeUiTest.hasText(text: String): Boolean =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `narrow width abbreviates the counts`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.width(120.dp)) {
                    SubjectDetailsDefaults.CollectionData(stats)
                }
            }
        }

        assertTrue(hasText("1.5k"), "窄宽度下 1555 应缩写为 1.5k")
        assertTrue(!hasText("1555"), "窄宽度下不应再显示完整数字")
    }

    @Test
    fun `wide width keeps the exact counts`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.width(640.dp)) {
                    SubjectDetailsDefaults.CollectionData(stats)
                }
            }
        }

        assertTrue(hasText("1555"), "宽宽度下应保留完整数字")
        assertTrue(!hasText("1.5k"), "宽宽度下不应缩写")
    }

    /** 千以内的数字任何宽度都不缩写. */
    @Test
    fun `small counts are never abbreviated`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.width(120.dp)) {
                    SubjectDetailsDefaults.CollectionData(
                        SubjectCollectionStats(wish = 0, doing = 42, done = 0, onHold = 0, dropped = 7),
                    )
                }
            }
        }

        assertTrue(hasText("42"), "42 应原样显示")
        assertTrue(hasText("7"), "7 应原样显示")
    }
}
