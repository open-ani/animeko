/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp", showSystemUi = false)
@Preview(
    showBackground = true,
    device = "spec:width=1920px,height=1080px,dpi=240",
    showSystemUi = false
)
@Composable
fun PreviewWelcomeScene() {
    ProvideCompositionLocalsForPreview {
        WelcomeScene({ }, contactActions = { TestContactActions() })
    }
}

@Composable
private fun TestContactActions(
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.Start),
    ) {
        repeat(4) { i ->
            SuggestionChip(
                { },
                icon = {
                    Icon(Icons.Default.Link, null, Modifier.size(24.dp))
                },
                label = { Text("Action$i") },
            )
        }
    }
}