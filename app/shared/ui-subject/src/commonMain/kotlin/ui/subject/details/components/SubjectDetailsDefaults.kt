/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.OutlinedTag
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.renderSubjectSeason
import me.him188.ani.datasources.api.PackedDate

object SubjectDetailsDefaults {
    val TabWidth = 80.dp
    val TabRowWidth = 80.dp * 3 // 240.dp, 三个 Tab 的宽度
    val MaximumContentWidth = 1300.dp
}

/** 手机 header 的季度 tag + 播出状态行. */
@Suppress("UnusedReceiverParameter")
@Composable
fun SubjectDetailsDefaults.SeasonTag(
    airDate: PackedDate,
    airingLabelState: AiringLabelState,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        OutlinedTag { Text(renderSubjectSeason(airDate)) }
        AiringLabel(
            airingLabelState,
            Modifier.align(Alignment.CenterVertically),
            style = LocalTextStyle.current,
            progressColor = LocalContentColor.current,
        )
    }
}
