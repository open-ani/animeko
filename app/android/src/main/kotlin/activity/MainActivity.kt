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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.DataStoreMediaCacheStorage
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.AppStartupTasks
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.MeteredNetworkDetector
import me.him188.ani.app.platform.rememberPlatformWindow
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.theme.SystemBarColorEffect
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.moveDirectoryRecursively
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.android.inject

class MainActivity : AniComponentActivity() {
    private val sessionManager: SessionManager by inject()
    private val meteredNetworkDetector: MeteredNetworkDetector by inject()

    private val appTerminator: AppTerminator by inject()
    private val mediaCacheManager: MediaCacheManager by inject()

    private val logger = logger<MainActivity>()
    private val aniNavigator = AniNavigator()

    private var migrationStatus: MigrationStatus by mutableStateOf(MigrationStatus.Init)

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

        setContent {
            AniApp {
                SystemBarColorEffect()

                CompositionLocalProvider(
                    LocalToaster provides toaster,
                    LocalPlatformWindow provides rememberPlatformWindow(this),
                ) {
                    AniAppContent(aniNavigator)
                }
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
    private fun migrateTorrentCache() {
        GlobalScope.launch(Dispatchers.IO) {
            // hard-coded directory name before 4.9
            val prevPath = filesDir.resolve("torrent-caches").toPath().toKtPath().inSystem
            val newPath = getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.toPath()?.toKtPath()?.inSystem

            withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Cache(null) }
            if (newPath == null) {
                logger.error { "Failed to get external files dir while migrating cache." }
                withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Error }
                return@launch
            }

            logger.info { "[migration] start move from $prevPath to $newPath" }
            prevPath.moveDirectoryRecursively(newPath) {
                migrationStatus = MigrationStatus.Cache(it.name)
            }
            logger.info { "[migration] move complete." }

            val metadataStore = dataStores.mediaCacheMetadataStore
            val torrentStorage = mediaCacheManager.storagesIncludingDisabled
                .find { it is DataStoreMediaCacheStorage && it.engine is TorrentMediaCacheEngine }

            if (torrentStorage == null) {
                logger.error("Failed to get TorrentMediaCacheEngine, it is null.")
                withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Error }
                return@launch
            }

            val torrentEngine = (torrentStorage.engine as TorrentMediaCacheEngine).torrentEngine
            val newTorrentDir = newPath.resolve(torrentEngine.type.id)

            metadataStore.updateData { original ->
                val nonTorrentMetadata = original.filter { it.engine != torrentStorage.engine.engineKey }
                val torrentMetadata = original.filter { it.engine == torrentStorage.engine.engineKey }

                nonTorrentMetadata + torrentMetadata.map { save ->
                    save.copy(
                        metadata = torrentStorage.engine.modifyMetadataForMigration(save.metadata, newTorrentDir.path),
                    )
                }
            }
        }
    }

    private sealed interface MigrationStatus {
        val isError: Boolean

        object Init : MigrationStatus {
            override val isError: Boolean = false
        }

        class Cache(val currentFile: String?) : MigrationStatus {
            override val isError: Boolean = false
        }

        class Metadata(val currentMetadata: String?) : MigrationStatus {
            override val isError: Boolean = false
        }

        object Error : MigrationStatus {
            override val isError: Boolean = true
        }
    }
}
