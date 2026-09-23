/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.tracking.anilist.createAniListHttpClient
import me.him188.ani.tracking.api.AndroidTrackingCredentialStore
import me.him188.ani.tracking.api.TrackingProvider
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.utils.ktor.getPlatformKtorEngine

@Composable
internal actual fun rememberAniListTrackingProvider(): TrackingProvider? {
    val context = LocalContext.current.applicationContext
    val client = remember { createAniListHttpClient(getPlatformKtorEngine()) }
    DisposableEffect(client) { onDispose { client.close() } }
    return remember(context, client) {
        AniListTrackingProvider(client, AndroidTrackingCredentialStore(context, TrackingProviderId("anilist")))
    }
}
