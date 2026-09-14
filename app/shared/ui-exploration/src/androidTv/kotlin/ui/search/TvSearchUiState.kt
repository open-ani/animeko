/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.search

import me.him188.ani.app.data.network.BatchSubjectDetails

data class TvSearchUiState(val keywords: String = "", val hasSearched: Boolean = false)

sealed interface TvSearchIntent {
    data class ChangeKeywords(val value: String) : TvSearchIntent
    data object Search : TvSearchIntent
    data class OpenSubject(val subject: BatchSubjectDetails) : TvSearchIntent
}
