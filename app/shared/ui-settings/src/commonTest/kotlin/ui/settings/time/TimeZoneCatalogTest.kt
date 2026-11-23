/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimeZoneCatalogTest {
    private val now = Instant.fromEpochSeconds(1_700_000_000)
    private val systemZone = TimeZone.of("Europe/Berlin")
    private val catalog = timeZoneCatalog(now, systemZone)

    @Test
    fun `catalog keeps only region city ids and groups them by region`() {
        assertEquals(
            listOf(
                "Africa",
                "America",
                "Antarctica",
                "Arctic",
                "Asia",
                "Atlantic",
                "Australia",
                "Europe",
                "Indian",
                "Pacific",
            ),
            catalog.map { it.region },
        )
        for (section in catalog) {
            assertTrue(section.options.isNotEmpty(), section.region)
            for (option in section.options) {
                assertEquals(section.region, option.zoneId.substringBefore('/'), option.zoneId)
                assertTrue(option.zoneId.contains('/'), option.zoneId)
            }
        }
    }

    @Test
    fun `catalog drops legacy aliases`() {
        val ids = catalog.flatMap { it.options }.map { it.zoneId }
        assertTrue(ids.none { it.startsWith("Etc/") })
        assertTrue(ids.none { it.startsWith("SystemV/") })
        assertTrue(ids.none { it.startsWith("US/") })
        assertTrue(ids.none { it.startsWith("GMT") })
        assertTrue(ids.none { !it.contains('/') })
        assertEquals(ids.size, ids.toSet().size, "duplicate zone ids")
    }

    @Test
    fun `options are sorted by current offset then by id`() {
        for (section in catalog) {
            val sorted = section.options.sortedWith(compareBy({ it.offsetSeconds }, { it.zoneId }))
            assertEquals(sorted, section.options, section.region)
        }
    }

    @Test
    fun `device system zone is not listed`() {
        val ids = catalog.flatMap { it.options }.map { it.zoneId }
        assertFalse(ids.contains(systemZone.id))
        assertTrue(ids.contains("Asia/Shanghai"))

        val anotherSystemZone = TimeZone.of("Asia/Shanghai")
        val anotherIds = timeZoneCatalog(now, anotherSystemZone).flatMap { it.options }.map { it.zoneId }
        assertFalse(anotherIds.contains(anotherSystemZone.id))
        assertTrue(anotherIds.contains(systemZone.id))
    }

    @Test
    fun `filter matches zone id city region and offset`() {
        val byId = filterTimeZoneSections(catalog, "asia/shanghai").flatMap { it.options }.map { it.zoneId }
        assertEquals(listOf("Asia/Shanghai"), byId)

        val byCity = filterTimeZoneSections(catalog, "shanghai").flatMap { it.options }.map { it.zoneId }
        assertEquals(listOf("Asia/Shanghai"), byCity)

        val byRegion = filterTimeZoneSections(catalog, "asia")
        assertEquals(setOf("Asia"), byRegion.map { it.region }.toSet())

        val byOffset = filterTimeZoneSections(catalog, "UTC+08:00").flatMap { it.options }
        assertTrue(byOffset.isNotEmpty())
        assertTrue(byOffset.all { it.offsetLabel == "UTC+08:00" })
    }

    @Test
    fun `filter drops sections without matches`() {
        val filtered = filterTimeZoneSections(catalog, "tokyo")
        assertTrue(filtered.isNotEmpty())
        assertEquals(listOf("Asia"), filtered.map { it.region })
        assertEquals(listOf("Asia/Tokyo"), filtered.flatMap { it.options }.map { it.zoneId })
    }

    @Test
    fun `blank query keeps every section`() {
        assertEquals(catalog, filterTimeZoneSections(catalog, ""))
        assertEquals(catalog, filterTimeZoneSections(catalog, "   "))
    }

    @Test
    fun `query matching ignores case and surrounding blanks`() {
        assertTrue(timeZoneQueryMatches("  TOKYO ", "Asia/Tokyo"))
        assertTrue(timeZoneQueryMatches("Asia/Tokyo", "Asia/Tokyo"))
        assertFalse(timeZoneQueryMatches("tokyo", "Asia/Shanghai"))
        assertTrue(timeZoneQueryMatches("", "Asia/Shanghai"))
    }

    @Test
    fun `offset formatting uses hours and minutes`() {
        assertEquals("UTC+08:00", formatUtcOffset(8 * 3600))
        assertEquals("UTC-05:00", formatUtcOffset(-5 * 3600))
        assertEquals("UTC+05:30", formatUtcOffset(5 * 3600 + 30 * 60))
        assertEquals("UTC+00:00", formatUtcOffset(0))
        assertEquals("UTC+14:00", formatUtcOffset(14 * 3600))
    }
}
