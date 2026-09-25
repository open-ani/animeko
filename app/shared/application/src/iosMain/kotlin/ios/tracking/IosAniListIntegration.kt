/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ios.tracking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.episode.EpisodeTrackingSync
import me.him188.ani.app.domain.tracking.TrackingEpisodeSynchronizer
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.ui.foundation.tracking.DisconnectableTrackingAccount
import me.him188.ani.app.ui.foundation.tracking.TrackingAccountConnector
import me.him188.ani.app.ui.foundation.tracking.TrackingLoginAction
import me.him188.ani.app.ui.foundation.tracking.toViewState
import me.him188.ani.tracking.api.PendingLoginGate
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

class IosAniListAccountConnector(private val provider: AniListTrackingProvider) : TrackingAccountConnector, DisconnectableTrackingAccount {
    private val pendingLogin = PendingLoginGate()
    override val providerId = AniListTrackingProvider.ID
    override val displayName = "AniList"
    override val loginAction: TrackingLoginAction
        get() {
            pendingLogin.begin()
            return TrackingLoginAction.Browser(AniListTrackingProvider.AUTHORIZE_URL)
        }
    override val state = provider.accountState.map { it.toViewState() }

    override suspend fun refresh() {
        try {
            provider.refreshAccount()
        } catch (_: TrackingProviderException.Unauthorized) {
            // The logged-out state is already observable.
        }
    }

    override suspend fun disconnect() = provider.logout()

    suspend fun completeRedirect(token: String) {
        if (!pendingLogin.consume()) return
        provider.login(TrackingLoginCredentials(secret = token))
    }
}

fun aniListTokenFromRedirect(urlString: String): String? {
    if (!urlString.startsWith("ani://anilist-auth")) return null
    val fragmentIndex = urlString.indexOf('#')
    if (fragmentIndex == -1) return null
    val fragment = urlString.substring(fragmentIndex + 1)
    for (part in fragment.split('&')) {
        val name = part.substringBefore('=')
        if (name == "access_token") {
            val value = part.substringAfter('=')
            if (value.isNotBlank()) return value
        }
    }
    return null
}

class IosRegistryEpisodeTrackingSync(
    private val synchronizer: TrackingEpisodeSynchronizer,
    private val scope: CoroutineScope,
) : EpisodeTrackingSync {
    override fun onEpisodeWatched(subjectId: Int, episodeId: Int) {
        scope.launch(Dispatchers.Default) {
            try {
                synchronizer.episodeWatched(subjectId, episodeId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger<IosRegistryEpisodeTrackingSync>().warn {
                    "Progress sync failed: ${failure::class.simpleName}"
                }
            }
        }
    }
}
