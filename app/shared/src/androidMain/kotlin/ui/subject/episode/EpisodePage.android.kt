/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.danmaku.DummyDanmakuEditor
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@Composable
@Preview(widthDp = 1080 / 3, heightDp = 2400 / 3, showBackground = true)
@Preview(device = Devices.TABLET, showBackground = true)
internal fun PreviewEpisodePage() {
    ProvideCompositionLocalsForPreview {
        val context = LocalContext.current
        EpisodeScreen(
            remember {
                EpisodeViewModel(
                    424663,
                    1277147,
                    context = context,
                )
            },
        )
    }
}

@Composable
@PreviewLightDark
fun PreviewEpisodeSceneContentPhoneScaffoldTabs() {
    ProvideCompositionLocalsForPreview {
        EpisodeScreenContentPhoneScaffold(
            videoOnly = false,
            commentCount = { 100 },
            video = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            },
            episodeDetails = { },
            commentColumn = { },
            tabRowContent = {
                DummyDanmakuEditor({})
            },
        )
    }
}
