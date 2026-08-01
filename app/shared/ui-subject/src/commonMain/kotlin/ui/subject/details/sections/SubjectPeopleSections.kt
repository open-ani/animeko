/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.nameCn
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_characters
import me.him188.ani.app.ui.lang.subject_details_characters_with_count
import me.him188.ani.app.ui.lang.subject_details_rating_summary
import me.him188.ani.app.ui.lang.subject_details_staff
import me.him188.ani.app.ui.lang.subject_details_staff_with_count
import me.him188.ani.app.ui.lang.subject_details_stat_collected
import me.him188.ani.app.ui.lang.subject_details_stat_watching
import me.him188.ani.app.ui.lang.subject_details_stat_wish
import me.him188.ani.app.ui.lang.subject_details_view_all
import me.him188.ani.app.ui.rating.FiveRatingStars
import me.him188.ani.app.ui.rating.renderScore
import me.him188.ani.app.ui.subject.details.components.PersonCard
import me.him188.ani.app.ui.subject.person.PeoplePreviewTarget
import me.him188.ani.app.ui.subject.person.rememberPeopleClickHandler
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * 收藏统计三格 (对齐 Figma 定稿: `74,553 收藏 / 5,120 在看 / 680 想看`).
 * 收藏 = 五档之和; 在看 = doing; 想看 = wish.
 */
@Composable
fun SubjectCollectionStatsRow(
    stats: SubjectCollectionStats,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCell(stats.collect, stringResource(Lang.subject_details_stat_collected), Modifier.weight(1f))
        StatCell(stats.doing, stringResource(Lang.subject_details_stat_watching), Modifier.weight(1f))
        StatCell(stats.wish, stringResource(Lang.subject_details_stat_wish), Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            remember(count) { groupThousands(count) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** `39983 -> "39,983"`. 详情页多处数字 (收藏统计/评分人数/评论数) 按定稿千分组显示. */
fun groupThousands(n: Int): String {
    val s = n.toString()
    val neg = s.startsWith("-")
    val digits = if (neg) s.substring(1) else s
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (neg) "-$grouped" else grouped
}

/**
 * 评分摘要块 (对齐定稿: 大分数 `8.4` + 右侧上下两行 [星星 / `#72 · 39,983 人评分`]).
 *
 * 用于双栏/三栏中栏评分行与三栏右栏"评分"卡.
 *
 * @param onClick 非 null 时整块可点击 (打开评分编辑, 见 `EditableRatingState.requestEdit`).
 */
@Composable
fun SubjectRatingSummary(
    ratingInfo: RatingInfo,
    modifier: Modifier = Modifier,
    scoreStyle: TextStyle = MaterialTheme.typography.displaySmall,
    starSize: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.small)
            .ifThen(onClick != null) { clickable(onClick = checkNotNull(onClick)) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            remember(ratingInfo.score) { renderScore(ratingInfo.score) },
            style = scoreStyle,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FiveRatingStars(
                remember(ratingInfo.score) { ratingInfo.scoreFloat.roundToInt() },
                starSize = starSize,
            )
            Text(
                stringResource(
                    Lang.subject_details_rating_summary,
                    ratingInfo.rank.toString(),
                    remember(ratingInfo.total) { groupThousands(ratingInfo.total) },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 角色区块: 标题行 (+"查看全部" -> 全量列表 sheet) + 横向头像条 (固定圆形头像 + 角色名 + CV).
 *
 * 头像为固定大小圆形, 图片 crop 顶部对齐 (角色图多为全身立绘, 顶部对齐保证露脸).
 * 尺寸对齐 Figma `CharacterCard`: 桌面 Large (头像 76, 间距 12), 手机 Small (头像 56, 间距 0).
 *
 * @param contentPadding 头像条与标题的水平内边距; 手机端传水平 16dp 可让头像条边到边滚动.
 */
@Composable
fun CharactersSection(
    exposedCharacters: LazyPagingItems<RelatedCharacterInfo>,
    allCharacters: LazyPagingItems<RelatedCharacterInfo>,
    totalCharactersCount: Int?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemWidth: Dp = 76.dp,
    avatarSize: Dp = 76.dp,
    itemSpacing: Dp = 12.dp,
    /** TV: 卡片下键的显式落点 (下一区块入口; 跨区块空间焦点搜索不可靠). null 交给空间搜索. */
    downFocus: FocusRequester? = null,
) {
    if (exposedCharacters.itemCount == 0) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    // TV: "查看全部" 在标题行右上角, 与头像条不同行, 空间焦点搜索向右够不到;
    // 把最右一个头像的右键显式指到该按钮 (同选集轮播的网格入口处理)
    val viewAllFocus = remember { FocusRequester() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(Lang.subject_details_characters),
            actionLabel = stringResource(Lang.subject_details_view_all),
            onAction = { showAll = true },
            modifier = Modifier.padding(contentPadding),
            actionModifier = Modifier.focusRequester(viewAllFocus),
        )
        val onClickCharacter = rememberPeopleClickHandler()
        if (LocalAniUiBehavior.current.focusDrivenNavigation) {
            // 焦点驱动形态: 卡片形态 (与"查看全部"弹窗同款 PersonCard + 聚焦高亮容器), 横向滚动行;
            // 卡宽按视口均分, 一行正好放下 CHARACTER_CARDS_PER_ROW 张完整卡
            BoxWithConstraints {
                val cardWidth =
                    (maxWidth - itemSpacing * (CHARACTER_CARDS_PER_ROW - 1)) / CHARACTER_CARDS_PER_ROW
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    contentPadding = contentPadding,
                ) {
                    items(exposedCharacters.itemCount) { i ->
                        val item = exposedCharacters[i] ?: return@items
                        FocusHighlightCard(
                            Modifier
                                .width(cardWidth)
                                // 最右一张卡的右键显式指到标题行"查看全部" (空间搜索够不到)
                                .ifThen(i == exposedCharacters.itemCount - 1) {
                                    focusProperties { right = viewAllFocus }
                                }
                                .ifThen(downFocus != null) {
                                    focusProperties { down = downFocus!! }
                                },
                        ) {
                            PersonCard(
                                item,
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        onClickCharacter(PeoplePreviewTarget.Character(item.character.id))
                                    },
                            )
                        }
                    }
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                contentPadding = contentPadding,
            ) {
                items(exposedCharacters.itemCount) { i ->
                    val item = exposedCharacters[i] ?: return@items
                    CharacterAvatarCell(
                        item, itemWidth, avatarSize,
                        onClick = { onClickCharacter(PeoplePreviewTarget.Character(item.character.id)) },
                        modifier = if (i == exposedCharacters.itemCount - 1) {
                            Modifier.focusProperties { right = viewAllFocus }
                        } else Modifier,
                    )
                }
            }
        }
    }
    if (showAll) {
        val onClickCharacter = rememberPeopleClickHandler()
        ViewAllSheet(
            title = totalCharactersCount?.let { stringResource(Lang.subject_details_characters_with_count, it) }
                ?: stringResource(Lang.subject_details_characters),
            items = allCharacters,
            onDismissRequest = { showAll = false },
            gridColumns = VIEW_ALL_GRID_COLUMNS,
        ) {
            PersonCard(
                it,
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        showAll = false
                        onClickCharacter(PeoplePreviewTarget.Character(it.character.id))
                    },
            )
        }
    }
}

@Composable
private fun CharacterAvatarCell(
    info: RelatedCharacterInfo,
    itemWidth: Dp,
    avatarSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cv = remember(info) { info.character.actors.firstOrNull()?.displayName }
    Column(
        modifier
            .width(itemWidth)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 固定圆形, crop, 顶部对齐 (立绘顶部为脸部)
        Box(Modifier.size(avatarSize).clip(CircleShape)) {
            AvatarImage(
                info.character.imageMedium,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        Text(
            info.character.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            cv ?: info.role.nameCn,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 制作人员区块: 标题行 (+"查看全部" -> 全量列表 sheet) + 内容.
 *
 * 内容形态 (对齐定稿):
 * - [gridColumns] 非 null: `职位 上 / 名字 下` 的网格 (双栏中栏单行 6 列, 手机 3 列两行);
 * - [gridColumns] 为 null: `职位 -> 名字` 竖排键值行 (三栏右栏卡, 显示 [maxItems] 个职位).
 */
@Composable
fun StaffSection(
    exposedStaff: LazyPagingItems<RelatedPersonInfo>,
    allStaff: LazyPagingItems<RelatedPersonInfo>,
    totalStaffCount: Int?,
    modifier: Modifier = Modifier,
    gridColumns: Int? = null,
    maxItems: Int = if (gridColumns != null) 6 else 10,
    /** TV: 末行卡片下键的显式落点 (下一区块入口; 跨区块空间焦点搜索不可靠). null 交给空间搜索. */
    downFocus: FocusRequester? = null,
) {
    if (exposedStaff.itemCount == 0) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    val onClickPerson = rememberPeopleClickHandler()
    // 焦点驱动形态: 最右一张卡的右键显式指到标题行"查看全部" (空间搜索够不到)
    val viewAllFocus = remember { FocusRequester() }
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(Lang.subject_details_staff),
            actionLabel = stringResource(Lang.subject_details_view_all),
            onAction = { showAll = true },
            actionModifier = if (focusDriven) Modifier.focusRequester(viewAllFocus) else Modifier,
        )
        when {
            // 焦点驱动形态: 卡片形态 (与"查看全部"弹窗同款 PersonCard + 聚焦高亮容器),
            // 固定 2 行 x 3 列网格 (最多 6 人, 更多的看"查看全部"弹窗)
            focusDriven -> FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = STAFF_GRID_COLUMNS,
            ) {
                val count = minOf(exposedStaff.itemCount, STAFF_GRID_COLUMNS * STAFF_GRID_ROWS)
                for (i in 0 until count) {
                    val item = exposedStaff[i] ?: continue
                    FocusHighlightCard(
                        Modifier
                            .weight(1f)
                            // 每行最右一张卡的右键显式指到标题行"查看全部" (空间搜索够不到)
                            .ifThen(i % STAFF_GRID_COLUMNS == STAFF_GRID_COLUMNS - 1 || i == count - 1) {
                                focusProperties { right = viewAllFocus }
                            }
                            // 正下方没有其他卡的 (末行 + 末行缺口上方) 下键送下一区块
                            .ifThen(downFocus != null && i + STAFF_GRID_COLUMNS >= count) {
                                focusProperties { down = downFocus!! }
                            },
                    ) {
                        PersonCard(
                            item,
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    onClickPerson(PeoplePreviewTarget.Person(item.personInfo.id))
                                },
                        )
                    }
                }
                // 补齐末行空位, 保持列宽一致
                val remainder = count % STAFF_GRID_COLUMNS
                if (remainder != 0) {
                    repeat(STAFF_GRID_COLUMNS - remainder) { Box(Modifier.weight(1f)) }
                }
            }

            gridColumns != null -> StaffGrid(
                exposedStaff, columns = gridColumns, maxItems = maxItems,
                onClick = { onClickPerson(PeoplePreviewTarget.Person(it.personInfo.id)) },
            )

            else -> StaffKeyValueList(
                exposedStaff, maxItems = maxItems,
                onClick = { onClickPerson(PeoplePreviewTarget.Person(it.personInfo.id)) },
            )
        }
    }
    if (showAll) {
        ViewAllSheet(
            title = totalStaffCount?.let { stringResource(Lang.subject_details_staff_with_count, it) }
                ?: stringResource(Lang.subject_details_staff),
            items = allStaff,
            onDismissRequest = { showAll = false },
            gridColumns = VIEW_ALL_GRID_COLUMNS,
        ) {
            PersonCard(
                it,
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        showAll = false
                        onClickPerson(PeoplePreviewTarget.Person(it.personInfo.id))
                    },
            )
        }
    }
}

/** `职位 上 / 名字 下` 单元格网格, 每行 [columns] 个, 最多 [maxItems] 个. */
@Composable
private fun StaffGrid(
    staff: LazyPagingItems<RelatedPersonInfo>,
    columns: Int,
    maxItems: Int,
    onClick: (RelatedPersonInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = columns,
    ) {
        val count = minOf(staff.itemCount, maxItems)
        for (i in 0 until count) {
            val person = staff[i] ?: continue
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onClick(person) },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    person.position.nameCn ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    person.personInfo.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 补齐末行空位, 保持列宽一致
        val remainder = count % columns
        if (remainder != 0) {
            repeat(columns - remainder) { Box(Modifier.weight(1f)) }
        }
    }
}

/** `职位 -> 名字` 竖排键值行, 最多 [maxItems] 个. */
@Composable
private fun StaffKeyValueList(
    staff: LazyPagingItems<RelatedPersonInfo>,
    maxItems: Int,
    onClick: (RelatedPersonInfo) -> Unit,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 78.dp,
    rowSpacing: Dp = 12.dp,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        for (i in 0 until minOf(staff.itemCount, maxItems)) {
            val person = staff[i] ?: continue
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onClick(person) },
            ) {
                Text(
                    person.position.nameCn ?: "",
                    Modifier.width(labelWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    person.personInfo.displayName,
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** TV 角色卡片行: 一行完整放下的卡片数 (卡宽 = 视口均分). */
private const val CHARACTER_CARDS_PER_ROW = 4

/** TV 角色/制作人员"查看全部"弹窗的固定列数. */
private const val VIEW_ALL_GRID_COLUMNS = 3

/** TV 制作人员网格列数. */
private const val STAFF_GRID_COLUMNS = 3

/** TV 制作人员网格行数 (显示 列x行 个, 更多的看"查看全部"弹窗). */
private const val STAFF_GRID_ROWS = 2
