/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.app.ui.subject.details.TestRelatedSubjects
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.math.ceil

@Composable
fun RelatedSubjectsRow(
    items: LazyPagingItems<RelatedSubjectInfo>,
    onClick: (RelatedSubjectInfo) -> Unit,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 24.dp,
    verticalSpacing: Dp = 24.dp,
) {
    BoxWithConstraints(modifier) {
        val maxItemsInEachRow by remember {
            derivedStateOf {
                ceil(maxWidth.value / 240f).toInt()
            }
        }
        val itemWidth by remember {
            derivedStateOf {
                val availableWidth = (maxWidth - (horizontalSpacing * (maxItemsInEachRow - 1).coerceAtLeast(0)))
                    .coerceAtLeast(0.dp)
                availableWidth / maxItemsInEachRow
            }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            maxItemsInEachRow = maxItemsInEachRow,
        ) {
            repeat(items.itemCount) {
                val item = items[it] ?: return@repeat
                RelatedSubjectItem(
                    item.image,
                    title = { RelatedSubjectItemDefaults.Title(item.displayName) },
                    relation = item.relation,
                    onClick = { onClick(item) },
                    Modifier.width(itemWidth),
                )
            }
        }
    }
}

private object RelatedSubjectItemDefaults {
    @Composable
    fun Title(text: String) {
        Box(contentAlignment = Alignment.Center) {
            Text("\n\n", Modifier.alpha(0f), maxLines = 2, overflow = TextOverflow.Ellipsis) // 占位置
            Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RelatedSubjectItem(
    coverImageUrl: String?,
    title: @Composable () -> Unit,
    relation: SubjectRelation?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    Card(
        onClick,
        modifier,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(bottom = 16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.clip(
                    MaterialTheme.shapes.medium.copy(
                        bottomEnd = CornerSize(0),
                        bottomStart = CornerSize(0),
                    ),
                ),
            ) {
                AsyncImage(
                    coverImageUrl,
                    null,
                    Modifier.fillMaxWidth().height(height),
                    contentScale = ContentScale.Crop,
                    placeholder = if (currentAniBuildConfig.isDebug) remember { ColorPainter(Color.Gray) } else null,
                )

                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    relation?.let {
                        Surface(
                            Modifier.align(Alignment.TopStart).alpha(0.9f),
                            shape = MaterialTheme.shapes.small.copy(
                                topStart = MaterialTheme.shapes.medium.topStart,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    when (it) {
                                        SubjectRelation.PREQUEL -> "前传"
                                        SubjectRelation.SEQUEL -> "续集"
                                        SubjectRelation.DERIVED -> "衍生"
                                        SubjectRelation.SPECIAL -> "番外篇"
                                    },
                                )
                            }
                        }
                    }
                }
            }

            ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    title()
                }
            }
        }
    }
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
fun PreviewRelatedSubjectsRow() = ProvideCompositionLocalsForPreview {
    Surface {
        RelatedSubjectsRow(
            createTestPager(TestRelatedSubjects).collectAsLazyPagingItemsWithLifecycle(),
            onClick = {},
        )
    }
}
