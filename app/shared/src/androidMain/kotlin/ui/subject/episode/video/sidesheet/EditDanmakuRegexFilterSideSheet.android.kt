/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.sidesheet


import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.settings.danmaku.createTestDanmakuRegexFilterState
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
@Composable
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewEditDanmakuRegexFilterSideSheet() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSideSheets.DanmakuRegexFilterSettings(
            state = createTestDanmakuRegexFilterState(),
            onDismissRequest = { },
        )
    }
}