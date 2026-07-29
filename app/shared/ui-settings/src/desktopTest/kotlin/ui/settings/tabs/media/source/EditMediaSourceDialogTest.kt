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
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.parameter.buildMediaSourceParameters
import me.him188.ani.datasources.api.source.parameter.hasValue
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test

class EditMediaSourceDialogTest {
    @Test
    fun `switching authentication mode shows only its credential fields`() = runAniComposeUiTest {
        val apiKeyMode = "apiKey"
        val passwordMode = "usernamePassword"
        val parameters = buildMediaSourceParameters {
            string("baseUrl", defaultProvider = { "http://localhost:8096" })
            val authMode = simpleEnum("authMode", apiKeyMode, passwordMode, default = apiKeyMode)
            string("userId", defaultProvider = { "" }, visibleWhen = authMode.hasValue(apiKeyMode))
            string("apikey", defaultProvider = { "" }, visibleWhen = authMode.hasValue(apiKeyMode))
            string("username", defaultProvider = { "" }, visibleWhen = authMode.hasValue(passwordMode))
            string("password", defaultProvider = { "" }, visibleWhen = authMode.hasValue(passwordMode))
        }
        val state = EditingMediaSource(
            editingMediaSourceId = "test",
            factoryId = FactoryId("test"),
            info = MediaSourceInfo(
                displayName = "Conditional source",
                description = "Test data source",
            ),
            parameters = parameters,
            persistedArguments = flowOf(MediaSourceConfig.Default),
            editMediaSourceMode = EditMediaSourceMode.Add(FactoryId("test")),
            onSave = {},
            parentCoroutineContext = EmptyCoroutineContext,
        )

        setContent {
            ProvideCompositionLocalsForPreview {
                EditMediaSourceDialog(state, onDismissRequest = {})
            }
        }

        onNodeWithTag(EditMediaSourceTestTags.argument("baseUrl")).assertExists()
        onNodeWithTag(EditMediaSourceTestTags.argument("authMode")).assertExists()
        onNodeWithTag(EditMediaSourceTestTags.argument("userId")).assertExists()
        onNodeWithTag(EditMediaSourceTestTags.argument("apikey")).assertExists()
        onNodeWithTag(EditMediaSourceTestTags.argument("username")).assertDoesNotExist()
        onNodeWithTag(EditMediaSourceTestTags.argument("password")).assertDoesNotExist()

        onNodeWithTag(EditMediaSourceTestTags.argument("authMode")).performClick()
        onNodeWithTag(EditMediaSourceTestTags.enumOption("authMode", passwordMode)).performClick()

        onNodeWithTag(EditMediaSourceTestTags.argument("baseUrl")).assertExists()
        onNodeWithTag(EditMediaSourceTestTags.argument("authMode")).assertExists()
        onNodeWithTag(EditMediaSourceTestTags.argument("userId")).assertDoesNotExist()
        onNodeWithTag(EditMediaSourceTestTags.argument("apikey")).assertDoesNotExist()
        onNodeWithTag(EditMediaSourceTestTags.argument("username")).assertExists()
        onNodeWithTag(EditMediaSourceTestTags.argument("password")).assertExists()

        state.close()
    }
}
