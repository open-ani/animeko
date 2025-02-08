/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.rating

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly

@TestOnly
private val TestRatingInfo = RatingInfo(
    rank = 123,
    total = 100,
    count = RatingCounts(IntArray(10) { it * 10 }),
    score = "6.7",
)

@Composable
@Preview
@PreviewLightDark
fun PreviewRatingText() {
    ProvideCompositionLocalsForPreview {
        Surface(Modifier.width(200.dp)) {
            RatingText(
                TestRatingInfo,
            )
        }
    }
}

@Composable
@Preview
@PreviewLightDark
fun PreviewRatingTextIntrinsicMin() {
    ProvideCompositionLocalsForPreview {
        Surface(Modifier.width(IntrinsicSize.Min)) {
            RatingText(
                TestRatingInfo,
            )
        }
    }
}