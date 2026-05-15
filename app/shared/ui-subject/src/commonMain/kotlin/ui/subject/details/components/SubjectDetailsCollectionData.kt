/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("UnusedReceiverParameter")

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.ui.foundation.theme.slightlyWeaken
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_collection_dropped
import me.him188.ani.app.ui.lang.subject_details_collection_summary
import org.jetbrains.compose.resources.stringResource

// 详情页内容 (不包含背景)
@Composable
fun SubjectDetailsDefaults.CollectionData(
    collectionStats: SubjectCollectionStats,
    modifier: Modifier = Modifier,
) {
    // 数据
    Row(modifier) {
        val collection = collectionStats
        Text(
            stringResource(
                Lang.subject_details_collection_summary,
                collection.collect.toString(),
                collection.doing.toString(),
            ),
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(Lang.subject_details_collection_dropped, collection.dropped.toString()),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            color = LocalContentColor.current.slightlyWeaken(),
        )
    }
}
