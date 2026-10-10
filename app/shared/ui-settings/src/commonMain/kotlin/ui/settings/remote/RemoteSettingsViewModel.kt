/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.domain.settings.remote.MediaSourceCommand
import me.him188.ani.app.domain.settings.remote.RemoteSettingsConnectionRequests
import me.him188.ani.app.domain.settings.remote.RemoteSettingsFailure
import me.him188.ani.app.domain.settings.remote.RemoteSettingsSession
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.remote_settings_invalid_qr
import me.him188.ani.app.ui.lang.remote_settings_restore_partial
import me.him188.ani.app.ui.settings.framework.AbstractSettingsViewModel
import me.him188.ani.remote.settings.RemoteSettingsLink
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.remote.settings.generated.models.LogSnapshot
import org.jetbrains.compose.resources.StringResource

/** Owns the connection and remote adapters for one RemoteSettings navigation entry. */
class RemoteSettingsViewModel(initialSession: RemoteSettingsSession? = null) :
    AbstractSettingsViewModel() {
    var remoteSession by mutableStateOf<RemoteSettingsSession?>(null)
        private set

    private var remoteState by mutableStateOf<RemoteSettingsFormState?>(null)
    private var remoteScope: CoroutineScope? = null
    private var connection: Job? = null
    private var connectionGeneration = 0
    var isConnecting by mutableStateOf(false)
        private set

    var pendingRemoteLink by mutableStateOf<RemoteSettingsLink?>(null)
        private set

    var message by mutableStateOf<StringResource?>(null)
    var isLoadingRemoteLog by mutableStateOf(false)
        private set

    var editingSubscription by mutableStateOf<MediaSourceSubscription?>(null)
        private set

    private var subscriptionRevision: String? = null
    val isConnected
        get() = remoteSession != null

    internal val form
        get() = remoteState

    init {
        initialSession?.let(::useRemoteSession)
        // 由外部链接创建的页面直接连接, 不经过扫码页.
        RemoteSettingsConnectionRequests.take()?.let(::requestConnection)
    }

    fun cancelConnection() {
        connectionGeneration++
        connection?.cancel()
        connection = null
        isConnecting = false
        pendingRemoteLink = null
    }

    fun scanConnection(uri: String) {
        try {
            requestConnection(RemoteSettingsLink.parse(uri))
        } catch (_: Exception) {
            message = Lang.remote_settings_invalid_qr
        }
    }

    fun requestConnection(link: RemoteSettingsLink) {
        cancelConnection()
        message = null
        pendingRemoteLink = link
    }

    suspend fun collectConnectionRequests() {
        RemoteSettingsConnectionRequests.requests.filterNotNull().collect {
            RemoteSettingsConnectionRequests.take()?.let(::requestConnection)
        }
    }

    fun connectAfterPermission() {
        val link = pendingRemoteLink ?: return
        pendingRemoteLink = null
        isConnecting = true
        val generation = connectionGeneration
        connection = backgroundScope.launch {
            try {
                val session =
                    RemoteSettingsSession.connect(
                        link,
                        currentAniBuildConfig.versionName,
                        backgroundScope,
                    )
                if (generation != connectionGeneration) session.close()
                else useRemoteSession(session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == connectionGeneration)
                    message = RemoteSettingsFailure.from(e).messageResource()
            } finally {
                if (generation == connectionGeneration) isConnecting = false
            }
        }
    }

    private fun useRemoteSession(session: RemoteSettingsSession) {
        disconnectRemote()
        val scope =
            backgroundScope.childScope(
                CoroutineExceptionHandler { _, error ->
                    if (remoteSession === session)
                        message = RemoteSettingsFailure.from(error).messageResource()
                }
            )
        remoteScope = scope
        remoteState = RemoteSettingsFormState(session, scope) {
            if (remoteSession === session) message = Lang.remote_settings_restore_partial
        }
        remoteSession = session
    }

    suspend fun refreshRemoteWhileVisible() {
        val session = remoteSession ?: return
        while (true) {
            delay(5_000)
            if (!session.busy.value) session.refresh()
        }
    }

    fun disconnectRemote() {
        remoteState?.sources?.edit?.cancelEdit()
        remoteSession?.close()
        remoteScope?.cancel()
        remoteScope = null
        remoteSession = null
        remoteState = null
        isLoadingRemoteLog = false
        editingSubscription = null
    }

    fun sourceEditor(instanceId: String) = remoteState?.sources?.editor(instanceId)

    fun fetchRemoteLog(onLoaded: suspend (LogSnapshot) -> Unit) {
        val session = remoteSession ?: return
        if (isLoadingRemoteLog) return
        isLoadingRemoteLog = true
        perform {
            try {
                onLoaded(session.log())
            } finally {
                if (remoteSession === session) isLoadingRemoteLog = false
            }
        }
    }

    fun editSubscription(subscription: MediaSourceSubscription) {
        subscriptionRevision = remoteSession?.snapshot?.value?.subscriptions?.revision
        editingSubscription = subscription
    }

    fun cancelSubscriptionEdit() {
        editingSubscription = null
    }

    fun saveSubscription(subscription: MediaSourceSubscription) {
        val session = remoteSession ?: return
        val revision = subscriptionRevision
        perform {
            session.mediaSource(MediaSourceCommand.SubscriptionEdit(subscription)) { revision }
            editingSubscription = null
        }
    }

    fun refreshSubscription(subscription: MediaSourceSubscription) {
        val session = remoteSession ?: return
        perform {
            session.mediaSource(MediaSourceCommand.SubscriptionRefresh(subscription.subscriptionId))
        }
    }

    fun toggleSubscription(subscription: MediaSourceSubscription) {
        val session = remoteSession ?: return
        perform {
            session.mediaSource(
                MediaSourceCommand.SubscriptionEdit(
                    subscription.copy(enabled = !subscription.enabled)
                )
            )
        }
    }

    private fun perform(action: suspend () -> Unit) {
        val target = remoteSession ?: return
        (remoteScope ?: return).launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (remoteSession === target)
                    message = RemoteSettingsFailure.from(e).messageResource()
            }
        }
    }

    override fun onCleared() {
        cancelConnection()
        disconnectRemote()
        super.onCleared()
    }
}
