/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.MediaSelectorView
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediafetch.request.TestMediaFetchRequest
import me.him188.ani.app.ui.subject.episode.TAG_MEDIA_SELECTOR_SHEET
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.settings.SideSheetLayout
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewLightDark

@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoSideSheets.MediaSelectorSheet(
    mediaSelectorState: MediaSelectorState,
    mediaSourceResultListPresentation: MediaSourceResultListPresentation,
    viewKind: ViewKind,
    onViewKindChange: (ViewKind) -> Unit,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    onDismissRequest: () -> Unit,
    onRefresh: () -> Unit,
    onRestartSource: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    SideSheetLayout(
        title = { Text(text = "选择数据源") },
        onDismissRequest = onDismissRequest,
        Modifier.testTag(TAG_MEDIA_SELECTOR_SHEET),
        contentPadding = contentPadding,
        closeButton = {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭")
            }
        },
    ) {
        MediaSelectorView(
            mediaSelectorState,
            viewKind,
            onViewKindChange,
            fetchRequest,
            onFetchRequestChange,
            mediaSourceResultListPresentation,
            onRestartSource = onRestartSource,
            onRefresh,
            modifier.padding(horizontal = 16.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            stickyHeaderBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onClickItem = {
                mediaSelectorState.select(it)
                onDismissRequest()
            },
            singleLineFilter = true,
        )
    }
}


@OptIn(TestOnly::class)
@Composable
@Preview
@PreviewLightDark
private fun PreviewEpisodeVideoMediaSelectorSideSheet() {
    ProvideCompositionLocalsForPreview {
        val (viewKind, onViewKindChange) = rememberSaveable { mutableStateOf(ViewKind.WEB) }
        EpisodeVideoSideSheets.MediaSelectorSheet(
            mediaSelectorState = rememberTestMediaSelectorState(),
            mediaSourceResultListPresentation = TestMediaSourceResultListPresentation,
            viewKind = viewKind,
            onViewKindChange = onViewKindChange,
            fetchRequest = TestMediaFetchRequest,
            onFetchRequestChange = {},
            onDismissRequest = {},
            onRefresh = {},
            onRestartSource = {},
        )
    }
}
