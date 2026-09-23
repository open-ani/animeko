package me.him188.ani.app.ui.foundation.icons

import androidx.compose.runtime.Composable
import me.him188.ani.tracking.api.TrackingProviderId

/** A provider contributes its own brand icon to every shared tracking surface. */
interface TrackingIconRenderer {
    val providerId: TrackingProviderId

    @Composable
    fun Icon()
}

class TrackingIconRegistry(renderers: List<TrackingIconRenderer>) {
    private val byProvider = renderers.associateBy { it.providerId }.also {
        require(it.size == renderers.size) { "Duplicate tracking icon provider ID" }
    }

    fun find(providerId: TrackingProviderId): TrackingIconRenderer? = byProvider[providerId]
}
