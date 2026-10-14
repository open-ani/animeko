/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.updatee2e

import java.io.File
import kotlin.system.exitProcess

/**
 * 两种用法:
 * - `generate-manifest <发布包 zip> <清单文件>`: 为发布包生成增量更新清单, ci-helper 上传发布包前调用.
 * - `--app <打包产物或包含它的目录> --work-dir <工作目录> [--scenario <名字片段>]`: 自动更新端到端测试 ([DesktopUpdateE2e]).
 *   CI 的 Verify job 以独立 jar 运行它: 那里只有下载下来的打包产物和一个 JDK.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "generate-manifest") {
        require(args.size == 3) { "Usage: generate-manifest <package.zip> <manifest.json>" }
        val manifest = readDesktopPackageManifest(File(args[1]))
        manifest.writeTo(File(args[2]))
        println("Wrote manifest of ${manifest.entries.size} entries for ${args[1]} to ${args[2]}")
        return
    }

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
