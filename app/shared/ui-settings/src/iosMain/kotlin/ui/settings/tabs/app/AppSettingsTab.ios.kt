/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_app_language
import me.him188.ani.app.ui.settings.framework.components.RowButtonItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
internal actual fun SettingsScope.AppSettingsTabPlatform() {
}

@Composable
internal actual fun SettingsScope.IosLanguageSettings() {
    RowButtonItem(
        onClick = {
            val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)!!
            val app = UIApplication.sharedApplication
            if (app.canOpenURL(url)) {
                app.openURL(
                    url,
                    options = emptyMap<_, Any?>(),
                    completionHandler = { _ ->
                        // ignored
                    },
                )
            }
        },
        title = { Text(stringResource(Lang.settings_app_language)) },
        icon = {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
            )
        },
        action = {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
        },
    )
}
