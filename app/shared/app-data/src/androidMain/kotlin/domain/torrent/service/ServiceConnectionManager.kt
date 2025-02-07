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
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext

/**
 * 管理与 [AniTorrentService] 的连接并获取 [IRemoteAniTorrentEngine] 远程访问接口.
 *
 * 服务连接控制依赖的 lifecycle 应当尽可能大, 所以应该使用
 * [ProcessLifecycleOwner][androidx.lifecycle.ProcessLifecycleOwner]
 * 或其他可以涵盖 app 全局生命周期的自定义 [LifecycleOwner] 来管理服务连接.
 * 不能使用 [Activity][android.app.Activity] (例如 [ComponentActivity][androidx.core.app.ComponentActivity])
 * 的生命周期, 因为在屏幕旋转 (例如竖屏转全屏播放) 的时候 Activity 可能会摧毁并重新创建,
 * 这会导致 [ServiceConnectionManager] 错误地重新绑定服务或重启服务.
 *
 * @see androidx.lifecycle.ProcessLifecycleOwner
 * @see ServiceConnection
 * @see AniTorrentService.onStartCommand
 * @see me.him188.ani.android.AniApplication
 */
@OptIn(DelicateCoroutinesApi::class)
class ServiceConnectionManager(
    context: Context,
    onRequiredRestartService: () -> ComponentName?,
    parentCoroutineContext: CoroutineContext = Dispatchers.Default,
    private val lifecycle: Lifecycle,
) {
    private val logger = logger<ServiceConnectionManager>()

    private val serviceStarter = AniTorrentServiceStarter(
        context = context,
        onRequiredRestartService = onRequiredRestartService,
        onServiceDisconnected = ::onServiceDisconnected,
    )
    private val _connection = LifecycleAwareTorrentServiceConnection(
        parentCoroutineContext = parentCoroutineContext,
        singleThreadDispatcher = newSingleThreadContext("AndroidTorrentServiceConnection"),
        lifecycle = lifecycle,
        serviceStarter,
    )

    val connection: TorrentServiceConnection<IRemoteAniTorrentEngine> get() = _connection

    private val serviceTimeLimitObserver = ForegroundServiceTimeLimitObserver(context) {
        logger.warn { "Service background time exceeded." }
        _connection.onServiceDisconnected()
    }

    fun startLifecycleLoop() {
        _connection.startLifecycleLoop()
        lifecycle.addObserver(serviceTimeLimitObserver)
    }

    private fun onServiceDisconnected() {
        _connection.onServiceDisconnected()
    }
}

/**
 * According to [documentation](https://developer.android.com/about/versions/15/behavior-changes-15#datasync-timeout):
 * in Android 15, foreground service with type `dataSync` or `mediaProcessing` is limited to run in background for 6 hours.
 *
 * This observer will listen to the time limit exceeded broadcast and update service connection state of app.
 */
private class ForegroundServiceTimeLimitObserver(
    private val context: Context,
    onServiceTimeLimitExceeded: () -> Unit
) : DefaultLifecycleObserver {
    private val logger = logger<ForegroundServiceTimeLimitObserver>()

    private var registered = false
    private val timeExceedLimitIntentFilter = IntentFilter(AniTorrentService.INTENT_BACKGROUND_TIMEOUT)
    private val timeExceedLimitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onServiceTimeLimitExceeded()
        }
    }

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