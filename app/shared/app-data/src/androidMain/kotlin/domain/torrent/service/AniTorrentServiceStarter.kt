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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * @param onServiceDisconnected optional callback when service disconnected.
 */
class AniTorrentServiceStarter(
    private val context: Context,
    private val onRequiredRestartService: () -> ComponentName?,
    private val onServiceDisconnected: () -> Unit = { },
) : ServiceConnection, TorrentServiceStarter<IRemoteAniTorrentEngine> {
    private val logger = logger<AniTorrentServiceStarter>()

    private val startupIntentFilter = IntentFilter(AniTorrentService.INTENT_STARTUP)
    private val binderDeferred = MutableStateFlow<CompletableDeferred<IRemoteAniTorrentEngine>?>(null)

    override suspend fun start(): IRemoteAniTorrentEngine {
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    logger.debug { "[2/4] Received service startup result broadcast: $intent" }
                    context.unregisterReceiver(this)

                    val result = intent?.getBooleanExtra(AniTorrentService.INTENT_STARTUP_EXTRA, false) == true

                    if (!result) {
                        cont.resumeWithException(ServiceStartException.StartRespondFailure)
                    } else {
                        cont.resume(Unit)
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                startupIntentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            cont.invokeOnCancellation {
                context.unregisterReceiver(receiver)
            }

            val startResult = try {
                onRequiredRestartService()
            } catch (e: Exception) {
                context.unregisterReceiver(receiver)

                cont.resumeWithException(ServiceStartException.StartFailed(e))
                return@suspendCancellableCoroutine
            }

            if (startResult == null) {
                context.unregisterReceiver(receiver)
                cont.resumeWithException(ServiceStartException.StartFailed())
            } else {
                logger.debug { "[1/4] Started service, result: $startResult" }
            }
        }

        val currentDeferred = binderDeferred.value
        if (currentDeferred != null && currentDeferred.isCompleted) {
            currentDeferred.cancel()
        }
        val newDeferred = CompletableDeferred<IRemoteAniTorrentEngine>()
        binderDeferred.value = newDeferred

        val bindResult = context.bindService(
            Intent(context, AniTorrentService.actualServiceClass),
            this,
            Context.BIND_ABOVE_CLIENT,
        )
        if (!bindResult) throw ServiceStartException.BindServiceFailed
        logger.debug { "[3/4] Bound service successfully." }

        val result = newDeferred.await()
        logger.debug { "[4/4] Got service binder: $result" }
        return result
    }


    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (service == null) {
            binderDeferred.value?.completeExceptionally(ServiceStartException.NullBinder)
        }
        val result = IRemoteAniTorrentEngine.Stub.asInterface(service)
        binderDeferred.value?.complete(result)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        binderDeferred.value?.completeExceptionally(ServiceStartException.DisconnectedUnexpectedly)
        onServiceDisconnected()
    }
}