/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
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
import org.koin.core.context.GlobalContext
import org.koin.mp.KoinPlatform
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