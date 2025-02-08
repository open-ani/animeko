/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.icons.PlayingIcon
import me.him188.ani.app.ui.foundation.preview.PreviewTabletLightDark
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
@PreviewTabletLightDark
fun PreviewEpisodeSelectorSideSheet() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSideSheets.EpisodeSelectorSheet(
            state = rememberTestEpisodeSelectorState(),
            onDismissRequest = {},
        )
    }
}


@Preview
@Composable
fun PreviewPlayingIcon() {
    ProvideCompositionLocalsForPreview {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.border(1.dp, color = Color.Magenta)) {
                PlayingIcon(contentDescription = "正在播放")
            }
        }
    }
}