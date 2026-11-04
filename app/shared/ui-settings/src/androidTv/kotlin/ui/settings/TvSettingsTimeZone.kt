/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import kotlin.time.Instant
import kotlinx.datetime.offsetAt
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.domain.settings.toTimeZoneOrNull
import me.him188.ani.app.ui.settings.time.TimeZoneSection
import me.him188.ani.app.ui.settings.time.formatUtcOffset

/**
 * 「跟随系统时区」置顶项的行标识.
 */
private const val FollowSystemRowKey = "follow-system"

/**
 * 时区选择列表的一行: [Section] 是分组的 Region 标题, [Option] 是可选的时区.
 */
internal sealed interface TvTimeZoneRow {
    /** LazyList 的稳定 key, 同时用作焦点锚点标识. */
    val key: String

    data class Section(val region: String) : TvTimeZoneRow {
        override val key: String get() = "section:$region"
    }

    /**
     * @property zoneId 写入 [UISettings.timeZoneId] 的值, `null` 表示跟随系统时区.
     * @property offsetLabel 行的右侧文字; 「跟随系统」项已把偏移写进 [title], 因此为 `null`.
     */
    data class Option(
        override val key: String,
        val zoneId: String?,
        val title: String,
        val offsetLabel: String?,
        val selected: Boolean,
    ) : TvTimeZoneRow
}

/**
 * 生成时区选择列表: 置顶「跟随系统时区」项, 其后按 [sections] 的顺序给出 Region 标题与选项.
 *
 * Region 标题不可聚焦, 选项之间只有顺序关系, 焦点在列表内按方向键移动.
 *
 * @param followSystemTitle 置顶项的完整文案, 已含系统时区 ID 与偏移.
 */
internal fun tvTimeZoneRows(
    sections: List<TimeZoneSection>,
    selectedZoneId: String?,
    followSystemTitle: String,
): List<TvTimeZoneRow> = buildList {
    add(
        TvTimeZoneRow.Option(
            key = FollowSystemRowKey,
            zoneId = null,
            title = followSystemTitle,
            offsetLabel = null,
            selected = selectedZoneId == null,
        ),
    )
    sections.forEach { section ->
        add(TvTimeZoneRow.Section(section.region))
        section.options.forEach { option ->
            add(
                TvTimeZoneRow.Option(
                    key = "zone:${option.zoneId}",
                    zoneId = option.zoneId,
                    title = option.zoneId,
                    offsetLabel = option.offsetLabel,
                    selected = option.zoneId == selectedZoneId,
                ),
            )
        }
    }
}

/**
 * [zoneId] 在 [now] 时刻的偏移串, 例如 `UTC+09:00`. 无法解析的 ID 返回 `null`.
 */
internal fun timeZoneOffsetLabel(zoneId: String, now: Instant): String? =
    zoneId.toTimeZoneOrNull()?.let { formatUtcOffset(it.offsetAt(now).totalSeconds) }

/**
 * 选中 [zoneId] (`null` 表示跟随系统时区) 后写回偏好.
 */
internal fun tvTimeZoneIntent(zoneId: String?): TvSettingsIntent =
    TvSettingsIntent.Appearance { copy(timeZoneId = zoneId) }
