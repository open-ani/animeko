/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.Res
import me.him188.ani.app.ui.foundation.bangumi
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_acknowledgements_bangumi
import me.him188.ani.app.ui.lang.settings_acknowledgements_bangumi_description
import me.him188.ani.app.ui.lang.settings_acknowledgements_dandanplay
import me.him188.ani.app.ui.lang.settings_acknowledgements_dandanplay_description
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AcknowledgementsTab(modifier: Modifier = Modifier) {
    Column(modifier) {
        val uriHandler = LocalUriHandler.current
        val listItemColors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )

        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_acknowledgements_bangumi)) },
            Modifier.clickable {
                uriHandler.openUri("https://bangumi.tv")
            },
            supportingContent = { Text(stringResource(Lang.settings_acknowledgements_bangumi_description)) },
            leadingContent = {
                Image(
                    painterResource(Res.drawable.bangumi),
                    contentDescription = null,
                    Modifier.clip(CircleShape).size(24.dp),
                )
            },
            colors = listItemColors,
        )

        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_acknowledgements_dandanplay)) },
            Modifier.clickable {
                uriHandler.openUri("https://www.dandanplay.com")
            },
            supportingContent = { Text(stringResource(Lang.settings_acknowledgements_dandanplay_description)) },
            colors = listItemColors,
        )
    }
}

@Composable
@Preview
private fun PreviewAcknowledgementsTab() = ProvideCompositionLocalsForPreview {
    Surface {
        AcknowledgementsTab()
    }
}
