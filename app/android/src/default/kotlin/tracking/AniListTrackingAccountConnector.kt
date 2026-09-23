package me.him188.ani.android.tracking

import kotlinx.coroutines.flow.map
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.ui.settings.account.TrackingAccountConnector
import me.him188.ani.app.ui.settings.account.TrackingAccountViewState
import me.him188.ani.app.ui.settings.account.TrackingLoginAction
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingProviderException

class AniListTrackingAccountConnector(private val provider: AniListTrackingProvider) : TrackingAccountConnector {
    override val providerId = AniListTrackingProvider.ID
    override val displayName = "AniList"
    override val loginAction = TrackingLoginAction.Browser(
        "https://anilist.co/api/v2/oauth/authorize?client_id=51393&response_type=token",
    )
    override val state = provider.accountState.map { account ->
        when (account) {
            is TrackingAccountState.LoggedIn -> TrackingAccountViewState(account.account.displayName, connected = true)
            is TrackingAccountState.Refreshing -> TrackingAccountViewState(
                account.previousAccount?.displayName ?: "Connecting", connected = account.previousAccount != null,
                refreshing = true,
            )
            TrackingAccountState.LoggedOut -> TrackingAccountViewState("Not connected", connected = false)
        }
    }

    override suspend fun refresh() {
        try {
            provider.refreshAccount()
        } catch (_: TrackingProviderException.Unauthorized) {
            // A disconnected account is already represented by the provider state.
        }
    }

    override suspend fun disconnect() {
        provider.logout()
    }
}
