/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.ConnectionTestResult
import me.him188.ani.app.ui.settings.framework.ConnectionTester
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class MediaSourceSelectionTest {
    @Test
    fun `long press selects and batch actions target applicable media sources`() = runAniComposeUiTest {
        val enabledSource = createPresentation("enabled", enabled = true)
        val disabledSource = createPresentation("disabled", enabled = false)
        val mediaSources = listOf(enabledSource, disabledSource)

        val enabledIds = mutableSetOf<String>()
        val disabledIds = mutableSetOf<String>()
        val deletedIds = mutableSetOf<String>()

        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val groupState = remember {
                    MediaSourceGroupState(
                        mediaSourcesState = mutableStateOf(mediaSources),
                        availableMediaSourceTemplatesState = mutableStateOf(emptyList()),
                        onReorder = {},
                        backgroundScope = scope,
                    )
                }
                val editState = remember {
                    EditMediaSourceState(
                        getConfigFlow = { flowOf(MediaSourceConfig.Default) },
                        onAdd = { _, _, _ -> },
                        onEdit = { _, _ -> },
                        onDelete = { deletedIds += it },
                        onSetEnabled = { instanceIds, enabled ->
                            if (enabled) {
                                enabledIds += instanceIds
                            } else {
                                disabledIds += instanceIds
                            }
                        },
                        backgroundScope = scope,
                    )
                }
                val selectionState = rememberMediaSourceSelectionState()

                Box(Modifier.fillMaxSize()) {
                    SettingsTab {
                        MediaSourceGroup(groupState, editState, selectionState)
                    }
                    if (selectionState.inSelection) {
                        MediaSourceSelectionActions(
                            mediaSources = mediaSources,
                            selectionState = selectionState,
                            editState = editState,
                            windowInsets = WindowInsets(0.dp),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }

        onNodeWithTag(MediaSourceGroupTestTags.item(enabledSource.instanceId))
            .performTouchInput { longClick() }
        onNodeWithTag(MediaSourceSelectionToolbarTestTags.TOOLBAR).assertExists()

        onNodeWithTag(MediaSourceGroupTestTags.SELECT_ALL).performClick()
        onNodeWithTag(MediaSourceSelectionToolbarTestTags.ENABLE).performClick()
        runOnIdle {
            assertEquals(setOf(disabledSource.instanceId), enabledIds)
        }

        onNodeWithTag(MediaSourceSelectionToolbarTestTags.DISABLE).performClick()
        runOnIdle {
            assertEquals(setOf(enabledSource.instanceId), disabledIds)
        }

        onNodeWithTag(MediaSourceSelectionToolbarTestTags.DELETE).performClick()
        onNodeWithTag(MediaSourceSelectionToolbarTestTags.DELETE_CONFIRM).performClick()
        runOnIdle {
            assertEquals(mediaSources.map { it.instanceId }.toSet(), deletedIds)
        }
        onNodeWithTag(MediaSourceSelectionToolbarTestTags.TOOLBAR).assertDoesNotExist()
    }

    private fun createPresentation(instanceId: String, enabled: Boolean): MediaSourcePresentation {
        val source = TestHttpMediaSource(mediaSourceId = instanceId)
        val instance = createTestMediaSourceInstance(
            source = source,
            instanceId = instanceId,
            isEnabled = enabled,
        )
        return MediaSourcePresentation(
            instanceId = instanceId,
            isEnabled = enabled,
            mediaSourceId = source.mediaSourceId,
            factoryId = FactoryId(instanceId),
            info = MediaSourceInfo(
                displayName = if (enabled) "Enabled source" else "Disabled source",
                description = "Test data source",
            ),
            parameters = MediaSourceParameters.Empty,
            connectionTester = ConnectionTester(instanceId) { ConnectionTestResult.SUCCESS },
            instance = instance,
            ownerSubscriptionUrl = null,
        )
    }
}
