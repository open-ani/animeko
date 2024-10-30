/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import me.him188.ani.android.activity.MainActivity
import me.him188.ani.app.domain.media.cache.MediaCacheNotificationTask
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.domain.torrent.service.AniTorrentService
import me.him188.ani.app.platform.AndroidLoggingConfigurator
import me.him188.ani.app.platform.JvmLogHelper
import me.him188.ani.app.platform.createAppRootCoroutineScope
import me.him188.ani.app.platform.getCommonKoinModule
import me.him188.ani.app.platform.startCommonKoinModule
import me.him188.ani.app.ui.settings.tabs.getLogsDir
import me.him188.ani.app.ui.settings.tabs.media.DEFAULT_TORRENT_CACHE_DIR_NAME
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
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
    }

    private var currentActivity: Activity? = null

    init {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                }

                override fun onActivityStarted(activity: Activity) {
                }

                override fun onActivityResumed(activity: Activity) {
                    currentActivity = activity
                }

                override fun onActivityPaused(activity: Activity) {
                }

                override fun onActivityStopped(activity: Activity) {
                    if (currentActivity == activity) {
                        currentActivity = null
                    }
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                }

                override fun onActivityDestroyed(activity: Activity) {
                }
            },
        )
    }

    inner class Instance

    override fun onCreate() {
        super.onCreate()
        
        val logsDir = applicationContext.getLogsDir().absolutePath
        AndroidLoggingConfigurator.configure(logsDir)
        runCatching {
            JvmLogHelper.deleteOldLogs(Paths.get(logsDir))
        }.onFailure {
            Log.e("AniApplication", "Failed to delete old logs", it)
        }

        
        if (processName().endsWith(AniTorrentService::class.simpleName!!)) {
            // In service process, we don't need any dependency which is use in app process.
            return
        }

        // Only use torrent service at Android 8.1 (27) or above.
        // Our minimal support is Android 8.0 (26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            startForegroundService(
                Intent(this, AniTorrentService::class.java).apply {
                    putExtra("app_name", me.him188.ani.R.string.app_name)
                    putExtra("app_service_title_text_idle", me.him188.ani.R.string.app_service_content_text)
                    putExtra("app_service_title_text_working", me.him188.ani.R.string.app_service_content_text)
                    putExtra("app_service_content_text", me.him188.ani.R.string.app_service_content_text)
                    putExtra("app_service_stop_text", me.him188.ani.R.string.app_service_content_text)
                    putExtra("app_icon", me.him188.ani.R.mipmap.a_round)
                    putExtra("open_activity_intent", Intent(this@AniApplication, MainActivity::class.java))
                },
            )
        }

        instance = Instance()

        val scope = createAppRootCoroutineScope()

        val defaultTorrentCacheDir = applicationContext.filesDir
            .resolve(DEFAULT_TORRENT_CACHE_DIR_NAME).apply { mkdir() }

        // since 3.5, 删除 libtorrent4j 缓存, 大概保留到 3.8 就可以删除个代码了
        defaultTorrentCacheDir.resolve("libtorrent4j").let {
            if (it.exists()) {
                it.deleteRecursively()
                Log.w("AniApplication", "Deleted libtorrent4j cache")
            }
        }

        OkHttp // survive R8

        startKoin {
            androidContext(this@AniApplication)
            modules(getCommonKoinModule({ this@AniApplication }, scope))
            modules(getAndroidModules(defaultTorrentCacheDir, scope))
        }.startCommonKoinModule(scope)

        val koin = getKoin()
        koin.get<TorrentManager>() // start sharing, connect to DHT now
        scope.launch(CoroutineName("MediaCacheNotificationTask")) {
            MediaCacheNotificationTask(koin.get(), koin.get()).run()
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
}
