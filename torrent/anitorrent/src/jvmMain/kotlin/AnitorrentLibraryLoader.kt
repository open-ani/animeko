/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.torrent.api.TorrentLibraryLoader
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.NativeLibraryLoader
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isAndroid
import java.io.File
import kotlin.concurrent.Volatile

object AnitorrentLibraryLoader : TorrentLibraryLoader {
    private val logger = logger<AnitorrentLibraryLoader>()

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

    // appResources/macos-arm64/anitorrent
    private fun getAnitorrentResourceDir(): File = NativeLibraryLoader.getResourceDir("anitorrent")

    @Suppress("UnsafeDynamicallyLoadedCode") // This code only runs on desktop
    private fun loadLibrary(libraryFilename: String) {
        val dir = getAnitorrentResourceDir().resolve("lib")
        dir.resolve(libraryFilename).let {
            if (!it.exists()) {
                throw UnsatisfiedLinkError("Anitorrent library not found: $it")
            }
            System.load(it.absolutePath)
        }
    }

    private fun loadDependencies() {
        if (currentPlatform().isAndroid()) {
            System.loadLibrary("anitorrent")
            return
        }

        val dir = getAnitorrentResourceDir()
        val map = Json.decodeFromString(
            JsonObject.serializer(),
            dir.resolve("anitorrent.deps.json").readText(),
        ).map {
            it.key to it.value.jsonPrimitive.content
        }
        for ((name, library) in map) {
            logger.info { "Loading library $name from: $library" }
            loadLibrary(library)
        }
    }

    @Synchronized
    @Throws(UnsatisfiedLinkError::class)
    override fun loadLibraries() {
        if (libraryLoaded) return

        try {
            loadDependencies()
            _initAnitorrent
            libraryLoaded = true
        } catch (e: Throwable) {
            libraryLoaded = false
            throw e
        }
    }
}