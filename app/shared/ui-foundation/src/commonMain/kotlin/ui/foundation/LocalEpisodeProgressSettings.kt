/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import me.him188.ani.app.data.models.preference.EpisodeProgressSettings

/**
 * 当前用户的选集列表偏好 ([EpisodeProgressSettings]), 由 App 根组件提供给所有选集列表.
 *
 * 未提供时使用 [EpisodeProgressSettings.Default], 预览与测试无需额外接线.
 */
val LocalEpisodeProgressSettings: ProvidableCompositionLocal<EpisodeProgressSettings> =
    compositionLocalOf { EpisodeProgressSettings.Default }
