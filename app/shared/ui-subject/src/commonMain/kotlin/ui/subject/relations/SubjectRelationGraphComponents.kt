/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.relations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import me.him188.ani.app.ui.lang.subject_relation_graph_episodes
import me.him188.ani.app.ui.lang.subject_relation_graph_label_current
import me.him188.ani.app.ui.lang.subject_relation_graph_ordinal
import me.him188.ani.app.ui.lang.subject_relation_graph_platform_movie
import me.him188.ani.app.ui.lang.subject_relation_graph_show_less
import me.him188.ani.app.ui.lang.subject_relation_graph_show_more
import me.him188.ani.app.ui.subject.details.components.renderSubjectRelation
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.stringResource

@Immutable
internal class TimelineColors(
    val reached: Color,
    val upcoming: Color,
    val currentRing: Color,
    val surface: Color,
)

internal object SubjectRelationGraphDefaults {
    @Composable
    fun timelineColors(): TimelineColors = TimelineColors(
        reached = MaterialTheme.colorScheme.primary,
        upcoming = MaterialTheme.colorScheme.outline,
        currentRing = MaterialTheme.colorScheme.primaryContainer,
        surface = MaterialTheme.colorScheme.surface,
    )

    /** 宽屏海报尺寸 */
    val PosterWidth = 176.dp
    val PosterHeight = 248.dp

    /** 手机上主线条目的海报尺寸 */
    val CompactPosterWidth = 96.dp
    val CompactPosterHeight = 136.dp
    val CompactCardPadding = 8.dp

    /** 相关条目的小封面尺寸 */
    val BranchCoverWidth = 40.dp
    val BranchCoverHeight = 56.dp

    /** 相关条目超过这个数量时, 其余的折叠 */
    const val COLLAPSED_BRANCH_COUNT_COMPACT = 3
    const val COLLAPSED_BRANCH_COUNT_WIDE = 4
}

internal enum class TimelineDot { CURRENT, REACHED, UPCOMING }

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
        TimelineLine.UPCOMING -> colors.upcoming.copy(alpha = 0.5f)
    }
    drawLine(color, start, end, strokeWidth = 2.dp.toPx())
}

private fun DrawScope.drawTimelineDot(colors: TimelineColors, dot: TimelineDot, center: Offset) {
    when (dot) {
        TimelineDot.CURRENT -> {
            drawCircle(colors.currentRing, 12.dp.toPx(), center)
            drawCircle(colors.reached, 7.dp.toPx(), center)
        }

        TimelineDot.REACHED -> drawCircle(colors.reached, 6.dp.toPx(), center)
        TimelineDot.UPCOMING -> {
            drawCircle(colors.surface, 6.dp.toPx(), center)
            drawCircle(colors.upcoming, 5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * 宽屏的主线条目: 大海报, 下方是 "第几部", 标题和信息. 收藏状态显示为海报左上角的角标.
 * 当前条目的海报有主色描边.
 */
@Composable
internal fun SubjectRelationGraphPoster(
    node: SubjectRelationGraphMainNode,
    ordinal: Int?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = node.subject
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            SubjectCover(
                subject,
                Modifier.size(SubjectRelationGraphDefaults.PosterWidth, SubjectRelationGraphDefaults.PosterHeight)
                    .then(if (isCurrent) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
                shape,
            )
            CollectionTypeBadge(subject.collectionType, Modifier.padding(6.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MainNodeLabel(node, ordinal, isCurrent)
            Text(
                subject.displayName,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                renderPlatformAndEpisodes(subject, omitMovie = node.isMovie),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 手机的主线条目: 左侧海报, 右侧文字. 当前条目有浅色底.
 */
@Composable
internal fun SubjectRelationGraphCompactCard(
    node: SubjectRelationGraphMainNode,
    ordinal: Int?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = node.subject
    Surface(
        onClick,
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
    ) {
        Row(
            Modifier.padding(SubjectRelationGraphDefaults.CompactCardPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubjectCover(
                subject,
                Modifier.size(
                    SubjectRelationGraphDefaults.CompactPosterWidth,
                    SubjectRelationGraphDefaults.CompactPosterHeight,
                ),
                RoundedCornerShape(10.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MainNodeLabel(node, ordinal, isCurrent)
                    CollectionTypeText(subject.collectionType)
                }
                Text(
                    subject.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        renderYear(subject),
                        renderPlatformAndEpisodes(subject, omitMovie = node.isMovie).ifEmpty { null },
                    )
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * "第 N 部" 或 "剧场版", 当前条目后接 "· 当前" 并使用主色.
 */
@Composable
private fun MainNodeLabel(node: SubjectRelationGraphMainNode, ordinal: Int?, isCurrent: Boolean) {
    val label = when {
        ordinal != null -> stringResource(Lang.subject_relation_graph_ordinal, ordinal)
        else -> stringResource(Lang.subject_relation_graph_platform_movie)
    }
    Text(
        if (isCurrent) stringResource(Lang.subject_relation_graph_label_current, label) else label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * 一个主线条目下的相关条目列表: 每行是小封面, 名称, 以及 "关系 · 年份 · 收藏状态".
 *
 * 超过 [collapsedCount] 个时只显示前 [collapsedCount] 个, 其余需点击展开. 用户查看的条目在被折叠的部分时默认展开.
 *
 * @param seriesName 系列名称. 以它开头的条目名称只显示后面的部分, 例如 "雪之回忆".
 * @param nameMaxLines 窄列中名称可以换行
 */
@Composable
internal fun SubjectRelationGraphBranchList(
    branches: List<SubjectRelationGraphBranch>,
    currentSubjectId: Int,
    seriesName: String,
    collapsedCount: Int,
    nameMaxLines: Int,
    onClick: (SubjectRelationGraphSubject) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (branches.isEmpty()) return
    val canCollapse = branches.size > collapsedCount
    var expanded by rememberSaveable(branches, currentSubjectId) {
        mutableStateOf(branches.drop(collapsedCount).any { it.subject.subjectId == currentSubjectId })
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val visible = if (canCollapse && !expanded) branches.take(collapsedCount) else branches
        for (branch in visible) {
            BranchRow(
                branch,
                name = remember(branch, seriesName) { branch.subject.displayName.removeSeriesPrefix(seriesName) },
                isCurrent = branch.subject.subjectId == currentSubjectId,
                nameMaxLines = nameMaxLines,
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
            }
        }
    }
}

@Composable
private fun BranchRow(
    branch: SubjectRelationGraphBranch,
    name: String,
    isCurrent: Boolean,
    nameMaxLines: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = branch.subject
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SubjectRelationGraphDefaults.BranchCoverHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectCover(
            subject,
            Modifier.size(SubjectRelationGraphDefaults.BranchCoverWidth, SubjectRelationGraphDefaults.BranchCoverHeight),
            RoundedCornerShape(6.dp),
        )
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = nameMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                Text(
                    listOfNotNull(
                        branch.relation?.let { renderSubjectRelation(it) },
                        renderYear(subject),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                renderCollectionType(subject.collectionType)?.let {
                    Text(
                        " · $it",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 名称以 "系列名 + 空格" 开头时去掉这部分. 只按完整的系列名匹配, 因此不会把名称截成半句.
 */
internal fun String.removeSeriesPrefix(seriesName: String): String {
    if (seriesName.isEmpty() || !startsWith(seriesName)) return this
    val rest = substring(seriesName.length)
    if (rest.firstOrNull()?.isWhitespace() != true) return this
    return rest.trim().ifEmpty { this }
}

@Composable
private fun SubjectCover(subject: SubjectRelationGraphSubject, modifier: Modifier, shape: Shape) {
    Surface(modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        AsyncImage(
            subject.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = if (currentAniBuildConfig.isDebug) remember { ColorPainter(Color.Gray) } else null,
        )
    }
}

/** 海报角标: 深色半透明底, 在任何封面上都清晰 */
@Composable
private fun CollectionTypeBadge(type: UnifiedCollectionType, modifier: Modifier = Modifier) {
    val text = renderCollectionType(type) ?: return
    Box(modifier.background(PosterBadgeColor, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun CollectionTypeText(type: UnifiedCollectionType) {
    val text = renderCollectionType(type) ?: return
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (type == UnifiedCollectionType.DOING) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
    )
}

/** 未收藏时为 `null` */
@Composable
private fun renderCollectionType(type: UnifiedCollectionType): String? = when (type) {
    UnifiedCollectionType.WISH -> stringResource(Lang.subject_collection_wish)
    UnifiedCollectionType.DOING -> stringResource(Lang.subject_collection_doing)
    UnifiedCollectionType.DONE -> stringResource(Lang.subject_collection_done)
    UnifiedCollectionType.ON_HOLD -> stringResource(Lang.subject_collection_on_hold)
    UnifiedCollectionType.DROPPED -> stringResource(Lang.subject_collection_dropped)
    UnifiedCollectionType.NOT_COLLECTED -> null
}

internal fun renderYear(subject: SubjectRelationGraphSubject): String? =
    subject.airDate.takeIf { it.isValid }?.year?.toString()

/**
 * 例如 "TV · 25 话". 未知的部分省略; 只有一集 (剧场版, OVA) 时不显示集数.
 *
 * @param omitMovie 不显示 "剧场版". 主线上的剧场版已经用 "剧场版" 代替了 "第几部".
 */
@Composable
private fun renderPlatformAndEpisodes(subject: SubjectRelationGraphSubject, omitMovie: Boolean): String {
    val platform = when (subject.platform.takeUnless { omitMovie && it == SubjectRelationGraphPlatform.MOVIE }) {
        SubjectRelationGraphPlatform.TV -> "TV"
        SubjectRelationGraphPlatform.OVA -> "OVA"
        SubjectRelationGraphPlatform.WEB -> "WEB"
        SubjectRelationGraphPlatform.MOVIE -> stringResource(Lang.subject_relation_graph_platform_movie)
        null -> null
    }
    val episodes = if (subject.episodeCount > 1) {
        stringResource(Lang.subject_relation_graph_episodes, subject.episodeCount)
    } else null
    return listOfNotNull(platform, episodes).joinToString(" · ")
}

private val PosterBadgeColor = Color(0xC7141218)
