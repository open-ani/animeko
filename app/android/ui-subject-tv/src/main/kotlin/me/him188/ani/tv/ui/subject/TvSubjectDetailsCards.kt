/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.subject

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.nameCn
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCard
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCardDefaults
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor

/*
 * 详情页的卡片与信息块组件 (纯视图, 数据来自复用的 SubjectDetailsState;
 * 页面结构与焦点接线见 TvSubjectDetailsScreen).
 */

/** 收藏统计单元: 数值 + 标签. */
@Composable
internal fun StatColumn(value: Int, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            formatCount(value),
            style = MaterialTheme.typography.titleMedium,
            color = tvHeroContentColor(),
        )
        Text(
            label,
            Modifier.padding(bottom = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tvHeroSecondaryContentColor(),
        )
    }
}

/** 评分直方图 (1..10 竖条) + 分数 + 评分人数. */
@Composable
internal fun RatingBlock(info: SubjectInfo, modifier: Modifier = Modifier) {
    val rating = info.ratingInfo
    val counts = (1..10).map { rating.count.get(it) }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(modifier.width(240.dp), horizontalAlignment = Alignment.End) {
        Row(
            Modifier.height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            counts.forEach { count ->
                val fraction = (count.toFloat() / max).coerceIn(0.04f, 1f)
                Box(
                    Modifier
                        .width(13.dp)
                        .height((44 * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                )
            }
        }
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            (1..10).forEach {
                Text(
                    "$it",
                    Modifier.width(13.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = tvHeroSecondaryContentColor(),
                )
            }
        }
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                rating.score.takeIf { it.isNotBlank() } ?: "-",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tvHeroContentColor(),
            )
            Text(
                buildString {
                    if (rating.rank > 0) append("#${rating.rank} · ")
                    append("${formatCount(rating.total)} 人评分")
                },
                style = MaterialTheme.typography.labelMedium,
                color = tvHeroSecondaryContentColor(),
            )
        }
    }
}

/** 选集剧照卡: 16:9, 色圈+留白焦点 (与竖版卡同规格), 卡内左下角序号+标题. */
@Composable
internal fun TvEpisodeCard(
    episode: EpisodeListItem,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val watched = episode.isDoneOrDropped
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier
            .width(TvSubjectDetailsDefaults.EpisodeCardWidth)
            .aspectRatio(16f / 9f)
            .then(
                if (focused) {
                    Modifier.border(
                        TvFocusDefaults.RingWidth,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(TvFocusDefaults.RingCornerRadius),
                    )
                } else Modifier,
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(TvFocusDefaults.RingInset)
                .clip(TvPosterCardDefaults.ImageShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ),
        ) {
            AsyncImage(
                imageUrl,
                contentDescription = null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    episode.sort.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (watched) Color.White.copy(alpha = 0.55f) else Color.White,
                )
                Text(
                    episode.nameCn.ifBlank { episode.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (watched) Color.White.copy(alpha = 0.55f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 下方区块的标题行: 标题 + 可选计数. */
@Composable
internal fun TvDetailsSectionHeader(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = TvSubjectDetailsDefaults.HorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = tvHeroContentColor())
        count?.let {
            Text(
                "$it",
                style = MaterialTheme.typography.labelLarge,
                color = tvHeroSecondaryContentColor(),
            )
        }
    }
}

/** 角色卡: 头像 + 角色名 + 声优名 (色圈聚焦). */
@Composable
internal fun TvCharacterCard(info: RelatedCharacterInfo, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier
            .width(TvSubjectDetailsDefaults.PersonCardWidth)
            .then(
                if (focused) {
                    Modifier.border(
                        TvFocusDefaults.RingWidth,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(TvFocusDefaults.RingCornerRadius),
                    )
                } else Modifier,
            )
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {}
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            info.character.imageMedium,
            contentDescription = null,
            Modifier.size(72.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Text(
            info.character.nameCn.ifBlank { info.character.name },
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tvHeroContentColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            info.character.actors.firstOrNull()?.displayName.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = tvHeroSecondaryContentColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 制作人员卡: 头像 + 名字 + 职位. */
@Composable
internal fun TvStaffCard(info: RelatedPersonInfo, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier
            .width(TvSubjectDetailsDefaults.PersonCardWidth)
            .then(
                if (focused) {
                    Modifier.border(
                        TvFocusDefaults.RingWidth,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(TvFocusDefaults.RingCornerRadius),
                    )
                } else Modifier,
            )
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {}
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            info.personInfo.imageMedium,
            contentDescription = null,
            Modifier.size(72.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Text(
            info.personInfo.displayName,
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tvHeroContentColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            info.position.nameCn.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = tvHeroSecondaryContentColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 关联条目卡: 竖版海报 + 关系标注. */
@Composable
internal fun TvRelatedSubjectCard(
    info: RelatedSubjectInfo,
    onClick: (subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(TvPosterCardDefaults.Width)) {
        // 与收藏页统一的海报卡样式 (标题在卡内, 聚焦跑马灯)
        TvPosterCard(
            imageUrl = info.image.orEmpty(),
            title = info.nameCn.ifBlank { info.name.orEmpty() },
            onClick = { onClick(info.subjectId) },
        )
        Text(
            info.relation?.let { renderSubjectRelation(it) }.orEmpty(),
            Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tvHeroSecondaryContentColor(),
            maxLines = 1,
        )
    }
}

/** 评价卡: 昵称 + 评分 + 内容 4 行截断 (富文本取纯文本). */
@Composable
internal fun TvCommentCard(comment: UIComment, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier
            .width(TvSubjectDetailsDefaults.CommentCardWidth)
            .then(
                if (focused) {
                    Modifier.border(
                        TvFocusDefaults.RingWidth,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(TvFocusDefaults.RingCornerRadius),
                    )
                } else Modifier,
            )
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {}
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                comment.author?.nickname ?: "匿名",
                style = MaterialTheme.typography.labelLarge,
                color = tvHeroContentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            comment.rating?.takeIf { it > 0 }?.let { rating ->
                Text(
                    "★ $rating",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            comment.content.elements
                .filterIsInstance<UIRichElement.AnnotatedText>()
                .flatMap { it.slice }
                .filterIsInstance<UIRichElement.Annotated.Text>()
                .joinToString("") { it.content }
                .ifBlank { "…" },
            style = MaterialTheme.typography.bodySmall,
            color = tvHeroSecondaryContentColor(),
            maxLines = 4,
            minLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatCount(value: Int): String = when {
    value >= 1000 -> "%,d".format(value)
    else -> value.toString()
}

/** 关联关系渲染 (手机 renderSubjectRelation 同语义, TV 侧自绘). */
internal fun renderSubjectRelation(relation: SubjectRelation): String =
    when (relation) {
        SubjectRelation.PREQUEL -> "前传"
        SubjectRelation.SEQUEL -> "续集"
        SubjectRelation.DERIVED -> "衍生"
        SubjectRelation.SPECIAL -> "番外篇"
    }
