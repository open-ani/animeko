/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.domain.mediasource.subscription.SubscriptionMetadata
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.ConnectionTestResult
import me.him188.ani.app.ui.settings.framework.ConnectionTester
import me.him188.ani.app.ui.settings.framework.DefaultConnectionTesterRunner
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class MediaSourceSubscriptionUiTest {
    private val subscription = MediaSourceSubscription(
        subscriptionId = "subscription",
        url = "https://example.com/sources.json",
        metadata = SubscriptionMetadata(name = "在线源合集", description = "常用在线视频网站合集"),
    )

    @Test
    fun `custom group lists only media sources outside subscriptions`() = runAniComposeUiTest {
        val local = createPresentation("local", ownerSubscriptionId = null)
        val fromSubscription = createPresentation("from-subscription", ownerSubscriptionId = subscription.subscriptionId)

        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val groupState = remember {
                    MediaSourceGroupState(
                        mediaSourcesState = mutableStateOf(listOf(local, fromSubscription)),
                        availableMediaSourceTemplatesState = mutableStateOf(emptyList()),
                        onReorder = {},
                        backgroundScope = scope,
                    )
                }
                val editState = remember { createEditState(scope) }
                SettingsTab {
                    MediaSourceGroup(groupState, editState, rememberMediaSourceSelectionState(), backgroundColor = Color.White)
                }
            }
        }

        onNodeWithTag(MediaSourceGroupTestTags.item(local.instanceId)).assertExists()
        onNodeWithTag(MediaSourceGroupTestTags.item(fromSubscription.instanceId)).assertDoesNotExist()
    }

    @Test
    fun `subscription item shows metadata and opens subscription`() = runAniComposeUiTest {
        val legacySubscription = MediaSourceSubscription(
            subscriptionId = "legacy",
            url = "https://example.com/legacy.json",
        )
        val opened = mutableListOf<String>()

        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val state = remember {
                    createSubscriptionState(listOf(subscription, legacySubscription), scope)
                }
                SettingsTab {
                    MediaSourceSubscriptionGroup(
                        state,
                        mediaSourcesOfSubscription = { emptyList() },
                        onOpenSubscription = { opened += it },
                        backgroundColor = Color.White,
                    )
                }
            }
        }

        onNodeWithText("在线源合集").assertExists()
        onNodeWithText("常用在线视频网站合集").assertExists()
        // 没有订阅信息时显示订阅链接
        onNodeWithText("example.com/legacy.json").assertExists()

        onNodeWithTag(MediaSourceSubscriptionGroupTestTags.item(subscription.subscriptionId)).performClick()
        runOnIdle {
            assertEquals(listOf(subscription.subscriptionId), opened)
        }
    }

    @Test
    fun `subscription page updates only this subscription`() = runAniComposeUiTest {
        val mediaSource = createPresentation("source", ownerSubscriptionId = subscription.subscriptionId)
        val updated = mutableListOf<String>()

        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val state = remember {
                    createSubscriptionState(listOf(subscription), scope, onUpdate = { updated += it })
                }
                val editState = remember { createEditState(scope) }
                SettingsTab {
                    MediaSourceSubscriptionPage(
                        subscription,
                        listOf(mediaSource),
                        testers = DefaultConnectionTesterRunner(listOf(mediaSource.connectionTester), scope),
                        subscriptionState = state,
                        editState = editState,
                    )
                }
            }
        }

        onNodeWithTag(MediaSourceSubscriptionPageTestTags.item(mediaSource.instanceId)).assertExists()
        onNodeWithTag(MediaSourceSubscriptionPageTestTags.UPDATE).performClick()
        runOnIdle {
            assertEquals(listOf(subscription.subscriptionId), updated)
        }
    }

    private fun createSubscriptionState(
        subscriptions: List<MediaSourceSubscription>,
        scope: CoroutineScope,
        onUpdate: suspend (String) -> Unit = {},
    ) = MediaSourceSubscriptionGroupState(
        subscriptionsState = mutableStateOf(subscriptions),
        onUpdateAll = {},
        onUpdate = onUpdate,
        onAdd = {},
        onDelete = {},
        onExportToString = { "" },
        backgroundScope = scope,
    )

    private fun createEditState(scope: CoroutineScope) = EditMediaSourceState(
        getConfigFlow = { flowOf(MediaSourceConfig.Default) },
        onAdd = { _, _, _ -> },
        onEdit = { _, _ -> },
        onDelete = {},
        onSetEnabled = { _, _ -> },
        backgroundScope = scope,
    )

    private fun createPresentation(instanceId: String, ownerSubscriptionId: String?): MediaSourcePresentation {
        val source = TestHttpMediaSource(mediaSourceId = instanceId)
        return MediaSourcePresentation(
            instanceId = instanceId,
            isEnabled = true,
            mediaSourceId = source.mediaSourceId,
            factoryId = FactoryId(instanceId),
            info = MediaSourceInfo(displayName = instanceId),
            parameters = MediaSourceParameters.Empty,
            connectionTester = ConnectionTester(instanceId) { ConnectionTestResult.SUCCESS },
            instance = createTestMediaSourceInstance(source = source, instanceId = instanceId),
            ownerSubscriptionId = ownerSubscriptionId,
        )
    }
}
