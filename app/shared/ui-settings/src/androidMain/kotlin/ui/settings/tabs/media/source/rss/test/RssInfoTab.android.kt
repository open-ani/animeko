/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source.rss.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.ui.foundation.preview.PreviewTabletLightDark
import me.him188.ani.app.ui.settings.mediasource.rss.test.RssInfoTab
import me.him188.ani.app.ui.settings.mediasource.rss.test.RssTestPaneDefaults
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
@PreviewTabletLightDark
fun PreviewRssInfoTab() {
    RssTestPaneDefaults.RssInfoTab(
        items = TestRssItemInfos,
        onViewDetails = { },
        selectedItemProvider = { TestRssItemInfos[1] },
    )
}
