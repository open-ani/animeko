/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("FunctionName")

package me.him188.ani.app.desktop.storage

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.W32APIOptions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal object WindowsJnaAppFolderResolver : AppFolderResolver {

    // 2) JNA interface to shell32.dll
    private interface Shell32 : Library {
        /**
         * SHGetFolderPathW:
         * https://learn.microsoft.com/en-us/windows/win32/api/shlobj_core/nf-shlobj_core-shgetfolderpathw
         */
        fun SHGetFolderPathW(
            hwndOwner: Pointer?,
            nFolder: Int,
            hToken: Pointer?,
            dwFlags: Int,
            pszPath: CharArray?
        ): Int

        companion object {
            val INSTANCE: Shell32 = Native.load(
                "shell32",
                Shell32::class.java,
                W32APIOptions.DEFAULT_OPTIONS,
            )
        }
    }

    // 3) Constants for folder identifiers:
    private const val CSIDL_APPDATA = 0x001A       // Roaming app data folder
    private const val CSIDL_LOCAL_APPDATA = 0x001C // Local app data folder
    private const val MAX_PATH = 260              // Typical Windows MAX_PATH

    /**
     * 4) Retrieve the Roaming AppData folder and append subdirectories.
     */
    private fun getRoamingAppDataDirectory(
        organizationName: String,
        applicationName: String
    ): Path {
        val pathBuffer = CharArray(MAX_PATH)
        val result = Shell32.INSTANCE.SHGetFolderPathW(null, CSIDL_APPDATA, null, 0, pathBuffer)

        val appDataPath = if (result == 0) {
            // Success: convert returned buffer to String
            Native.toString(pathBuffer)
        } else {
            // Fallback to %APPDATA%
            System.getenv("APPDATA")
                ?: throw RuntimeException("Failed to retrieve APPDATA. SHGetFolderPath error code: $result")
        }

        val targetDir = Paths.get(appDataPath, organizationName, applicationName)
        ensureDirectoriesExist(targetDir)
        return targetDir
    }

    /**
     * 5) Retrieve the Local AppData folder and append subdirectories.
     */
    private fun getLocalAppDataDirectory(
        organizationName: String,
        applicationName: String
    ): Path {
        val pathBuffer = CharArray(MAX_PATH)
        val result = Shell32.INSTANCE.SHGetFolderPathW(null, CSIDL_LOCAL_APPDATA, null, 0, pathBuffer)

        val localAppDataPath = if (result == 0) {
            // Success
            Native.toString(pathBuffer)
        } else {
            // Fallback to %LOCALAPPDATA%
            System.getenv("LOCALAPPDATA")
                ?: throw RuntimeException("Failed to retrieve LOCALAPPDATA. SHGetFolderPath error code: $result")
        }

        val targetDir = Paths.get(localAppDataPath, organizationName, applicationName)
        ensureDirectoriesExist(targetDir)
        return targetDir
    }

    /**
     * 6) Public function that returns a data class holding both paths.
     */
    @JvmStatic
    fun getAppDataDirectories(
        organizationName: String,
        applicationName: String
    ): AppDataDirectories {
        val roamingDir = getRoamingAppDataDirectory(organizationName, applicationName)
        val localDir = getLocalAppDataDirectory(organizationName, applicationName)
        return AppDataDirectories(roamingDir.resolve("data"), localDir.resolve("cache"))
    }

    /**
     * Helper function to create directories if they don't already exist.
     */
    private fun ensureDirectoriesExist(dir: Path) {
        if (Files.notExists(dir)) {
            try {
                Files.createDirectories(dir)
            } catch (e: Exception) {
                throw RuntimeException("Failed to create or access directory: $dir", e)
            }
        }
    }

    override fun resolve(appInfo: AppInfo): AppDataDirectories =
        getAppDataDirectories(appInfo.organization, appInfo.name)
}
