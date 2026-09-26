/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import android.content.Intent
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.him188.ani.android.navigation.AndroidBrowserNavigator
import me.him188.ani.android.provider.ExternalContentProviderFactoryImpl
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.mediasource.web.AndroidOnnxImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.AndroidCaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.app.domain.torrent.RemoteAnitorrentEngineFactory
import me.him188.ani.app.domain.torrent.service.AniTorrentService
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnection
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnectionManager
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.AniComponentActivity
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.findActivity
import me.him188.ani.app.tools.update.AndroidUpdateInstaller
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.exprovider.ExternalContentProviderFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * 手机 flavor 专属绑定; 与 TV 共用的绑定见交集源集 `src/main` 的 [getCommonAndroidModules].
 */
fun getAndroidModules(
    serviceConnectionManager: TorrentServiceConnectionManager,
    coroutineScope: CoroutineScope,
) = module {
    single<BrowserNavigator> { AndroidBrowserNavigator() }
    single<CaptchaBrowserFactory> { AndroidCaptchaBrowserFactory(androidContext()) }
    single<ImageCaptchaRecognizer> { AndroidOnnxImageCaptchaRecognizer() }

    single<TorrentEngineAccess> { serviceConnectionManager }
    single<TorrentServiceConnection<IRemoteAniTorrentEngine>> { serviceConnectionManager.connection }

    includes(getAndroidMediaModules(coroutineScope) {
        RemoteAnitorrentEngineFactory(get(), get(), get<ProxyProvider>().proxy)
    })

    single<UpdateInstaller> { AndroidUpdateInstaller() }

    single<AppTerminator> {
        object : AppTerminator {
            override fun exitApp(context: ContextMP, status: Int): Nothing {
                runBlocking(Dispatchers.Main.immediate) {
                    (context.findActivity() as? AniComponentActivity)?.finishAffinity()
                    context.startService(
                        Intent(context, AniTorrentService.actualServiceClass)
                            .apply { putExtra("stopService", true) },
                    )
                    exitProcess(status)
                }
            }
        }
    }

    single<ExternalContentProviderFactory> {
        ExternalContentProviderFactoryImpl(get())
    }
}
