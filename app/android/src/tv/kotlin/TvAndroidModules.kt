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
import me.him188.ani.app.domain.media.resolver.AndroidWebMediaResolver
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaResolver
import me.him188.ani.app.domain.media.resolver.LocalFileMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.mediasource.web.AndroidOnnxImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.AndroidCaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.NoopBrowserNavigator
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.findActivity
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import kotlin.system.exitProcess

/**
 * TV flavor 专属平台绑定 (atv-architecture.md §4.3-R2 / §6.1).
 *
 * 按 §1.2 裁剪, torrent/缓存链路与 UpdateInstaller (D8 暂缓) 不注册.
 * Captcha 浏览器/识别器**需要**注册 —— 它们是 Web 数据源解析链 (WebSessionManager) 的依赖,
 * 服务于播放取源, 而非评论发送 (评论发送的 TurnstileState 才是裁剪对象).
 */
fun getTvAndroidModules() = module {
    // M2: 换成二维码降级实现 (弹对话框展示 URL 二维码, §6.1)
    single<BrowserNavigator> { NoopBrowserNavigator }

    // Web 数据源解析链 (取源播放必需, §8.1)
    single<CaptchaBrowserFactory> { AndroidCaptchaBrowserFactory(androidContext()) }
    single<ImageCaptchaRecognizer> { AndroidOnnxImageCaptchaRecognizer() }

    // TV 版 MediaResolver: 仅在线链路 —— 无 torrent / offline 解析 (§1.2 裁剪)
    factory<MediaResolver> {
        MediaResolver.from(
            listOf<MediaResolver>(LocalFileMediaResolver())
                .plus(HttpStreamingMediaResolver())
                .plus(
                    AndroidWebMediaResolver(
                        get<MediaSourceManager>().webVideoMatcherLoader,
                        get<SettingsRepository>(),
                        get<WebSessionManager>(),
                    ),
                ),
        )
    }

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
