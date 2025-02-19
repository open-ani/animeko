/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.theme.SystemBarColorEffect
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

    private val logger = logger<MainActivity>()

    private val aniNavigator = AniNavigator()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val code = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        if (code != -1) {
            KoinPlatformTools.defaultContext().getOrNull()?.get<NotifManager>()?.let {
                logger.info { "onNewIntent requestCode: $code" }
                AndroidNotifManager.handleIntent(code)
            }
        }

        handleStartIntent(intent)
    }

    private fun handleStartIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "ani") return
        if (data.host == "subjects") {
            val id = data.pathSegments.getOrNull(0)?.toIntOrNull() ?: return
            lifecycleScope.launch {
                try {
                    if (!aniNavigator.isNavControllerReady()) {
                        aniNavigator.awaitNavController()
                        delay(1000) // 等待初始化好, 否则跳转可能无效
                    }
                    aniNavigator.navigateSubjectDetails(id, placeholder = null)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to navigate to subject details" }
                }
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

        val settingsRepository = KoinPlatform.getKoin().get<SettingsRepository>()
        setContent {
            AniApp {
                SystemBarColorEffect()

                val platformWindow = remember(this) {
                    PlatformWindow(this)
                }
                DisposableEffect(platformWindow) {
                    onDispose { platformWindow.close() }
                }
                
                CompositionLocalProvider(
                    LocalToaster provides toaster,
                    LocalPlatformWindow provides platformWindow,
                ) {
                    val uiSettings by settingsRepository.uiSettings.flow.collectAsStateWithLifecycle(null)
                    uiSettings?.let {
                        AniAppContent(aniNavigator, NavRoutes.Main(it.mainSceneInitialPage))
                    }
                }
            }
        }

        lifecycleScope.launch {
            AppStartupTasks.verifySession(sessionManager, aniNavigator)
        }
    }
}
