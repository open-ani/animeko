/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_relation_derived
import me.him188.ani.app.ui.lang.subject_details_relation_prequel
import me.him188.ani.app.ui.lang.subject_details_relation_sequel
import me.him188.ani.app.ui.lang.subject_details_relation_special
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.app.ui.subject.details.TestRelatedSubjects
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

/**
 * 关联作品卡 (对齐 Figma `RelatedCard` 1559:8082): 海报封面 (圆角 12) + 下方标题 + 关系文案.
 *
 * - 桌面 (双栏/三栏): [RelatedSubjectsGrid], 定宽 `Large150` 卡换行排列;
 * - 手机: [RelatedSubjectsLazyRow], `Small96` 卡横滑.
 */
@Composable
fun RelatedSubjectsGrid(
    items: LazyPagingItems<RelatedSubjectInfo>,
    onClick: (RelatedSubjectInfo) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 150.dp,
    spacing: Dp = 20.dp,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(items.itemCount) { i ->
            val item = items[i] ?: return@repeat
            RelatedSubjectCard(item, onClick = { onClick(item) }, Modifier.width(itemWidth))
        }
    }
}

/** 手机横滑变体, 见 [RelatedSubjectsGrid]. */
@Composable
fun RelatedSubjectsLazyRow(
    items: LazyPagingItems<RelatedSubjectInfo>,
    onClick: (RelatedSubjectInfo) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 96.dp,
    spacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = contentPadding,
    ) {
        items(items.itemCount) { i ->
            items[i]?.let { item ->
                RelatedSubjectCard(item, onClick = { onClick(item) }, Modifier.width(itemWidth))
            }
        }
    }
}

/** 单张关联作品卡; 供横滑/网格与"查看全部" sheet 复用. */
@Composable
fun RelatedSubjectCard(
    info: RelatedSubjectInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            Modifier.fillMaxWidth().aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            AsyncImage(
                info.image,
                contentDescription = null,
                Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
                placeholder = if (currentAniBuildConfig.isDebug) remember { ColorPainter(Color.Gray) } else null,
            )
        }
        Column {
            Text(
                info.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            info.relation?.let { relation ->
                Text(
                    renderSubjectRelation(relation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 点击关联作品 -> 跳转其详情页 (带 placeholder 以便秒开). 双栏/三栏与手机共用. */
@Composable
fun rememberNavigateToRelatedSubject(): (RelatedSubjectInfo) -> Unit {
    val navigator = LocalNavigator.current
    return remember(navigator) {
        { info ->
            navigator.navigateSubjectDetails(
                info.subjectId,
                placeholder = SubjectDetailPlaceholder(
                    id = info.subjectId,
                    name = info.name ?: "",
                    nameCN = info.nameCn,
                    coverUrl = info.image ?: "",
                ),
            )
        }
    }
}

@Composable
private fun renderSubjectRelation(relation: SubjectRelation): String = when (relation) {
    SubjectRelation.PREQUEL -> stringResource(Lang.subject_details_relation_prequel)
    SubjectRelation.SEQUEL -> stringResource(Lang.subject_details_relation_sequel)
    SubjectRelation.DERIVED -> stringResource(Lang.subject_details_relation_derived)
    SubjectRelation.SPECIAL -> stringResource(Lang.subject_details_relation_special)
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewRelatedSubjectsGrid() = ProvideCompositionLocalsForPreview {
    Surface {
        RelatedSubjectsGrid(
            createTestPager(TestRelatedSubjects).collectAsLazyPagingItemsWithLifecycle(),
            onClick = {},
        )
    }
}
