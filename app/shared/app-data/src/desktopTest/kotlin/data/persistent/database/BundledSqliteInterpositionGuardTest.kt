/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression guard for #3188 / #3195 / #3213.
 *
 * The crash these tests protect against is a load-order race that cannot be triggered on demand,
 * so they assert the *invariant* instead of the symptom: after the guard runs, the process must
 * hold exactly one `libsqliteJni.so` image and the bundled sqlite must own the `sqlite3_*` symbols
 * in the global scope.
 *
 * This also acts as the canary for androidx.sqlite upgrades — the redirect relies on the
 * semi-internal `androidx.sqlite.driver.bundled.path`/`.name` properties, and an upgrade that
 * stops honouring them shows up here as a second image.
 */
@EnabledOnOs(
    value = [OS.LINUX],
    disabledReason = "ELF global symbol interposition; macOS and Windows are immune by design.",
)
class BundledSqliteInterpositionGuardTest {
    @Test
    fun `driver reuses the preloaded image instead of extracting its own`() {
        BundledSqliteInterpositionGuard.install(Files.createTempDirectory("ani-sqlite-guard-test"))

        // Forces BundledSQLiteDriver to load its JNI library, which is what would extract a second
        // copy into /tmp if the redirect properties were not in place before this point.
        BundledSQLiteDriver().open(":memory:").use { }

        val mappings = BundledSqliteInterpositionGuard.mappedSqliteJniImages()
        assertEquals(
            1, mappings.size,
            "expected exactly one libsqliteJni image in the process, got $mappings",
        )
        assertTrue(
            mappings.single().endsWith(BundledSqliteInterpositionGuard.LIB_FILE_NAME),
            "the mapped image is not the guard's copy: ${mappings.single()}",
        )
    }

    @Test
    fun `install is idempotent`() {
        BundledSqliteInterpositionGuard.install(Files.createTempDirectory("ani-sqlite-guard-test"))
        val path = System.getProperty("androidx.sqlite.driver.bundled.path")
        val name = System.getProperty("androidx.sqlite.driver.bundled.name")

        // A second call must not repoint the driver at a different file: by the time anything calls
        // install() twice, the driver may already have resolved these properties.
        BundledSqliteInterpositionGuard.install(Files.createTempDirectory("ani-sqlite-guard-test-2"))

        assertEquals(path, System.getProperty("androidx.sqlite.driver.bundled.path"))
        assertEquals(name, System.getProperty("androidx.sqlite.driver.bundled.name"))
        assertEquals(1, BundledSqliteInterpositionGuard.mappedSqliteJniImages().size)
    }

    /**
     * Covers the `dlsym`/`dladdr` plumbing that the `sqlite-bundled-load-test` CI task relies on,
     * and the property itself: publishing the system sqlite into the global scope afterwards — what
     * CEF does through NSS — must not displace symbols the guard already bound with `RTLD_NOW`.
     */
    @Test
    fun `bundled sqlite keeps global scope ownership after the system library is published`() {
        BundledSqliteInterpositionGuard.install(Files.createTempDirectory("ani-sqlite-guard-test"))

        val before = SqliteGlobalScopeProbe.globalSqliteSymbolOwner()
        assertNotNull(before, "sqlite3_initialize is not globally visible; the guard did not load")
        assertTrue(
            before.endsWith(BundledSqliteInterpositionGuard.LIB_FILE_NAME),
            "the global sqlite3_initialize belongs to $before, not the bundled library",
        )

        assumeTrue(
            SqliteGlobalScopeProbe.loadSystemSqliteIntoGlobalScope(),
            "no system libsqlite3 on this machine; the takeover half would prove nothing",
        )
        assertEquals(
            before, SqliteGlobalScopeProbe.globalSqliteSymbolOwner(),
            "the system libsqlite3 took over sqlite3_initialize after being published globally",
        )
    }
}
