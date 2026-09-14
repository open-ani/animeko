/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.danmaku

import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuEpisode
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.api.provider.DanmakuSubject
import me.him188.ani.leanback.ui.episode.TvPlayerError

internal sealed interface TvDanmakuAdjustment {
    data class Parameter(val property: TvDanmakuProperty) : TvDanmakuAdjustment
    data class Timing(val serviceId: DanmakuServiceId) : TvDanmakuAdjustment
}

enum class TvDanmakuProperty { FontSize, Opacity, Speed, Density, Area, Stroke, Weight, Top, Bottom, Floating, Color }

data class TvDanmakuOrigin(
    val serviceId: DanmakuServiceId,
    val providerId: DanmakuProviderId,
    val match: DanmakuMatchMethod,
    val count: Int,
    val enabled: Boolean,
    val shiftMillis: Long,
    val canMatch: Boolean,
)

data class TvDanmakuMatchState(
    val requestId: Long = 0,
    val providerId: DanmakuProviderId? = null,
    val query: String = "",
    val subjects: List<DanmakuSubject> = emptyList(),
    val selectedSubject: DanmakuSubject? = null,
    val episodes: List<DanmakuEpisode> = emptyList(),
    val loading: Boolean = false,
    val error: TvPlayerError? = null,
    val searched: Boolean = false,
)
