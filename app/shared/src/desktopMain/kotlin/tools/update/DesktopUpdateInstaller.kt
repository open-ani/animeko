/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.ExecutableDirectoryDetector
import me.him188.ani.app.platform.features.DesktopFileRevealer
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Platform
import java.awt.Desktop
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

interface DesktopUpdateInstaller : UpdateInstaller {
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

        val dmgFile = file.toFile()
        if (!dmgFile.exists()) {
            return failed("DMG file does not exist: ${dmgFile.absolutePath}")
        }

        val tempDir = createTempDirectory(prefix = "ani-macos-update-").toFile()
        val scriptFile = File(tempDir, "macos-update.command")
        logger.info { "tempMountDir: ${tempDir.absolutePath}" }

        // We’ll pass in some essential parameters to the script.
        // 1) oldPid = our current process PID
        // 2) path to the DMG
        // 3) path to the mount dir
        // 4) the current .app name (e.g. Ani.app)
        // 5) the parent directory where the new app should be copied
        val oldPid = ProcessHandle.current().pid()
        val appName = appDir.name  // e.g. Ani.app
        val targetParentDir = appDir.parentFile.absolutePath

        // Generate the shell script content
        val scriptContent = generateShellScript(
            oldPid = oldPid,
            dmgFilePath = dmgFile.absolutePath,
            convertedDmgFilePath = tempDir.resolve("converted.dmg").absolutePath,
            mountPath = tempDir.resolve("mount").absolutePath,
            appName = appName,
            targetParent = targetParentDir,
        )

        // Write the script to disk and make it executable
        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true)

        // Now run the script
        logger.info { "Launching update script: ${scriptFile.absolutePath}" }
        ProcessBuilder(scriptFile.absolutePath)
            .redirectOutput(File(tempDir, "update-output.log"))
            .redirectError(File(tempDir, "update-error.log"))
            .start()

        logger.info { "Exiting old instance." }
        Thread.sleep(1000)
        exitProcess(0)
    }

    /**
     * Generates the shell script that:
     * 1) Waits for the old process to exit
     * 2) Converts + mounts the DMG
     * 3) Copies the .app into place
     * 4) Removes the quarantine attribute
     * 5) Detaches the DMG
     * 6) Cleans up
     * 7) Launches the new app
     */
    private fun generateShellScript(
        oldPid: Long,
        dmgFilePath: String,
        convertedDmgFilePath: String,
        mountPath: String,
        appName: String,
        targetParent: String,
    ): String {
        return $$"""
            #!/usr/bin/env bash
            set -euo pipefail

            OLD_PID=$$oldPid
            DMG_FILE="$$dmgFilePath"
            MOUNT_DIR="$$mountPath"
            APP_NAME="$$appName"
            TARGET_PARENT="$$targetParent"

            echo "Update script started."
            echo "Will wait for process PID=$$oldPid to exit."

            # 1) Wait for the old process to fully exit.
            #    We'll loop until kill -0 fails (meaning process does not exist).
            while kill -0 "$OLD_PID" 2>/dev/null; do
              echo "Waiting for old app process $OLD_PID to exit..."
              sleep 1
            done

            # 2) Convert the DMG to a CDR (UDTO format)
            echo "Converting DMG into CDR..."
            NEW_DMG_FILE="$$convertedDmgFilePath"
            hdiutil convert "$DMG_FILE" -format UDTO -o "$NEW_DMG_FILE"

            # 3) Mount the converted DMG (the .cdr will appear after conversion)
            echo "Mounting the DMG at $MOUNT_DIR ..."
            hdiutil attach "${NEW_DMG_FILE}.cdr" -nobrowse -noverify -noautoopen -mountpoint "$MOUNT_DIR"

            # 4) Copy the updated .app from the DMG to the parent of the current .app
            echo "Copying updated app to $TARGET_PARENT ..."
            cp -R "$MOUNT_DIR/$APP_NAME" "$TARGET_PARENT"

            # 5) Detach the DMG
            echo "Detaching the DMG..."
            hdiutil detach "$MOUNT_DIR" || echo "Warning: failed to detach DMG."

            # 6) Remove the com.apple.quarantine attribute
            echo "Removing quarantine..."
            xattr -r -d com.apple.quarantine "$TARGET_PARENT/$APP_NAME" || true

            # 7) Clean up the temporary mount directory
            echo "Cleaning up temporary mount directory..."
            rm -rf "$MOUNT_DIR"

            # 8) Launch the newly copied app
            echo "Launching updated app: $TARGET_PARENT/$APP_NAME"
            open "$TARGET_PARENT/$APP_NAME"

            echo "Update script finished."
        """.trimIndent()
    }

    override fun deleteOldUpdater() {
        // Noop or your implementation
    }

    private fun failed(
        message: String,
        reason: InstallationFailureReason? = InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE
    ): InstallationResult.Failed {
        logger.error { message }
        return InstallationResult.Failed(reason ?: InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE, message)
    }
}

object LinuxUpdateInstaller : DesktopUpdateInstaller {
    override fun deleteOldUpdater() {
        // no-op
    }

    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        Desktop.getDesktop().open(file.toFile())
        exitProcess(0)
    }
}

object WindowsUpdateInstaller : DesktopUpdateInstaller {
    private val logger = logger<WindowsUpdateInstaller>()

    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        logger.info { "Installing update for Windows" }
        val appDir = ExecutableDirectoryDetector.INSTANCE.getExecutableDirectory()
        logger.info { "Current app dir: ${appDir.absolutePath}" }
        if (!appDir.resolve("Ani.exe").exists()) {
            logger.info { "Current app dir does not have 'Ani.exe'. Fallback to manual update" }
            return InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE)
        }

        val resourcesDir = File(
            System.getProperty("compose.application.resources.dir")
                ?: throw IllegalStateException("Cannot get resources directory"),
        )
        val updateExecutable = resourcesDir.resolve("ani_update.exe")
        if (!updateExecutable.exists()) {
            logger.info { "'ani_update.exe' not found. Fallback to manual update" }
            return InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE)
        }

        // Copy ani_update.exe to current dir
        val copiedUpdateExecutable = appDir.resolve("ani_update.exe")
        updateExecutable.copyTo(copiedUpdateExecutable, true)

        ProcessBuilder(
            "cmd", "/c", "start", "cmd", "/c",
            "\"", copiedUpdateExecutable.absolutePath, file.absolutePath, appDir.absolutePath, "\"",
        )
            .directory(appDir)
            .start()

        logger.info { "Installer started" }
        exitProcess(0)
    }

    override fun deleteOldUpdater() {
        val appDir = ExecutableDirectoryDetector.INSTANCE.getExecutableDirectory()
        val updateExecutable = appDir.resolve("ani_update.exe")
        if (updateExecutable.exists()) {
            updateExecutable.delete()
        }
    }
}
