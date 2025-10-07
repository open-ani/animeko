/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.char
import kotlinx.datetime.number
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
import me.him188.ani.datasources.api.EpisodeSort

/**
 * 新番时间表的一个项目.
 *
 * @param subjectTitle 条目的标题, [ScheduleItemDefaults.SubjectTitle]
 * @param episode 剧集的序号以及名称, [ScheduleItemDefaults.Episode]
 * @param leadingImage 条目的封面, [AsyncImage]
 * @param time 时间, 例如 "12:00", [ScheduleItemDefaults.Time]
 *
 * @see ScheduleItemDefaults
 *
 * [Design](https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=349-10249&t=hBPSAEVlsmuEWPJt-0)
 */
@Composable
fun ScheduleItem(
    onClick: () -> Unit,
    subjectTitle: @Composable () -> Unit,
    episode: @Composable () -> Unit,
    leadingImage: @Composable () -> Unit,
    time: @Composable () -> Unit,
    action: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
//    ListItem(
//        headlineContent = subjectTitle,
//        supportingContent = episode,
//        leadingContent = {
//            Box(Modifier.size(56.dp)) {
//                leadingImage()
//            }
//        },
//        trailingContent = {
//            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
//                time()
//            }
//        },
//        colors = colors,
//        modifier = modifier,
//    )

    Column {
        Row(Modifier.paddingIfNotEmpty(horizontal = 16.dp).paddingIfNotEmpty(top = 8.dp)) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                time()
            }
        }
        ListItem(
            headlineContent = subjectTitle,
            supportingContent = episode,
            leadingContent = {
                Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.small)) {
                    leadingImage()
                }
            },
            trailingContent = action,
            colors = colors,
            modifier = modifier.clickable(role = Role.Button, onClickLabel = "查看详情", onClick = onClick),
        )
    }

}

/**
 * Material3 DividerWithSubhead.
 */
@Composable
fun HorizontalDividerWithSubhead(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    dividerColor: Color = DividerDefaults.color,
    subheadAlignment: Alignment.Horizontal = Alignment.Start,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    subhead: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = verticalArrangement) {
        HorizontalDivider(thickness = thickness, color = dividerColor)

        ProvideTextStyle(textStyle) {
            Box(Modifier.align(subheadAlignment)) {
                subhead()
            }
        }
    }
}

object ScheduleItemDefaults {
    @Composable
    fun SubjectTitle(
        text: String,
        modifier: Modifier = Modifier,
    ) {
        Text(text, overflow = TextOverflow.Ellipsis, maxLines = 1, modifier = modifier)
    }

    @Composable
    fun Episode(
        episodeSort: EpisodeSort,
        episodeEp: EpisodeSort?,
        episodeName: String?,
        modifier: Modifier = Modifier,
    ) {
        val text = remember(episodeSort) {
            renderEpisodeDisplay(episodeSort, episodeEp, episodeName)
        }
        Text(
            text,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = modifier,
        )
    }

    @Composable
    fun Time(
        time: LocalTime,
        modifier: Modifier = Modifier
    ) {
        val text = renderTime(null, time)
        Text(
            text,
            modifier,
            textAlign = TextAlign.End,
            softWrap = false,
            maxLines = 2,
            fontWeight = FontWeight.SemiBold,
        )
    }

    private val timeFormatter = LocalTime.Format {
        hour()
        char(':')
        minute()
    }

    fun renderTime(
        futureStartDate: LocalDate?,
        time: LocalTime,
    ): String {
        val timeString = timeFormatter.format(time)

        return if (futureStartDate != null) {
            "${futureStartDate.month.number}/${futureStartDate.day} 起\n${timeString}"
        } else {
            timeString
        }
    }

    // internal for testing
    internal fun renderEpisodeDisplay(
        episodeSort: EpisodeSort,
        episodeEp: EpisodeSort?,
        episodeName: String?
    ): String {
        val epText = episodeEp?.toString()?.removePrefix("0")
        val sortText = episodeSort.toString().removePrefix("0")

        val sortDisplay = if (episodeEp == null || episodeEp == episodeSort) {
            if (episodeSort is EpisodeSort.Normal) {
                "第 $sortText 话"
            } else {
                sortText
            }
        } else {
            check(epText != null)
            // episodeEp != episodeSort
            if (episodeSort is EpisodeSort.Normal && episodeEp is EpisodeSort.Normal) {
                "第 $epText ($sortText) 话"
            } else {
                "$epText ($sortText)"
            }
        }

        return if (episodeName == null) {
            sortDisplay
        } else {
            "$sortDisplay  $episodeName"
        }
    }
}
