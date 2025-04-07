/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.bangumi.processing

import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiEpisodeCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectCollectionType
import me.him188.ani.datasources.bangumi.next.models.BangumiNextCollectionType


fun UnifiedCollectionType.toSubjectCollectionType(): BangumiSubjectCollectionType? {
    return when (this) {
        UnifiedCollectionType.WISH -> BangumiSubjectCollectionType.Wish
        UnifiedCollectionType.DOING -> BangumiSubjectCollectionType.Doing
        UnifiedCollectionType.DONE -> BangumiSubjectCollectionType.Done
        UnifiedCollectionType.ON_HOLD -> BangumiSubjectCollectionType.OnHold
        UnifiedCollectionType.DROPPED -> BangumiSubjectCollectionType.Dropped
        UnifiedCollectionType.NOT_COLLECTED -> null
    }
}

fun UnifiedCollectionType.toBangumiNextCollectionType(): BangumiNextCollectionType? {
    return when (this) {
        UnifiedCollectionType.WISH -> BangumiNextCollectionType.Wish
        UnifiedCollectionType.DOING -> BangumiNextCollectionType.Doing
        UnifiedCollectionType.DONE -> BangumiNextCollectionType.Collect
        UnifiedCollectionType.ON_HOLD -> BangumiNextCollectionType.OnHold
        UnifiedCollectionType.DROPPED -> BangumiNextCollectionType.Dropped
        UnifiedCollectionType.NOT_COLLECTED -> null
    }
}

fun BangumiSubjectCollectionType?.toCollectionType(): UnifiedCollectionType {
    return when (this) {
        BangumiSubjectCollectionType.Wish -> UnifiedCollectionType.WISH
        BangumiSubjectCollectionType.Doing -> UnifiedCollectionType.DOING
        BangumiSubjectCollectionType.Done -> UnifiedCollectionType.DONE
        BangumiSubjectCollectionType.OnHold -> UnifiedCollectionType.ON_HOLD
        BangumiSubjectCollectionType.Dropped -> UnifiedCollectionType.DROPPED
        null -> UnifiedCollectionType.NOT_COLLECTED
    }
}

fun BangumiNextCollectionType?.toCollectionType(): UnifiedCollectionType {
    return when (this) {
        BangumiNextCollectionType.Wish -> UnifiedCollectionType.WISH
        BangumiNextCollectionType.Doing -> UnifiedCollectionType.DOING
        BangumiNextCollectionType.Collect -> UnifiedCollectionType.DONE
        BangumiNextCollectionType.OnHold -> UnifiedCollectionType.ON_HOLD
        BangumiNextCollectionType.Dropped -> UnifiedCollectionType.DROPPED
        null -> UnifiedCollectionType.NOT_COLLECTED
    }
}

fun BangumiEpisodeCollectionType.toCollectionType(): UnifiedCollectionType {
    return when (this) {
        BangumiEpisodeCollectionType.NOT_COLLECTED -> UnifiedCollectionType.NOT_COLLECTED
        BangumiEpisodeCollectionType.WATCHLIST -> UnifiedCollectionType.WISH
        BangumiEpisodeCollectionType.WATCHED -> UnifiedCollectionType.DONE
        BangumiEpisodeCollectionType.DISCARDED -> UnifiedCollectionType.DROPPED
    }
}

fun UnifiedCollectionType.toEpisodeCollectionType(): BangumiEpisodeCollectionType {
    return when (this) {
        UnifiedCollectionType.NOT_COLLECTED -> BangumiEpisodeCollectionType.NOT_COLLECTED
        UnifiedCollectionType.WISH -> BangumiEpisodeCollectionType.WATCHLIST
        UnifiedCollectionType.DOING -> BangumiEpisodeCollectionType.WATCHLIST
        UnifiedCollectionType.DONE -> BangumiEpisodeCollectionType.WATCHED
        UnifiedCollectionType.ON_HOLD -> BangumiEpisodeCollectionType.WATCHLIST
        UnifiedCollectionType.DROPPED -> BangumiEpisodeCollectionType.DISCARDED
    }
}