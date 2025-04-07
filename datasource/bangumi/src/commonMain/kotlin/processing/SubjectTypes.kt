/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.bangumi.processing

import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.next.models.BangumiNextSubjectType

fun BangumiSubjectType.toNextSubjectType(): BangumiNextSubjectType = when (this) {
    BangumiSubjectType.Book -> BangumiNextSubjectType.Book
    BangumiSubjectType.Anime -> BangumiNextSubjectType.Anime
    BangumiSubjectType.Music -> BangumiNextSubjectType.Music
    BangumiSubjectType.Game -> BangumiNextSubjectType.Game
    BangumiSubjectType.Real -> BangumiNextSubjectType.Real
}

fun BangumiNextSubjectType.toLegacySubjectType(): BangumiSubjectType = when (this) {
    BangumiNextSubjectType.Book -> BangumiSubjectType.Book
    BangumiNextSubjectType.Anime -> BangumiSubjectType.Anime
    BangumiNextSubjectType.Music -> BangumiSubjectType.Music
    BangumiNextSubjectType.Game -> BangumiSubjectType.Game
    BangumiNextSubjectType.Real -> BangumiSubjectType.Real
}
