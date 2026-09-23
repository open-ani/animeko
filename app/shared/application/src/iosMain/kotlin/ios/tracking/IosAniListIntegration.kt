/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ios.tracking

import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.episode.EpisodeTrackingSync
import me.him188.ani.app.domain.tracking.TrackingEpisodeSynchronizer
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.ui.settings.account.TrackingAccountConnector
import me.him188.ani.app.ui.settings.account.TrackingAccountViewState
import me.him188.ani.app.ui.settings.account.TrackingLoginAction
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

class IosAniListAccountConnector(private val provider: AniListTrackingProvider) : TrackingAccountConnector {
    private var pendingLoginMark: TimeMark? = null
    override val providerId = AniListTrackingProvider.ID
    override val displayName = "AniList"
    override val loginAction: TrackingLoginAction
        get() {
            pendingLoginMark = TimeSource.Monotonic.markNow()
            return TrackingLoginAction.Browser(
                "https://anilist.co/api/v2/oauth/authorize?client_id=51393&response_type=token",
            )
        }
    override val state = provider.accountState.map { account ->
        when (account) {
            is TrackingAccountState.LoggedIn -> TrackingAccountViewState(account.account.displayName, connected = true)
            is TrackingAccountState.Refreshing -> TrackingAccountViewState(
                account.previousAccount?.displayName ?: "Connecting",
                connected = account.previousAccount != null,
                refreshing = true,
            )
            TrackingAccountState.LoggedOut -> TrackingAccountViewState("Not connected", connected = false)
        }
    }

    override suspend fun refresh() {
        try {
            provider.refreshAccount()
        } catch (_: TrackingProviderException.Unauthorized) {
            // The logged-out state is already observable.
        }
    }

    override suspend fun disconnect() = provider.logout()

    suspend fun completeRedirect(token: String) {
        val mark = pendingLoginMark ?: return
        pendingLoginMark = null
        if (mark.elapsedNow() > 5.minutes) return
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
