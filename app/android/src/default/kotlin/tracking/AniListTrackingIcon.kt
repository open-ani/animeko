package me.him188.ani.android.tracking

import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.foundation.icons.TrackingIconRenderer
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider

class AniListTrackingIcon : TrackingIconRenderer {
    override val providerId = AniListTrackingProvider.ID

    @Composable
    override fun Icon() {
        AniListIcon()
    }
}
