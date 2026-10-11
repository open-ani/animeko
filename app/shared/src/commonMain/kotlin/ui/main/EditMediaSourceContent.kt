/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.domain.mediasource.MediaSourceConfigurationEditor
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.download.details.MediaDetails
import me.him188.ani.app.ui.download.details.MediaDetailsLazyGrid
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceScreen
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceViewModel
import me.him188.ani.app.ui.settings.mediasource.selector.EditSelectorMediaSourceScreen
import me.him188.ani.app.ui.settings.mediasource.selector.EditSelectorMediaSourceViewModel
import me.him188.ani.datasources.api.source.FactoryId

/**
 * 数据源的独立编辑页, 按 [factoryId] 选择编辑器.
 *
 * @param viewModelKey 区分编辑页 ViewModel. 编辑其他设备上的数据源时需要与本机同 ID 的数据源区分开.
 * @param editor 配置的读写入口. 为 `null` 时编辑本机数据源.
 */
@Composable
internal fun EditMediaSourceContent(
    factoryId: FactoryId,
    instanceId: String,
    onNavigateBack: () -> Unit,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
    viewModelKey: String = instanceId,
    editor: MediaSourceConfigurationEditor? = null,
) {
    val navigationIcon = @Composable { BackNavigationIconButton(onNavigateBack) }
    when (factoryId) {
        RssMediaSource.FactoryId -> EditRssMediaSourceScreen(
            viewModel<EditRssMediaSourceViewModel>(key = viewModelKey) {
                EditRssMediaSourceViewModel(instanceId, editor)
            },
            mediaDetailsColumn = { media ->
                MediaDetailsLazyGrid(
                    MediaDetails.from(media, null, null),
                    Modifier.fillMaxSize(),
                    showSourceInfo = false,
                )
            },
            modifier,
            windowInsets,
            navigationIcon = navigationIcon,
        )

        SelectorMediaSource.FactoryId -> {
            val context = LocalContext.current
            EditSelectorMediaSourceScreen(
                viewModel<EditSelectorMediaSourceViewModel>(key = viewModelKey) {
                    EditSelectorMediaSourceViewModel(instanceId, context, editor)
                },
                modifier,
                windowInsets = windowInsets,
                navigationIcon = navigationIcon,
            )
        }

        else -> error("Unknown factoryId: $factoryId")
    }
}
