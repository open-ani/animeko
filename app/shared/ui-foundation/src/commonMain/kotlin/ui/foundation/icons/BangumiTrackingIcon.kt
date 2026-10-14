package me.him188.ani.app.ui.foundation.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.tracking.BangumiTrackingSource

class BangumiTrackingIcon : TrackingIconRenderer {
    override val providerId = BangumiTrackingSource.ID

    @Composable
    override fun Icon() {
        Image(Icons.Default.BangumiNext, null, Modifier.size(32.dp))
    }
}
