/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.tracking.anilist.createAniListHttpClient
import me.him188.ani.app.ui.settings.account.AniListAccountChanges
import me.him188.ani.tracking.api.AndroidTrackingCredentialStore
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.utils.ktor.getPlatformKtorEngine
import me.him188.ani.android.BuildConfig
import me.him188.ani.app.data.repository.user.QrLoginRepository
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.AniComponentActivity
import me.him188.ani.app.platform.rememberPlatformWindow
import me.him188.ani.app.ui.exprovider.ExternalContentProviderFactory
import me.him188.ani.app.ui.exprovider.LocalExternalContentProvider
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.theme.SystemBarColorEffect
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.android.inject

class MainActivity : AniComponentActivity() {
    private val logger = logger<MainActivity>()
    private val aniNavigator = AniNavigator()

    private val externalContentProviderFactory: ExternalContentProviderFactory by inject()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleStartIntent(intent)
    }

    private fun handleStartIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "ani") return
        when (data.host) {
            "anilist-auth" -> {
                val token = data.encodedFragment?.let {
                    Uri.parse("https://localhost.invalid/?$it").getQueryParameter("access_token")
                }?.takeIf(String::isNotBlank) ?: return
                intent.data = null
                lifecycleScope.launch {
                    val client = createAniListHttpClient(getPlatformKtorEngine())
                    try {
                        val provider = AniListTrackingProvider(
                            client,
                            AndroidTrackingCredentialStore(applicationContext, TrackingProviderId("anilist")),
                        )
                        provider.login(TrackingLoginCredentials(secret = token))
                        AniListAccountChanges.notifyConnected()
                        Toast.makeText(this@MainActivity, "AniList connected", Toast.LENGTH_SHORT).show()
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "AniList connection failed", Toast.LENGTH_SHORT).show()
                    } finally {
                        client.close()
                    }
                }
            }

            "subjects" -> {
                val id = data.pathSegments.getOrNull(0)?.toIntOrNull() ?: return
                navigateWhenReady("subject details") { navigateSubjectDetails(id, placeholder = null) }
            }

            "qr-login" -> {
                val requestId = QrLoginRepository.parseRequestId(data.toString()) ?: return
                navigateWhenReady("QR login confirm") { navigateQrLoginConfirm(requestId) }
            }
        }
    }

    private fun navigateWhenReady(destination: String, action: AniNavigator.() -> Unit) {
        lifecycleScope.launch {
            try {
                if (!aniNavigator.isBackStackReady()) {
                    aniNavigator.awaitBackStack()
                    delay(1000) // 等待初始化好, 否则跳转可能无效
                }
                aniNavigator.action()
            } catch (e: Exception) {
                logger.error(e) { "Failed to navigate to $destination" }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStartIntent(intent)

        enableEdgeToEdge(
            // 透明状态栏
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            // 透明导航栏
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        // 允许画到 system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val toaster = object : Toaster {
            override fun toast(text: String) {
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
            }
        }

        val externalContentProvider = externalContentProviderFactory.create(this, lifecycleScope)

        setContent {
            AniApp {
                val externalComponentProviderUpdated by rememberUpdatedState(externalContentProvider)

                SystemBarColorEffect()

                CompositionLocalProvider(
                    LocalToaster provides toaster,
                    LocalPlatformWindow provides rememberPlatformWindow(this),
                    LocalExternalContentProvider provides externalComponentProviderUpdated,
                ) {
                    // Expose Modifier.testTag as resource-id in accessibility/uiautomator dumps,
                    // so UI-automation agents can locate elements by stable ids (debug only).
                    @OptIn(ExperimentalComposeUiApi::class)
                    val rootModifier = if (BuildConfig.DEBUG) {
                        Modifier.semantics { testTagsAsResourceId = true }
                    } else {
                        Modifier
                    }
                    Box(rootModifier) {
                        AniAppContent(aniNavigator)
                    }
                }
            }
        }
    }
}
