/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.jellyfin.EmbyMediaSource

class EmbyMediaSourceDialogTest {
    @Test
    fun `emby accepts a named server and username with an empty password`() = runAniComposeUiTest {
        val factory = EmbyMediaSource.Factory()
        val state = EditingMediaSource(
            editingMediaSourceId = "emby-instance",
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
            onNodeWithTag(EditMediaSourceTestTags.argument("name")).performTextInput("Living room")
            onNodeWithTag(EditMediaSourceTestTags.argument("authMode")).performScrollTo().performClick()
            onNodeWithTag(EditMediaSourceTestTags.enumOption("authMode", EmbyMediaSource.AUTH_MODE_USERNAME_PASSWORD)).performClick()
            onNodeWithTag(EditMediaSourceTestTags.argument("username")).performScrollTo().performTextInput("test-user")
            onNodeWithTag(EditMediaSourceTestTags.argument("password"))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            onNodeWithTag(EditMediaSourceTestTags.argument("apikey")).assertDoesNotExist()
            assertFalse(state.hasError)
            val config = state.createConfig()
            assertEquals("Living room", config.arguments["name"])
            assertEquals("test-user", config.arguments["username"])
            assertEquals("", config.arguments["password"])
        } finally {
            state.close()
        }
    }
}
