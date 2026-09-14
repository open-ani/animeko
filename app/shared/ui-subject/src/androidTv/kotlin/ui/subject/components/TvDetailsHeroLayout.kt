/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The loaded hero and its skeleton share positioning, responsive widths and vertical spacing. */
@Composable
internal fun TvDetailsHeroLayout(
    height: Dp,
    identity: @Composable (compact: Boolean) -> Unit,
    introduction: @Composable (Modifier) -> Unit,
    actions: @Composable (compact: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = TvSubjectDetailsDefaults.HorizontalPadding)) {
        val compact = maxWidth < 600.dp
        Column(Modifier.fillMaxWidth().heightIn(min = height)
            .padding(top = 36.dp, bottom = TvSubjectDetailsDefaults.OverviewBottomPadding),
            verticalArrangement = Arrangement.Bottom) {
            identity(compact)
            Spacer(Modifier.height(20.dp))
            introduction(Modifier.fillMaxWidth(if (compact) 1f else .49f))
            Spacer(Modifier.height(32.dp))
            actions(compact)
        }
    }
}
