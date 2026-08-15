/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search_filter_season_all
import me.him188.ani.app.ui.lang.exploration_search_filter_year_all
import org.jetbrains.compose.resources.stringResource

/**
 * 番剧索引的年份筛选 chip. 形态与 [SearchFilterChip] 一致: InputChip + DropdownMenu.
 *
 * [selectedYear] 为 null 表示"全部年份".
 */
@Composable
fun YearFilterChip(
    years: List<Int>,
    selectedYear: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }

    Box(modifier) {
        InputChip(
            selected = selectedYear != null,
            onClick = { showDropdown = true },
            label = {
                Text(
                    selectedYear?.toString() ?: stringResource(Lang.exploration_search_filter_year_all),
                    Modifier.widthIn(max = 120.dp),
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Rounded.ArrowDropDown, null,
                    Modifier.size(InputChipDefaults.IconSize),
                )
            },
        )

        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Lang.exploration_search_filter_year_all)) },
                onClick = {
                    onSelect(null)
                    showDropdown = false
                },
                contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
            )
            for (year in years) {
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        onSelect(year)
                        showDropdown = false
                    },
                    contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
                )
            }
        }
    }
}

/**
 * 番剧索引的季度筛选 chip. 季度从属于年份: [enabled] 为 false 时 (未选年份) 禁用.
 *
 * [selectedQuarter] 为 null 表示"全部季度"; 取值为 1..4, 显示 Q1..Q4.
 */
@Composable
fun QuarterFilterChip(
    selectedQuarter: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }

    Box(modifier) {
        InputChip(
            selected = selectedQuarter != null,
            onClick = { showDropdown = true },
            enabled = enabled,
            label = {
                Text(
                    selectedQuarter?.let { "Q$it" } ?: stringResource(Lang.exploration_search_filter_season_all),
                    Modifier.widthIn(max = 120.dp),
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Rounded.ArrowDropDown, null,
                    Modifier.size(InputChipDefaults.IconSize),
                )
            },
        )

        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Lang.exploration_search_filter_season_all)) },
                onClick = {
                    onSelect(null)
                    showDropdown = false
                },
                contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
            )
            for (quarter in 1..4) {
                DropdownMenuItem(
                    text = { Text("Q$quarter") },
                    onClick = {
                        onSelect(quarter)
                        showDropdown = false
                    },
                    contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
                )
            }
        }
    }
}
