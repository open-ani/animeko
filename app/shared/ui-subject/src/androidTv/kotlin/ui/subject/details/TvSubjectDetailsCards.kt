/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.subject.details

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.nameCn
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_relation_derived
import me.him188.ani.app.ui.lang.subject_details_relation_prequel
import me.him188.ani.app.ui.lang.subject_details_relation_sequel
import me.him188.ani.app.ui.lang.subject_details_relation_special
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.leanback.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.leanback.ui.foundation.focus.tvLongPressKey
import me.him188.ani.leanback.ui.foundation.widgets.TvLandscapeCard
import me.him188.ani.leanback.ui.foundation.widgets.TvPosterCardDefaults
import me.him188.ani.leanback.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.leanback.ui.foundation.widgets.tvHeroSecondaryContentColor
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import org.jetbrains.compose.resources.stringResource

/*
 * 详情页的卡片与信息块组件 (纯视图, 数据来自复用的 SubjectDetailsState;
 * 页面结构与焦点接线见 TvSubjectDetailsScreen).
 */

/** 选集剧照卡: 16:9, 色圈+留白焦点 (与竖版卡同规格), 卡内左下角序号+标题. */
@Composable
internal fun TvEpisodeCard(
    episode: EpisodeListItem,
    imageUrl: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
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
            )
            .tvLongPressKey(onLongPress = onLongClick, onShortPress = onClick)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(TvFocusDefaults.RingInset)
                .clip(TvPosterCardDefaults.ImageShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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

/** Cast and crew use the same circular image and two-line caption geometry. */
@Composable
internal fun TvCharacterCard(info: RelatedCharacterInfo, modifier: Modifier = Modifier, onClick: () -> Unit) =
    TvDetailsPersonCard(
        info.character.imageMedium,
        info.character.nameCn.ifBlank { info.character.name },
        info.character.actors.firstOrNull()?.displayName.orEmpty(),
        onClick, modifier,
    )

@Composable
internal fun TvStaffCard(info: RelatedPersonInfo, modifier: Modifier = Modifier, onClick: () -> Unit) =
    TvDetailsPersonCard(info.personInfo.imageMedium, info.personInfo.displayName, info.position.nameCn.orEmpty(),
        onClick, modifier, portrait = info.personInfo.type == PersonType.Individual)

@Composable
internal fun TvDetailsPersonCard(
    image: String,
    name: String,
    role: String,
    onClick: () -> Unit,
    modifier: Modifier,
    portrait: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier.width(TvSubjectDetailsDefaults.PersonCardWidth)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(TvSubjectDetailsDefaults.PersonCardWidth)
                .then(if (focused) Modifier.border(TvFocusDefaults.RingWidth, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                .padding(TvFocusDefaults.RingInset)
                .clip(CircleShape).background(Color(0xFF202124)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, null, Modifier.size(28.dp), tint = TvSubjectDetailsDefaults.Content)
            AsyncImage(image, null, Modifier.fillMaxSize(),
                contentScale = if (portrait) ContentScale.Crop else ContentScale.Fit,
                alignment = if (portrait) Alignment.TopCenter else Alignment.Center)
        }
        Text(name, Modifier.fillMaxWidth().padding(top = 10.dp), color = TvSubjectDetailsDefaults.Content,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(role, Modifier.fillMaxWidth().padding(top = 6.dp), color = TvSubjectDetailsDefaults.SecondaryContent,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Related anime retains its title/relation while adopting the launcher's landscape cards. */
@Composable
internal fun TvRelatedSubjectCard(
    info: RelatedSubjectInfo,
    onClick: (subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvLandscapeCard(
        imageUrl = info.image,
        title = info.displayName,
        onClick = { onClick(info.subjectId) },
        modifier = modifier,
        width = TvSubjectDetailsDefaults.RelatedCardWidth,
        overline = info.relation?.let { renderSubjectRelation(it) },
    )
}

/** 评价卡保留富文本样式和遮罩；引用及图片显示明确的预览标记。 */
internal fun formatCount(value: Int): String = when {
    value >= 1000 -> "%,d".format(value)
    else -> value.toString()
}

/** 关联关系渲染 (手机 renderSubjectRelation 同语义, TV 侧自绘). */
@Composable
internal fun renderSubjectRelation(relation: SubjectRelation): String =
    when (relation) {
        SubjectRelation.PREQUEL -> stringResource(Lang.subject_details_relation_prequel)
        SubjectRelation.SEQUEL -> stringResource(Lang.subject_details_relation_sequel)
        SubjectRelation.DERIVED -> stringResource(Lang.subject_details_relation_derived)
        SubjectRelation.SPECIAL -> stringResource(Lang.subject_details_relation_special)
    }
