/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent

import me.him188.ani.app.torrent.api.TorrentLibraryLoader
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isAndroid
import org.openani.anitorrent.binding.anitorrentJNI
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream

object AnitorrentLibraryLoader : TorrentLibraryLoader {
    private val logger = logger<AnitorrentLibraryLoader>()
    private val platform = currentPlatform()

    @Volatile
    private var libraryLoaded = false

    private val _initAnitorrent by lazy {
        // 注意, JVM 也会 install signal handler, 它需要 sig handler 才能工作. 
        // 这里覆盖之后会导致 JVM crash (SIGSEGV/SIGBUS). crash 如果遇到一个无 symbol 的比较低的地址, 那就大概率是 JVM.
        // 应当仅在需要 debug 一个已知的 anitorrent 的 crash 时才开启这个.
        // 其实不开的话, OS 也能输出 crash report. macOS 输出的 crash report 会包含 native 堆栈.

        // 如果需要调试, 可以在 anitorrent 搜索 ENABLE_TRACE_LOGGING 并修改为 true. 将会打印非常详细的 function call 记录.

//    anitorrent.install_signal_handlers()
    }

    @kotlin.jvm.Throws(IOException::class)
    private fun loadDependencies() {
        if (platform.isAndroid()) {
            System.loadLibrary("anitorrent")
            return
        }
        val platform = platform as Platform.Desktop
        logger.info { "Loading anitorrent library" }
        logger.info { "java.library.path: ${System.getProperty("java.library.path")}" }
        try {
            System.loadLibrary("anitorrent")
            logger.info { "Loading anitorrent library: success (from java.library.path)" }
        } catch (firstE: UnsatisfiedLinkError) {
            try {
                // 可能是调试状态, 从 resources 加载
                logger.info { "Failed to load anitorrent directly from java.library.path, trying resources instead" }
                val temp = getTempDirForPlatform()
                logger.info { "Temp dir: ${temp.absolutePathString()}" }
                when (platform) {
                    is Platform.Windows -> {
                        extractLibraryFromResources("libssl-3-x64", temp)?.let {
                            logger.info { "Extract ssl: ${it.absolutePathString()}" }
                        }
                        extractLibraryFromResources("libcrypto-3-x64", temp)?.let {
                            logger.info { "Extract crypto: ${it.absolutePathString()}" }
                        }
                        loadLibraryFromResources("torrent-rasterbar", temp)
                    }

                    is Platform.MacOS -> {
                        // noop
                    }

                    is Platform.Linux -> {
                        TODO("loadDependencies for Linux")
                    }
                }
                loadLibraryFromResources("anitorrent", temp)
            } catch (e: Throwable) {
                e.addSuppressed(firstE)
            }
            logger.info { "Loading anitorrent library: success (from resources)" }
        }
    }

    private fun getTempDirForPlatform(): Path {
        return Paths.get(System.getProperty("user.dir")) // macos 也得输出到当前目录, 因为 link path 只包含一些系统路径和 .
//        return if (platform is Platform.Windows) {
//            Paths.get(System.getProperty("user.dir"))
//        } else {
//            Files.createTempDirectory("libanitorrent${Random.nextInt().absoluteValue}").apply {
//                Runtime.getRuntime().addShutdownHook(
//                    Thread {
//                        try {
//                            deleteRecursively()
//                        } catch (e: IOException) {
//                            logger.error(e) { "Failed to delete temp directory $this" }
//                        }
//                    },
//                )
//            }
//        }
    }


    private fun makeDesktopPlatformName(name: String): String {
        return when (platform as Platform.Desktop) {
            is Platform.Linux -> "lib$name.so"
            is Platform.Windows -> "$name.dll"
            is Platform.MacOS -> "lib$name.dylib"
        }
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun extractLibraryFromResources(
        name: String,
        tempDir: Path,
        destinationName: String = name,
    ): Path? {
        val filename = makeDesktopPlatformName(name)
        this::class.java.classLoader?.getResourceAsStream(filename)?.use {
            val tempFile = tempDir.resolve(makeDesktopPlatformName(destinationName))
            tempFile.outputStream().use { output ->
                it.copyTo(output)
            }
            return tempFile
        }
        return null
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun loadLibraryFromResources(
        name: String,
        tempDir: Path
    ) {
        extractLibraryFromResources(name, tempDir)?.let {
            System.load(it.absolutePathString())
        } ?: throw UnsatisfiedLinkError("Failed to extract library $name from resources (possibly no such resource)")
    }

    @Synchronized
    @Throws(UnsatisfiedLinkError::class)
    override fun loadLibraries() = synchronized(this) {
        if (libraryLoaded) return

        try {
            loadDependencies()
            _initAnitorrent
            anitorrentJNI.lt_version() // check SWIG init
            libraryLoaded = true
        } catch (e: Throwable) {
            libraryLoaded = false
            throw e
        }
    }
}