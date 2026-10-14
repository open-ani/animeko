/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.platform.DesktopContext
import me.him188.ani.app.ui.update.AppUpdateState
import me.him188.ani.app.ui.update.AppUpdateViewModel
import me.him188.ani.app.ui.update.UpdateChecker
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * 测试任务 `update-e2e`: 在打包好的应用里走一遍真实的自动更新流程. 驱动它的是 `tools/desktop-update-e2e` 的 `DesktopUpdateE2e`:
 * 在本机 mock 版本 API 和发布包, 以这个任务启动旧版本, 之后核对安装目录和服务器收到的请求.
 *
 * 流程与用户点击「更新」相同, 都经 [AppUpdateViewModel]: 检查版本, 自动下载 (增量或整包), 调用安装器. 安装器启动外部
 * 更新器并结束本进程; 更新器换好文件后启动新版本, 新版本带着同样的环境变量再次进入这里, 看到 [Config.installedMarker]
 * 已在自己的安装目录里, 知道自己就是更新后的版本, 报告后退出.
 *
 * 只有版本 API 的地址和报告文件的位置来自配置, 应用的其余部分不知道自己在测试里.
 */
object UpdateE2eTestTask : KoinComponent {
    @Serializable
    class Config(
        val serverUrl: String,
        /**
         * 报告文件, 每行一个 [Report] 的 JSON.
         */
        val resultFile: String,
        /**
         * 只有新版本才有的文件 (绝对路径). 它存在即说明更新已完成, 当前进程是新版本.
         */
        val installedMarker: String,
    )

    @Serializable
    class Report(val phase: String, val detail: String? = null)

    private val logger = logger<UpdateE2eTestTask>()
    private const val WATCHDOG_MILLIS = 6 * 60_000L

    fun run(configFile: File, context: DesktopContext): Nothing {
        val config = Json.decodeFromString(Config.serializer(), configFile.readText())
        val resultFile = File(config.resultFile)
        fun report(phase: String, detail: String? = null) {
            logger.info { "update-e2e: $phase ${detail.orEmpty()}" }
            resultFile.parentFile?.mkdirs()
            resultFile.appendText(Json.encodeToString(Report.serializer(), Report(phase, detail)) + "\n")
        }

        fun fail(message: String): Nothing {
            logger.error { "update-e2e failed: $message" }
            report("failed", message)
            exitProcess(1)
        }

        val marker = File(config.installedMarker)
        if (marker.isFile) {
            report("relaunched", marker.readText().trim())
            exitProcess(0)
        }
        // 没有标记文件却已有报告: 安装没生效, 重新启动的仍是旧版本 (比如安装脚本失败后恢复了旧版本). 不能再走一遍更新, 会循环.
        if (resultFile.isFile && resultFile.length() > 0) {
            fail("relaunched without the installed marker, the update did not take effect")
        }

        // 流程卡住时留下线程栈再退出, 驱动器只能看到进程没退出, 看不到卡在哪.
        thread(isDaemon = true, name = "update-e2e-watchdog") {
            Thread.sleep(WATCHDOG_MILLIS)
            val dump = Thread.getAllStackTraces().entries.joinToString("\n\n") { (thread, frames) ->
                "\"${thread.name}\" daemon=${thread.isDaemon} state=${thread.state}\n" +
                        frames.joinToString("\n") { "\tat $it" }
            }
            runCatching { File(config.resultFile + ".threads.txt").writeText(dump) }
            report("timeout", "no progress in ${WATCHDOG_MILLIS / 60_000} minutes, thread dump written")
            Runtime.getRuntime().halt(2)
        }

        return runBlocking {
            get<SettingsRepository>().updateSettings.update {
                copy(autoCheckUpdate = true, autoDownloadUpdate = true, inAppDownload = true)
            }
            val vm = AppUpdateViewModel(UpdateChecker(listOf(config.serverUrl)))
            vm.startCheckLatestVersion(uriHandler = null)
            val presentation = vm.presentationFlow.first { p ->
                p.checkUpdateError != null
                        || p.state is AppUpdateState.AlreadyUpToDate
                        || p.state is AppUpdateState.Downloaded
                        || p.state is AppUpdateState.DownloadFailed
            }
            presentation.checkUpdateError?.let { fail("Update check failed: $it") }
            when (val state = presentation.state) {
                is AppUpdateState.AlreadyUpToDate -> {
                    report("up-to-date")
                    exitProcess(0)
                }

                is AppUpdateState.DownloadFailed -> fail("Download failed: ${state.throwable}")

                is AppUpdateState.Downloaded -> {
                    report("downloaded", state.file.toString())
                    vm.install(context)
                    // 安装成功时安装器会结束本进程, 这里只会等到失败.
                    val failure = vm.presentationFlow.first { it.installationFailure != null }.installationFailure
                    fail("Installation failed: $failure")
                }

                else -> fail("Unexpected state: $state")
            }
        }
    }
}
