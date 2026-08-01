/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv

import android.os.Bundle
import androidx.activity.compose.setContent
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.BaseComponentActivity
import me.him188.ani.tv.ui.foundation.theme.AniTvTheme
import me.him188.ani.tv.ui.main.TvAniAppContent

/**
 * TV 单 Activity (atv-architecture.md §6.2): 横屏 (manifest 声明)、singleTask、Compose 全屏.
 *
 * M1: handleStartIntent 解析 `ani://subjects/<id>` deep link -> navigateSubjectDetails.
 */
class MainActivity : BaseComponentActivity() {

    private val aniNavigator = AniNavigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AniTvTheme {
                TvAniAppContent(aniNavigator)
            }
        }
    }
}
