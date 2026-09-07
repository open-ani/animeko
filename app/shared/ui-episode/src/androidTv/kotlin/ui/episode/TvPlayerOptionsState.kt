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
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFailedOrAbandoned
import me.him188.ani.app.videoplayer.ui.PlayerStatsSnapshot
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuEpisode
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.api.provider.DanmakuSubject
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

data class TvSourceItem(val media: Media, val excludedReason: String? = null)

data class TvSourceGroup(
    val instanceId: String,
    val sourceId: String,
    val name: String,
    val iconUrl: String?,
    val state: MediaSourceFetchState,
    val items: List<TvSourceItem>,
    val isCaptchaSupported: Boolean = true,
    val isResolvingCaptcha: Boolean = false,
) {
    val loading: Boolean get() = state == MediaSourceFetchState.Idle || state == MediaSourceFetchState.Working
    val failed: Boolean get() = state.isFailedOrAbandoned
    val showInSimpleMode: Boolean
        get() = state != MediaSourceFetchState.Disabled &&
                (state !is MediaSourceFetchState.Succeed || items.any { it.excludedReason == null })
}

data class TvSourceSelectionState(
    val groups: List<TvSourceGroup> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

enum class TvPlayerDialog { Speed, Subtitles, EpisodeActions, DanmakuMatch, DanmakuList }

enum class TvDanmakuProperty { FontSize, Opacity, Speed, Density, Area, Stroke, Weight, Top, Bottom, Floating, Color }

data class TvDanmakuOrigin(
    val serviceId: DanmakuServiceId,
    val providerId: DanmakuProviderId,
    val name: String,
    val match: String,
    val enabled: Boolean,
    val shiftMillis: Long,
    val canMatch: Boolean,
)

data class TvSubtitleOption(val id: String, val label: String)

data class TvDanmakuMatchState(
    val providerId: DanmakuProviderId? = null,
    val query: String = "",
    val subjects: List<DanmakuSubject> = emptyList(),
    val selectedSubject: DanmakuSubject? = null,
    val episodes: List<DanmakuEpisode> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
)

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
    val statsVisible: Boolean = false,
    val preview: ImageBitmap? = null,
    val previewAvailable: Boolean = false,
    val previewLoading: Boolean = false,
    val chapters: List<TvChapter> = emptyList(),
    val skipPrompt: TvSkipPrompt? = null,
    val message: String? = null,
)

data class TvChapter(val name: String, val offsetMillis: Long, val durationMillis: Long)
data class TvSkipPrompt(val name: String, val secondsRemaining: Int)

internal fun UnifiedCollectionType.tvLabel(): String = when (this) {
    UnifiedCollectionType.WISH -> "想看"
    UnifiedCollectionType.DOING -> "在看"
    UnifiedCollectionType.DONE -> "看过"
    UnifiedCollectionType.ON_HOLD -> "搁置"
    UnifiedCollectionType.DROPPED -> "抛弃"
    UnifiedCollectionType.NOT_COLLECTED -> "收藏"
}
