/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.torrent.peer

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.repository.torrent.peer.PeerFilterSubscriptionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.torrent.peer.PeerFilterSubscription
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class PeerFilterSettingsViewModel : AbstractViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val subscriptionRepo: PeerFilterSubscriptionRepository by inject()

    private val peerFilterConfig = settingsRepository.torrentPeerConfig.flow
    private val updateTasker = MonoTasker(backgroundScope)
    private val localConfig: MutableState<TorrentPeerConfig?> = mutableStateOf(null)

    private val builtInSubPresentation = subscriptionRepo.presentationFlow
        .map { list -> list.filter { it.subscriptionId == PeerFilterSubscription.BUILTIN_SUBSCRIPTION_ID } }
        .stateInBackground(initialValue = emptyList())

    val state = PeerFilterSettingsState(
        subscriptions = builtInSubPresentation,
        storage = SaveableStorage(
            localConfig,
            onSave = { update(it) },
            isSavingFlow = updateTasker.isRunning,
        ),
        backgroundScope,
        updateSubscriptions = { subscriptionRepo.updateOrLoadAll() },
        toggleSubscription = { id, enable ->
            launchInBackground {
                if (enable) subscriptionRepo.enable(id) else subscriptionRepo.disable(id)
            }
        },
    )

    init {
        launchInBackground {
            peerFilterConfig.distinctUntilChanged().collectLatest { config ->
                withContext(Dispatchers.Main) {
                    localConfig.value = config
                }
            }
        }
    }

    private fun update(new: TorrentPeerConfig) {
        updateTasker.launch {
            localConfig.value = new
            delay(500)
            settingsRepository.torrentPeerConfig.update { new }
        }
    }
}