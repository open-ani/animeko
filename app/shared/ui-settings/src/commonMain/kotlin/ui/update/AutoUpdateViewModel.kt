/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.UriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.domain.update.UpdateManager
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.tools.update.DefaultFileDownloader
import me.him188.ani.app.tools.update.FileDownloaderState
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.list
import me.him188.ani.utils.ktor.UnsafeWrapperHttpClientApi
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentTimeMillis
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 主页使用的自动更新检查
 */
@Stable
class AutoUpdateViewModel : AbstractViewModel(), KoinComponent {
    private val proxyProvider: ProxyProvider by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val updateSettings = settingsRepository.updateSettings.flow
    private val updateManager: UpdateManager by inject()
    private val clientProvider: HttpClientProvider by inject()

    private val updateChecker: UpdateChecker by lazy { UpdateChecker() }

    private val client by lazy { clientProvider.get() }

    @OptIn(UnsafeWrapperHttpClientApi::class)
    private val borrowedClient = lazy { clientProvider.get().borrow() }

    private val fileDownloader by lazy { DefaultFileDownloader(borrowedClient.value) }

    /**
     * 新版本下载进度
     */
    private val fileDownloaderPresentation = FileDownloaderPresentation(fileDownloader, backgroundScope)

    /**
     * 最新的版本. 当 [checked] 为 `true` 时, `null` 表示没有新版本. 否则表示还没有检查过.
     */
    var latestVersion: NewVersion? by mutableStateOf(null)
    val currentVersion get() = currentAniBuildConfig.versionName

    private var lastCheckTime: Long by mutableLongStateOf(0L)

    /**
     * 是否检查过更新
     */
    val checked by derivedStateOf {
        lastCheckTime != 0L
    }

    private val autoCheckTasker = MonoTasker(backgroundScope)

    val logoState: UpdateLogoState by derivedStateOf {
        val latestVersion = latestVersion
        val fileDownloaderState = fileDownloaderPresentation.state
        when {
            !checked -> UpdateLogoState.ClickToCheck
            latestVersion == null -> UpdateLogoState.UpToDate
            else -> {
                when (fileDownloaderState) {
                    FileDownloaderState.Idle -> UpdateLogoState.HasUpdate(latestVersion)
                    is FileDownloaderState.Failed ->
                        UpdateLogoState.DownloadFailed(latestVersion, fileDownloaderState.throwable)

                    FileDownloaderState.Downloading ->
                        UpdateLogoState.Downloading(latestVersion, fileDownloaderPresentation)

                    is FileDownloaderState.Succeed ->
                        UpdateLogoState.Downloaded(latestVersion, fileDownloaderState.file)
                }
            }
        }
    }

    val hasUpdate by derivedStateOf {
        logoState is UpdateLogoState.HasNewVersion
    }

    // 一小时内只会检查一次
    fun startAutomaticCheckLatestVersion() {
        if (autoCheckTasker.isRunning.value) {
            return
        } else {
            if (currentTimeMillis() - lastCheckTime < 1000 * 60 * 60 * 1) {
                return // 1 小时内检查过
            }

            startCheckLatestVersion(null)
        }
    }

    /**
     * @param context 为 null 则不会自动下载
     */
    fun startCheckLatestVersion(
        uriHandler: UriHandler?
    ) {
        autoCheckTasker.launch {
            val updateSettings = updateSettings.first()

            val ver = try {
                if (!updateSettings.autoCheckUpdate) {
                    logger.info { "autoCheckUpdate disabled" }
                    return@launch
                }
                logger.info { "Checking latest version, updateSettings=${updateSettings}" }

                updateChecker.checkLatestVersion(updateSettings.releaseClass)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                logger.info { "Auto update checking failed due to IOException: $e" } // 故意不打印堆栈
                return@launch
            } finally {
                withContext(Dispatchers.Main) {
                    lastCheckTime = currentTimeMillis()
                }
            }
            withContext(Dispatchers.Main) { latestVersion = ver }

            if (ver != null && updateSettings.autoDownloadUpdate) {
                logger.info { "autoDownloadUpdate is true, starting download" }
                startDownload(ver, uriHandler)
            }
        }
    }

    private val autoDownloadTasker = MonoTasker(backgroundScope)
    fun startDownload(ver: NewVersion, uriHandler: UriHandler?) {
        autoDownloadTasker.launch {
            val settings = updateSettings.first()
            if (!settings.inAppDownload) {
                if (uriHandler == null) {
                    logger.warn { "uriHandler is null, cannot navigate to browser (may happen for auto check)" }
                    return@launch
                }
                ver.downloadUrlAlternatives.firstOrNull()?.let {
                    uriHandler.openUri(it)
                } ?: run {
                    logger.warn { "No download URL found, ignoring" }
                }
                return@launch
            }

            val dir = updateManager.saveDir
            if (dir.exists()) {
                // 删除旧的文件
                val allowedFilenames = ver.downloadUrlAlternatives.map {
                    it.substringAfterLast("/", "")
                }.let { list ->
                    list + list.map { "$it.sha1" }
                }
                for (file in dir.list()) {
                    if (file.name == ".DS_Store") continue

                    if (allowedFilenames.none { file.name.contains(it) }) {
                        logger.info { "Deleting old installer: $file" }
                        updateManager.deleteInstaller(file.inSystem)
                    }
                }
            }

            withContext(Dispatchers.IO) { dir.createDirectories() }
            fileDownloader.download(
                alternativeUrls = ver.downloadUrlAlternatives,
                filenameProvider = { it.substringAfterLast("/", "") },
                saveDir = dir,
            )
        }
    }

    fun restartDownload(uriHandler: UriHandler) {
        latestVersion?.let { startDownload(it, uriHandler) }
    }

    @OptIn(UnsafeWrapperHttpClientApi::class)
    override fun onCleared() {
        super.onCleared()
        if (borrowedClient.isInitialized()) {
            client.returnClient(borrowedClient.value)
        }
    }
}

@Immutable
class NewVersion(
    val name: String,
    val changelogs: List<Changelog>,
    /**
     * 所有可行的下载地址. 任意一个都可以用
     */
    val downloadUrlAlternatives: List<String>,
    val publishedAt: String,
)

@Immutable
class Changelog(
    val version: String,
    val publishedAt: String,
    changes: String
) {
    val changes = changes.lineSequence()
        .filterNot {
            it.startsWith("**Full Changelog**: ", ignoreCase = true)
                    || it.startsWith("Full Changelog:", ignoreCase = true)
        }
        .joinToString("\n")
        .trim()
}

@TestOnly
val TestNewVersion
    get() = NewVersion(
        "1.0.0",
        listOf(),
        listOf("https://example.com"),
        "2024-01-02",
    )

