/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.app.domain.torrent.LifecycleAwareTorrentServiceConnection
import me.him188.ani.app.domain.torrent.TorrentServiceConnection
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes

/**
 * 管理与 [AniTorrentService] 的连接并获取 [IRemoteAniTorrentEngine] 远程访问接口.
 * 通过 [getBinder] 获取服务接口, 再启动完成并绑定之前将挂起协程.
 *
 * 服务连接控制依赖的 lifecycle 应当尽可能大, 所以应该使用
 * [ProcessLifecycleOwner][androidx.lifecycle.ProcessLifecycleOwner]
 * 或其他可以涵盖 app 全局生命周期的自定义 [LifecycleOwner] 来管理服务连接.
 * 不能使用 [Activity][android.app.Activity] (例如 [ComponentActivity][androidx.core.app.ComponentActivity])
 * 的生命周期, 因为在屏幕旋转 (例如竖屏转全屏播放) 的时候 Activity 可能会摧毁并重新创建,
 * 这会导致 [AndroidTorrentServiceConnection] 错误地重新绑定服务或重启服务.
 *
 * @see androidx.lifecycle.ProcessLifecycleOwner
 * @see ServiceConnection
 * @see AniTorrentService.onStartCommand
 * @see me.him188.ani.android.AniApplication
 */
class AndroidTorrentServiceConnection(
    private val context: Context,
    private val onRequiredRestartService: () -> ComponentName?,
    parentCoroutineContext: CoroutineContext = Dispatchers.Default,
) : ServiceConnection,
    LifecycleAwareTorrentServiceConnection<IRemoteAniTorrentEngine>(parentCoroutineContext) {
    private val startupIntentFilter by lazy { IntentFilter(AniTorrentService.INTENT_STARTUP) }
    private val acquireWakeLockIntent by lazy {
        Intent(context, AniTorrentService::class.java).apply {
            putExtra("acquireWakeLock", 1.minutes.inWholeMilliseconds)
        }
    }

    /**
     * Android 15 限制了 `dataSync` 和 `mediaProcessing` 的 FGS 后台运行时间限制
     */
    private var registered = false
    private val timeExceedLimitIntentFilter by lazy { IntentFilter(AniTorrentService.INTENT_BACKGROUND_TIMEOUT) }
    private val timeExceedLimitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logger.warn { "Service background time exceeded." }
            onServiceDisconnected()
        }
    }

    override suspend fun startService(): TorrentServiceConnection.StartResult {
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    logger.debug { "Received service startup broadcast: $intent, starting bind service." }
                    context.unregisterReceiver(this)

                    if (intent?.getBooleanExtra("success", false) != true) {
                        cont.resume(TorrentServiceConnection.StartResult.FAILED)
                        return
                    }

                    val bindResult = context.bindService(
                        Intent(
                            context,
                            AniTorrentService::class.java,
                        ),
                        this@AndroidTorrentServiceConnection,
                        Context.BIND_ABOVE_CLIENT,
                    )
                    if (!bindResult) {
                        logger.error { "Failed to bind service, context.bindService returns false." }
                        cont.resume(TorrentServiceConnection.StartResult.FAILED)
                    } else {
                        cont.resume(TorrentServiceConnection.StartResult.STARTED)
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                startupIntentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            val result = onRequiredRestartService()
            if (result == null) {
                cont.resume(TorrentServiceConnection.StartResult.FAILED)
                logger.error { "Failed to start service, context.startForegroundService returns null component info." }
            } else {
                logger.debug { "Service started, component name: $result" }
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (service == null) {
            logger.error { "Service is connected, but got null binder!" }
        }
        val binder = IRemoteAniTorrentEngine.Stub.asInterface(service)
        onServiceConnected(binder)
    }

    override fun onPause(owner: LifecycleOwner) {
        // app 到后台的时候注册监听
        if (!registered) {
            ContextCompat.registerReceiver(
                context,
                timeExceedLimitReceiver,
                timeExceedLimitIntentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            registered = true
        }
        try {
            // 请求 wake lock, 如果在 app 中息屏可以保证 service 正常跑 [acquireWakeLockIntent] 分钟.
            context.startService(acquireWakeLockIntent)
        } catch (ex: IllegalStateException) {
            // 大概率是 ServiceStartForegroundException, 服务已经终止了, 不需要再请求 wakelock.
            logger.warn(ex) { "Failed to acquire wake lock. Service has already died." }
        }
        super.onPause(owner)
    }

    override fun onResume(owner: LifecycleOwner) {
        // app 到前台的时候取消监听
        if (registered) {
            context.unregisterReceiver(timeExceedLimitReceiver)
            registered = false
        }
        super.onResume(owner)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        onServiceDisconnected()
    }
}