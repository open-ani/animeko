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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import me.him188.ani.app.data.models.person.InfoboxRowInfo
import me.him188.ani.app.data.models.person.PersonSubjectSummary
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.layout.rememberConnectedScrollState
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_richtext_external_app_link_warning_prefix
import me.him188.ani.app.ui.lang.foundation_richtext_open_failed_prefix
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
import me.him188.ani.app.ui.subject.details.components.SubjectCommentColumn
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.details.sections.groupThousands
import me.him188.ani.app.ui.subject.details.sections.toPlainText
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
 * 头部行: 竖版立绘/照片 (固定 110x147, crop 顶部对齐) + 名字/原名/meta 行. 用于单栏与侧边预览.
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
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(110.dp, 147.dp)
                .clip(MaterialTheme.shapes.medium)
                .placeholder(isPlaceholder),
        ) {
            AvatarImage(
                imageUrl,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        Column(Modifier.weight(1f)) {
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

/** Figma 组件 `PersonCastCard` 的头像宽度; 头像高按 3:4 (96x128). */
internal val PersonCastCardWidth = 96.dp

/**
 * 人物卡, 对应 Figma 组件 `PersonCastCard`: 头像固定 96x128 (crop, 顶部对齐) + 角色名 + 作品名 (左对齐).
 * [circleCrop] 用于真人照片 (声优条): 圆形头像 + 居中文本.
 */
@Composable
internal fun PeoplePortraitCard(
    imageUrl: String?,
    name: String,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = PersonCastCardWidth,
    circleCrop: Boolean = false,
) {
    Column(
        modifier
            .width(width)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        horizontalAlignment = if (circleCrop) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (circleCrop) {
            Box(Modifier.size(width).clip(CircleShape)) {
                AvatarImage(
                    imageUrl,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            }
        } else {
            Box(Modifier.size(width, width * 4 / 3).clip(MaterialTheme.shapes.small)) {
                AvatarImage(
                    imageUrl,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
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
 * 评论区块: 标题 (+`N 条` 入口) + 前几条预览 (纯文本, 同条目评价预览). 无评分.
 */
@Composable
internal fun PersonCommentsSection(
    state: CommentState,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
    maxPreviewItems: Int = 3,
) {
    val comments = state.list.collectAsLazyPagingItemsWithLifecycle()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(Lang.person_details_comments),
            actionLabel = state.count?.takeIf { it > 0 }
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
            PersonCommentPreviewItem(
                comment,
                Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onShowAll),
            )
        }
    }
}

/** 单条评论预览: `头像 名字 时间` + 纯文本摘要 (截断). */
@Composable
private fun PersonCommentPreviewItem(
    comment: UIComment,
    modifier: Modifier = Modifier,
    maxTextLines: Int = 3,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AvatarImage(
                comment.author?.avatarUrl,
                Modifier.size(24.dp).clip(CircleShape),
            )
            Text(
                comment.author?.nickname ?: "",
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatDateTime(comment.createdAt, showTime = false),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            remember(comment) { comment.content.toPlainText() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxTextLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 全量评论 sheet: 复用剧集/条目评论列 ([SubjectCommentColumn]), 支持 BBCode 表情/图片/链接.
 * 与条目评论 sheet 的差别: 人物评论无评分, 也没有 "写评价" 入口.
 */
@Composable
internal fun PersonCommentsSheet(
    state: CommentState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val browserNavigator = LocalUriHandler.current
    val toaster = LocalToaster.current
    val externalAppLinkWarningPrefix = stringResource(Lang.foundation_richtext_external_app_link_warning_prefix)
    val openLinkFailedPrefix = stringResource(Lang.foundation_richtext_open_failed_prefix)
    val imageViewer = rememberImageViewerHandler()

    ModalBottomSheet(
        onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                state.count?.takeIf { it > 0 }?.let {
                    stringResource(Lang.person_details_comments) + " · " + remember(it) { groupThousands(it) }
                } ?: stringResource(Lang.person_details_comments),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            SubjectDetailsDefaults.SubjectCommentColumn(
                state = state,
                onClickUrl = { url ->
                    RichTextDefaults.checkSanityAndOpen(
                        url,
                        browserNavigator,
                        toaster,
                        externalAppLinkWarningPrefix,
                        openLinkFailedPrefix,
                    )
                },
                onClickImage = { imageViewer.viewImage(it) },
                connectedScrollState = rememberConnectedScrollState(),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            )
        }
    }
    ImageViewer(imageViewer) { imageViewer.clear() }
}
