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
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.AniComponentActivity
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.foundation.rememberAniSketchInstance
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.tv.ui.foundation.theme.AniTvTheme
import me.him188.ani.tv.ui.main.TvAniAppContent
import org.koin.android.ext.android.getKoin

/**
 * TV 单 Activity (atv-architecture.md §6.2): 横屏 (manifest 声明)、singleTask、Compose 全屏.
 *
 * M2: handleStartIntent 解析 `ani://subjects/<id>` deep link -> navigateSubjectDetails.
 */
class MainActivity : AniComponentActivity() {

    private val aniNavigator = AniNavigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全面屏: 内容画到系统栏后面 (对齐参考版沉浸效果)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            AniTvTheme {
                // 与手机 AniApp 同款 Sketch 装配 (§5.6; main 已从 coil 迁移至 sketch)
                val imageLoaderClient = remember {
                    getKoin().get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI)
                }
                val sketch = rememberAniSketchInstance(imageLoaderClient)
                val toaster = remember {
                    // TV 端 Toaster: 原生 Toast (10-foot 下自绘胶囊 M4 视觉阶段再换, §5.3)
                    object : Toaster {
                        override fun toast(text: String) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalSketch provides sketch,
                    LocalToaster provides toaster,
                ) {
                    TvAniAppContent(aniNavigator)
                }
            }
        }
    }
}
