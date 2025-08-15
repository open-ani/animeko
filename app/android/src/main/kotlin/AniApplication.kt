/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.lifecycle.ProcessLifecycleOwner
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.him188.ani.android.activity.MainActivity
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.torrent.service.AniTorrentService
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnectionManager
import me.him188.ani.app.platform.AndroidLoggingConfigurator
import me.him188.ani.app.platform.AppStartupTasks
import me.him188.ani.app.platform.JvmLogHelper
import me.him188.ani.app.platform.StartupTimeMonitor
import me.him188.ani.app.platform.StepName
import me.him188.ani.app.platform.create
import me.him188.ani.app.platform.createAppRootCoroutineScope
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.platform.getCommonKoinModule
import me.him188.ani.app.platform.startCommonKoinModule
import me.him188.ani.app.platform.trace.recordAppStart
import me.him188.ani.app.ui.settings.tabs.log.getLogsDir
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsConfig
import me.him188.ani.utils.analytics.AnalyticsImpl
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Paths


class AniApplication : Application() {

    companion object {
        init {
            if (BuildConfig.DEBUG) {
                System.setProperty("kotlinx.coroutines.debug", "on")
                System.setProperty("kotlinx.coroutines.stacktrace.recovery", "true")
            }
//            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
//            val v = kotlinx.coroutines.RECOVER_STACK_TRACES
//            println(v)
        }

        lateinit var instance: Instance
            private set

        /**
         * Only use torrent service at Android 8.1 (27) or above.
         * Our minimal support is Android 8.0 (26).
         */
        val FEATURE_USE_TORRENT_SERVICE = true
    }

    inner class Instance()

    override fun onCreate() {
        super.onCreate()
        val startupTimeMonitor = StartupTimeMonitor()

        val logsDir = applicationContext.getLogsDir().absolutePath
        AndroidLoggingConfigurator.configure(logsDir)
        AppStartupTasks.printVersions()
        startupTimeMonitor.mark(StepName.Logging)

        val defaultUEH = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logger<AniApplication>().error(e) { "!!!ANI FATAL EXCEPTION!!! ($e)" }
            Thread.sleep(500)
            defaultUEH?.uncaughtException(t, e)
        }
        startupTimeMonitor.mark(StepName.UncaughtExceptionHandler)

        if (processName().contains("torrent_service")) {
            // In service process, we don't need any dependency which is use in app process.
            return
        }

        instance = Instance() // set instance

        val scope = createAppRootCoroutineScope()

        val mediaCacheDataStore: MutableStateFlow<DataStore<List<MediaCacheSave>>?> = MutableStateFlow(null)
        val mediaCacheBaseSaveDir: MutableStateFlow<File?> = MutableStateFlow(null)
        val connectionManager = TorrentServiceConnectionManager(
            this,
            dataStoreFlow = mediaCacheDataStore,
            mediaCacheBaseSaveDirFlow = mediaCacheBaseSaveDir,
            startServiceImpl = ::startAniTorrentService,
            stopServiceImpl = ::stopService,
            processLifecycle = ProcessLifecycleOwner.get().lifecycle,
            parentCoroutineContext = scope.coroutineContext,
        )

        startupTimeMonitor.mark(StepName.WindowAndContext)

        scope.launch(Dispatchers.IO_) {
            runCatching {
                JvmLogHelper.deleteOldLogs(Paths.get(logsDir))
            }.onFailure {
                Log.e("AniApplication", "Failed to delete old logs", it)
            }
        }

        OkHttp // survive R8

        startKoin {
            androidContext(this@AniApplication)
            modules(getCommonKoinModule({ this@AniApplication }, scope))

            modules(getAndroidModules(connectionManager, scope))
        }.startCommonKoinModule(this@AniApplication, scope)
        startupTimeMonitor.mark(StepName.Modules)

        val koin = getKoin()
        val analyticsInitializer = scope.launch {
            val settingsRepository = koin.get<SettingsRepository>()
            val settings = settingsRepository.analyticsSettings.flow.first()
            if (settings.isInit) {
                settingsRepository.analyticsSettings.update {
                    copy(isInit = false) // save user id
                }
            }
            if (settings.allowAnonymousBugReport) {
                AppStartupTasks.initializeSentry(settings.userId)
            }
            if (settings.allowAnonymousAnalytics) {
                AppStartupTasks.initializeAnalytics {
                    AnalyticsImpl(
                        AnalyticsConfig.create(),
                        userId = settings.userId,
                    ).apply {
                        init(
                            this@AniApplication,
                            apiKey = currentAniBuildConfig.analyticsKey,
                            host = currentAniBuildConfig.analyticsServer,
                        )
                    }
                }
            }
        }

        mediaCacheDataStore.value = applicationContext.dataStores.mediaCacheMetadataStore
        mediaCacheBaseSaveDir.value = File(koin.get<MediaSaveDirProvider>().saveDir)
        connectionManager.launchCheckLoop()

        runBlocking { analyticsInitializer.join() }
        startupTimeMonitor.mark(StepName.Analytics)

        scope.launch {
            Analytics.recordAppStart(startupTimeMonitor)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun processName(): String {
        if (Build.VERSION.SDK_INT >= 28) return getProcessName()

        // Using the same technique as Application.getProcessName() for older devices
        // Using reflection since ActivityThread is an internal API
        try {
            @SuppressLint("PrivateApi") val activityThread = Class.forName("android.app.ActivityThread")

            // Before API 18, the method was incorrectly named "currentPackageName", but it still returned the process name
            // See https://github.com/aosp-mirror/platform_frameworks_base/commit/b57a50bd16ce25db441da5c1b63d48721bb90687
            val getProcessName: Method = activityThread.getDeclaredMethod("currentProcessName")
            return getProcessName.invoke(null) as String

        } catch (e: ClassNotFoundException) {
            throw RuntimeException(e)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException(e)
        }
    }

    private fun startAniTorrentService(): ComponentName? {
        return startForegroundService(
            Intent(this, AniTorrentService.actualServiceClass).apply {
                putExtra("app_name", me.him188.ani.R.string.app_name)
                putExtra("app_service_title_text_idle", me.him188.ani.R.string.app_service_title_text_idle)
                putExtra("app_service_title_text_working", me.him188.ani.R.string.app_service_title_text_working)
                putExtra("app_service_content_text", me.him188.ani.R.string.app_service_content_text)
                putExtra("app_service_stop_text", me.him188.ani.R.string.app_service_stop_text)
                putExtra("app_icon", me.him188.ani.R.mipmap.ic_launcher)
                putExtra("open_activity_intent", Intent(this@AniApplication, MainActivity::class.java))
            },
        )
    }

    private fun stopService() {
        startService(
            Intent(this, AniTorrentService.actualServiceClass)
                .apply { putExtra(AniTorrentService.INTENT_STOP_EXTRA, true) },
        )

    }

}