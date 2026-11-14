/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.persistent.createTestPreferencesDataStore
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.tools.TimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TimeZoneProviderTest {
    private val shanghai = TimeZone.of("Asia/Shanghai")
    private val newYork = TimeZone.of("America/New_York")

    private fun TestScope.createProvider(
        store: DataStore<Preferences>,
        systemZone: () -> TimeZone,
    ): DefaultTimeZoneProvider = DefaultTimeZoneProvider(
        settingsRepository = PreferencesRepositoryImpl(store),
        scope = backgroundScope,
        systemZone = systemZone,
    )

    @Test
    fun `null preference resolves to the system time zone`() = runTest {
        val provider = createProvider(createTestPreferencesDataStore()) { shanghai }

        assertNull(provider.preference.first())
        assertEquals(shanghai, provider.timeZone.first())
    }

    @Test
    fun `stored preference is loaded and resolved`() = runTest {
        val store = createTestPreferencesDataStore()
        PreferencesRepositoryImpl(store).uiSettings.set(UISettings.Default.copy(timeZoneId = newYork.id))

        val provider = createProvider(store) { shanghai }

        assertEquals(newYork.id, provider.preference.first { it != null })
        assertEquals(newYork, provider.timeZone.first { it == newYork })
    }

    @Test
    fun `setting a zone id moves the resolved zone`() = runTest {
        val provider = createProvider(createTestPreferencesDataStore()) { shanghai }
        assertEquals(shanghai, provider.timeZone.first())

        provider.setTimeZoneId(newYork.id)

        assertEquals(newYork.id, provider.preference.first { it != null })
        assertEquals(newYork, provider.timeZone.first { it == newYork })
    }

    @Test
    fun `preference persists across provider instances`() = runTest {
        val store = createTestPreferencesDataStore()
        val first = createProvider(store) { shanghai }
        first.setTimeZoneId(newYork.id)
        first.preference.first { it != null }

        val second = createProvider(store) { shanghai }

        assertEquals(newYork.id, second.preference.first { it != null })
        assertEquals(newYork, second.timeZone.first { it == newYork })
    }

    @Test
    fun `refresh re-resolves the system time zone`() = runTest {
        var systemZone = shanghai
        val provider = createProvider(createTestPreferencesDataStore()) { systemZone }
        assertEquals(shanghai, provider.timeZone.first())

        systemZone = newYork
        provider.refresh()

        assertEquals(newYork, provider.timeZone.first { it == newYork })
    }

    @Test
    fun `refresh keeps an explicit preference`() = runTest {
        var systemZone = shanghai
        val provider = createProvider(createTestPreferencesDataStore()) { systemZone }
        provider.setTimeZoneId(newYork.id)
        provider.preference.first { it != null }

        systemZone = TimeZone.UTC
        provider.refresh()

        assertEquals(newYork, provider.timeZone.first { it == newYork })
    }

    @Test
    fun `unresolvable zone id falls back to the system time zone`() = runTest {
        val provider = createProvider(createTestPreferencesDataStore()) { shanghai }

        provider.setTimeZoneId("Not/AZone")

        assertEquals("Not/AZone", provider.preference.first { it != null })
        assertEquals(shanghai, provider.timeZone.first())
    }

    @Test
    fun `preference change changes the formatted date time`() = runTest {
        val provider = createProvider(createTestPreferencesDataStore()) { shanghai }
        val instant = Instant.parse("2020-01-01T10:00:00Z")
        val getTimeNow = { Instant.parse("2020-01-20T00:00:00Z") }

        provider.setTimeZoneId(shanghai.id)
        val inShanghai = TimeFormatter(
            timeZone = provider.timeZone.first { it == shanghai },
            getTimeNow = getTimeNow,
        ).format(instant)
        assertEquals("2020-01-01 18:00", inShanghai)

        provider.setTimeZoneId(newYork.id)
        val inNewYork = TimeFormatter(
            timeZone = provider.timeZone.first { it == newYork },
            getTimeNow = getTimeNow,
        ).format(instant)
        assertEquals("2020-01-01 05:00", inNewYork)
    }
}
