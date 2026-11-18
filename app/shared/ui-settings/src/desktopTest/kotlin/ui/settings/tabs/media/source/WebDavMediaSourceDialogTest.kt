/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.webdav.WebDavMediaSource

class WebDavMediaSourceDialogTest {
    @Test
    fun `webdav configuration saves a named anonymous server`() = runAniComposeUiTest {
        val factory = WebDavMediaSource.Factory()
        val state = EditingMediaSource(
            editingMediaSourceId = "webdav-instance",
            factoryId = factory.factoryId,
            info = factory.info,
            parameters = factory.parameters,
            persistedArguments = flowOf(MediaSourceConfig.Default),
            editMediaSourceMode = EditMediaSourceMode.Add(factory.factoryId),
            onSave = {},
            parentCoroutineContext = EmptyCoroutineContext,
        )
        try {
            setContent {
                ProvideCompositionLocalsForPreview { EditMediaSourceDialog(state, onDismissRequest = {}) }
            }
            assertTrue(state.hasError)
            onNodeWithTag(EditMediaSourceTestTags.argument("name")).performTextInput("NAS")
            onNodeWithTag(EditMediaSourceTestTags.argument("baseUrl")).performTextInput("https://nas.test/dav/")
            assertFalse(state.hasError)
            assertEquals("https://nas.test/dav/", state.createConfig().arguments["baseUrl"])
        } finally {
            state.close()
        }
    }
}
