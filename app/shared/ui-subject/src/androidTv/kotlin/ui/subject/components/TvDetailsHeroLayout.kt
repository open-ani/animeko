/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The loaded hero and its skeleton share positioning, responsive widths and vertical spacing. */
@Composable
internal fun TvDetailsHeroLayout(
    height: Dp,
    identity: @Composable (compact: Boolean) -> Unit,
    introduction: (@Composable (Modifier) -> Unit)?,
    actions: @Composable (compact: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = TvSubjectDetailsDefaults.HorizontalPadding, end = TvSubjectDetailsDefaults.HorizontalPadding,
        top = 36.dp, bottom = TvSubjectDetailsDefaults.OverviewBottomPadding,
    ),
    actionSpacing: Dp = 32.dp,
) {
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(
            start = contentPadding.calculateStartPadding(direction),
            end = contentPadding.calculateEndPadding(direction),
        ),
    ) {
        val compact = maxWidth < 600.dp
        Column(
            Modifier.fillMaxWidth().heightIn(min = height)
                .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.Bottom,
        ) {
            identity(compact)
            if (introduction != null) {
                Spacer(Modifier.height(20.dp))
                introduction(Modifier.fillMaxWidth(if (compact) 1f else .49f))
            }
            Spacer(Modifier.height(actionSpacing))
            actions(compact)
        }
    }
}
