package me.him188.ani.app.ui.settings.account

import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.settings.DetailPaneRoutes
import me.him188.ani.tracking.api.TrackingProviderId

data class TrackingAccountViewState(
    val description: String,
    val connected: Boolean,
    val enabled: Boolean = true,
    val refreshing: Boolean = false,
)

sealed interface TrackingLoginAction {
    data class Browser(val url: String) : TrackingLoginAction
    data class OAuth(val platform: OAuthPlatform) : TrackingLoginAction
}

/** The shared Settings row handles layout and disconnect confirmation. */
interface TrackingAccountConnector {
    val providerId: TrackingProviderId
    val displayName: String
    val state: Flow<TrackingAccountViewState>
    val loginAction: TrackingLoginAction
    val detailsRoute: DetailPaneRoutes?
        get() = null

    suspend fun refresh() {}
    suspend fun disconnect()
}

class TrackingAccountRegistry(connectors: List<TrackingAccountConnector>) {
    val connectors = connectors.also {
        require(it.map(TrackingAccountConnector::providerId).distinct().size == it.size) {
            "Duplicate tracking account provider ID"
        }
    }
}
