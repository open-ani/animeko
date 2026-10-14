/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import java.io.File
import kotlin.system.exitProcess

/**
 * [DesktopUpdateE2e] 的独立入口, 给 CI 的 Verify job 用: 那里只有下载下来的打包产物, 没有主构建.
 * 由 build-logic 自己的 `updateE2e` 任务运行: `./gradlew -p build-logic updateE2e -Pani.updateE2e.appDir=<目录>`.
 *
 * 参数: `--app <打包产物或包含它的目录>`, `--work-dir <工作目录>`, 可选 `--scenario <名字片段>`.
 */
fun main(args: Array<String>) {
    var app: File? = null
    var workDir: File? = null
    var scenario: String? = null
    val iterator = args.iterator()
    while (iterator.hasNext()) {
        when (val arg = iterator.next()) {
            "--app" -> app = File(iterator.next())
            "--work-dir" -> workDir = File(iterator.next())
            "--scenario" -> scenario = iterator.next()
            else -> {
                System.err.println("Unknown argument: $arg")
                exitProcess(2)
            }
        }
    }
    val log = E2eLog(
        lifecycle = ::println,
        warn = { println("WARNING: $it") },
        error = { message, e ->
            System.err.println(message)
            e?.printStackTrace()
        },
    )
    try {
        DesktopUpdateE2e(
            appDir = locatePackagedApp(requireNotNull(app) { "--app is required" }),
            workDir = requireNotNull(workDir) { "--work-dir is required" },
            logger = log,
        ).run(scenario)
    } catch (e: Exception) {
        log.error(e.message ?: e.toString(), null)
        exitProcess(1)
    }
}
