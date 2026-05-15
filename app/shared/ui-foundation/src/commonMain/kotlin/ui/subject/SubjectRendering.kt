/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_rendering_season_year_month
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.seasonMonth
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Stable
@Composable
fun renderSubjectSeason(date: PackedDate): String {
    if (date == PackedDate.Invalid) return "TBA"
    if (date.seasonMonth == 0) {
        return date.toString()
    }
    return stringResource(Lang.subject_rendering_season_year_month, date.year.toString(), date.seasonMonth.toString())
}

suspend fun getSubjectSeasonText(date: PackedDate): String {
    if (date == PackedDate.Invalid) return "TBA"
    if (date.seasonMonth == 0) {
        return date.toString()
    }
    return getString(Lang.subject_rendering_season_year_month, date.year.toString(), date.seasonMonth.toString())
}
