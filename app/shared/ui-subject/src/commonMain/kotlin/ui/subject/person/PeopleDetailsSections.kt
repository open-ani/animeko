/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.data.models.person.InfoboxRowInfo
import me.him188.ani.app.data.models.person.PersonCommentInfo
import me.him188.ani.app.data.models.person.PersonSubjectSummary
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.person_details_career_actor
import me.him188.ani.app.ui.lang.person_details_career_artist
import me.him188.ani.app.ui.lang.person_details_career_illustrator
import me.him188.ani.app.ui.lang.person_details_career_mangaka
import me.him188.ani.app.ui.lang.person_details_career_producer
import me.him188.ani.app.ui.lang.person_details_career_seiyu
import me.him188.ani.app.ui.lang.person_details_career_writer
import me.him188.ani.app.ui.lang.person_details_comments
import me.him188.ani.app.ui.lang.person_details_comments_count
import me.him188.ani.app.ui.lang.person_details_meta
import me.him188.ani.app.ui.lang.person_details_no_comments
import me.him188.ani.app.ui.lang.person_details_person
import me.him188.ani.app.ui.lang.person_details_role_character
import me.him188.ani.app.ui.lang.person_details_role_mecha
import me.him188.ani.app.ui.lang.person_details_role_organization
import me.him188.ani.app.ui.lang.person_details_role_ship
import me.him188.ani.app.ui.lang.subject_details_view_all
import me.him188.ani.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.details.sections.groupThousands
import org.jetbrains.compose.resources.stringResource

/**
 * 人物/角色详情内各处点击的导航行为.
 *
 * @param onBeforeNavigate 任何导航前调用 (侧边预览 sheet 用它先关闭自己).
 */
@Immutable
class PeopleDetailsNavigation(
    val onClickPerson: (personId: Int) -> Unit,
    val onClickCharacter: (characterId: Int) -> Unit,
    val onClickSubject: (PersonSubjectSummary) -> Unit,
)

@Composable
fun rememberPeopleDetailsNavigation(onBeforeNavigate: () -> Unit = {}): PeopleDetailsNavigation {
    val navigator = LocalNavigator.current
    return remember(navigator, onBeforeNavigate) {
        PeopleDetailsNavigation(
            onClickPerson = {
                onBeforeNavigate()
                navigator.navigatePersonDetails(it)
            },
            onClickCharacter = {
                onBeforeNavigate()
                navigator.navigateCharacterDetails(it)
            },
            onClickSubject = { subject ->
                onBeforeNavigate()
                navigator.navigateSubjectDetails(
                    subject.subjectId,
                    placeholder = SubjectDetailPlaceholder(
                        id = subject.subjectId,
                        name = subject.name,
                        nameCN = subject.nameCn,
                        coverUrl = subject.imageLarge,
                    ),
                )
            },
        )
    }
}

/** 人物职业/角色类型文案, 用于 `声优 · 518 人收藏` meta 行. */
@Composable
internal fun personKindLabel(career: List<String>): String {
    for (c in career) {
        val res = when (c) {
            "seiyu" -> Lang.person_details_career_seiyu
            "producer" -> Lang.person_details_career_producer
            "mangaka" -> Lang.person_details_career_mangaka
            "artist" -> Lang.person_details_career_artist
            "writer" -> Lang.person_details_career_writer
            "illustrator" -> Lang.person_details_career_illustrator
            "actor" -> Lang.person_details_career_actor
            else -> null
        }
        if (res != null) return stringResource(res)
    }
    return stringResource(Lang.person_details_person)
}

@Composable
internal fun characterRoleLabel(role: Int): String = stringResource(
    when (role) {
        2 -> Lang.person_details_role_mecha
        3 -> Lang.person_details_role_ship
        4 -> Lang.person_details_role_organization
        else -> Lang.person_details_role_character
    },
)

@Composable
internal fun peopleMetaLine(kindLabel: String, collects: Int): String =
    stringResource(Lang.person_details_meta, kindLabel, remember(collects) { groupThousands(collects) })

/**
 * 头部行: 竖版立绘/照片 (110x147, 圆角) + 名字/原名/meta 行. 用于单栏与侧边预览.
 */
@Composable
internal fun PeopleHeaderRow(
    imageUrl: String?,
    displayName: String,
    originalName: String?,
    metaLine: String,
    modifier: Modifier = Modifier,
    isPlaceholder: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .size(110.dp, 147.dp)
                .clip(MaterialTheme.shapes.medium)
                .placeholder(isPlaceholder),
        ) {
            AvatarImage(imageUrl, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Column(
            Modifier.weight(1f).height(147.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                displayName,
                Modifier.placeholder(isPlaceholder),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!originalName.isNullOrBlank() && originalName != displayName) {
                Text(
                    originalName,
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                metaLine,
                Modifier.padding(top = 8.dp).placeholder(isPlaceholder),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 多栏中栏的标题块: 大标题 + 原名 + meta 行 (对应 Figma `Title` 96 高). */
@Composable
internal fun PeopleTitleBlock(
    displayName: String,
    originalName: String?,
    metaLine: String,
    modifier: Modifier = Modifier,
    isPlaceholder: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            displayName,
            Modifier.placeholder(isPlaceholder),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!originalName.isNullOrBlank() && originalName != displayName) {
            Text(
                originalName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            metaLine,
            Modifier.placeholder(isPlaceholder),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 基本信息键值表, 结构同条目详情的作品信息表. */
@Composable
internal fun PeopleInfoTable(
    rows: List<InfoboxRowInfo>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 78.dp,
    rowSpacing: Dp = 12.dp,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    row.key,
                    Modifier.width(labelWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.value,
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** 横向条目卡: 2:3 封面 + 标题 + 说明 (职位/主配角). */
@Composable
internal fun PeopleSubjectCard(
    subject: PersonSubjectSummary,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 96.dp,
) {
    Column(
        modifier
            .width(width)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO).clip(MaterialTheme.shapes.small)) {
            AvatarImage(subject.imageLarge, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Text(
            subject.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 横向人物卡: 2:3 立绘 (Fit, 不裁切) + 名字 + 说明. 与条目详情角色条一致. */
@Composable
internal fun PeoplePortraitCard(
    imageUrl: String?,
    name: String,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 76.dp,
    circleCrop: Boolean = false,
) {
    Column(
        modifier
            .width(width)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (circleCrop) {
            Box(Modifier.size(width).clip(CircleShape)) {
                AvatarImage(imageUrl, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
        } else {
            Box(Modifier.size(width, width * 3 / 2).clip(MaterialTheme.shapes.small)) {
                AvatarImage(imageUrl, Modifier.matchParentSize(), contentScale = ContentScale.Fit)
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 通用横滑条区块: 标题 (+可选 查看全部) + LazyRow 内容. */
@Composable
internal fun <T : Any> PeopleStripSection(
    title: String,
    items: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
    itemSpacing: Dp = 12.dp,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.itemCount == 0) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onViewAll != null) {
            SectionHeader(title, actionLabel = stringResource(Lang.subject_details_view_all), onAction = onViewAll)
        } else {
            SectionHeader(title)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(itemSpacing)) {
            items(items.itemCount) { i ->
                val item = items[i] ?: return@items
                itemContent(item)
            }
        }
    }
}

/**
 * 评论区块: 标题 (+`N 条` 入口) + 前几条预览. 无评分.
 */
@Composable
internal fun PersonCommentsSection(
    comments: LazyPagingItems<PersonCommentInfo>,
    commentCount: Int?,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
    maxPreviewItems: Int = 3,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(Lang.person_details_comments),
            actionLabel = commentCount?.takeIf { it > 0 }
                ?.let { stringResource(Lang.person_details_comments_count, remember(it) { groupThousands(it) }) }
                ?: stringResource(Lang.subject_details_view_all),
            onAction = onShowAll,
        )
        val previewCount = minOf(comments.itemCount, maxPreviewItems)
        if (previewCount == 0) {
            Text(
                stringResource(Lang.person_details_no_comments),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (i in 0 until previewCount) {
            val comment = comments[i] ?: continue
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PersonCommentItem(
                comment,
                Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onShowAll),
                maxTextLines = 3,
            )
        }
    }
}

/** 单条评论: `头像 名字 时间` + 正文 (可截断). */
@Composable
internal fun PersonCommentItem(
    comment: PersonCommentInfo,
    modifier: Modifier = Modifier,
    maxTextLines: Int = Int.MAX_VALUE,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AvatarImage(
                comment.authorAvatarUrl,
                Modifier.size(24.dp).clip(CircleShape),
            )
            Text(
                comment.authorNickname ?: "",
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatDateTime(comment.createdAt.toEpochMilliseconds(), showTime = false),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            comment.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxTextLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 全量评论列表 sheet, 结构同条目详情的评论 sheet (无评分). */
@Composable
internal fun PersonCommentsSheet(
    comments: LazyPagingItems<PersonCommentInfo>,
    commentCount: Int?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                commentCount?.takeIf { it > 0 }?.let {
                    stringResource(Lang.person_details_comments) + " · " + remember(it) { groupThousands(it) }
                } ?: stringResource(Lang.person_details_comments),
                style = MaterialTheme.typography.titleLarge,
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                items(comments.itemCount) { i ->
                    val comment = comments[i] ?: return@items
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PersonCommentItem(comment)
                    }
                }
            }
        }
    }
}
