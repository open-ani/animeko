/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.preview.PreviewSizeClasses

@Composable
@PreviewSizeClasses
@PreviewLightDark
fun PreviewSubjectItemLayout() = ProvideCompositionLocalsForPreview {
    SubjectItemLayout(
        selected = false,
        {},
        image = { SubjectItemDefaults.Image("a", Modifier.fillMaxSize(), null) },
        title = { maxLines ->
            Text("关于我转生变成史莱姆这档事 第三季", maxLines = maxLines)
        },
        tags = { Text("2024 年 10 月 · 全 24 话 · 奇幻 / 战斗") },
        extraInfo = {
            Text("配音:  岡咲美保 · 前野智昭 ·  古川慎")
            Text("制作:  8bit · 中山敦史 · 泽野弘之 ")
        },
        rating = {},
        actions = {
            SubjectItemDefaults.ActionPlay({})
        },
    )
}
