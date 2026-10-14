/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.test

import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.settings.mediasource.AbstractMediaSourceTestState
import me.him188.ani.app.ui.settings.mediasource.EditMediaSourceTestDataCardDefaults

/**
 * 测试页顶部的输入卡片: 搜索关键字与集号.
 */
@Composable
fun MediaSourceTestDataCard(
    state: AbstractMediaSourceTestState,
    modifier: Modifier = Modifier,
) {
    with(EditMediaSourceTestDataCardDefaults) {
        Card(
            modifier,
            shape = cardShape,
            colors = cardColors,
        ) {
            FlowRow {
                KeywordTextField(state, Modifier.weight(1f))
                EpisodeSortTextField(state, Modifier.weight(1f))
            }
        }
    }
}
