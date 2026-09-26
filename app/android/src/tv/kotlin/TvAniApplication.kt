/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.tv

import android.app.Application
import me.him188.ani.android.getCommonAndroidModules
import me.him188.ani.app.platform.AndroidLoggingConfigurator
import me.him188.ani.app.platform.createAppRootCoroutineScope
import me.him188.ani.app.platform.getCommonKoinModule
import me.him188.ani.app.platform.startCommonKoinModule
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** TV 应用使用共享下载存储, BT 引擎始终在应用进程内运行. */
class TvAniApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val logsDir = filesDir.resolve("logs").absolutePath
        AndroidLoggingConfigurator.configure(logsDir)

        val defaultUEH = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logger<TvAniApplication>().error(e) { "!!!ANI TV FATAL EXCEPTION!!! ($e)" }
            Thread.sleep(500)
            defaultUEH?.uncaughtException(t, e)
        }

        val scope = createAppRootCoroutineScope()

        startKoin {
            androidContext(this@TvAniApplication)
            modules(getCommonKoinModule({ this@TvAniApplication }, scope))
            modules(getCommonAndroidModules(scope))
            modules(getTvAndroidModules(scope))
        }.startCommonKoinModule(this@TvAniApplication, scope)
    }
}
