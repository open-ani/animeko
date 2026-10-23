/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.ui.settings.time.TimeZoneOption
import me.him188.ani.app.ui.settings.time.TimeZoneSection
import me.him188.ani.app.ui.settings.time.timeZoneCatalog

/**
 * TV 时区条目的候选列表构造与写回.
 */
class TvSettingsTimeZoneTest {
    private val followSystemTitle = "跟随系统时区（Asia/Shanghai · UTC+08:00）"

    private val sections = listOf(
        TimeZoneSection(
            "Asia",
            listOf(TimeZoneOption("Asia/Tokyo", 9 * 3600), TimeZoneOption("Asia/Shanghai", 8 * 3600)),
        ),
        TimeZoneSection("Europe", listOf(TimeZoneOption("Europe/Paris", 3600))),
    )

    @Test
    fun `follow system is pinned first and selected when no zone is chosen`() {
        val rows = tvTimeZoneRows(sections, null, followSystemTitle)

        val pinned = rows.first() as TvTimeZoneRow.Option
        assertNull(pinned.zoneId)
        assertEquals(followSystemTitle, pinned.title)
        assertNull(pinned.offsetLabel)
        assertTrue(pinned.selected)
        assertEquals(
            listOf("Asia", "Asia/Tokyo", "Asia/Shanghai", "Europe", "Europe/Paris"),
            rows.drop(1).map { row ->
                when (row) {
                    is TvTimeZoneRow.Section -> row.region
                    is TvTimeZoneRow.Option -> checkNotNull(row.zoneId)
                }
            },
        )
    }

    @Test
    fun `rows have unique keys for the list and the focus anchors`() {
        val rows = tvTimeZoneRows(sections, "Europe/Paris", followSystemTitle)

        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }

    @Test
    fun `selected zone row keeps its catalog position and offset label`() {
        val rows = tvTimeZoneRows(sections, "Europe/Paris", followSystemTitle)
        val options = rows.filterIsInstance<TvTimeZoneRow.Option>()

        assertFalse(options.first().selected)
        assertEquals(listOf("Europe/Paris"), options.filter { it.selected }.map { it.zoneId })
        assertEquals(listOf("Europe/Paris"), options.filter { it.selected }.map { it.title })
        assertEquals(listOf("UTC+01:00"), options.filter { it.selected }.map { it.offsetLabel })
    }

    @Test
    fun `catalog sections become region headers without the system zone`() {
        val systemZone = TimeZone.of("Asia/Shanghai")
        val catalog = timeZoneCatalog(Instant.fromEpochSeconds(1_700_000_000), systemZone)
        val rows = tvTimeZoneRows(catalog, null, followSystemTitle)

        assertEquals(catalog.map { it.region }, rows.filterIsInstance<TvTimeZoneRow.Section>().map { it.region })
        assertTrue(rows.filterIsInstance<TvTimeZoneRow.Option>().none { it.zoneId == systemZone.id })
    }

    @Test
    fun `offset label follows the zone at the catalog instant`() {
        val now = Instant.fromEpochSeconds(1_700_000_000)

        assertEquals("UTC+09:00", timeZoneOffsetLabel("Asia/Tokyo", now))
        assertNull(timeZoneOffsetLabel("Not/AZone", now))
    }

    @Test
    fun `selecting a zone writes only the time zone preference`() {
        val initial = UISettings.Default.copy(
            searchSettings = UISettings.Default.searchSettings.copy(nsfwMode = NsfwMode.BLUR),
        )
        val update = (tvTimeZoneIntent("Asia/Tokyo") as TvSettingsIntent.Appearance).update(initial)

        assertEquals("Asia/Tokyo", update.timeZoneId)
        assertEquals(NsfwMode.BLUR, update.searchSettings.nsfwMode)
    }

    @Test
    fun `following the system clears the time zone preference`() {
        val initial = UISettings.Default.copy(timeZoneId = "Asia/Tokyo")
        val update = (tvTimeZoneIntent(null) as TvSettingsIntent.Appearance).update(initial)

        assertNull(update.timeZoneId)
    }
}
