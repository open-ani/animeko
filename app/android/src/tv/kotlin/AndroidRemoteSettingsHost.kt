/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.tv

import android.content.Context
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.domain.settings.remote.LocalNetworkPermission
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHost
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostState
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostStatus
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.remote.settings.RemoteSettingsLink
import me.him188.ani.remote.settings.RemoteSettingsProtocol
import me.him188.ani.remote.settings.generated.models.LogSnapshot
import me.him188.ani.tv.remotesettings.HmacSettingsRevision
import me.him188.ani.tv.remotesettings.LocalRemoteSettingsBackend
import me.him188.ani.tv.remotesettings.RemoteSettingsServer
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin

/**
 * 电视进程内的远程设置服务, 由 Application 持有, 与界面生命周期无关. 界面只负责申请权限和展示 [state].
 *
 * [start] 后先等待局域网权限, 再创建并启动服务; 之后随局域网地址与登录用户的变化更新二维码对应的 [RemoteSettingsLink].
 */
class AndroidRemoteSettingsHost(
    private val context: Context,
    private val createServer: () -> RemoteSettingsServer,
    private val appVersion: String,
    private val userId: Flow<String?>,
) : RemoteSettingsHost {
    private val mutableState = MutableStateFlow(RemoteSettingsHostState())
    override val state: StateFlow<RemoteSettingsHostState> = mutableState.asStateFlow()

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        awaitLocalNetworkPermission()
        createServer().use { server ->
            val port = startServer(server)
            combine(lanAddresses(context), userId) { address, user ->
                if (address == null) {
                    RemoteSettingsHostState(status = RemoteSettingsHostStatus.NO_NETWORK)
                } else {
                    RemoteSettingsHostState(
                        RemoteSettingsLink(address, port, server.accessKey, appVersion, user.orEmpty()),
                    )
                }
            }.retryWhen { cause, _ ->
                logger.warn(cause) { "Failed to update remote settings link" }
                mutableState.value = RemoteSettingsHostState(status = RemoteSettingsHostStatus.UNAVAILABLE)
                delay(RETRY_INTERVAL)
                true
            }.collect { mutableState.value = it }
        }
    }

    /** 权限没有变化通知. 撤销权限会结束进程, 因此只需等到授予. */
    private suspend fun awaitLocalNetworkPermission() {
        while (!LocalNetworkPermission.isGranted(context)) {
            mutableState.value = RemoteSettingsHostState(status = RemoteSettingsHostStatus.PERMISSION_REQUIRED)
            delay(PERMISSION_CHECK_INTERVAL)
        }
    }

    private suspend fun startServer(server: RemoteSettingsServer): Int {
        while (true) {
            try {
                return server.start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to start remote settings server" }
                mutableState.value = RemoteSettingsHostState(status = RemoteSettingsHostStatus.UNAVAILABLE)
                delay(RETRY_INTERVAL)
            }
        }
    }

    companion object {
        private val logger = logger<AndroidRemoteSettingsHost>()
        private val PERMISSION_CHECK_INTERVAL = 2.seconds
        private val RETRY_INTERVAL = 10.seconds

        /** 电视本机的设置、数据源与订阅都经由 [koin] 中的仓库读写, 与电视界面共用同一份存储. */
        fun create(context: Context, koin: Koin, scope: CoroutineScope): AndroidRemoteSettingsHost {
            val application = context.applicationContext
            val stores = application.dataStores
            val appVersion = currentAniBuildConfig.versionName
            val userId = stores.selfInfoStore.data.map { it?.id?.toString() }.distinctUntilChanged()
            val logFile = application.filesDir.resolve("logs/app.log")
            val createServer = {
                val backend = LocalRemoteSettingsBackend(
                    settings = koin.get(),
                    stores = stores,
                    sources = koin.get(),
                    codecs = koin.get(),
                    updater = koin.get(),
                    revisions = HmacSettingsRevision(),
                    readLog = { withContext(Dispatchers.IO) { readLogTail(logFile) } },
                )
                RemoteSettingsServer(
                    scope,
                    backend,
                    appVersion,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    userUuid = { userId.first() },
                )
            }
            return AndroidRemoteSettingsHost(application, createServer, appVersion, userId)
        }
    }
}

/** 读取日志末尾最多 [RemoteSettingsProtocol.MAX_LOG_BYTES] 字节. */
private fun readLogTail(file: File): LogSnapshot {
    val name = "tv-app.log"
    if (!file.isFile) return LogSnapshot(name, "", false)
    return RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        val count = minOf(length, RemoteSettingsProtocol.MAX_LOG_BYTES.toLong()).toInt()
        input.seek(length - count)
        val bytes = ByteArray(count)
        input.readFully(bytes)
        LogSnapshot(name, bytes.decodeToString(), length > count)
    }
}
