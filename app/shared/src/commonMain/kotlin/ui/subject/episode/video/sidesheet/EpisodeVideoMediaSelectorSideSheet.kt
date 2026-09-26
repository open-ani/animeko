/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_close_selector
import me.him188.ani.app.ui.lang.subject_episode_select_media_source
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.auto.AutoMatchPage
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorModeChip
import me.him188.ani.app.ui.subject.episode.TAG_MEDIA_SELECTOR_SHEET
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.settings.SideSheetLayout
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

/**
 * 全屏播放器侧边栏: 只放自动匹配页, 标题行右侧是模式 chip.
 *
 * @param onModeChange 宿主写 vm.mediaSelectorMode; MANUAL / BT 由宿主的 LaunchedEffect(mode) 关侧边栏 + 开全屏容器.
 */
@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoSideSheets.MediaSelectorSheet(
    mediaSelectorState: MediaSelectorState,
    mediaSourceResultListPresentation: MediaSourceResultListPresentation,
    mode: MediaSelectorMode,
    onModeChange: (MediaSelectorMode) -> Unit,
    onDismissRequest: () -> Unit,
    onRestartSource: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectMediaSourceText = stringResource(Lang.subject_episode_select_media_source)
    val closeSelectorText = stringResource(Lang.subject_episode_close_selector)

    SideSheetLayout(
        title = {
            // SideSheetLayout 把 title 放在 Row(weight(1f)) 里, 内层不 fillMaxWidth 的话 chip 会贴着标题而不是靠右.
            // 标题 weight(1f) 承担压缩并省略, chip 先按固有宽度测量: 手机横屏 300dp 侧边栏放不下英文标题 + chip 时被截的是标题, 不是模式入口.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = selectMediaSourceText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MediaSelectorModeChip(
                    mode,
                    onModeChange,
                    showBt = mediaSourceResultListPresentation.btSources.isNotEmpty(),
                )
            }
        },
        onDismissRequest = onDismissRequest,
        Modifier.testTag(TAG_MEDIA_SELECTOR_SHEET),
        closeButton = {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Rounded.Close, contentDescription = closeSelectorText)
            }
        },
    ) {
        AutoMatchPage(
            mediaSelectorState,
            onClickItem = {
                mediaSelectorState.select(it)
                onDismissRequest()
            },
            onRestartSource = onRestartSource,
            onRequestManualSearch = { onModeChange(MediaSelectorMode.MANUAL) },
            modifier.padding(horizontal = 16.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}


@OptIn(TestOnly::class)
@Composable
@Preview
@PreviewLightDark
private fun PreviewEpisodeVideoMediaSelectorSideSheet() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSideSheets.MediaSelectorSheet(
            mediaSelectorState = rememberTestMediaSelectorState(),
            mediaSourceResultListPresentation = TestMediaSourceResultListPresentation,
            mode = MediaSelectorMode.AUTO,
            onModeChange = {},
            onDismissRequest = {},
            onRestartSource = {},
        )
    }
}
