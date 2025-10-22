/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.episode.SubjectRecommendation
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SubjectRecommendationCard(
    onClick: () -> Unit,
    item: SubjectRecommendation,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick,
        modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.clip(MaterialTheme.shapes.medium)) {
                AsyncImage(
                    item.imageUrl,
                    modifier = Modifier.size(139.dp, 78.dp),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }

            Column(
                Modifier.heightIn(min = 78.dp).weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    item.nameCn ?: item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Column {
                    Text(
                        item.desc1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.desc2,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@TestOnly
val TestSubjectRecommendation
    get() = SubjectRecommendation(
        subjectId = 123456,
        name = "進撃の巨人",
        nameCn = "进击的巨人",
        desc1 = "2022 年 10 月",
        desc2 = "2 万收藏 · 7.3 分",
        imageUrl = "https://lain.bgm.tv/pic/cover/l/8e",
        uri = null,
    )

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewSubjectRecommendationCard() = ProvideCompositionLocalsForPreview {
    SubjectRecommendationCard({}, TestSubjectRecommendation)
}
