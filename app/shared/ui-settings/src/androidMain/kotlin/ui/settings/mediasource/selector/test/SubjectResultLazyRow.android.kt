/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.domain.mediasource.test.MatchTag
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestSubjectPresentation
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.xml.Element
import kotlin.random.Random
import kotlin.random.nextInt

@TestOnly
fun createTestSelectorTestSubjectPresentation(
    name: String,
    subjectDetailsPageUrl: String = "",
    origin: Element? = null,
    tags: List<MatchTag> = emptyList(),
): SelectorTestSubjectPresentation {
    return SelectorTestSubjectPresentation(
        name, subjectDetailsPageUrl, origin, tags,
    )
}

@TestOnly
val TestSelectorTestSubjectPresentations
    get() = listOf(
        createTestSelectorTestSubjectPresentation(
            "孤独摇滚",
        ),
        createTestSelectorTestSubjectPresentation(
            "孤独摇滚",
        ),
        createTestSelectorTestSubjectPresentation(
            "孤独摇滚",
        ),
        createTestSelectorTestSubjectPresentation(
            "孤独摇滚",
        ),
        createTestSelectorTestSubjectPresentation(
            "孤独摇滚",
            tags = listOf(MatchTag("标题", isMatch = false), MatchTag("字幕", isMatch = true)),
        ),
        createTestSelectorTestSubjectPresentation(
            "孤独摇滚".repeat(10),
            tags = listOf(MatchTag("标题", isMatch = false)),
        ),
        createTestSelectorTestSubjectPresentation(
            "孤独摇滚".repeat(10),
            tags = listOf(MatchTag("标题", isMatch = false)),
        ),
        createTestSelectorTestSubjectPresentation(
            "孤独摇滚".repeat(10),
            tags = listOf(MatchTag("标题", isMatch = false)),
        ),
        createTestSelectorTestSubjectPresentation(
            "孤独摇滚".repeat(10),
            tags = listOf(MatchTag("标题", isMatch = false)),
        ),
    )

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewSubjectResultLazyRow() = ProvideCompositionLocalsForPreview {
    Surface {
        Column {
            var selectedIndex by remember { mutableIntStateOf(1) }
            SelectorTestSubjectResultLazyRow(
                TestSelectorTestSubjectPresentations,
                selectedItemIndex = selectedIndex,
                { _, _ -> },
            )

            Row {
                Text("Selected index: $selectedIndex")
                Button({ selectedIndex = Random.nextInt(TestSelectorTestSubjectPresentations.indices) }) {
                    Text("Select random")
                }
            }
        }
    }
}