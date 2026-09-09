/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.people_no_information
import me.him188.ani.app.ui.lang.person_details_basic_info
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.subject.components.TvDetailsFullscreenOverlay
import me.him188.ani.leanback.ui.subject.components.TvDetailsReadingArea
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.person.TvPeopleKind
import me.him188.ani.leanback.ui.subject.person.TvPeopleProfile
import me.him188.ani.leanback.ui.subject.person.peopleIntroductionTitle
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvPeopleIntroduction(kind: TvPeopleKind, profile: TvPeopleProfile?, loading: Boolean, error: LoadError?, onClose: () -> Unit) {
    TvDetailsFullscreenOverlay(profile?.image.orEmpty(), onClose, initialKey = "people-reader") { focus ->
        Column(Modifier.fillMaxSize().padding(horizontal = TvSubjectDetailsDefaults.HorizontalPadding, vertical = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Subject, null, Modifier.size(28.dp), tint = TvSubjectDetailsDefaults.Content)
                Text(peopleIntroductionTitle(kind), color = TvSubjectDetailsDefaults.Content,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp))
            }
            profile?.name?.let { Text(it, Modifier.padding(top = 10.dp), color = TvSubjectDetailsDefaults.SecondaryContent,
                style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(32.dp))
            TvDetailsReadingArea(Modifier.weight(1f).fillMaxWidth()
                .tvFocusAnchor(focus, TvDetailsKey("people-reader")).testTag("tv-people-reader")) {
                if (!profile?.summary.isNullOrBlank()) {
                    Text(checkNotNull(profile).summary, color = TvSubjectDetailsDefaults.SecondaryContent,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 38.sp),
                        modifier = Modifier.testTag("tv-people-biography"))
                }
                val rows = profile?.infobox.orEmpty().filter { it.value.isNotBlank() }
                if (rows.isNotEmpty()) {
                    if (!profile?.summary.isNullOrBlank()) {
                        HorizontalDivider(Modifier.padding(vertical = 28.dp), color = TvSubjectDetailsDefaults.SecondaryContent.copy(alpha = .2f))
                    }
                    Text(stringResource(Lang.person_details_basic_info), color = TvSubjectDetailsDefaults.Content,
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 20.dp))
                    Column(Modifier.testTag("tv-people-infobox"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        rows.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Text(row.key, Modifier.weight(.22f), color = TvSubjectDetailsDefaults.SecondaryContent,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 30.sp))
                                Text(row.value, Modifier.weight(.78f), color = TvSubjectDetailsDefaults.Content,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 30.sp))
                            }
                        }
                    }
                } else if (profile?.summary.isNullOrBlank()) {
                    Text(error?.let { renderLoadErrorMessage(it) }
                        ?: stringResource(if (loading) Lang.foundation_loading else Lang.people_no_information),
                        color = TvSubjectDetailsDefaults.SecondaryContent)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
