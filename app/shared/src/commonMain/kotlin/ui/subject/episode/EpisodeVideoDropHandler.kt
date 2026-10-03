/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import me.him188.ani.app.domain.media.DroppedFileMedia
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.app.ui.foundation.WindowDropCardContent
import me.him188.ani.app.ui.foundation.WindowDropHandler
import me.him188.ani.app.ui.foundation.WindowDropPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.episode_drop_video_description
import me.him188.ani.app.ui.lang.episode_drop_video_supported_hint
import me.him188.ani.app.ui.lang.episode_drop_video_title
import me.him188.ani.app.ui.lang.episode_drop_video_unknown_name
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.name
import org.jetbrains.compose.resources.stringResource

/**
 * 播放页的窗口拖放处理者: 将本地视频文件拖入窗口, 即在当前剧集播放该文件, 不经过数据源选择.
 *
 * 只接管含有视频文件的文件列表, 以及拖动阶段读不到内容的拖放 (松手后再判断); 其他内容交给后续处理者.
 * 拖入多个文件时播放其中首个视频文件.
 *
 * @see EpisodeViewModel.playDroppedFile
 */
class EpisodeVideoDropHandler(
    private val onPlay: (SystemPath) -> Unit,
) : WindowDropHandler {
    override fun onDragStarted(content: DragAndDropContent?): WindowDropPreview? {
        val file = when (content) {
            null -> null
            is DragAndDropContent.FileList -> DroppedFileMedia.findVideoFile(content.files) ?: return null
            is DragAndDropContent.PlainText, DragAndDropContent.Unsupported -> return null
        }
        return WindowDropPreview { EpisodeVideoDropCard(file) }
    }

    override fun onDrop(content: DragAndDropContent): Boolean {
        if (content !is DragAndDropContent.FileList) return false
        val file = DroppedFileMedia.findVideoFile(content.files) ?: return false
        onPlay(file)
        return true
    }

    @Composable
    override fun supportedHint(): String = stringResource(Lang.episode_drop_video_supported_hint)
}

@Composable
fun rememberEpisodeVideoDropHandler(onPlay: (SystemPath) -> Unit): EpisodeVideoDropHandler {
    val onPlayUpdated by rememberUpdatedState(onPlay)
    return remember { EpisodeVideoDropHandler { onPlayUpdated(it) } }
}

/**
 * 拖入视频文件时的卡片内容. [file] 为 `null` 表示拖动阶段读不到文件名.
 */
@Composable
private fun EpisodeVideoDropCard(file: SystemPath?) {
    WindowDropCardContent(
        icon = Icons.Rounded.PlayCircle,
        title = stringResource(Lang.episode_drop_video_title),
        subtitle = file?.name ?: stringResource(Lang.episode_drop_video_unknown_name),
        description = stringResource(Lang.episode_drop_video_description),
    )
}
