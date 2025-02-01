/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.W32APIOptions
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.FileOpener
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Platform
import org.intellij.lang.annotations.Language
import java.awt.Desktop
import java.io.File
import kotlin.system.exitProcess

interface DesktopUpdateInstaller : UpdateInstaller {
    override fun openForManualInstallation(file: SystemPath, context: ContextMP) {
        FileOpener.openInFileBrowser(file)
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
    override fun deleteOldUpdater() {
        // no-op
    }

    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        Desktop.getDesktop().open(file.toFile())
        exitProcess(0)
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
        val appDir = getExecutableDirectory()
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
        val appDir = File(System.getProperty("user.dir") ?: throw IllegalStateException("Cannot get app directory"))
        val updateExecutable = appDir.resolve("ani_update.exe")
        if (updateExecutable.exists()) {
            updateExecutable.delete()
        }
    }

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
            val INSTANCE: MyKernel32 = Native.load(
                "kernel32",
                MyKernel32::class.java,
                W32APIOptions.DEFAULT_OPTIONS,
            )
        }
    }

    /**
     * Returns the absolute directory containing the actual .exe on Windows.
     */
    fun getExecutableDirectory(): File {
        val buffer = CharArray(1024)
        val length = MyKernel32.INSTANCE.GetModuleFileNameW(null, buffer, buffer.size)
        val fullPath = String(buffer, 0, length)
        return File(fullPath).parentFile
    }

    @Language("cmd")
    private fun getInstallerScript(
        zipFile: File,
    ) = """
setlocal

rmdir /S /Q ".\Ani"

echo Extracting update.zip to the current folder
powershell -Command "Expand-Archive -Path '${zipFile.absolutePath}' -DestinationPath '.' -Force"

echo Waiting until Ani.exe ends
:waitloop
tasklist /FI "IMAGENAME eq Ani.exe" 2>NUL | find /I /N "Ani.exe">NUL
if "%ERRORLEVEL%"=="0" (
    timeout /T 1 /NOBREAK > NUL
    goto waitloop
)

echo Deleting the specified files and directories
del /F /Q "Ani.exe"
del /F /Q "Ani.ico"
rmdir /S /Q "app"
rmdir /S /Q "runtime"

echo Copying everything from ./Ani to ./
xcopy /E /H /R /Y ".\Ani\*" "."

echo Deleting the extracted Ani directory
rmdir /S /Q ".\Ani"

echo Deleting the update zip file
del /F /Q "${zipFile.absolutePath}"
del /F /Q "${zipFile.absolutePath}.sha256"

echo Launching Ani.exe
start "" ".\Ani.exe"

echo Exiting script
exit
    """.trimIndent()

//    private fun getInstallerBat(
//        zipfile: String,
//    ) = """
//        setlocal
//    
//        set "ZIPFILE=$zipfile"
//        set "ANIPROCESS=Ani.exe"
//        set "TEMP_DIR=Ani"
//    
//        echo "安装包目录: %ZIPFILE%"
//        
//        REM Wait until Ani.exe ends
//        :checkProcess
//        tasklist /FI "IMAGENAME eq %ANIPROCESS%" 2>NUL | find /I /N "%ANIPROCESS%">NUL
//        if "%ERRORLEVEL%"=="0" (
//            echo "正在等待 Ani 程序关闭..."
//            timeout /T 1 >NUL
//            goto checkProcess
//        )
//    
//        REM Extract the zip file
//        echo "正在解压缩安装包 %ZIPFILE%..."
//        powershell -Command "Expand-Archive -Path '%ZIPFILE%' -DestinationPath ."
//        
//        pause
//        
//        echo "正在卸载旧版本..."
//    
//        rmdir /s /q "app"
//        rmdir /s /q "runtime"
//        rmdir /s /q "Ani.exe"
//        rmdir /s /q "Ani.ico"
//        
//        pause
//        
//        REM Copy files from Ani directory to current directory
//        echo "正在安装新版本..."
//        xcopy "%TEMP_DIR%\*" ".\" /E /Y
//        echo "安装成功"
//        
//        pause
//        
//        echo "正在清理临时文件..."
//        
//        rmdir /s /q "Ani"
//        
//        echo "安装全部完成, 正在启动 Ani..."
//        
//        start "" "%ANIPROCESS%"
//        
//        echo "本自动更新工具将在 5 秒后自动关闭..."
//        timeout /t 5 /nobreak >nul
//
//        REM Exit
//        endlocal
//        exit /b 0
//""".trimIndent()

//    del "%ZIPFILE%"
}
