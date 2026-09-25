/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import android.content.Intent
import android.graphics.drawable.Icon
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.him188.ani.app.data.R
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.download.DownloadOperation
import me.him188.ani.app.domain.media.download.DownloadOperations
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps the app process alive while PikPak caches are downloading.
 *
 * The PikPak engine runs inside the app process, while [AniTorrentService] is declared with
 * `android:process=":torrent_service"` and therefore only protects that other process. Without a
 * foreground service here, the system freezes the app process shortly after it goes to the
 * background and the cloud downloads stall until the user reopens the app.
 */
class PikPakCacheService : LifecycleService(), KoinComponent {
    private val logger = logger<PikPakCacheService>()

    private val downloadManager: MediaDownloadManager by inject()
    private val downloadOperations: DownloadOperations by inject()

    private val notification = ServiceNotification(
        this,
        notificationId = NOTIFICATION_ID,
        channelId = NOTIFICATION_CHANNEL_ID,
        buildStopServiceIntent = { context ->
            Intent(context, PikPakCacheService::class.java)
                .apply { putExtra(INTENT_STOP_EXTRA, true) }
        },
    )

    override fun onCreate() {
        super.onCreate()

        notification.setAppearance(
            NotificationAppearance(
                name = getString(R.string.pikpak_cache_service_name),
                titleIdle = getString(R.string.pikpak_cache_service_title_idle),
                titleWorking = getString(R.string.pikpak_cache_service_title_working),
                // PikPak never uploads, so the appearance drops the second format argument.
                content = getString(R.string.pikpak_cache_service_content),
                stopActionText = getString(R.string.pikpak_cache_service_pause_all),
                icon = Icon.createWithResource(this, applicationInfo.icon),
            ),
            // Resolved here rather than passed in the start Intent so that a sticky restart, which
            // delivers a null Intent, still produces a tappable notification.
            openActivityIntent = packageManager.getLaunchIntentForPackage(packageName),
        )

        lifecycleScope.launch {
            pikPakDownloadActivity(downloadManager)
                .onEach { activity ->
                    notification.updateNotification(
                        if (activity.taskCount > 0) {
                            NotificationDisplayStrategy.Working(
                                activity.downloadSpeed,
                                FileSize.Zero,
                                activity.taskCount,
                            )
                        } else {
                            NotificationDisplayStrategy.Idle(activity.downloadSpeed, FileSize.Zero)
                        },
                    )
                }
                .collect()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.getBooleanExtra(INTENT_STOP_EXTRA, false) == true) {
            // The action has to pause the downloads, not merely hide the notification:
            // PikPakCacheServiceController would otherwise still see unfinished downloads and
            // start the service straight back up.
            pauseAllDownloads()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!notification.createNotification(this)) {
            // The system refused the foreground start. Staying alive would miss the 5 second
            // startForeground deadline and crash the app process.
            logger.info { "Foreground start was refused, stopping PikPakCacheService." }
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        super.onDestroy()
    }

    private fun pauseAllDownloads() {
        val ids = downloadManager.downloads.value
            .filter { it.engineKey == MediaCacheEngineKey.PikPak }
            .map { it.id }
            .toSet()
        if (ids.isEmpty()) return
        // Runs on the application scope inside DownloadOperations, so it outlives this service.
        downloadOperations.submit(ids, DownloadOperation.Pause)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        backgroundTimedOut.value = true
        pauseAllDownloads()
        stopSelf()
    }

    companion object {
        // Android's dataSync budget resets when the app returns to the foreground.
        internal val backgroundTimedOut = MutableStateFlow(false)
        internal val isRunning = MutableStateFlow(false)

        const val INTENT_STOP_EXTRA = "stopService"

        private const val NOTIFICATION_ID = 115
        private const val NOTIFICATION_CHANNEL_ID =
            "me.him188.ani.app.domain.torrent.service.PikPakCacheService"
    }
}

/**
 * Transfer state of the PikPak downloads, as the notification and the service lifecycle need it.
 */
internal data class PikPakDownloadActivity(
    val taskCount: Int,
    val downloadSpeed: FileSize,
)

/**
 * Only [MediaCacheState.IN_PROGRESS] counts: a paused or failed record fetches nothing, and keeping
 * a foreground service for it would pin the process for no reason. Records that merely follow
 * playback are already excluded by [MediaDownloadManager.snapshots].
 */
internal fun pikPakDownloadActivity(manager: MediaDownloadManager): Flow<PikPakDownloadActivity> =
    manager.snapshots()
        .map { snapshots ->
            val active = snapshots.filter {
                it.engineKey == MediaCacheEngineKey.PikPak && it.status == MediaCacheState.IN_PROGRESS
            }
            PikPakDownloadActivity(
                taskCount = active.size,
                downloadSpeed = active
                    .sumOf { snapshot -> snapshot.downloadSpeed.takeIf { !it.isUnspecified }?.inBytes ?: 0L }
                    .bytes,
            )
        }
        .distinctUntilChanged()
