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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.BaseComponentActivity
import me.him188.ani.app.ui.foundation.LocalImageLoader
import me.him188.ani.app.ui.foundation.createDefaultImageLoader
import me.him188.ani.tv.ui.foundation.theme.AniTvTheme
import me.him188.ani.tv.ui.main.TvAniAppContent
import org.koin.android.ext.android.getKoin

/**
 * TV 单 Activity (atv-architecture.md §6.2): 横屏 (manifest 声明)、singleTask、Compose 全屏.
 *
 * M2: handleStartIntent 解析 `ani://subjects/<id>` deep link -> navigateSubjectDetails.
 */
class MainActivity : BaseComponentActivity() {

    private val aniNavigator = AniNavigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AniTvTheme {
                val coilContext = LocalPlatformContext.current
                val imageLoader = remember(coilContext) {
                    // 与手机 AniApp 同款装配 (§5.6)
                    createDefaultImageLoader(
                        coilContext,
                        getKoin().get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI),
                    )
                }
                CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                    TvAniAppContent(aniNavigator)
                }
            }
        }
    }
}
