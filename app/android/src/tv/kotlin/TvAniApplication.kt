/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv

import android.app.Application
import kotlinx.coroutines.launch
import me.him188.ani.android.getCommonAndroidModules
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.platform.AndroidLoggingConfigurator
import me.him188.ani.app.platform.createAppRootCoroutineScope
import me.him188.ani.app.platform.getTvCommonKoinModule
import me.him188.ani.app.platform.startCommonKoinModule
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.tv.ui.di.getTvKoinModule
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * TV variant 的 Application (atv-architecture.md §6.1).
 *
 * 与手机 AniApplication 的差异 (flavor 门控):
 * - getTvCommonKoinModule: 缓存/BT 绑定为空引擎实现 (§1.2 裁剪), 其余装配与手机一致;
 * - 单进程, 无 torrent 服务连接管理;
 * - M0 不接入 Sentry/Firebase (tv classpath 已剔除 firebase, 见 build.gradle.kts).
 */
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

        val koinApp = startKoin {
            androidContext(this@TvAniApplication)
            // 共享装配 + 空引擎缓存门控 —— TV 无缓存/BT, 选源池自然无 LocalCache 源 (§1.2)
            modules(getTvCommonKoinModule({ this@TvAniApplication }, scope))
            modules(getCommonAndroidModules(scope)) // src/main 交集 (无 torrent 绑定)
            modules(getTvAndroidModules()) // src/tv — Web 解析链 / BrowserNavigator 降级 / AppTerminator
            modules(getTvKoinModule()) // :app:android:ui-main-tv — 薄 VM 注册表 (M1 起)
        }.startCommonKoinModule(this@TvAniApplication, scope) // proxy/Session 后台任务; 缓存恢复段判空跳过

        scope.launch {
            // TV 固定偏好在线源 (§8.1): BT 已裁剪且无 torrent 引擎, 自动选源必须优先 WEB.
            // TV 是独立应用有独立 DataStore, 该写入不影响手机端配置.
            val settings = koinApp.koin.get<SettingsRepository>()
            settings.mediaSelectorSettings.update {
                if (preferKind == MediaSourceKind.WEB) this else copy(preferKind = MediaSourceKind.WEB)
            }
        }
    }
}
