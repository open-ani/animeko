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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.him188.ani.android.AniApplication
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.torrent.TorrentCacheMigrator
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.AppStartupTasks
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.rememberPlatformWindow
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.theme.SystemBarColorEffect
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.android.inject

class MainActivity : AniComponentActivity() {
    private val sessionManager: SessionManager by inject()

    private val appTerminator: AppTerminator by inject()
    private val mediaCacheManager: MediaCacheManager by inject()
    private val settingsRepo: SettingsRepository by inject()

    private val logger = logger<MainActivity>()
    private val aniNavigator = AniNavigator()

    private val torrentCacheMigrator by lazy {
        TorrentCacheMigrator(
            context = this,
            metadataStore = applicationContext.dataStores.mediaCacheMetadataStore,
            mediaCacheManager = mediaCacheManager,
            settingsRepo = settingsRepo,
            appTerminator = appTerminator,
        )
    }
    private val migrationStatus: StateFlow<TorrentCacheMigrator.Status?> by lazy {
        torrentCacheMigrator.status
    }

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

        /**
         * Since 4.9, Default directory of torrent cache is changed to external/shared storage and
         * cannot be changed. This is the workaround for startup migration.
         *
         * This class should be called only `AniApplication.Instance.requiresTorrentCacheMigration` is true,
         * which means we are going to migrate torrent caches from internal storage to shared/external storage.
         */
        if (AniApplication.instance.requiresTorrentCacheMigration) {
            torrentCacheMigrator.migrateTorrentCache()
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

                val requiresMigration by rememberUpdatedState(AniApplication.instance.requiresTorrentCacheMigration)
                if (requiresMigration) {
                    val status by migrationStatus.collectAsStateWithLifecycle()
                    status?.let { MigrationDialog(status = it) }
                }
            }
        }

        lifecycleScope.launch {
            AppStartupTasks.verifySession(sessionManager)
        }
    }

    @Composable
    private fun MigrationDialog(
        status: TorrentCacheMigrator.Status,
    ) {
        AlertDialog(
            title = { Text("正在迁移缓存") },
            text = {
                Column {
                    Text(renderMigrationStatus(status = status))
                    if (status !is TorrentCacheMigrator.Status.Error) {
                        Spacer(modifier = Modifier.height(24.dp))
                        if (status is TorrentCacheMigrator.Status.Cache) {
                            LinearProgressIndicator(
                                progress = { status.migratedSize.toFloat() / status.totalSize.coerceAtLeast(1L) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("迁移过程中设备可能会轻微卡顿, 请不要强制关闭 Ani，这可能导致缓存损坏或下次启动应用闪退")
                    }
                }
            },
            onDismissRequest = { /* not dismiss-able */ },
            confirmButton = {
                if (status !is TorrentCacheMigrator.Status.Error) return@AlertDialog
                val clipboard = LocalClipboardManager.current
                TextButton(
                    {
                        val errorMessage = status.throwable?.toString()
                        if (errorMessage != null) {
                            clipboard.setText(AnnotatedString(errorMessage))
                        }
                        appTerminator.exitApp(this, 0)
                    },
                ) { Text(text = "复制并退出") }
            },
        )
    }

    @Composable
    private fun renderMigrationStatus(status: TorrentCacheMigrator.Status) = when (status) {
        is TorrentCacheMigrator.Status.Init -> "正在准备..."
        is TorrentCacheMigrator.Status.Cache ->
            if (status.currentFile != null)
                "迁移缓存（${status.migratedSize.bytes} / ${status.totalSize.bytes}）:\n${status.currentFile}"
            else "迁移缓存..."

        is TorrentCacheMigrator.Status.Metadata -> "合并元数据..."

        is TorrentCacheMigrator.Status.Error ->
            """
            迁移时发生错误, 可能会导致 Ani 后续的闪退等意料之外的问题.
            
            错误信息:
            ${status.throwable}
            
            请点击下方复制按钮复制完整错误日志，随后前往 GitHub 反馈错误信息.
        """.trimIndent()
    }
}
