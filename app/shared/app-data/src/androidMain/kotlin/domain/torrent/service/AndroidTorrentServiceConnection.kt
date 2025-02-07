/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.app.domain.torrent.LifecycleAwareTorrentServiceConnection
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes

/**
 * 管理与 [AniTorrentService] 的连接并获取 [IRemoteAniTorrentEngine] 远程访问接口.
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
@OptIn(DelicateCoroutinesApi::class)
class AndroidTorrentServiceConnection(
    private val context: Context,
    private val onRequiredRestartService: () -> ComponentName?,
    parentCoroutineContext: CoroutineContext = Dispatchers.Default,
) : ServiceConnection, LifecycleAwareTorrentServiceConnection<IRemoteAniTorrentEngine>(
    parentCoroutineContext = parentCoroutineContext,
    singleThreadDispatcher = newSingleThreadContext("AndroidTorrentServiceConnection"),
) {
    private val logger = logger<AndroidTorrentServiceConnection>()

    private val startupIntentFilter = IntentFilter(AniTorrentService.INTENT_STARTUP)
    private val binderDeferred = MutableStateFlow(CompletableDeferred<IRemoteAniTorrentEngine?>())

    private val acquireWakeLockIntent = Intent(context, AniTorrentService.actualServiceClass).apply {
        putExtra("acquireWakeLock", 1.minutes.inWholeMilliseconds)
    }
    private var registered = false
    private val timeExceedLimitIntentFilter = IntentFilter(AniTorrentService.INTENT_BACKGROUND_TIMEOUT)
    private val timeExceedLimitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logger.warn { "Service background time exceeded." }

        }
    }

    override suspend fun startService(): IRemoteAniTorrentEngine? {
        val startResult = suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    logger.debug { "Received service startup broadcast: $intent, starting bind service." }
                    context.unregisterReceiver(this)

                    val result = intent?.getBooleanExtra("success", false) == true
                    if (!result) {
                        logger.error { "Failed to start service, service responded start result with false." }
                    }

                    cont.resume(result)
                    return
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
                logger.error { "Failed to start service, context.startForegroundService returns null component info." }
                context.unregisterReceiver(receiver)
                cont.resume(false)
            } else {
                logger.debug { "Service started, component name: $result" }
            }
        }
        if (!startResult) {
            return null
        }

        val currentDeferred = binderDeferred.value
        if (!currentDeferred.isCompleted) {
            currentDeferred.cancel()
        }
        val newDeferred = CompletableDeferred<IRemoteAniTorrentEngine?>()
        binderDeferred.value = newDeferred

        val bindResult = context.bindService(
            Intent(context, AniTorrentService.actualServiceClass),
            this@AndroidTorrentServiceConnection,
            Context.BIND_ABOVE_CLIENT,
        )
        if (!bindResult) return null

        return try {
            newDeferred.await()
        } catch (ex: CancellationException) {
            // onServiceDisconnected will cancel the deferred
            null
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (service == null) {
            logger.error { "Service is connected, but got null binder!" }
        }
        binderDeferred.value.complete(IRemoteAniTorrentEngine.Stub.asInterface(service))
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        binderDeferred.value.cancel(CancellationException("Service disconnected."))
    }

    @RequiresApi(31)
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)

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
        } catch (ex: ForegroundServiceStartNotAllowedException) {
            // 大概率是 ServiceStartForegroundException, 服务已经终止了, 不需要再请求 wakelock.
            logger.warn(ex) { "Failed to acquire wake lock. Service has already died." }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)

        // app 到前台的时候取消监听
        if (registered) {
            context.unregisterReceiver(timeExceedLimitReceiver)
            registered = false
        }
    }
}