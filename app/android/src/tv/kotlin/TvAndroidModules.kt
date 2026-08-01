/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv

import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.NoopBrowserNavigator
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.findActivity
import org.koin.dsl.module
import kotlin.system.exitProcess

/**
 * TV flavor 专属平台绑定 (atv-architecture.md §4.3-R2 / §6.1).
 *
 * 按 §1.2 裁剪, 以下绑定**永不注册**: CaptchaBrowserFactory / ImageCaptchaRecognizer (评论发送),
 * torrent/缓存链路 (BT/缓存); UpdateInstaller 按 D8 暂缓.
 */
fun getTvAndroidModules() = module {
    // M1: 换成二维码降级实现 (弹对话框展示 URL 二维码, §6.1)
    single<BrowserNavigator> { NoopBrowserNavigator }

    single<AppTerminator> {
        object : AppTerminator {
            override fun exitApp(context: ContextMP, status: Int): Nothing {
                runBlocking(Dispatchers.Main.immediate) {
                    (context.findActivity() as? Activity)?.finishAffinity()
                    exitProcess(status)
                }
            }
        }
    }
}
