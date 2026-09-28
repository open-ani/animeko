package me.him188.ani.app.ui.settings.account

import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.tracking.BangumiTrackingSource
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.foundation.tracking.TrackingAccountConnector
import me.him188.ani.app.ui.foundation.tracking.TrackingAccountStatus
import me.him188.ani.app.ui.foundation.tracking.TrackingAccountViewState
import me.him188.ani.app.ui.foundation.tracking.TrackingLoginAction
import me.him188.ani.app.ui.settings.DetailPaneRoutes
import me.him188.ani.app.ui.user.SelfInfoStateProducer

/** Shows and links the Animeko account's Bangumi account; unlinking stays in the profile. */
class BangumiTrackingAccountConnector : TrackingAccountConnector, TrackingAccountDetails {
    override val providerId = BangumiTrackingSource.ID
    override val displayName = "Bangumi"
    override val loginAction = TrackingLoginAction.OAuth(OAuthPlatform.BANGUMI)
    override val detailsRoute = DetailPaneRoutes.BangumiSync
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
}
