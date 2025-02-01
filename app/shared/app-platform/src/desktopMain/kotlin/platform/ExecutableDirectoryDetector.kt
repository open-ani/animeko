/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.W32APIOptions
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatformDesktop
import java.io.File

interface ExecutableDirectoryDetector {
    /**
     * Returns the absolute directory containing the actual .exe on Windows.
     */
    fun getExecutableDirectory(): File

    companion object {
        val INSTANCE: ExecutableDirectoryDetector by lazy {
            when (currentPlatformDesktop()) {
                is Platform.Linux -> SystemPropertyExecutableDirectoryDetector
                is Platform.MacOS -> SystemPropertyExecutableDirectoryDetector
                is Platform.Windows -> WindowsExecutableDirectoryDetector
            }
        }
    }
}

object SystemPropertyExecutableDirectoryDetector : ExecutableDirectoryDetector {
    override fun getExecutableDirectory(): File {
        val path = System.getProperty("user.dir")
        return File(path).absoluteFile
    }
}

object WindowsExecutableDirectoryDetector : ExecutableDirectoryDetector() {
    @Suppress("FunctionName")
    interface MyKernel32 : Kernel32 {

        /**
         * Retrieves the fully qualified path for the file that contains the specified module.
         * If this parameter is null, GetModuleFileName retrieves the path of the executable file
         * of the current process.
         *
         * @param hModule A handle to the loaded module whose path is being requested. If null,
         *                this function returns the path of the current process's executable.
         * @param lpFilename A buffer that receives the fully qualified path of the module.
         * @param nSize The size of the lpFilename buffer, in characters.
         * @return The length of the string copied to lpFilename.
         *
         * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/libloaderapi/nf-libloaderapi-getmodulefilenamew">
         *      Microsoft Docs: GetModuleFileNameW</a>
         */
        fun GetModuleFileNameW(
            hModule: WinDef.HMODULE?,
            lpFilename: CharArray,
            nSize: Int
        ): Int

        companion object {
            val INSTANCE: MyKernel32 by lazy {
                Native.load(
                    "kernel32",
                    MyKernel32::class.java,
                    W32APIOptions.DEFAULT_OPTIONS,
                )
            }
        }
    }

    /**
     * Returns the absolute directory containing the actual .exe on Windows.
     */
    override fun getExecutableDirectory(): File {
        val buffer = CharArray(1024)
        val length = MyKernel32.INSTANCE.GetModuleFileNameW(null, buffer, buffer.size)
        val fullPath = String(buffer, 0, length)
        return File(fullPath).parentFile
    }
}