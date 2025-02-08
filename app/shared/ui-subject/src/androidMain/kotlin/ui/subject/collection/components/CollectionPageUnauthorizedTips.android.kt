/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.domain.session.SessionStatus
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.session.SessionTipsArea
import me.him188.ani.app.ui.foundation.session.SessionTipsIcon

@Composable
private fun PreviewSessionTipsAreaImpl(
    status: SessionStatus,
    modifier: Modifier = Modifier,
) {
    Column {
        Text(status.toString(), Modifier.padding(bottom = 4.dp))
        Surface(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            SessionTipsArea(status, {}, {}, {}, modifier)
        }
    }
}

@Composable
private fun PreviewSessionTipsIconImpl(
    status: SessionStatus,
    modifier: Modifier = Modifier,
) {
    Column {
        Text(status.toString(), Modifier.padding(bottom = 4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SessionTipsIcon(status, {}, {}, modifier)
        }
    }
}

@Composable
@PreviewLightDark
fun PreviewSessionTipsArea() {
    ProvideCompositionLocalsForPreview {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                for (status in PreviewSessionStatuses) {
                    PreviewSessionTipsAreaImpl(status)
                }
            }
        }
    }
}

@Composable
@PreviewLightDark
fun PreviewSessionTipsIcon() {
    ProvideCompositionLocalsForPreview {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                for (status in PreviewSessionStatuses) {
                    PreviewSessionTipsIconImpl(status)
                }
            }
        }
    }
}

@Stable
val PreviewSessionStatuses = listOf(
    SessionStatus.Refreshing,
    SessionStatus.Verifying("1"),
    SessionStatus.Verified("1", UserInfo.EMPTY),
    SessionStatus.Expired,
    SessionStatus.NetworkError,
    SessionStatus.ServiceUnavailable,
    SessionStatus.NoToken,
)
