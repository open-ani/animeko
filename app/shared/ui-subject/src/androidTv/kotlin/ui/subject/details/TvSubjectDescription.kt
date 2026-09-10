/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_no_summary
import me.him188.ani.app.ui.lang.subject_details_summary
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusExit
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.subject.TvSubjectDetailsContentState
import me.him188.ani.leanback.ui.subject.components.TvDetailsReadingArea
import me.him188.ani.leanback.ui.subject.components.TvDetailsFullscreenOverlay
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSubjectDescription(
    details: TvSubjectDetailsContentState,
    backdrop: String,
    onClose: () -> Unit,
    onTag: (String) -> Unit,
) {
    TvDetailsFullscreenOverlay(
        backdrop, onClose,
        initialKey = details.info.tags.firstOrNull()?.let { "tag:${it.name}" } ?: "description-body",
    ) { focus ->
        Column(Modifier.fillMaxSize().padding(
            start = TvSubjectDetailsDefaults.HorizontalPadding, end = TvSubjectDetailsDefaults.HorizontalPadding,
            top = 50.dp, bottom = 22.dp,
        )) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Subject, null, Modifier.size(28.dp), tint = TvSubjectDetailsDefaults.Content)
                Text(stringResource(Lang.subject_details_summary), color = TvSubjectDetailsDefaults.Content,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                    modifier = Modifier.testTag("tv-description-title"))
            }
            Spacer(Modifier.height(44.dp))
            if (details.info.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(end = 24.dp)
                        .tvFocusExit(focus, FocusDirection.Down to TvDetailsKey("description-body"))
                        .testTag("tv-description-tags"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    details.info.tags.distinctBy { it.name }.forEach { tag ->
                        key(tag.name) {
                            TvDetailsAction(
                                label = tag.name,
                                onClick = { onTag(tag.name) },
                                modifier = Modifier.tvFocusAnchor(focus, TvDetailsKey("tag:${tag.name}"))
                                    .testTag("tv-description-tag:${tag.name}"),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            TvDetailsReadingArea(
                Modifier.weight(1f).fillMaxWidth()
                    .tvFocusAnchor(focus, TvDetailsKey("description-body"))
                    .tvFocusLink(focus, up = details.info.tags.firstOrNull()?.let { TvDetailsKey("tag:${it.name}") })
                    .testTag("tv-details-panel-summary-text"),
            ) {
                Column(Modifier.fillMaxWidth(.78f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Text(details.info.summary.ifBlank { stringResource(Lang.subject_details_no_summary) },
                        color = TvSubjectDetailsDefaults.SecondaryContent,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 42.sp),
                        modifier = Modifier.testTag("tv-description-text"))
                    TvSubjectInformation(
                        details.info, totalEpisodes = if (details.episodesLoading) null else details.mainEpisodeIds.size,
                        modifier = Modifier.testTag("tv-description-info"),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
