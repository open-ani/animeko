/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import com.sun.jna.Library
import com.sun.jna.NativeLibrary
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
import kotlin.io.path.readLines

/**
 * Works around a native symbol interposition crash on Linux (#3188).
 *
 * Room's [androidx.sqlite.driver.bundled.BundledSQLiteDriver] loads its bundled `libsqliteJni.so`
 * via `System.load` (`RTLD_LOCAL`, lazy binding). When JCEF starts, CEF pulls in NSS, which loads
 * the system `/usr/lib/libsqlite3.so` into the *global* symbol scope. From then on, any not-yet-
 * called `sqlite3_*` PLT entry inside `libsqliteJni.so` resolves to the system library, mixing two
 * sqlite builds with incompatible internal layouts. macOS (two-level namespace) and Windows
 * (per-module imports) are immune, so this guard is Linux-only.
 *
 * Mitigation: load the bundled library ourselves with `RTLD_NOW | RTLD_GLOBAL` before JCEF/NSS or
 * the driver touches sqlite, so its symbols bind to its own implementations first and occupy the
 * global scope; the whole process then uses exactly one sqlite build. The `...bundled.path`/`.name`
 * properties make the driver `System.load()` this same file instead of its own `RTLD_LOCAL` copy.
 *
 * ## Two independent ordering constraints, both required
 *
 * 1. **Before the driver loads.** Violating this puts *two* `libsqliteJni.so` images in the
 *    process: the driver's own `RTLD_LOCAL` copy under `/tmp` plus this one. The `/tmp` copy's
 *    `sqlite3_*` PLT entries then bind to our globally-scoped copy on first call, so the JNI entry
 *    points and the sqlite implementation live in different libraries, each with its own
 *    `sqlite3GlobalConfig`. The one receiving calls never ran `sqlite3_initialize`, leaving
 *    `xMalloc` NULL — `SIGSEGV` at `pc=0` in `dbMallocRawFinish`. This is the regression #3195
 *    introduced by installing the guard from a coroutine that raced Koin's driver construction.
 *
 * 2. **Before JCEF/NSS.** Violating this lets the system `libsqlite3` occupy the global scope
 *    first; `RTLD_NOW` then binds *our* `sqlite3_*` references to it. This is the original #3188 —
 *    `SIGSEGV` at `pc=0` in `walIndexReadHdr`.
 *
 * Constraint 1 holds structurally: [Context.createDatabaseBuilder] installs the guard before
 * handing out a builder, so no `BundledSQLiteDriver` can be constructed ahead of it. Constraint 2
 * cannot be enforced that way — nothing about JCEF startup routes through this class — and is why
 * the desktop `main()` still calls [install] explicitly, ahead of JCEF initialization.
 *
 * Trade-offs: NSS will use the bundled sqlite instead of the system one (format-compatible, low
 * risk); the guard relies on the semi-internal property and jar resource layout, so an
 * androidx.sqlite upgrade could silently break it — failure mode is a logged warning plus the
 * original racy behavior. `BundledSqliteInterpositionGuardTest` is the canary for that upgrade.
 * The proper fix is upstream compiling `sqlite3_*` with hidden visibility.
 */
object BundledSqliteInterpositionGuard {
    private val logger = logger<BundledSqliteInterpositionGuard>()

    // dlopen(2) flags on Linux.
    private const val RTLD_NOW = 0x2
    private const val RTLD_GLOBAL = 0x100

    /** Fixed name of the extracted copy, so [mappedSqliteJniImages] callers can recognise it. */
    const val LIB_FILE_NAME = "ani-bundled-sqliteJni.so"

    private val lock = Any()

    // Strong reference to the preloaded library. JNA 5.x registers a Cleaner that dlclose()es the
    // native handle when the NativeLibrary instance is GC'd, and JNA's own cache only keeps a
    // WeakReference — dropping this reference would let a GC undo the preload and reintroduce
    // the race this guard exists to prevent.
    private var preloadedLibrary: NativeLibrary? = null

    /**
     * Preloads the bundled `libsqliteJni.so` into the global symbol scope. No-op off Linux, and
     * no-op on every call after the first: [install] is reached from both desktop startup and
     * [Context.createDatabaseBuilder], and re-running it would rewrite the properties the driver
     * may already have read.
     *
     * @param cacheDir app-owned directory used to hold the extracted library. A stable location
     * with a fixed name (rewritten only when the bundled bytes change) avoids leaving stale
     * `.so` files in the system temp directory after abnormal exits.
     */
    fun install(cacheDir: Path) {
        if (!currentPlatformDesktop().isLinux()) return
        synchronized(lock) {
            if (preloadedLibrary != null) return
            try {
                install0(cacheDir)
            } catch (e: Throwable) {
                // Never break startup because of this workaround; the crash it prevents is a race anyway.
                logger.warn(e) { "Failed to preload bundled libsqliteJni with RTLD_GLOBAL" }
            }
        }
    }

    /**
     * Backing files of every `sqliteJni` mapping this process currently holds, as seen by the
     * kernel. Exactly one entry, ending in [LIB_FILE_NAME], is the invariant both ordering
     * constraints exist to preserve; anything else is one of the two crashes described above.
     *
     * Linux-only — returns an empty set elsewhere, where the problem does not exist.
     */
    fun mappedSqliteJniImages(): Set<String> {
        if (!currentPlatformDesktop().isLinux()) return emptySet()
        // A /proc/self/maps line ends with the mapped path, e.g.
        // `7f..-7f.. r-xp 00000000 00:1b 123  /home/u/.cache/ani/ani-bundled-sqliteJni.so`.
        return Path.of("/proc/self/maps").readLines()
            .mapNotNull { line ->
                line.substringAfter(" /", missingDelimiterValue = "")
                    .let { if (it.isEmpty()) null else "/$it" }
            }
            .filter { it.substringAfterLast('/').contains("sqliteJni", ignoreCase = true) }
            .toSet()
    }

    private fun install0(cacheDir: Path) {
        Files.createDirectories(cacheDir)

        val resource = when (System.getProperty("os.arch")) {
            "aarch64" -> "natives/linux_arm64/libsqliteJni.so"
            else -> "natives/linux_x64/libsqliteJni.so"
        }
        val bytes = BundledSqliteInterpositionGuard::class.java.classLoader
            .getResourceAsStream(resource)?.use { it.readBytes() }
            ?: error("Resource $resource not found on classpath")

        val libFile = cacheDir.resolve(LIB_FILE_NAME)
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
