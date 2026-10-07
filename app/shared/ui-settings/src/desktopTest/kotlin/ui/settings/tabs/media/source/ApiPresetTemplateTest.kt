/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.mediasource.api.ApiMediaSource
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourcePresets
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApiPresetTemplateTest {
    @Test
    fun `selecting Xifan persists a complete API configuration immediately`() = runAniComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var saved: MediaSourceConfig? = null
        val editor = EditMediaSourceState(
            getConfigFlow = { flowOf(MediaSourceConfig.Default) },
            onAdd = { factoryId, _, config ->
                assertEquals(ApiMediaSource.FactoryId, factoryId)
                saved = config
            }, onEdit = { _, _ -> }, onDelete = {}, onSetEnabled = { _, _ -> },
            backgroundScope = scope,
        )
        var selected: EditingMediaSource? = null
        try {
            setContent {
                ProvideCompositionLocalsForPreview {
                    SelectMediaSourceTemplateDialog(
                        templates = builtInApiMediaSourceTemplates(),
                        onClick = { selected = editor.startAdding(it) },
                        onDismissRequest = {},
                    )
                }
            }
            onNodeWithText("稀饭动漫 Next").performClick()
            val editing = assertNotNull(selected)
            assertEquals(ApiMediaSource.FactoryId, editing.factoryId)
            val arguments = assertNotNull(editing.createConfig().deserializeArgumentsOrNull(ApiMediaSourceArguments.serializer()))
            arguments.validate()
            assertEquals(ApiMediaSourcePresets.xifanNext, arguments)
            runBlocking { editor.confirmEdit(editing).join() }
            assertEquals(arguments, assertNotNull(saved).deserializeArgumentsOrNull(ApiMediaSourceArguments.serializer()))
        } finally {
            editor.cancelEdit()
            scope.cancel()
        }
    }

    @Test
    fun `generic API template retains its empty initial configuration`() {
        val template = MediaSourceTemplate(
            ApiMediaSource.FactoryId, MediaSourceInfo("JSON API"), MediaSourceParameters.Empty,
        )
        assertEquals(MediaSourceConfig.Default, template.initialConfig)
    }
}
