package me.him188.ani.app.ui.settings.account

import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.data.tracking.BangumiTrackingSource
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.settings.DetailPaneRoutes
import me.him188.ani.app.ui.user.SelfInfoStateProducer

class BangumiTrackingAccountConnector(private val userRepository: UserRepository) : TrackingAccountConnector {
    override val providerId = BangumiTrackingSource.ID
    override val displayName = "Bangumi"
    override val loginAction = TrackingLoginAction.OAuth(OAuthPlatform.BANGUMI)
    override val detailsRoute = DetailPaneRoutes.BangumiSync
    override val state = SelfInfoStateProducer().flow.map { info ->
        val name = info.selfInfo?.bangumiUsername
        TrackingAccountViewState(
            description = name ?: if (info.isSessionValid == true) "Not connected" else "Sign in to Animeko to manage",
            connected = !name.isNullOrBlank(),
            enabled = info.isSessionValid == true,
            refreshing = info.isLoading,
        )
    }

    override suspend fun disconnect() {
        userRepository.unbindBangumi()
    }
}
