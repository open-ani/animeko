/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

class TorrentServiceConnection(
    private val context: Context,
    private val onRequiredRestartService: () -> ComponentName?,
): LifecycleEventObserver, ServiceConnection {
    private val logger = logger<TorrentServiceConnection>()

    private var binder: CompletableDeferred<IRemoteAniTorrentEngine> = CompletableDeferred()
    val connected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val lock = SynchronizedObject()
    private var lifecycleActive = false

    /**
     * [AniTorrentService] 启动完成时发送广播, 随后 app 应该绑定服务获取接口
     *
     * 首次启动 [AniTorrentService] 完成不在此绑定接口, 由 [onStateChanged] 中的 [Lifecycle.Event.ON_CREATE] 触发绑定.
     *
     * [onServiceDisconnected] 断开连接后将会注册此广播接收 [AniTorrentService] 启动完成事件.
     */
    private val restartBroadcast = ServiceRestartBroadcast { _, _ ->
        logger.debug { "AniTorrentService is restarted, rebinding." }
        bindService()
        unregisterStartupBroadcast()
    }
    
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                lifecycleActive = true
                bindService()
            }

            Lifecycle.Event.ON_DESTROY -> {
                lifecycleActive = false
                try {
                    context.unbindService(this)
                } catch (ex: IllegalArgumentException) {
                    logger.warn { "Failed to unregister AniTorrentService service." }
                }
            }

            else -> {}
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        logger.debug { "AniTorrentService is connected, name = $name" }
        if (service != null) {
            val result = IRemoteAniTorrentEngine.Stub.asInterface(service)

            synchronized(lock) {
                if (binder.isCompleted) {
                    binder = CompletableDeferred(result)
                } else {
                    binder.complete(result)
                }
            }
            connected.value = true
        } else {
            logger.error { "Failed to get binder of AniTorrentService." }
            connected.value = false
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        logger.debug { "AniTorrentService is disconnected, name = $name" }
        synchronized(lock) {
            binder = CompletableDeferred()
        }
        connected.value = false

        // app activity 还存在时必须重启 service
        if (lifecycleActive) {
            ContextCompat.registerReceiver(
                context,
                restartBroadcast,
                IntentFilter(AniTorrentService.INTENT_STARTUP),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            logger.debug { "AniTorrentService is disconnected while app is running, restarting." }
            onRequiredRestartService()
        }
    }

    private fun bindService(): Boolean {
        val bindResult = context.bindService(
            Intent(context, AniTorrentService::class.java), this, Context.BIND_ABOVE_CLIENT,
        )
        if (!bindResult) logger.error { "Failed to bind AniTorrentService." }
        return bindResult
    }

    private fun unregisterStartupBroadcast() {
        context.unregisterReceiver(restartBroadcast)
    }

    suspend fun awaitBinder(): IRemoteAniTorrentEngine {
        return binder.await()
    }
}