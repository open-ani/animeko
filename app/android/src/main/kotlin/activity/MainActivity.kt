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
import android.os.Environment
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.android.AniApplication
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.DataStoreMediaCacheStorage
import me.him188.ani.app.domain.session.SessionManager
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
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.moveDirectoryRecursively
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.android.inject

class MainActivity : AniComponentActivity() {
    private val sessionManager: SessionManager by inject()

    private val appTerminator: AppTerminator by inject()
    private val mediaCacheManager: MediaCacheManager by inject()
    private val settingsRepo: SettingsRepository by inject()

    private val logger = logger<MainActivity>()
    private val aniNavigator = AniNavigator()

    private var migrationStatus: MigrationStatus? by mutableStateOf(null)

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

        if (AniApplication.instance.requiresTorrentCacheMigration) {
            migrateTorrentCache()
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

                migrationStatus?.let { MigrationDialog(it) }
            }
        }

        lifecycleScope.launch {
            AppStartupTasks.verifySession(sessionManager)
        }
    }

    /**
     * Since 4.9, Default directory of torrent cache is changed to external/shared storage and
     * cannot be changed. This is the workaround for startup migration.
     *
     * This function should be called only [AniApplication.Instance.requiresTorrentCacheMigration] is true,
     * which means we are going to migrate torrent caches from internal storage to shared/external storage.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun migrateTorrentCache() = GlobalScope.launch(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Init }
            delay(15000) // 让用户能看清楚目前做的事情

            // hard-coded directory name before 4.9
            val prevPath = filesDir.resolve("torrent-caches").toPath().toKtPath().inSystem
            val newPath = getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.toPath()?.toKtPath()?.inSystem

            withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Cache(null) }
            if (newPath == null) {
                logger.error { "[migration] Failed to get external files dir while migrating cache." }
                withContext(Dispatchers.Main) {
                    migrationStatus =
                        MigrationStatus.Error(IllegalStateException("Shared storage is not currently available."))
                }
                return@launch
            }

            logger.info { "[migration] Start move from $prevPath to $newPath" }
            if (prevPath.exists() && prevPath.isDirectory()) {
                newPath.createDirectories()
                prevPath.moveDirectoryRecursively(newPath) {
                    migrationStatus = MigrationStatus.Cache(it.name)
                }
            }
            logger.info { "[migration] Move complete." }

            val metadataStore = applicationContext.dataStores.mediaCacheMetadataStore
            val torrentStorage = mediaCacheManager.storagesIncludingDisabled
                .find { it is DataStoreMediaCacheStorage && it.engine is TorrentMediaCacheEngine }

            if (torrentStorage == null) {
                logger.error("[migration] Failed to get TorrentMediaCacheEngine, it is null.")
                withContext(Dispatchers.Main) {
                    migrationStatus =
                        MigrationStatus.Error(IllegalStateException("Media cache storage with engine TorrentMediaCacheEngine is not found."))
                }
                return@launch
            }
            logger.info { "[migration] New torrent dir: $newPath" }

            withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Metadata }
            metadataStore.updateData { original ->
                val nonTorrentMetadata = original.filter { it.engine != torrentStorage.engine.engineKey }
                val torrentMetadata = original.filter { it.engine == torrentStorage.engine.engineKey }

                nonTorrentMetadata + torrentMetadata.map { save ->
                    save.copy(
                        metadata = torrentStorage.engine
                            .modifyMetadataForMigration(save.metadata, newPath.path),
                    )
                }
            }

            settingsRepo.mediaCacheSettings.update { copy(saveDir = newPath.absolutePath) }
            logger.info { "[migration] Migration success." }

            appTerminator.exitApp(this@MainActivity, 0)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Error(e) }
            logger.error(e) { "[migration] Failed to migrate torrent cache." }
        }
    }

    private sealed interface MigrationStatus {
        object Init : MigrationStatus

        class Cache(val currentFile: String?) : MigrationStatus

        object Metadata : MigrationStatus

        class Error(val throwable: Throwable? = null) : MigrationStatus
    }

    @Composable
    private fun MigrationDialog(
        status: MigrationStatus,
    ) {
        AlertDialog(
            title = { Text("迁移缓存") },
            text = {
                Column {
                    Text(renderMigrationStatus(status = status))
                    if (status !is MigrationStatus.Error) {
                        Spacer(modifier = Modifier.height(24.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            """
                            从 4.9 版本起，Ani 不再将 BT 缓存存放之内部目录. 目前你的缓存位置在内部目录，Ani 正在迁移它们到外部目录.
                            
                            将缓存存放在外部目录可以使其他较高权限的应用访问或播放你的缓存，例如使用 Shizuku 授权的应用.
                            
                            此过程是完全自动的，迁移过程中设备可能会轻微卡顿. 请不要强制关闭 Ani，这可能导致缓存损坏或下次启动应用闪退.
                        """.trimIndent(),
                        )
                    }
                }
            },
            onDismissRequest = { /* not dismiss-able */ },
            confirmButton = {
                if (status is MigrationStatus.Error) {
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
                }
            },
        )
    }

    @Composable
    private fun renderMigrationStatus(status: MigrationStatus) = when (status) {
        is MigrationStatus.Init -> "正在准备..."
        is MigrationStatus.Cache ->
            if (status.currentFile != null) "迁移缓存: \n${status.currentFile}" else "迁移缓存..."

        is MigrationStatus.Metadata -> "合并元数据..."

        is MigrationStatus.Error ->
            """
            迁移时发生错误, 可能会导致 Ani 后续的闪退等意料之外的问题.
            
            错误信息:
            ${status.throwable}
            
            请点击下方复制按钮复制完整错误日志，随后前往 GitHub 反馈错误信息.
        """.trimIndent()
    }
}
