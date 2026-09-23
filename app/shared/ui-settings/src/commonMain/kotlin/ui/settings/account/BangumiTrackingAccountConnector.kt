package me.him188.ani.app.ui.settings.account

import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.data.tracking.BangumiTrackingSource
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_tracking_bangumi_disconnect_message
import me.him188.ani.app.ui.settings.DetailPaneRoutes
import me.him188.ani.app.ui.user.SelfInfoStateProducer

class BangumiTrackingAccountConnector(private val userRepository: UserRepository) : TrackingAccountConnector {
    override val providerId = BangumiTrackingSource.ID
    override val displayName = "Bangumi"
    override val loginAction = TrackingLoginAction.OAuth(OAuthPlatform.BANGUMI)
    override val detailsRoute = DetailPaneRoutes.BangumiSync
    override val disconnectMessage = Lang.settings_tracking_bangumi_disconnect_message
    override val state = SelfInfoStateProducer().flow.map { info ->
        val name = info.selfInfo?.bangumiUsername?.takeIf { it.isNotBlank() }
        TrackingAccountViewState(
            status = when {
                name != null -> TrackingAccountStatus.Account(name)
                info.isSessionValid == false -> TrackingAccountStatus.SignInWithProvider
                else -> TrackingAccountStatus.NotConnected
            },
            connected = name != null,
            refreshing = info.isLoading,
        )
    }

    /** Unlinks Bangumi from the Animeko account on the server; the Animeko session stays signed in. */
    override suspend fun disconnect() {
        userRepository.unbindBangumi()
    }
}
