/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.rating

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.format1f


// https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Main?node-id=133-2765&t=innxKfrf4vLdukgs-4
@Composable
fun RatingText(
    rating: RatingInfo,
    modifier: Modifier = Modifier
) {
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        ProvideTextStyleContentColor(
            MaterialTheme.typography.titleLarge,
            MaterialTheme.colorScheme.tertiary,
        ) {
            val text = remember(rating.score) {
                renderScore(rating.score)
            }
            Text(
                text,
                softWrap = false,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
//        var hasOverflow by remember { mutableStateOf(false) }
        Box(Modifier.padding(start = 4.dp).fillMaxHeight()) {
            ProvideTextStyleContentColor(
                MaterialTheme.typography.labelSmall,
                MaterialTheme.colorScheme.tertiary,
            ) {
                Column {
                    Text(
                        "#${rating.rank}\n${rating.total} 人评",
                        maxLines = 2,
                        softWrap = false,
                    )
                }
//                Row {
//                    Text(
//                        "${rating.total} 人评丨#${rating.rank}",
//                        maxLines = 1,
//                        onTextLayout = { hasOverflow = it.hasVisualOverflow },
//                    )
//                }
//                if (hasOverflow) {
//                    Column {
//                        Text(
//                            "#${rating.rank}\n${rating.total} 人评",
//                            maxLines = 2,
//                            softWrap = false,
//                        )
//                    }
//                }
            }
        }
    }
}

/**
 * Output format: `1.0`, `6.1`, `0.0`, `10.0`
 */
@Stable
fun renderScore(score: String): String {
    return if (!score.contains(".")) {
        "$score.0"
    } else {
        score.toFloatOrNull()?.let {
            String.format1f(it)
        } ?: score
    }
}

@TestOnly
private val TestRatingInfo = RatingInfo(
    rank = 123,
    total = 100,
    count = RatingCounts(IntArray(10) { it * 10 }),
    score = "6.7",
)

@Composable
@Preview
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
fun PreviewRatingTextIntrinsicMin() {
    ProvideCompositionLocalsForPreview {
        Surface(Modifier.width(IntrinsicSize.Min)) {
            RatingText(
                TestRatingInfo,
            )
        }
    }
}

