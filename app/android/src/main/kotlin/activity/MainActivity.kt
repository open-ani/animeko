/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.configIfEnabledOrNull
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.platform.AppStartupTasks
import me.him188.ani.app.platform.MeteredNetworkDetector
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.platform.notification.AndroidNotifManager
import me.him188.ani.app.platform.notification.AndroidNotifManager.Companion.EXTRA_REQUEST_CODE
import me.him188.ani.app.platform.notification.NotifManager
import me.him188.ani.app.ui.foundation.LocalImageLoader
import me.him188.ani.app.ui.foundation.getDefaultImageLoader
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.android.inject
import org.koin.mp.KoinPlatform
import org.koin.mp.KoinPlatformTools

class MainActivity : AniComponentActivity() {
    private val sessionManager: SessionManager by inject()
    private val meteredNetworkDetector: MeteredNetworkDetector by inject()
    
    private val logger = logger(MainActivity::class)

    private val aniNavigator = AniNavigator()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        KoinPlatformTools.defaultContext().getOrNull()?.get<NotifManager>()?.let {
            val code = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
            if (code != -1) {
                logger.info { "onNewIntent requestCode: $code" }
                AndroidNotifManager.handleIntent(code)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val settingsRepository = KoinPlatform.getKoin().get<SettingsRepository>()
        val proxyConfig = settingsRepository.proxySettings.flow.map {
            it.default.configIfEnabledOrNull
        }
        setContent {
            AniApp {
                val proxy by proxyConfig.collectAsStateWithLifecycle(null)

                val coilContext = LocalPlatformContext.current
                val imageLoader by remember(coilContext) {
                    derivedStateOf {
                        getDefaultImageLoader(coilContext, proxyConfig = proxy)
                    }
                }

                CompositionLocalProvider(
                    LocalToaster provides toaster,
                    LocalPlatformWindow provides remember {
                        PlatformWindow()
                    },
                    LocalImageLoader provides imageLoader,
                ) {
                    val uiSettings by settingsRepository.uiSettings.flow.collectAsStateWithLifecycle(null)
                    uiSettings?.let {
                        AniAppContent(aniNavigator, NavRoutes.Main(it.mainSceneInitialPage))
                    }
                }
            }
        }

        lifecycleScope.launch {
            runCatching {
                AppStartupTasks.verifySession(sessionManager, aniNavigator)
            }.onFailure {
                logger.error(it)
            }
        }
    }
}
