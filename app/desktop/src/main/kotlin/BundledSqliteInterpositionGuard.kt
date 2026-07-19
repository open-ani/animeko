/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import com.sun.jna.Library
import com.sun.jna.NativeLibrary
import me.him188.ani.app.platform.AniCefApp
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentPlatformDesktop
import me.him188.ani.utils.platform.isLinux
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

/**
 * Works around a native symbol interposition crash on Linux (#3188).
 *
 * Room's [androidx.sqlite.driver.bundled.BundledSQLiteDriver] loads its bundled `libsqliteJni.so`
 * via `System.load` (`RTLD_LOCAL`, lazy binding). When JCEF starts, CEF pulls in NSS, which loads
 * the system `/usr/lib/libsqlite3.so` into the *global* symbol scope. From then on, any not-yet-
 * called `sqlite3_*` PLT entry inside `libsqliteJni.so` resolves to the system library, mixing two
 * sqlite builds with incompatible internal layouts — observed as `SIGSEGV` in
 * `sqlite3_bind_text16` on the home screen's first DB query. Whether a machine crashes is a pure
 * load-order race. macOS (two-level namespace) and Windows (per-module imports) are immune, so
 * this guard is Linux-only.
 *
 * Mitigation: load the bundled library ourselves with `RTLD_NOW | RTLD_GLOBAL` before JCEF/NSS or
 * the driver touches sqlite, so its symbols bind to its own implementations first and occupy the
 * global scope; the whole process then uses exactly one sqlite build. The `...bundled.path`/`.name`
 * properties make the driver `System.load()` this same file instead of its own `RTLD_LOCAL` copy.
 *
 * Trade-offs: NSS will use the bundled sqlite instead of the system one (format-compatible, low
 * risk); the guard relies on the semi-internal property and jar resource layout, so an
 * androidx.sqlite upgrade could silently break it — failure mode is a logged warning plus the
 * original racy behavior. The proper fix is upstream compiling `sqlite3_*` with hidden visibility.
 *
 * Must be called before database initialization and before [AniCefApp.initialize]. It runs as
 * the first step of the desktop `loadLibraryJob`, which the JCEF init coroutine joins.
 */
object BundledSqliteInterpositionGuard {
    private val logger = logger<BundledSqliteInterpositionGuard>()

    // dlopen(2) flags on Linux.
    private const val RTLD_NOW = 0x2
    private const val RTLD_GLOBAL = 0x100

    // Strong reference to the preloaded library. JNA 5.x registers a Cleaner that dlclose()es the
    // native handle when the NativeLibrary instance is GC'd, and JNA's own cache only keeps a
    // WeakReference — dropping this reference would let a GC undo the preload and reintroduce
    // the race this guard exists to prevent.
    private var preloadedLibrary: NativeLibrary? = null

    /**
     * @param cacheDir app-owned directory used to hold the extracted library. A stable location
     * with a fixed name (rewritten only when the bundled bytes change) avoids leaving stale
     * `.so` files in the system temp directory after abnormal exits.
     */
    fun install(cacheDir: Path) {
        if (!currentPlatformDesktop().isLinux()) return
        try {
            install0(cacheDir)
        } catch (e: Throwable) {
            // Never break startup because of this workaround; the crash it prevents is a race anyway.
            logger.warn(e) { "Failed to preload bundled libsqliteJni with RTLD_GLOBAL" }
        }
    }

    private fun install0(cacheDir: Path) {
        val resource = when (System.getProperty("os.arch")) {
            "aarch64" -> "natives/linux_arm64/libsqliteJni.so"
            else -> "natives/linux_x64/libsqliteJni.so"
        }
        val bytes = BundledSqliteInterpositionGuard::class.java.classLoader
            .getResourceAsStream(resource)?.use { it.readBytes() }
            ?: error("Resource $resource not found on classpath")

        val libFile = cacheDir.resolve("ani-bundled-sqliteJni.so")
        if (!Files.exists(libFile) || !Files.readAllBytes(libFile).contentEquals(bytes)) {
            // Write to a sibling temp file and move atomically: the target may still be mapped
            // by a previous process (or this one), and in-place writes to a mapped .so can SIGBUS.
            val tmp = Files.createTempFile(cacheDir, "ani-bundled-sqliteJni", ".tmp")
            Files.write(tmp, bytes)
            try {
                Files.move(tmp, libFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, libFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        // Load with RTLD_GLOBAL | RTLD_NOW so the bundled sqlite symbols occupy the global scope
        // before CEF/NSS can load the system libsqlite3.
        preloadedLibrary = NativeLibrary.getInstance(
            libFile.absolutePathString(),
            mapOf(Library.OPTION_OPEN_FLAGS to (RTLD_NOW or RTLD_GLOBAL)),
        )

        // Make BundledSQLiteDriver System.load() the same file rather than its own RTLD_LOCAL copy.
        System.setProperty("androidx.sqlite.driver.bundled.path", libFile.parent.absolutePathString())
        System.setProperty("androidx.sqlite.driver.bundled.name", libFile.fileName.toString())

        logger.info { "Preloaded bundled libsqliteJni ($libFile) with RTLD_GLOBAL to avoid symbol interposition with the system libsqlite3." }
    }
}
