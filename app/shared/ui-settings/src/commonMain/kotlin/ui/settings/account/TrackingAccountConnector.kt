package me.him188.ani.app.ui.settings.account

import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_tracking_disconnect_message
import me.him188.ani.app.ui.settings.DetailPaneRoutes
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingProviderId
import org.jetbrains.compose.resources.StringResource

sealed interface TrackingAccountStatus {
    data class Account(val name: String) : TrackingAccountStatus
    data object Checking : TrackingAccountStatus
    data object Connecting : TrackingAccountStatus
    data object NotConnected : TrackingAccountStatus
    data object SignInToManage : TrackingAccountStatus
}

data class TrackingAccountViewState(
    val status: TrackingAccountStatus,
    val connected: Boolean,
    val enabled: Boolean = true,
    val refreshing: Boolean = false,
)

fun TrackingAccountState.toViewState(): TrackingAccountViewState = when (this) {
    is TrackingAccountState.LoggedIn -> TrackingAccountViewState(
        TrackingAccountStatus.Account(account.displayName), connected = true,
    )
    is TrackingAccountState.Refreshing -> TrackingAccountViewState(
        previousAccount?.let { TrackingAccountStatus.Account(it.displayName) } ?: TrackingAccountStatus.Connecting,
        connected = previousAccount != null,
        refreshing = true,
    )
    TrackingAccountState.LoggedOut -> TrackingAccountViewState(TrackingAccountStatus.NotConnected, connected = false)
}

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

    /** Explains what disconnecting removes, shown before the user confirms. */
    val disconnectMessage: StringResource
        get() = Lang.settings_tracking_disconnect_message

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
