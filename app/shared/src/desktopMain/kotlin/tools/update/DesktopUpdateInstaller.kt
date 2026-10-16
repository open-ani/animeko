/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.ExecutableDirectoryDetector
import me.him188.ani.app.platform.features.DesktopFileRevealer
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Platform
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

interface DesktopUpdateInstaller : UpdateInstaller {
    override fun createPackageDownloader(client: ScopedHttpClient): FileDownloader =
        createDeltaPackageAssembler(client)?.let { DeltaPackageDownloader(it, DefaultFileDownloader(client)) }
            ?: DefaultFileDownloader(client)

    override suspend fun openForManualInstallation(file: SystemPath, context: ContextMP): Boolean {
        return DesktopFileRevealer.revealFile(file)
    }

    fun deleteOldUpdater()

    companion object {
        fun currentOS(): DesktopUpdateInstaller {
            return when (me.him188.ani.utils.platform.currentPlatformDesktop()) {
                is Platform.MacOS -> MacOSUpdateInstaller
                is Platform.Windows -> WindowsUpdateInstaller
                is Platform.Linux -> LinuxUpdateInstaller
            }
        }
    }
}

object MacOSUpdateInstaller : DesktopUpdateInstaller {
    private val logger = logger<MacOSUpdateInstaller>()

    override val installablePackageExtensions: Set<String> = setOf("dmg", "zip")

    /**
     * 版本 API 对 macOS 返回 dmg, 应用内更新改用同一发布里的 zip: 增量下载要按偏移从发布包取 entry, dmg 做不到;
     * 整包回退时 zip 也不用经过 hdiutil 转换挂载. dmg 只用于首次安装.
     */
    override fun getUpdatePreparationUrls(packageUrls: List<String>): List<String> =
        packageUrls.map { it.replace(DMG_SUFFIX, ".zip") }

    override fun createDeltaPackageAssembler(client: ScopedHttpClient): DeltaPackageAssembler? {
        val appDir = ExecutableDirectoryDetector.INSTANCE.getExecutableDirectory().parentFile
        if (appDir == null || !appDir.name.endsWith(".app", ignoreCase = true)) {
            // 开发运行时不在 .app 里, 没有可复用的安装目录.
            return null
        }
        return DesktopDeltaPackageAssembler(client, installRoot = appDir)
    }

    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        logger.info { "Preparing to install update for macOS using external script." }

        val contentsDir = ExecutableDirectoryDetector.INSTANCE.getExecutableDirectory()
        logger.info { "contentsDir: $contentsDir" }

        val appDir = contentsDir.parentFile ?: return failed(
            "Cannot find .app dir",
            InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE,
        )

        if (!appDir.name.endsWith(".app", ignoreCase = true)) {
            return failed(
                "Current directory is not inside a .app bundle: $appDir",
                InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE,
            )
        }

        val updateFile = file.toFile()
        if (!updateFile.exists()) {
            return failed("Update file does not exist: ${updateFile.absolutePath}")
        }

        val extension = updateFile.extension.lowercase()
        if (extension !in installablePackageExtensions) {
            return failed("Unsupported update file format: $extension")
        }

        // 新版本先放到 .app 旁边, 再以重命名替换整个 .app, 因此需要能写入它所在的目录.
        if (!appDir.parentFile.canWrite()) {
            return failed(
                "Cannot write to the directory containing the app: ${appDir.parentFile}",
                InstallationFailureReason.FAILED_TO_COPY,
            )
        }

        val tempDir = createTempDirectory(prefix = "ani-macos-update-").toFile()
        logger.info { "tempDir: ${tempDir.absolutePath}" }

        val scriptFile = File(tempDir, "macos-update.command")
        scriptFile.writeText(MACOS_UPDATE_SCRIPT)
        scriptFile.setExecutable(true)

        logger.info { "Launching update script: ${scriptFile.absolutePath}" }
        ProcessBuilder(
            scriptFile.absolutePath,
            ProcessHandle.current().pid().toString(),
            updateFile.absolutePath,
            appDir.absolutePath,
            tempDir.absolutePath,
        )
            .redirectOutput(File(tempDir, "update-output.log"))
            .redirectError(File(tempDir, "update-error.log"))
            .start()

        logger.info { "Exiting old instance." }
        return exitProcessForUpdate(delayMillis = 1000)
    }

    override fun deleteOldUpdater() {
        // Noop or your custom implementation
    }

    private fun failed(
        message: String,
        reason: InstallationFailureReason? = InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE
    ): InstallationResult.Failed {
        logger.error { message }
        return InstallationResult.Failed(reason ?: InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE, message)
    }
}

/**
 * 等待旧进程退出后, 用安装包 (`.dmg` 或 `.zip`) 中的 `.app` 替换当前 `.app` 并启动它.
 *
 * 参数: 旧进程 PID, 安装包路径, 当前 `.app` 路径, 工作目录.
 *
 * 新版本先完整复制到当前 `.app` 旁边并校验签名, 再以重命名替换整个 `.app`. 不能把新文件覆盖到现有 `.app` 中:
 * 原地覆盖已签名的可执行文件会保留原 inode, 内核仍按旧签名校验它, 新版本启动时会因 "Code Signature Invalid" 被杀死;
 * 覆盖也会留下旧版本独有的文件, 破坏签名封印. 任一步骤失败时保留或恢复旧 `.app`, 并重新启动它.
 */
internal val MACOS_UPDATE_SCRIPT = $$"""
    #!/usr/bin/env bash
    set -euo pipefail

    OLD_PID="$1"
    PACKAGE_FILE="$2"
    TARGET_APP="$3"
    WORK_DIR="$4"

    APP_NAME="$(basename "$TARGET_APP")"
    TARGET_PARENT="$(dirname "$TARGET_APP")"
    # Kept next to the target so that the swaps below are renames within one volume.
    STAGING_APP="$TARGET_PARENT/.$APP_NAME.update"
    BACKUP_APP="$TARGET_PARENT/.$APP_NAME.old"
    MOUNT_DIR="$WORK_DIR/mount"
    MOUNTED=0

    cleanup() {
      local status=$?
      if [ "$MOUNTED" = 1 ]; then
        hdiutil detach "$MOUNT_DIR" >/dev/null || echo "Warning: failed to detach DMG." >&2
      fi
      # Logs in WORK_DIR are kept for troubleshooting.
      rm -rf -- "$STAGING_APP" "$WORK_DIR/converted.cdr" "$WORK_DIR/unzip"
      if [ "$status" -ne 0 ]; then
        if [ ! -e "$TARGET_APP" ] && [ -e "$BACKUP_APP" ]; then
          mv -- "$BACKUP_APP" "$TARGET_APP" || true
        fi
        echo "Update failed with status $status, launching the current app." >&2
        open "$TARGET_APP" || true
      fi
    }
    trap cleanup EXIT

    echo "Waiting for process $OLD_PID to exit..."
    while kill -0 "$OLD_PID" 2>/dev/null; do
      sleep 1
    done

    shopt -s nocasematch
    case "$PACKAGE_FILE" in
      *.dmg)
        # A DMG with a license agreement cannot be attached without user interaction; the CDR copy has none.
        echo "Converting DMG into CDR..."
        hdiutil convert "$PACKAGE_FILE" -format UDTO -o "$WORK_DIR/converted"
        mkdir -p "$MOUNT_DIR"
        echo "Mounting DMG at $MOUNT_DIR..."
        hdiutil attach "$WORK_DIR/converted.cdr" -nobrowse -noverify -noautoopen -readonly -mountpoint "$MOUNT_DIR"
        MOUNTED=1
        SOURCE_DIR="$MOUNT_DIR"
        ;;
      *.zip)
        echo "Unzipping update package..."
        mkdir -p "$WORK_DIR/unzip"
        unzip -q "$PACKAGE_FILE" -d "$WORK_DIR/unzip"
        SOURCE_DIR="$WORK_DIR/unzip"
        ;;
      *)
        echo "Error: unsupported update package: $PACKAGE_FILE" >&2
        exit 1
        ;;
    esac
    shopt -u nocasematch

    SOURCE_APP="$SOURCE_DIR/$APP_NAME"
    if [ ! -d "$SOURCE_APP" ]; then
      echo "Error: $APP_NAME not found in the update package." >&2
      exit 1
    fi

    echo "Copying $SOURCE_APP to $STAGING_APP..."
    rm -rf -- "$STAGING_APP" "$BACKUP_APP"
    ditto "$SOURCE_APP" "$STAGING_APP"
    xattr -r -d com.apple.quarantine "$STAGING_APP" 2>/dev/null || true

    echo "Verifying code signature..."
    codesign --verify --deep --strict "$STAGING_APP"

    echo "Replacing $TARGET_APP..."
    mv -- "$TARGET_APP" "$BACKUP_APP"
    mv -- "$STAGING_APP" "$TARGET_APP"
    rm -rf -- "$BACKUP_APP" || echo "Warning: failed to remove $BACKUP_APP." >&2

    echo "Launching $TARGET_APP"
    open "$TARGET_APP"
""".trimIndent()

/**
 * 在后台线程退出进程, 让外部更新程序接管.
 *
 * [UpdateInstaller.install] 在 AWT 事件线程 (EDT) 上被调用, 而 System.exit 会阻塞当前线程等待
 * 所有 JVM shutdown hook 完成, 其中 JCEF 的清理 hook 又需要 EDT 处理事件 —
 * 在 EDT 上直接 exitProcess 会互相等待, 进程永远无法退出 (#3067 后表现为点击更新后 app 冻结).
 */
private fun exitProcessForUpdate(delayMillis: Long): InstallationResult {
    thread(name = "ani-update-exit") {
        if (delayMillis > 0) Thread.sleep(delayMillis)
        exitProcess(0)
    }
    return InstallationResult.Succeed
}

object LinuxUpdateInstaller : DesktopUpdateInstaller {
    private val logger = logger<LinuxUpdateInstaller>()

    override fun getUpdatePreparationUrls(packageUrls: List<String>): List<String> =
        packageUrls.toAppImageZsyncUrls()

    override fun deleteOldUpdater() {
        // no-op
    }

    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        runBlocking {
            DesktopFileRevealer.revealFile(file)
        }
        return InstallationResult.Succeed
    }

    override suspend fun install(
        file: SystemPath,
        packageUrls: List<String>,
        context: ContextMP,
    ): InstallationResult = withContext(Dispatchers.IO) {
        val appImage = System.getenv("APPIMAGE")
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?: return@withContext failed("APPIMAGE is not set; the app was not started from an AppImage")

        if (!appImage.isFile) {
            return@withContext failed("APPIMAGE does not point to a file: ${appImage.absolutePath}")
        }
        if (!appImage.canWrite() || appImage.parentFile?.canWrite() != true) {
            return@withContext failed("The current AppImage is not writable: ${appImage.absolutePath}")
        }

        val updateInformation = packageUrls.toAppImageZsyncUrls().map { "zsync|$it" }
        if (updateInformation.isEmpty()) {
            return@withContext failed("No AppImage update URLs were provided")
        }

        val resourcesDir = System.getProperty("compose.application.resources.dir")
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?: return@withContext failed("Cannot find Compose application resources directory")
        val bundledUpdater = resourcesDir.resolve(LINUX_APPIMAGE_UPDATE_TOOL)
        if (!bundledUpdater.isFile) {
            return@withContext failed("Bundled AppImage update tool was not found: ${bundledUpdater.absolutePath}")
        }

        try {
            val tempDir = createTempDirectory(prefix = "animeko-appimage-update-").toFile()
            val updater = tempDir.resolve(LINUX_APPIMAGE_UPDATE_TOOL)
            bundledUpdater.copyTo(updater)
            check(updater.setExecutable(true)) { "Failed to make updater executable: ${updater.absolutePath}" }

            val script = tempDir.resolve("update.sh")
            script.writeText(LINUX_APPIMAGE_UPDATE_SCRIPT)
            check(script.setExecutable(true)) { "Failed to make update script executable: ${script.absolutePath}" }

            val command = buildList {
                add(script.absolutePath)
                add(appImage.absolutePath)
                add(updater.absolutePath)
                addAll(updateInformation)
            }
            logger.info { "Launching AppImage updater for ${appImage.absolutePath}" }
            val logFile = tempDir.resolve("update.log")
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
                .start()
            val exitCode = waitForUpdateProcess(process)
            if (exitCode == 0) {
                logger.info { "AppImage update completed successfully" }
                exitProcessForUpdate(delayMillis = 0)
            } else {
                failed("AppImage updater exited with code $exitCode. Log: ${logFile.absolutePath}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (throwable: Throwable) {
            logger.error(throwable) { "Failed to launch AppImage updater" }
            InstallationResult.Failed(
                InstallationFailureReason.FAILED_TO_COPY,
                throwable.message,
            )
        }
    }

    private suspend fun waitForUpdateProcess(process: Process): Int {
        try {
            return runInterruptible { process.waitFor() }
        } catch (e: CancellationException) {
            process.descendants().use { descendants ->
                descendants.forEach { it.destroy() }
            }
            process.destroy()
            runInterruptible {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.descendants().use { descendants ->
                        descendants.forEach { it.destroyForcibly() }
                    }
                    process.destroyForcibly().waitFor()
                }
            }
            throw e
        }
    }

    private fun failed(message: String): InstallationResult.Failed {
        logger.error { message }
        return InstallationResult.Failed(
            InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE,
            message,
        )
    }
}

internal const val LINUX_APPIMAGE_UPDATE_TOOL = "appimageupdatetool-x86_64.AppImage"

private val DMG_SUFFIX = Regex("\\.dmg$", RegexOption.IGNORE_CASE)

/**
 * 版本 API 返回的是各平台通用的完整安装包地址. Linux Release 将 zsync 元数据作为同名 AppImage 的
 * `.zsync` 伴随资源发布, 因此只在 Linux 安装准备阶段派生该地址, 而不向版本 API 引入平台专用字段.
 */
internal fun List<String>.toAppImageZsyncUrls(): List<String> = map { url ->
    if (url.endsWith(".zsync", ignoreCase = true)) url else "$url.zsync"
}

internal val LINUX_APPIMAGE_UPDATE_SCRIPT = $$"""
    #!/usr/bin/env bash
    set -euo pipefail

    APPIMAGE_PATH="$1"
    UPDATER_PATH="$2"
    shift 2

    APPIMAGE_DIR="$(dirname "$APPIMAGE_PATH")"
    APPIMAGE_NAME="$(basename "$APPIMAGE_PATH")"
    WORK_DIR="$(mktemp -d --tmpdir="$APPIMAGE_DIR" ".${APPIMAGE_NAME}.update.XXXXXX")"
    WORKING_APPIMAGE="$WORK_DIR/$APPIMAGE_NAME"

    cleanup() {
      rm -rf -- "$WORK_DIR"
    }
    trap cleanup EXIT
    trap 'exit 130' INT TERM

    # Keep the original path untouched while zsync downloads and rebuilds the new image.
    # A hard link avoids copying the full AppImage; copy-on-write is used as a fallback.
    if ! ln -- "$APPIMAGE_PATH" "$WORKING_APPIMAGE"; then
      cp --reflink=auto --preserve=mode -- "$APPIMAGE_PATH" "$WORKING_APPIMAGE"
    fi

    for UPDATE_INFORMATION in "$@"; do
      if "$UPDATER_PATH" --overwrite --update-info "$UPDATE_INFORMATION" "$WORKING_APPIMAGE"; then
        chmod a+x "$WORKING_APPIMAGE"
        # WORKING_APPIMAGE and APPIMAGE_PATH are on the same filesystem, so this is an atomic commit.
        mv -f -- "$WORKING_APPIMAGE" "$APPIMAGE_PATH"
        exit 0
      fi
    done

    echo "All AppImage update sources failed." >&2
    exit 1
""".trimIndent()

object WindowsUpdateInstaller : DesktopUpdateInstaller {
    private val logger = logger<WindowsUpdateInstaller>()

    override val installablePackageExtensions: Set<String> = setOf("zip")

    /**
     * 增量下载直接拼出解压好的目录, 更新器把它整个换进安装目录, 省掉写 zip 再解压.
     */
    override fun createDeltaPackageAssembler(client: ScopedHttpClient): DeltaPackageAssembler? {
        val appDir = ExecutableDirectoryDetector.INSTANCE.getExecutableDirectory()
        if (!appDir.resolve(APP_EXECUTABLE).exists()) {
            // 开发运行时不是安装目录, 没有可复用的文件.
            return null
        }
        return DesktopDeltaPackageAssembler(client, installRoot = appDir, output = PackageOutput.Directory)
    }

    /**
     * [file] 是增量下载拼出的新版本目录, 或整包 zip (整包回退、拖拽安装、开发版). 更新器 `tools/ani-update`
     * 两种都能装: 等本进程退出后把新版本换进安装目录, 再启动新版本.
     *
     * 更新器复制到临时目录运行而不是安装目录: 它要重命名安装目录, 自己不能在里面.
     */
    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        logger.info { "Installing update for Windows" }
        val appDir = ExecutableDirectoryDetector.INSTANCE.getExecutableDirectory()
        logger.info { "Current app dir: ${appDir.absolutePath}" }
        if (!appDir.resolve(APP_EXECUTABLE).exists()) {
            logger.info { "Current app dir does not have '$APP_EXECUTABLE'. Fallback to manual update" }
            return InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE)
        }

        val resourcesDir = File(
            System.getProperty("compose.application.resources.dir")
                ?: throw IllegalStateException("Cannot get resources directory"),
        )
        val updateExecutable = resourcesDir.resolve(UPDATER_EXECUTABLE)
        if (!updateExecutable.exists()) {
            logger.info { "'$UPDATER_EXECUTABLE' not found. Fallback to manual update" }
            return InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE)
        }

        val updaterDir = Files.createTempDirectory(UPDATER_TEMP_DIR_PREFIX).toFile()
        val updater = updaterDir.resolve(UPDATER_EXECUTABLE)
        updateExecutable.copyTo(updater, overwrite = true)

        val command = updaterCommand(
            updater = updater,
            appDir = appDir,
            source = file.toFile(),
            pid = ProcessHandle.current().pid(),
        )
        logger.info { "Launching updater: $command" }
        ProcessBuilder(command)
            .directory(updaterDir)
            .start()

        logger.info { "Installer started" }
        return exitProcessForUpdate(delayMillis = 0)
    }

    /**
     * 启动更新器的命令行. 经 `cmd /c start` 启动, 更新器在自己的控制台窗口里运行并显示进度.
     * [source] 是目录时按新版本目录安装, 否则按 zip 安装.
     */
    internal fun updaterCommand(updater: File, appDir: File, source: File, pid: Long): List<String> = listOf(
        "cmd", "/c", "start", "",
        updater.absolutePath,
        "--pid", pid.toString(),
        "--app-dir", appDir.absolutePath,
        if (source.isDirectory) "--staging" else "--archive", source.absolutePath,
    )

    /**
     * 启动时清理上次更新留下的东西: 旧版本放在安装目录里的更新器, 更新器交换目录失败时留下的 `<安装目录>.old*`,
     * 以及临时目录里的更新器副本. 删不掉的 (比如更新器进程还没退出) 下次再删.
     */
    override fun deleteOldUpdater() {
        val appDir = ExecutableDirectoryDetector.INSTANCE.getExecutableDirectory()
        appDir.resolve(UPDATER_EXECUTABLE).delete()
        appDir.parentFile?.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(appDir.name + ".old") }
            ?.forEach { leftover ->
                logger.info { "Deleting leftover of a previous update: $leftover" }
                leftover.deleteRecursively()
            }
        File(System.getProperty("java.io.tmpdir")).listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(UPDATER_TEMP_DIR_PREFIX) }
            ?.forEach { it.deleteRecursively() }
    }

    private const val APP_EXECUTABLE = "Ani.exe"
    private const val UPDATER_EXECUTABLE = "ani_update.exe"
    private const val UPDATER_TEMP_DIR_PREFIX = "ani-update-"
}
