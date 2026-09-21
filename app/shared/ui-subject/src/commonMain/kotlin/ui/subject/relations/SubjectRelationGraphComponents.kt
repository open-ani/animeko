/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.relations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.SubjectRelationGraphBranch
import me.him188.ani.app.data.models.subject.SubjectRelationGraphMainNode
import me.him188.ani.app.data.models.subject.SubjectRelationGraphPlatform
import me.him188.ani.app.data.models.subject.SubjectRelationGraphSubject
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_collection_doing
import me.him188.ani.app.ui.lang.subject_collection_done
import me.him188.ani.app.ui.lang.subject_collection_dropped
import me.him188.ani.app.ui.lang.subject_collection_on_hold
import me.him188.ani.app.ui.lang.subject_collection_wish
import me.him188.ani.app.ui.lang.subject_relation_graph_current
import me.him188.ani.app.ui.lang.subject_relation_graph_episodes
import me.him188.ani.app.ui.lang.subject_relation_graph_ordinal
import me.him188.ani.app.ui.lang.subject_relation_graph_ordinal_current
import me.him188.ani.app.ui.lang.subject_relation_graph_platform_movie
import me.him188.ani.app.ui.lang.subject_relation_graph_show_less
import me.him188.ani.app.ui.lang.subject_relation_graph_show_more
import me.him188.ani.app.ui.subject.details.components.renderSubjectRelation
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.stringResource

/**
 * 主线条目卡片的尺寸
 */
@Immutable
internal class MainNodeStyle(
    val height: Dp,
    val coverWidth: Dp,
    val coverHeight: Dp,
    val titleStyle: TextStyle,
)

@Immutable
internal class TimelineColors(
    val reached: Color,
    val upcoming: Color,
    val currentRing: Color,
    val surface: Color,
)

internal object SubjectRelationGraphDefaults {
    /**
     * @param large 宽屏布局使用更大的正片卡片
     */
    @Composable
    fun mainNodeStyle(isMinor: Boolean, large: Boolean): MainNodeStyle = when {
        isMinor -> MainNodeStyle(64.dp, 36.dp, 48.dp, MaterialTheme.typography.bodyMedium)
        large -> MainNodeStyle(136.dp, 84.dp, 112.dp, MaterialTheme.typography.titleMedium)
        else -> MainNodeStyle(112.dp, 66.dp, 88.dp, MaterialTheme.typography.titleSmall)
    }

    @Composable
    fun timelineColors(): TimelineColors = TimelineColors(
        reached = MaterialTheme.colorScheme.primary,
        upcoming = MaterialTheme.colorScheme.outlineVariant,
        currentRing = MaterialTheme.colorScheme.primaryContainer,
        surface = MaterialTheme.colorScheme.surface,
    )

    /** 分支超过这个数量时, 手机布局将其余的折叠 */
    const val COLLAPSED_BRANCH_COUNT = 3
}

internal enum class TimelineDot { CURRENT, REACHED, REACHED_SMALL, UPCOMING, UPCOMING_SMALL }

internal enum class TimelineLine { NONE, REACHED, UPCOMING }

/**
 * 在左侧画纵向时间线: 一条贯穿整个高度的线, 以及位于 [dotCenterY] 的节点圆点.
 */
internal fun Modifier.timelineVertical(
    colors: TimelineColors,
    dotCenterY: Dp,
    dot: TimelineDot,
    lineBefore: TimelineLine,
    lineAfter: TimelineLine,
    centerX: Dp = 12.dp,
): Modifier = drawBehind {
    val center = Offset(centerX.toPx(), dotCenterY.toPx())
    drawTimelineLine(colors, lineBefore, Offset(center.x, 0f), center)
    drawTimelineLine(colors, lineAfter, center, Offset(center.x, size.height))
    drawTimelineDot(colors, dot, center)
}

/**
 * 画横向时间线: 一条贯穿整个宽度的线, 以及位于 [dotCenterX] 的节点圆点.
 */
internal fun Modifier.timelineHorizontal(
    colors: TimelineColors,
    dotCenterX: Dp,
    dot: TimelineDot,
    lineBefore: TimelineLine,
    lineAfter: TimelineLine,
): Modifier = drawBehind {
    val center = Offset(dotCenterX.toPx(), size.height / 2)
    drawTimelineLine(colors, lineBefore, Offset(0f, center.y), center)
    drawTimelineLine(colors, lineAfter, center, Offset(size.width, center.y))
    drawTimelineDot(colors, dot, center)
}

private fun DrawScope.drawTimelineLine(colors: TimelineColors, line: TimelineLine, start: Offset, end: Offset) {
    val color = when (line) {
        TimelineLine.NONE -> return
        TimelineLine.REACHED -> colors.reached
        TimelineLine.UPCOMING -> colors.upcoming
    }
    drawLine(color, start, end, strokeWidth = 2.dp.toPx())
}

private fun DrawScope.drawTimelineDot(colors: TimelineColors, dot: TimelineDot, center: Offset) {
    when (dot) {
        TimelineDot.CURRENT -> {
            drawCircle(colors.currentRing, 12.dp.toPx(), center)
            drawCircle(colors.reached, 8.dp.toPx(), center)
        }

        TimelineDot.REACHED -> drawCircle(colors.reached, 8.dp.toPx(), center)
        TimelineDot.REACHED_SMALL -> drawCircle(colors.reached, 5.dp.toPx(), center)
        TimelineDot.UPCOMING, TimelineDot.UPCOMING_SMALL -> {
            val radius = if (dot == TimelineDot.UPCOMING) 8.dp else 5.dp
            drawCircle(colors.surface, radius.toPx(), center)
            drawCircle(colors.upcoming, radius.toPx() - 1.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * 主线条目卡片. 次要条目 ([SubjectRelationGraphMainNode.isMinor]) 显示为较矮的虚线框卡片.
 *
 * @param ordinal 第几部, 次要条目为 `null`
 * @param isCurrent 是否为用户查看的条目
 */
@Composable
internal fun SubjectRelationGraphMainNodeCard(
    node: SubjectRelationGraphMainNode,
    ordinal: Int?,
    isCurrent: Boolean,
    style: MainNodeStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = node.subject
    val containerColor =
        if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor =
        if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val secondaryColor =
        if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    if (node.isMinor) {
        val shape = RoundedCornerShape(12.dp)
        Surface(
            onClick,
            modifier.height(style.height).dashedBorder(borderColor, cornerRadius = 12.dp),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                Modifier.padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubjectCover(subject, style, RoundedCornerShape(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(subject.displayName, style = style.titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        renderMeta(subject),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor,
                        maxLines = 1,
                    )
                }
                if (isCurrent) {
                    LabelChip(
                        stringResource(Lang.subject_relation_graph_current),
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    CollectionTypeChip(subject.collectionType)
                }
            }
        }
        return
    }

    Surface(
        onClick,
        modifier.height(style.height),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubjectCover(subject, style, RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            ordinal == null -> ""
                            isCurrent -> stringResource(Lang.subject_relation_graph_ordinal_current, ordinal)
                            else -> stringResource(Lang.subject_relation_graph_ordinal, ordinal)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = secondaryColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    CollectionTypeChip(subject.collectionType)
                }
                Text(subject.displayName, style = style.titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    renderMeta(subject),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 挂在一个主线条目下的分支列表, 左侧用虚线连到时间线.
 *
 * @param collapsible 为 `true` 时只显示前 [SubjectRelationGraphDefaults.COLLAPSED_BRANCH_COUNT] 个, 其余需点击展开.
 * 用户查看的条目在被折叠的部分时默认展开.
 * @param lineStart 虚线距离左边缘的距离
 */
@Composable
internal fun SubjectRelationGraphBranchList(
    branches: List<SubjectRelationGraphBranch>,
    currentSubjectId: Int,
    collapsible: Boolean,
    onClick: (SubjectRelationGraphSubject) -> Unit,
    modifier: Modifier = Modifier,
    lineStart: Dp = 20.dp,
) {
    if (branches.isEmpty()) return
    val collapsedCount = SubjectRelationGraphDefaults.COLLAPSED_BRANCH_COUNT
    val canCollapse = collapsible && branches.size > collapsedCount
    var expanded by rememberSaveable(branches, currentSubjectId) {
        mutableStateOf(branches.drop(collapsedCount).any { it.subject.subjectId == currentSubjectId })
    }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .drawBehind {
                val x = lineStart.toPx() + 1.dp.toPx()
                drawLine(
                    lineColor, Offset(x, 0f), Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
            .padding(start = lineStart + 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val visible = if (canCollapse && !expanded) branches.take(collapsedCount) else branches
        for (branch in visible) {
            BranchRow(
                branch,
                isCurrent = branch.subject.subjectId == currentSubjectId,
                onClick = { onClick(branch.subject) },
            )
        }
        if (canCollapse) {
            TextButton({ expanded = !expanded }) {
                Text(
                    if (expanded) {
                        stringResource(Lang.subject_relation_graph_show_less)
                    } else {
                        stringResource(Lang.subject_relation_graph_show_more, branches.size - collapsedCount)
                    },
                )
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    Modifier.padding(start = 4.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun BranchRow(
    branch: SubjectRelationGraphBranch,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = branch.subject
    val contentColor =
        if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 2.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectCover(
            subject,
            SubjectRelationGraphDefaults.mainNodeStyle(isMinor = true, large = false),
            RoundedCornerShape(6.dp),
        )
        // 名称独占一行, 收藏状态和关系标签放在第二行, 窄屏上名称才不会被标签挤掉
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                subject.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    renderMeta(subject),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CollectionTypeChip(subject.collectionType)
                branch.relation?.let { RelationChip(it) }
            }
        }
    }
}

@Composable
private fun SubjectCover(subject: SubjectRelationGraphSubject, style: MainNodeStyle, shape: RoundedCornerShape) {
    Surface(
        Modifier.size(style.coverWidth, style.coverHeight),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        AsyncImage(
            subject.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = if (currentAniBuildConfig.isDebug) remember { ColorPainter(Color.Gray) } else null,
        )
    }
}

@Composable
private fun CollectionTypeChip(type: UnifiedCollectionType) {
    when (type) {
        UnifiedCollectionType.NOT_COLLECTED -> {}
        UnifiedCollectionType.DOING -> LabelChip(
            stringResource(Lang.subject_collection_doing),
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
        )

        UnifiedCollectionType.DONE -> LabelChip(
            stringResource(Lang.subject_collection_done),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )

        UnifiedCollectionType.WISH,
        UnifiedCollectionType.ON_HOLD,
        UnifiedCollectionType.DROPPED -> LabelChip(
            when (type) {
                UnifiedCollectionType.WISH -> stringResource(Lang.subject_collection_wish)
                UnifiedCollectionType.ON_HOLD -> stringResource(Lang.subject_collection_on_hold)
                else -> stringResource(Lang.subject_collection_dropped)
            },
            Color.Transparent,
            MaterialTheme.colorScheme.onSurfaceVariant,
            borderColor = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun RelationChip(relation: SubjectRelation) {
    val (containerColor, contentColor) = when (relation) {
        SubjectRelation.DERIVED ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

        SubjectRelation.MAIN_STORY ->
            MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant

        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    LabelChip(renderSubjectRelation(relation), containerColor, contentColor)
}

@Composable
private fun LabelChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color = Color.Transparent,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.background(containerColor, shape).border(1.dp, borderColor, shape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1)
    }
}

/**
 * 例如 "2016 · TV · 25 话". 未知的部分省略; 只有一集 (剧场版, OVA) 时不显示集数.
 */
@Composable
private fun renderMeta(subject: SubjectRelationGraphSubject): String {
    val platform = when (subject.platform) {
        SubjectRelationGraphPlatform.TV -> "TV"
        SubjectRelationGraphPlatform.OVA -> "OVA"
        SubjectRelationGraphPlatform.WEB -> "WEB"
        SubjectRelationGraphPlatform.MOVIE -> stringResource(Lang.subject_relation_graph_platform_movie)
        null -> null
    }
    val episodes = if (subject.episodeCount > 1) {
        stringResource(Lang.subject_relation_graph_episodes, subject.episodeCount)
    } else null
    return listOfNotNull(
        subject.airDate.takeIf { it.isValid }?.year?.toString(),
        platform,
        episodes,
    ).joinToString(" · ")
}

// 画在内容之上: Surface 的背景会盖住 drawBehind 的内容
private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp): Modifier = drawWithContent {
    drawContent()
    val strokeWidth = 1.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
        size = size.copy(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
    )
}
