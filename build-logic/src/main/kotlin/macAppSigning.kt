/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.GradleException
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.io.path.isExecutable

/**
 * 给 macOS 的 `.app` 重新做 ad-hoc 签名, 顺序与 jpackage 打 dmg 时对 app image 的重签相同
 * (`MacAppImageBuilder.signAppBundle`, 签名身份 `-`): 逐个去掉再重签每个可执行文件和 dylib (启动器除外),
 * 然后签 `Contents/runtime`、`Contents/Frameworks` 下的各项, 最后签整个 `.app`.
 *
 * jpackage 签名之后, `createReleaseDistributable` 的后续步骤 (复制 CEF、解压 native 库、重写 jar) 还会改动 `.app`,
 * 封印随之失效. dmg 由 jpackage 打包时重签, 没有问题; zip 发布包直接打包这个目录, 自动更新的安装脚本对它
 * `codesign --verify` 会失败, 只能放弃更新. 这里按同样的方式重签, zip 里的 `.app` 就与 dmg 里的相同,
 * 只差 jpackage 额外放进 dmg 的 `.package` 以及因此变化的启动器签名和封印; 增量更新也因此能复用 dmg 安装的文件.
 *
 * 重签要做两遍. bundle 主可执行文件 (`runtime` 的 `libjli.dylib`、`cef_server.app` 的 `cef_server`) 的签名
 * 绑定 bundle 当时的封印, 第一遍改变了封印, jpackage 打 dmg 时再签一遍结果就和第一遍不同. 第二遍之后不再变化,
 * jpackage 的重签不再改动它们.
 *
 * 必须在所有改动 `.app` 内容的步骤之后运行. 非 macOS 时什么都不做.
 */
fun AbstractJPackageTask.signMacAppBundleAdHoc() {
    if (getOs() != Os.MacOS) return
    val app = destinationDir.get().asFile.walk().maxDepth(1)
        .find { it.isDirectory && it.name.endsWith(".app") }
        ?: throw GradleException("Cannot find .app bundle in ${destinationDir.get().asFile}")
    repeat(2) { signAppBundleAdHocOnce(app, launcher = app.resolve("Contents/MacOS/${packageName.get()}")) }
    // 与安装脚本的校验相同.
    codesign("--verify", "--deep", "--strict", app)
    logger.lifecycle("Ad-hoc signed $app")
}

private fun signAppBundleAdHocOnce(app: File, launcher: File) {
    val codeFiles = app.walk()
        .filter { file ->
            val path = file.toPath()
            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    (path.isExecutable() || file.name.endsWith(".dylib")) &&
                    !file.invariantSeparatorsPath.contains("dylib.dSYM/Contents") &&
                    file != launcher
        }
        .toList()
    for (file in codeFiles) {
        codesign("--remove-signature", file)
        codesign("-s", "-", file)
    }

    codesign("-s", "-", "--force", app.resolve("Contents/runtime"))
    app.resolve("Contents/Frameworks").listFiles()?.sortedBy { it.name }?.forEach {
        codesign("-s", "-", "--force", it)
    }
    codesign("-s", "-", "--force", app)
}

private fun codesign(vararg args: Any) {
    val command = listOf("/usr/bin/codesign") + args.map { if (it is File) it.absolutePath else it.toString() }
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (process.waitFor() != 0) {
        throw GradleException("${command.joinToString(" ")} failed:\n$output")
    }
}
