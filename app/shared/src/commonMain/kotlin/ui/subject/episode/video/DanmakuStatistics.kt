/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import androidx.compose.runtime.Immutable
import me.him188.ani.app.domain.danmaku.DanmakuLoadingState
import me.him188.ani.danmaku.api.DanmakuMatchInfo
import me.him188.ani.danmaku.api.DanmakuMatchMethod
import me.him188.ani.utils.platform.annotations.TestOnly

@Immutable
data class DanmakuStatistics(
    val danmakuLoadingState: DanmakuLoadingState,
    val danmakuEnabled: Boolean,
)


@TestOnly
fun createTestDanmakuStatistics(
    state: DanmakuLoadingState = DanmakuLoadingState.Success(
        listOf(
            DanmakuMatchInfo(
                "弹幕源弹幕源弹幕源 A", 100,
                DanmakuMatchMethod.Fuzzy("条目标题", "剧集标题"),
            ),
            DanmakuMatchInfo(
                "弹幕源 B", 100,
                DanmakuMatchMethod.ExactId(123456, 222222),
            ),
        ),
    ),
    danmakuEnabled: Boolean = true,
): DanmakuStatistics = DanmakuStatistics(
    danmakuLoadingState = state,
    danmakuEnabled = danmakuEnabled,
)