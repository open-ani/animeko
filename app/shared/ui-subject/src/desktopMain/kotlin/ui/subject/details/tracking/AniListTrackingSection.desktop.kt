/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ui.subject.details.tracking

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.data.models.subject.SubjectInfo

@Composable
internal actual fun AniListTrackingSection(info: SubjectInfo, showCollection: Boolean, highlightTrack: Boolean, bangumiConnected: Boolean, collectionAction: @Composable () -> Unit, modifier: Modifier) {
    collectionAction()
}
