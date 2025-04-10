/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.subject.episode.video.sidesheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.preview.PreviewTabletLightDark
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@Preview
@PreviewTabletLightDark
fun PreviewEpisodeVideoMediaSelectorSideSheet() {
    ProvideCompositionLocalsForPreview {
        val (viewKind, onViewKindChange) = rememberSaveable { mutableStateOf(ViewKind.WEB) }
        EpisodeVideoSideSheets.MediaSelectorSheet(
            mediaSelectorState = rememberTestMediaSelectorState(),
            mediaSourceResultListPresentation = TestMediaSourceResultListPresentation,
            viewKind = viewKind,
            onViewKindChange = onViewKindChange,
            onDismissRequest = {},
            onRefresh = {},
            onRestartSource = {},
        )
    }
}
