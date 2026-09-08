/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.ui.graphics.ImageBitmap
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.videoplayer.ui.PlayerStatsSnapshot
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.episode.controls.TvSubtitleOption
import me.him188.ani.leanback.ui.episode.danmaku.TvDanmakuOrigin
import me.him188.ani.leanback.ui.episode.playback.TvChapter
import me.him188.ani.leanback.ui.episode.playback.TvSkipPrompt

data class TvPlayerOptionsState(
    val danmakuEnabled: Boolean = true,
    val danmakuConfig: DanmakuConfig = DanmakuConfig.Default,
    val danmakuOrigins: List<TvDanmakuOrigin> = emptyList(),
    val videoConfig: VideoScaffoldConfig = VideoScaffoldConfig.Default,
    val collectionType: UnifiedCollectionType = UnifiedCollectionType.NOT_COLLECTED,
    val collectionBusy: Boolean = false,
    val subtitles: List<TvSubtitleOption> = emptyList(),
    val selectedSubtitleId: String? = null,
    val supportsSubtitles: Boolean = false,
    val enhancementMode: VideoEnhancementMode? = null,
    val stats: PlayerStatsSnapshot? = null,
    val preview: ImageBitmap? = null,
    val previewAvailable: Boolean = false,
    val previewLoading: Boolean = false,
    val chapters: List<TvChapter> = emptyList(),
    val skipPrompt: TvSkipPrompt? = null,
    val message: TvPlayerMessage? = null,
)

enum class TvPlayerError { SourceInfoUnavailable, EmptyDanmakuQuery, DanmakuSearchFailed }

enum class TvPlayerMessage { FollowingHost, DanmakuMatched, OperationFailed }
