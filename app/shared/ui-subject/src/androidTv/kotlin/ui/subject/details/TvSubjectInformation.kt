/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_air_date
import me.him188.ani.app.ui.lang.subject_details_air_date_format
import me.him188.ani.app.ui.lang.subject_details_aliases
import me.him188.ani.app.ui.lang.subject_details_info
import me.him188.ani.app.ui.lang.subject_details_total_episodes
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import org.jetbrains.compose.resources.stringResource

/** One reading target; its heading follows section focus without adding a card decoration. */
@Composable
internal fun TvSubjectInformationSection(
    info: SubjectInfo,
    totalEpisodes: Int?,
    focusProgress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.focusable()
            .padding(horizontal = TvSubjectDetailsDefaults.HorizontalPadding),
        verticalArrangement = Arrangement.spacedBy((16 + 10 * focusProgress).dp),
    ) {
        Text(
            stringResource(Lang.subject_details_info),
            modifier = Modifier.testTag("tv-details-info-heading"),
            color = TvSubjectDetailsDefaults.Content,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = (16 + 10 * focusProgress).sp, lineHeight = (20 + 12 * focusProgress).sp,
            ),
        )
        TvSubjectInformation(info, totalEpisodes)
    }
}

/** Shared by the description reader and the details footer; unknown fields stay omitted. */
@Composable
internal fun TvSubjectInformation(info: SubjectInfo, totalEpisodes: Int?, modifier: Modifier = Modifier) {
    val rows = buildList {
        if (info.airDate.isValid) add(
            stringResource(Lang.subject_details_air_date) to
                    stringResource(
                        Lang.subject_details_air_date_format, info.airDate.year.toString(),
                        info.airDate.month.toString(), info.airDate.day.toString(),
                    ),
        )
        if (totalEpisodes != null && totalEpisodes > 0)
            add(stringResource(Lang.subject_details_total_episodes) to totalEpisodes.toString())
        if (info.aliases.isNotEmpty()) add(stringResource(Lang.subject_details_aliases) to info.aliases.joinToString(" / "))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TvSubjectInformationDefaults.RowSpacing)) {
        rows.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(TvSubjectInformationDefaults.ColumnSpacing)) {
                Text(
                    label, Modifier.width(TvSubjectInformationDefaults.LabelWidth),
                    color = TvSubjectDetailsDefaults.SecondaryContent,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                )
                Text(
                    value, Modifier.weight(1f), color = TvSubjectDetailsDefaults.Content,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
                )
            }
        }
    }
}

private object TvSubjectInformationDefaults {
    val RowSpacing = 18.dp
    val ColumnSpacing = 24.dp
    val LabelWidth = 100.dp
}
