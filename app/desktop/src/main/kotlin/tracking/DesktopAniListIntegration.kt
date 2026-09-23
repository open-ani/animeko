package me.him188.ani.app.desktop.tracking

import java.awt.Desktop
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.episode.EpisodeTrackingSync
import me.him188.ani.app.domain.tracking.TrackingEpisodeSynchronizer
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.ui.settings.account.TrackingAccountConnector
import me.him188.ani.app.ui.settings.account.TrackingAccountViewState
import me.him188.ani.app.ui.settings.account.TrackingLoginAction
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

class DesktopAniListAccountConnector(private val provider: AniListTrackingProvider) : TrackingAccountConnector {
    private val pendingLoginAt = AtomicLong(0)
    override val providerId = AniListTrackingProvider.ID
    override val displayName = "AniList"
    override val loginAction: TrackingLoginAction
        get() {
            pendingLoginAt.set(System.nanoTime())
            return TrackingLoginAction.Browser(
                "https://anilist.co/api/v2/oauth/authorize?client_id=51393&response_type=token",
            )
        }
    override val state = provider.accountState.map { account ->
        when (account) {
            is TrackingAccountState.LoggedIn -> TrackingAccountViewState(account.account.displayName, connected = true)
            is TrackingAccountState.Refreshing -> TrackingAccountViewState(
                account.previousAccount?.displayName ?: "Connecting", connected = account.previousAccount != null,
                refreshing = true,
            )
            TrackingAccountState.LoggedOut -> TrackingAccountViewState("Not connected", connected = false)
        }
    }

    override suspend fun refresh() {
        try {
            provider.refreshAccount()
        } catch (_: TrackingProviderException.Unauthorized) {
            // The logged-out state is already observable.
        }
    }

    override suspend fun disconnect() = provider.logout()

    internal suspend fun completeRedirect(token: String) {
        val started = pendingLoginAt.getAndSet(0)
        if (started == 0L || System.nanoTime() - started > 5 * 60 * 1_000_000_000L) return
        provider.login(TrackingLoginCredentials(secret = token))
    }
}

fun installAniListOpenUriHandler(connector: DesktopAniListAccountConnector, scope: CoroutineScope) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.APP_OPEN_URI)) return
    desktop.setOpenURIHandler { event ->
        val token = aniListTokenFromRedirect(event.uri) ?: return@setOpenURIHandler
        scope.launch(Dispatchers.IO) {
            try {
                connector.completeRedirect(token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger<DesktopAniListAccountConnector>().warn {
                    "AniList sign-in failed: ${failure.javaClass.simpleName}"
                }
            }
        }
    }
}

internal fun aniListTokenFromRedirect(uri: URI): String? {
    if (uri.scheme != "ani" || uri.host != "anilist-auth") return null
    val encoded = uri.rawFragment ?: return null
    return encoded.split('&').firstNotNullOfOrNull { field ->
        val name = field.substringBefore('=', "")
        if (name != "access_token") return@firstNotNullOfOrNull null
        runCatching { URLDecoder.decode(field.substringAfter('=', ""), Charsets.UTF_8) }
            .getOrNull()?.takeIf(String::isNotBlank)
    }
}

class DesktopRegistryEpisodeTrackingSync(
    private val synchronizer: TrackingEpisodeSynchronizer,
    private val scope: CoroutineScope,
) : EpisodeTrackingSync {
    override fun onEpisodeWatched(subjectId: Int, episodeId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                synchronizer.episodeWatched(subjectId, episodeId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger<DesktopRegistryEpisodeTrackingSync>().warn {
                    "Progress sync failed: ${failure.javaClass.simpleName}"
                }
            }
        }
    }
}
