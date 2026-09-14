/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.exploration

import me.him188.ani.app.data.models.subject.SubjectCollectionInfo

/** Immutable media snapshots; no loader, repository, or writable cache reaches the view. */
data class TvSubjectMediaUiState(
    val infoCache: Map<Int, SubjectCollectionInfo> = emptyMap(),
    val backdropCache: Map<Int, String?> = emptyMap(),
    val summaryFallbackCache: Map<Int, String> = emptyMap(),
)

data class TvHeroSubject(val subjectId: Int, val title: String, val imageUrl: String)

sealed interface TvExplorationIntent {
    data class ShowHero(val subject: TvHeroSubject) : TvExplorationIntent
    data class CardVisible(val subjectId: Int, val collection: SubjectCollectionInfo? = null) : TvExplorationIntent
    data class OpenSubject(val subject: TvHeroSubject) : TvExplorationIntent
}
