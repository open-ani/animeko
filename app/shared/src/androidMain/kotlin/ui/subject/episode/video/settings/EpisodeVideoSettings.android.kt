/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@Preview
@Composable
private fun PreviewEpisodeVideoSettings() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSettings(
            remember { EpisodeVideoSettingsViewModel() },
            { },
        )
    }
}

@Preview(heightDp = 200)
@Composable
private fun PreviewEpisodeVideoSettingsSmall() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSettings(
            remember { EpisodeVideoSettingsViewModel() },
            { },
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Preview
@Composable
private fun PreviewEpisodeVideoSettingsSideSheet() = ProvideCompositionLocalsForPreview {
    var showSettings by remember { mutableStateOf(true) }
    if (showSettings) {
        SideSheetLayout(
            title = {},
            onDismissRequest = { showSettings = false },
        ) {
            EpisodeVideoSettings(
                remember { EpisodeVideoSettingsViewModel() },
                { },
                Modifier.padding(8.dp),
            )
        }
    }
}