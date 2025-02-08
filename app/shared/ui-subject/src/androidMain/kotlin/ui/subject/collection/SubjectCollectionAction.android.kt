/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionTypeButton
import me.him188.ani.datasources.api.topic.UnifiedCollectionType


@Composable
@Preview
fun PreviewCollectionActionButton() = ProvideCompositionLocalsForPreview {
    Surface {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                for (entry in UnifiedCollectionType.entries) {
                    SubjectCollectionTypeButton(
                        type = entry,
                        onEdit = {},
                    )
                }
            }
        }
    }
}
