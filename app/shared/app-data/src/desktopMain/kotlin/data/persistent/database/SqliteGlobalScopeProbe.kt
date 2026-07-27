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
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import me.him188.ani.utils.platform.currentPlatformDesktop
import me.him188.ani.utils.platform.isLinux

/**
 * Verification helper for constraint 2 of [BundledSqliteInterpositionGuard]: that the bundled
 * sqlite owns the `sqlite3_*` symbols in the process-wide global scope, and that a library loaded
 * into that scope afterwards cannot take them over.
 *
 * [BundledSqliteInterpositionGuard.mappedSqliteJniImages] cannot answer this — `/proc/self/maps`
 * shows which files are mapped, not which of them a symbol resolves to, and the system
 * `libsqlite3` is mapped by NSS in the healthy case too.
 *
 * Used by the `sqlite-bundled-load-test` CI task. Kept out of the guard itself because
 * [loadSystemSqliteIntoGlobalScope] deliberately does the thing the guard defends against.
 */
object SqliteGlobalScopeProbe {
    // dlopen(2) flags, Linux values — macOS uses different ones (RTLD_GLOBAL is 0x8 there), so
    // every entry point below is gated on Linux rather than relying on callers to do it.
    private const val RTLD_NOW = 0x2
    private const val RTLD_GLOBAL = 0x100

    /** `dlsym(3)` pseudo-handle for a global-scope lookup; NULL on glibc. */
    private val RTLD_DEFAULT: Pointer? = null

    @Suppress("FunctionName", "SpellCheckingInspection")
    private interface Dl : Library {
        fun dlsym(handle: Pointer?, symbol: String): Pointer?
        fun dladdr(addr: Pointer, info: DlInfo): Int
    }

    @Suppress("unused", "PropertyName", "SpellCheckingInspection")
    @Structure.FieldOrder("dli_fname", "dli_fbase", "dli_sname", "dli_saddr")
    class DlInfo : Structure() {
        @JvmField
        var dli_fname: String? = null

        @JvmField
        var dli_fbase: Pointer? = null

        @JvmField
        var dli_sname: String? = null

        @JvmField
        var dli_saddr: Pointer? = null
    }

    // glibc 2.34+ folded libdl into libc, but libdl.so.2 remains as a forwarding stub. Try it
    // first so older glibc keeps working, then fall back.
    private val dl: Dl by lazy {
        runCatching { Native.load("dl", Dl::class.java) }
            .getOrElse { Native.load("c", Dl::class.java) }
    }

    /**
     * Path of the library that currently owns `sqlite3_initialize` in the global scope, or `null`
     * if the symbol is not globally visible (which is itself fine — it means nothing has published
     * sqlite globally yet).
     */
    fun globalSqliteSymbolOwner(): String? {
        if (!currentPlatformDesktop().isLinux()) return null
        val symbol = dl.dlsym(RTLD_DEFAULT, "sqlite3_initialize") ?: return null
        val info = DlInfo()
        if (dl.dladdr(symbol, info) == 0) return null
        info.read()
        return info.dli_fname
    }

    /**
     * Does to this process what CEF does via NSS: publishes the *system* sqlite into the global
     * scope. If the guard ran first, its symbols were already bound by `RTLD_NOW` and stay in
     * front; if it ran late, this is what takes them over.
     *
     * @return false if the system library is not installed, in which case the check it supports
     * proves nothing and callers must say so rather than pass silently.
     */
    fun loadSystemSqliteIntoGlobalScope(): Boolean {
        if (!currentPlatformDesktop().isLinux()) return false
        val flags = mapOf(Library.OPTION_OPEN_FLAGS to (RTLD_NOW or RTLD_GLOBAL))
        // Distributions ship the runtime SONAME; the bare `libsqlite3.so` symlink comes with the
        // -dev package and is often absent.
        for (name in listOf("libsqlite3.so.0", "sqlite3")) {
            runCatching { NativeLibrary.getInstance(name, flags) }.onSuccess { return true }
        }
        return false
    }
}
