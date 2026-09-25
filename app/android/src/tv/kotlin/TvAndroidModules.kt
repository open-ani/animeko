/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.tv

import android.app.Activity
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.him188.ani.android.getAndroidMediaModules
import me.him188.ani.app.domain.media.cache.engine.AlwaysUseTorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.mediasource.web.AndroidOnnxImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.AndroidCaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import me.him188.ani.app.domain.torrent.LocalAnitorrentEngineFactory
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.NoopBrowserNavigator
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.findActivity
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** TV 平台绑定: 下载引擎与播放器同进程, Web 验证由共享解析链处理. */
fun getTvAndroidModules(coroutineScope: CoroutineScope) = module {
    single<BrowserNavigator> { NoopBrowserNavigator }
    single<CaptchaBrowserFactory> { AndroidCaptchaBrowserFactory(androidContext()) }
    single<ImageCaptchaRecognizer> { AndroidOnnxImageCaptchaRecognizer() }
    single<TorrentEngineAccess> { AlwaysUseTorrentEngineAccess }
    includes(getAndroidMediaModules(coroutineScope) { LocalAnitorrentEngineFactory })

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
