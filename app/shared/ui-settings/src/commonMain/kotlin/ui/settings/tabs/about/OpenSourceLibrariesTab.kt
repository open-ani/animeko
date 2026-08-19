/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_oss_licenses_homepage
import me.him188.ani.app.ui.lang.settings_oss_licenses_license
import org.jetbrains.compose.resources.stringResource

/**
 * 开源许可页, 展示应用使用的开源库列表及许可证.
 *
 * @param loadLibrariesJson 读取 AboutLibraries gradle 插件导出的 `aboutlibraries.json`.
 * 数据在 `:app:shared` 的 compose resources 里, 由调用方传入以避免依赖上层模块的 Res.
 */
@Composable
fun OpenSourceLibrariesTab(
    loadLibrariesJson: suspend () -> ByteArray,
    modifier: Modifier = Modifier,
) {
    val libraries by produceLibraries { loadLibrariesJson().decodeToString() }
    LibrariesContainer(
        libraries,
        modifier,
        // 列表容器背景透明, 使用设置页自己的背景色; item 由下面的 libraryRow 全权渲染, 本身无背景.
        colors = LibraryDefaults.libraryColors(libraryBackgroundColor = Color.Transparent),
        libraryRow = { _, library, expanded, toggle, _ ->
            OpenSourceLibraryRow(library, expanded, onToggleLicense = toggle)
        },
    )
}

/**
 * 库条目: 库名完整显示. 一行放得下时库名居左、许可证与主页链接居右;
 * 放不下时库名折行完整显示, 链接自动换到单独一行.
 * 点击许可证展开许可证原文, 点击主页用浏览器打开.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenSourceLibraryRow(
    library: Library,
    expanded: Boolean,
    onToggleLicense: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                library.name,
                Modifier.padding(end = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )

            val linkStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
            Row(
                // weight 让链接组填满所在行的剩余宽度, 配合 Alignment.End
                // 在同行和独占一行两种情况下都靠右对齐.
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (library.licenses.isNotEmpty()) {
                    Text(
                        stringResource(Lang.settings_oss_licenses_license),
                        Modifier.clickable(onClick = onToggleLicense),
                        style = linkStyle,
                        maxLines = 1,
                    )
                }
                val homepage = library.website?.takeIf { it.isNotBlank() }
                    ?: library.scm?.url?.takeIf { it.isNotBlank() }
                if (homepage != null) {
                    val uriHandler = LocalUriHandler.current
                    Text(
                        stringResource(Lang.settings_oss_licenses_homepage),
                        Modifier.clickable { uriHandler.openUri(homepage) },
                        style = linkStyle,
                        maxLines = 1,
                    )
                }
            }
        }

        AniAnimatedVisibility(expanded) {
            Text(
                library.strippedLicenseContent.takeIf { it.isNotBlank() }
                    ?: library.licenses.mapNotNull { it.url }.joinToString("\n"),
                Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
