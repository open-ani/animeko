/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.data.persistent.database.BundledSqliteInterpositionGuard
import me.him188.ani.app.data.persistent.database.SqliteGlobalScopeProbe
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.platform.DesktopContext
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.update.DefaultFileDownloader
import me.him188.ani.app.tools.update.InstallationResult
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.torrent.anitorrent.AnitorrentLibraryLoader
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatformDesktop
import me.him188.ani.utils.platform.isLinux
import org.koin.core.context.GlobalContext
import org.koin.mp.KoinPlatform
import org.openani.mediamp.ffmpeg.FFmpegKit
import java.io.File
import kotlin.system.exitProcess

object TestTasks {
    private val koin = GlobalContext.get()
    private val clientProvider by koin.inject<HttpClientProvider>()

    private val logger = logger<TestTasks>()
    fun handleTestTask(taskName: String, args: List<String>, context: DesktopContext): Nothing {
        when (taskName) {
            "anitorrent-load-test" -> {
                AnitorrentLibraryLoader.loadLibraries()
                exitProcess(0)
            }

            "mediamp-ffmpeg-smoke-test" -> {
                checkMediampFfmpeg()
                exitProcess(0)
            }

            "sqlite-bundled-load-test" -> {
                checkBundledSqlite()
                exitProcess(0)
            }

            "download-update-and-install" -> {
                downloadUpdateAndInstall(args, context)
            }

            "dandanplay-app-id" -> {
                if (currentAniBuildConfig.dandanplayAppId.isBlank()) {
                    logger.error { "dandanplayAppId is empty" }
                    exitProcess(1)
                }
                if (currentAniBuildConfig.dandanplayAppSecret.isBlank()) {
                    logger.error { "dandanplayAppSecret is empty" }
                    exitProcess(1)
                }
                exitProcess(0)
            }

            "sentry-dsn" -> {
                if (currentAniBuildConfig.sentryDsn.isBlank()) {
                    logger.error { "sentryDsn is empty" }
                    exitProcess(1)
                }
                exitProcess(0)
            }

            else -> {
                logger.error { "Unknown test task: $taskName" }
                exitProcess(1)
            }
        }
    }

    private fun checkMediampFfmpeg() {
        val runtimeDirectory = File(System.getProperty("compose.application.resources.dir"))
            .parentFile.absolutePath
        FFmpegKit.setRuntimeLibraryDirectory(runtimeDirectory, false)
        val result = runBlocking {
            FFmpegKit().execute(listOf("-version"))
        }
        check(result.isSuccess) { "FFmpeg smoke test failed: $result" }
    }

    private fun checkBundledSqlite() {
        BundledSQLiteDriver().open(":memory:").use { }

        // On Linux the driver loading successfully is not enough: #3188 and #3213 both crashed
        // *after* a successful load, once a second sqlite build got into the process. Runs against
        // the packaged AppImage after full startup, so JCEF/NSS and Koin have had their chance to
        // break the ordering — which a unit test JVM cannot reproduce.
        // See BundledSqliteInterpositionGuard for the two ordering constraints.
        if (currentPlatformDesktop().isLinux()) {
            val images = BundledSqliteInterpositionGuard.mappedSqliteJniImages()
            check(images.size == 1) {
                "expected exactly one libsqliteJni image in the process, got $images"
            }
            check(images.single().endsWith(BundledSqliteInterpositionGuard.LIB_FILE_NAME)) {
                "the mapped libsqliteJni is not the guard's copy: ${images.single()}"
            }
            logger.info { "Bundled SQLite check: single image ${images.single()}" }

            checkBundledSqliteOwnsGlobalScope()
        }
    }

    /**
     * Constraint 2 of [BundledSqliteInterpositionGuard]: the bundled sqlite must own the global
     * scope, so that the system libsqlite3 cannot take the symbols over when CEF loads NSS later.
     *
     * This task exits the process long before JCEF initialization, so rather than reordering
     * startup — every other verify task dispatches from the same point and would inherit a JCEF
     * dependency — it publishes the system sqlite into the global scope itself, which is the only
     * part of NSS that matters here.
     */
    private fun checkBundledSqliteOwnsGlobalScope() {
        check(SqliteGlobalScopeProbe.loadSystemSqliteIntoGlobalScope()) {
            "system libsqlite3 is not installed on this machine, so global scope ownership " +
                    "cannot be verified; the check would pass vacuously"
        }
        val owner = SqliteGlobalScopeProbe.globalSqliteSymbolOwner()
        check(owner != null && owner.endsWith(BundledSqliteInterpositionGuard.LIB_FILE_NAME)) {
            "sqlite3_initialize resolves to $owner in the global scope, not the bundled library; " +
                    "the guard ran too late and the system libsqlite3 has taken the symbols over"
        }
        logger.info { "Bundled SQLite check: global scope owned by $owner" }
    }

    // https://d.myani.org/v4.0.0-release-checksum-1/ani-4.0.0-release-checksum-1-macos-aarch64.dmg
    // https://d.myani.org/v4.0.0-release-checksum-1/ani-4.0.0-release-checksum-1-windows-x86_64.zip
    private fun downloadUpdateAndInstall(args: List<String>, context: DesktopContext): Nothing {
        val url = args[0]

        val result = runBlocking {
            logger.info { "Downloading update from $url" }
            DefaultFileDownloader(clientProvider.get(ScopedHttpClientUserAgent.ANI)).download(
                listOf(url),
                saveDir = File(".").toKtPath().inSystem,
            ).also {
                logger.info { "Downloading done" }
            } ?: error("Download failed")
        }

        when (currentPlatformDesktop()) {
            is Platform.Linux -> {
                // not supported
                exitProcess(0)
            }

            is Platform.MacOS -> {
                check(result.path.toString().endsWith(".dmg")) { "Not a dmg file: $result" }
                // no auto update so OK
                exitProcess(0)
            }

            is Platform.Windows -> {
                logger.info { "Performing install" }
                val updateInstaller = KoinPlatform.getKoin().get<UpdateInstaller>()
                val installationResult = updateInstaller.install(result, context)
                when (installationResult) {
                    InstallationResult.Succeed -> {
                        // OK
                        exitProcess(0)
                    }

                    is InstallationResult.Failed -> {
                        logger.error { "Failed to install update: $installationResult" }
                        exitProcess(1)
                    }
                }
            }
        }
    }

}
