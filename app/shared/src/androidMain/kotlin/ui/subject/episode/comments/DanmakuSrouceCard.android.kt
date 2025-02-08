/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.subject.episode.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.subject.episode.details.components.DanmakuSourceCard
import me.him188.ani.app.ui.subject.episode.statistics.fuzzy
import me.him188.ani.danmaku.api.DanmakuMatchInfo
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@Preview
fun PreviewDanmakuSourceCard() {
    ProvideCompositionLocalsForPreview {
        Box(Modifier.width(240.dp)) {
            DanmakuSourceCard(
                info = DanmakuMatchInfo.fuzzy(),
                enabled = true,
                showDetails = false,
                onClickSettings = {},
            )
        }
    }
}

@Composable
@Preview
fun PreviewDanmakuSourceCardDetails() {
    ProvideCompositionLocalsForPreview {
        Box(Modifier.width(240.dp)) {
            DanmakuSourceCard(
                info = DanmakuMatchInfo.fuzzy(),
                enabled = true,
                showDetails = true,
                onClickSettings = {},
            )
        }
    }
}

@Composable
@Preview
fun PreviewDanmakuSourceCardDisabled() {
    ProvideCompositionLocalsForPreview {
        Box(Modifier.width(240.dp)) {
            DanmakuSourceCard(
                info = DanmakuMatchInfo.fuzzy(),
                enabled = false,
                showDetails = false,
                onClickSettings = {},
            )
        }
    }
}
