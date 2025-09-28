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
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.cache.storage.MediaCacheMigrator
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.rememberPlatformWindow
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.theme.SystemBarColorEffect
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import me.him188.ani.app.ui.media.cache.storage.MediaCacheMigrationDialog
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.android.inject

class MainActivity : AniComponentActivity() {
    private val logger = logger<MainActivity>()
    private val aniNavigator = AniNavigator()

    private val mediaCacheMigrator: MediaCacheMigrator by inject()
    private val migrationStatus: StateFlow<MediaCacheMigrator.Status?> by lazy { mediaCacheMigrator.status }
    private val settingsRepository: SettingsRepository by inject()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

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
        applyLanguage()
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

        setContent {
            AniApp {
                SystemBarColorEffect()

                CompositionLocalProvider(
                    LocalToaster provides toaster,
                    LocalPlatformWindow provides rememberPlatformWindow(this),
                ) {
                    AniAppContent(aniNavigator)
                }

                val migrationStatus by migrationStatus.collectAsStateWithLifecycle()
                migrationStatus?.let { MediaCacheMigrationDialog(status = it) }
            }
        }
    }

    private fun applyLanguage() {
        lifecycleScope.launch {
            settingsRepository.uiSettings.flow.drop(1).collect { settings ->
                settings.appLanguage?.let {
                    try {
                        val locales = LocaleListCompat.forLanguageTags(it.toLanguageTag())
                        logger.info("Set locale to $locales")
                        AppCompatDelegate.setApplicationLocales(locales)
                    } catch (e: Throwable) {
                        logger.error(e) { "Failed to set app language, see exception" }
                    }
                }
            }
        }
    }
}
