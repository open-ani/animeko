package me.him188.ani.android.activity

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.ui.settings.account.AniListAccountChanges
import me.him188.ani.tracking.api.TrackingLoginCredentials

internal class AniListAuthRedirectHandler(private val provider: AniListTrackingProvider) {
    fun handle(intent: Intent, scope: CoroutineScope, onResult: (Boolean) -> Unit): Boolean {
        val token = intent.data?.encodedFragment?.let {
            Uri.parse("https://localhost.invalid/?$it").getQueryParameter("access_token")
        }?.takeIf(String::isNotBlank) ?: return false

        intent.data = null
        scope.launch {
            val success = try {
                provider.login(TrackingLoginCredentials(secret = token))
                AniListAccountChanges.notifyConnected()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            onResult(success)
        }
        return true
    }
}
