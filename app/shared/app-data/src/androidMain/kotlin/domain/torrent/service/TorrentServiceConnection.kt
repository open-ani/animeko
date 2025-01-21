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
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.utils.logging.*
import kotlin.time.Duration.Companion.minutes

/**
 * 管理与 [AniTorrentService] 的连接并获取 [IRemoteAniTorrentEngine] 远程访问接口.
 * 通过 [awaitBinder] 获取服务接口, 再启动完成并绑定之前将挂起协程.
 *
 * 连接管理由传入的 [lifecycle] 机制控制. 在 [ON_CREATE][Lifecycle.Event.ON_CREATE]
 * 和 [ON_DESTROY][Lifecycle.Event.ON_DESTROY] 范围控制服务的绑定与解绑.
 * 在 [ON_RESUME][Lifecycle.Event.ON_RESUME] 和 [ON_STOP][Lifecycle.Event.ON_STOP]
 * 范围控制服务始终保持运行.
 *
 * 服务连接控制依赖的 lifecycle 应当尽可能大, 所以应该使用
 * [ProcessLifecycleOwner][androidx.lifecycle.ProcessLifecycleOwner]
 * 或其他可以涵盖 app 全局生命周期的自定义 [LifecycleOwner] 来管理服务连接.
 * 不能使用 [Activity][android.app.Activity] (例如 [ComponentActivity][androidx.core.app.ComponentActivity])
 * 的生命周期, 因为在屏幕旋转 (例如竖屏转全屏播放) 的时候 Activity 可能会摧毁并重新创建,
 * 这会导致 [TorrentServiceConnection] 错误地重新绑定服务或重启服务.
 *
 * ## 管理连接的逻辑
 *
 * 需要调用 [startLifecycleJob] 来启动生命周期监听:
 *
 * * App 正在运行时 (生命周期在 [ON_RESUME][Lifecycle.Event.ON_RESUME] 至 [ON_STOP][Lifecycle.Event.ON_STOP] 期间)
 *   如果 [服务断开][onServiceConnected], [TorrentServiceConnection] 会尝试重启服务并监听
 *   [启动完成的广播][AniTorrentService.INTENT_STARTUP]. [AniTorrentService] 将在启动完成后发送此广播来触发绑定 Binder
 *   并取消监听启动完成的广播.
 *
 * * App 在后台时 (生命周期在 [ON_STOP][Lifecycle.Event.ON_STOP] 至 [ON_DESTROY][Lifecycle.Event.ON_DESTROY] 期间)
 *   如果 [服务断开][onServiceConnected], [TorrentServiceConnection] 不会尝试重启服务.
 *   但在下一次进入 [ON_RESUME][Lifecycle.Event.ON_RESUME] 时会重启, 步骤和上面相同.
 *
 * 上方的三条逻辑保证了 app 在 [ON_RESUME][Lifecycle.Event.ON_RESUME] 至 [ON_STOP][Lifecycle.Event.ON_STOP] 期间服务一定存活.
 * 所以前面建议应该使用 [ProcessLifecycleOwner][androidx.lifecycle.ProcessLifecycleOwner] 管理连接.
 *
 * @see androidx.lifecycle.ProcessLifecycleOwner
 * @see ServiceConnection
 * @see AniTorrentService.onStartCommand
 * @see me.him188.ani.android.AniApplication
 */
class TorrentServiceConnection(
    private val context: Context,
    private val onRequiredRestartService: () -> ComponentName?,
    private val backgroundScope: CoroutineScope,
    private val lifecycle: Lifecycle,
) : ServiceConnection, BroadcastReceiver() {
    private val logger = logger<TorrentServiceConnection>()

    private var binder: CompletableDeferred<IRemoteAniTorrentEngine> = CompletableDeferred()
    val connected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val lock = SynchronizedObject()

    /**
     * service 断开连接后是否需要立即重启,
     */
    private var shouldRestartServiceImmediately = false
    private val restartServiceIntentFilter = IntentFilter(AniTorrentService.INTENT_STARTUP)

    /**
     * 在 [AniApplication][me.him188.ani.android.AniApplication] 启动时设置, 该方法设定了服务是否正常随着 app 的启动而启动.
     *
     * 因为 app 可能会在后台启动 (例如息屏的时候使用 adb am 指令), 此时启动前台服务会抛出 [ForegroundServiceStartNotAllowedException].
     * 如果出现了这种情况, 需要延迟启动服务到 Activity 出现时, 也就是 [ON_START][Lifecycle.Event.ON_START].
     *
     * @see setStartServiceResultWhileAppStartup
     */
    var startServiceResultWhileAppStartup = false

    private val acquireWakeLockIntent by lazy {
        Intent(context, anitorrentServiceClass).apply {
            putExtra("acquireWakeLock", 1.minutes.inWholeMilliseconds)
        }
    }

    private var startLifecycleJobCalled = false

    /**
     * 启动 [Lifecycle] 监听, 在 [Lifecycle.State.RESUMED] 时自动 [restartService]
     */
    fun startLifecycleJob() {
        if (startLifecycleJobCalled) return
        startLifecycleJobCalled = true
        lifecycle.addObserver(
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    this@TorrentServiceConnection.onStateChanged(event)
                }
            },
        )

        backgroundScope.launch(CoroutineName("AniTorrentService auto restart")) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                shouldRestartServiceImmediately = true

                // auto restart if not connected
                if (!connected.value) {
                    logger.debug {
                        "AniTorrentService is not started or stopped while app is switching to foreground, restarting."
                    }
                    while (!connected.value) {
                        restartService()
                        delay(1000)
                        if (connected.value) {
                            break
                        } else {
                            logger.warn { "AniTorrentService is not started while application is ON_RESUME, retrying." }
                        }
                    }
                }

                try {
                    awaitCancellation()
                } finally {
                    // so shouldRestartServiceImmediately is true iff state is RESUMED
                    shouldRestartServiceImmediately = false
                }
            }
        }
    }

    private fun onStateChanged(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                if (startServiceResultWhileAppStartup) {
                    bindService()
                    // 服务启动成功了，一定可以连接
                    // 因为 onServiceConnected 如果在 Lifecycle.Event.ON_START 之后调用, 会尝试重新连接
                    connected.value = true
                }
            }

            Lifecycle.Event.ON_STOP -> {
                try {
                    // 请求 wake lock, 如果在 app 中息屏可以保证 service 正常跑 [acquireWakeLockIntent] 分钟.
                    context.startService(acquireWakeLockIntent)
                } catch (ex: IllegalStateException) {
                    // 大概率是 ServiceStartForegroundException, 服务已经终止了, 不需要再请求 wakelock.
                    logger.warn(ex) { "Failed to acquire wake lock. Service has already died." }
                }
            }

            Lifecycle.Event.ON_DESTROY -> {
                try {
                    context.unbindService(this)
                    connected.value = false
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
            binder.cancel()
            binder = CompletableDeferred()
        }
        connected.value = false

        // app activity 还存在时必须重启 service
        if (shouldRestartServiceImmediately) {
            logger.debug { "AniTorrentService is disconnected while app is running, restarting." }
            restartService()
        }
    }

    /**
     * [AniTorrentService] 启动完成时发送广播, 随后 app 应该绑定服务获取接口
     *
     * 首次启动 [AniTorrentService] 完成不在此绑定接口, 由 [onStateChanged] 中的 [Lifecycle.Event.ON_CREATE] 触发绑定.
     *
     * [onServiceDisconnected] 断开连接后将会注册此广播接收 [AniTorrentService] 启动完成事件.
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        logger.debug { "AniTorrentService is restarted, rebinding." }
        bindService()
    }

    private val registerReceiver by lazy {
        ContextCompat.registerReceiver(
            context,
            this,
            restartServiceIntentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        Unit
    }

    private fun restartService() {
        registerReceiver // init lazy
        onRequiredRestartService()
    }

    private fun bindService(): Boolean {
        val bindResult = context.bindService(
            Intent(context, anitorrentServiceClass), this, Context.BIND_ABOVE_CLIENT,
        )
        if (!bindResult) logger.error { "Failed to bind AniTorrentService." }
        return bindResult
    }

    suspend fun awaitBinder(): IRemoteAniTorrentEngine {
        return binder.await()
    }

    companion object {
        val anitorrentServiceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            AniTorrentServiceApi34::class.java
        } else {
            AniTorrentServiceApiDefault::class.java
        }
    }
}